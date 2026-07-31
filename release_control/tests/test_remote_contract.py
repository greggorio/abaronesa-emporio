from __future__ import annotations

import hashlib
import io
import json
import socket
import threading
import time
import zipfile
from collections.abc import Callable
from datetime import UTC, datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any

import httpx
import pytest
from cryptography.hazmat.primitives import serialization
from sqlalchemy import select
from sqlalchemy.orm import Session, sessionmaker

from emporio_release_control.artifacts import (
    canonical,
    digest,
    extract_zip,
    validate_candidate_bundle,
    validate_outcome_bundle,
    validate_release_bundle,
)
from emporio_release_control.errors import (
    PreDispatchFailure,
    RemoteHttpFailure,
    RemoteTransportFailure,
    RuntimeFailure,
)
from emporio_release_control.github import GitHubClient
from emporio_release_control.persistence import CandidateSnapshot, ReleaseSnapshot, SyncState
from emporio_release_control.sync import Synchronizer, parse_time, positive_id

ROOT = Path(__file__).resolve().parents[2]
REPOSITORY = "greggorio/abaronesa-emporio"
SHA = "1" * 40
CANDIDATE = f"candidate-{SHA}-200-1"


def zip_bytes(
    files: dict[str, bytes],
    mutate: Callable[[zipfile.ZipInfo], None] | None = None,
) -> bytes:
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in files.items():
            info = zipfile.ZipInfo(name)
            info.compress_type = zipfile.ZIP_DEFLATED
            if mutate:
                mutate(info)
            archive.writestr(info, data)
    return output.getvalue()


def candidate_bundle() -> tuple[bytes, str, dict[str, Any]]:
    manifest = json.loads(
        (ROOT / "ops/releases/examples/candidate-manifest.example.json").read_text()
    )
    data = canonical(manifest)
    metadata = canonical(
        {
            "schemaVersion": 1,
            "stage": "final",
            "candidateId": CANDIDATE,
            "repository": REPOSITORY,
            "commitSha": SHA,
            "workflowRunId": "200",
            "workflowAttempt": 1,
            "manifestSha256": digest(data),
        }
    )
    raw = zip_bytes(
        {
            "candidate.json": data,
            "candidate.json.sha256": (hashlib.sha256(data).hexdigest() + "\n").encode(),
            "metadata.json": metadata,
        }
    )
    return raw, digest(raw), manifest


def candidate_outcome_bundle(
    candidate_digest: str = "sha256:" + "a" * 64,
    status: str = "published",
    predecessor: str | None = None,
) -> tuple[bytes, str]:
    value = {
        "schemaVersion": 1,
        "status": status,
        "repository": REPOSITORY,
        "commitSha": SHA,
        "workflowRunId": "200",
        "workflowAttempt": 1,
        "candidateId": CANDIDATE,
        "candidateArtifactId": "301",
        "candidateArtifactDigest": candidate_digest.removeprefix("sha256:"),
        "predecessorCandidateId": (
            CANDIDATE if status == "already_published" else predecessor
        ),
    }
    data = canonical(value)
    raw = zip_bytes(
        {
            "outcome.json": data,
            "outcome.json.sha256": (hashlib.sha256(data).hexdigest() + "\n").encode(),
        }
    )
    return raw, digest(raw)


def release_files() -> tuple[dict[str, bytes], dict[str, Any]]:
    manifest = json.loads((ROOT / "ops/releases/examples/global-release.example.json").read_text())
    data = canonical(manifest)
    metadata = canonical(
        {
            "schemaVersion": 1,
            "stage": "final",
            "kind": "global-release",
            "release": manifest["release"],
            "repository": REPOSITORY,
            "sourceCommit": manifest["sourceCommit"],
            "publicationWorkflowRunId": manifest["publication"]["workflowRunId"],
            "publicationWorkflowAttempt": manifest["publication"]["workflowAttempt"],
            "manifestSha256": digest(data),
        }
    )
    return {
        "release.json": data,
        "release.json.sha256": (hashlib.sha256(data).hexdigest() + "\n").encode(),
        "metadata.json": metadata,
    }, manifest


def test_artifact_positive_bundles_and_low_level_identifiers() -> None:
    raw, raw_digest, manifest = candidate_bundle()
    evidence = validate_candidate_bundle(
        raw,
        artifact_id=301,
        artifact_digest=raw_digest,
        candidate_id=CANDIDATE,
        run_id=200,
        attempt=1,
        commit_sha=SHA,
    )
    assert evidence.manifest == manifest
    outcome_raw, outcome_digest = candidate_outcome_bundle()
    assert validate_outcome_bundle(outcome_raw, outcome_digest, run_id=200, attempt=1)[
        "status"
    ] == "published"
    files, release = release_files()
    assert validate_release_bundle(files) == release
    assert parse_time("2026-07-29T12:00:00Z", "TIME") == datetime(
        2026, 7, 29, 12, tzinfo=UTC
    )
    assert positive_id(1, "ID") == 1


@pytest.mark.parametrize(
    "mutator",
    [
        lambda files: files.__setitem__("candidate.json.sha256", b"0" * 65),
        lambda files: files.__setitem__("metadata.json", b"{}\n"),
        lambda files: files.__setitem__("extra", b"x"),
    ],
)
def test_candidate_bundle_rejects_sidecar_metadata_and_extra(
    mutator: Callable[[dict[str, bytes]], None],
) -> None:
    raw, raw_digest, _ = candidate_bundle()
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        files = {name: archive.read(name) for name in archive.namelist()}
    mutator(files)
    mutant = zip_bytes(files)
    with pytest.raises(RuntimeFailure):
        validate_candidate_bundle(
            mutant,
            artifact_id=301,
            artifact_digest=digest(mutant),
            candidate_id=CANDIDATE,
            run_id=200,
            attempt=1,
            commit_sha=SHA,
        )


@pytest.mark.parametrize("name", ["../escape", "/absolute"])
def test_zip_rejects_path_traversal(name: str) -> None:
    raw = zip_bytes({name: b"x"})
    with pytest.raises(RuntimeFailure, match="ZIP_INVALID"):
        extract_zip(raw, digest(raw), {name: 10}, "ZIP_INVALID")


def test_zip_rejects_symlink_digest_size_and_bomb() -> None:
    def symlink(info: zipfile.ZipInfo) -> None:
        info.external_attr = 0o120777 << 16

    raw = zip_bytes({"a": b"x"}, symlink)
    with pytest.raises(RuntimeFailure):
        extract_zip(raw, digest(raw), {"a": 10}, "ZIP_INVALID")
    with pytest.raises(RuntimeFailure):
        extract_zip(raw, "sha256:" + "0" * 64, {"a": 10}, "ZIP_INVALID")
    large = zip_bytes({"a": b"x" * 20000})
    with pytest.raises(RuntimeFailure):
        extract_zip(large, digest(large), {"a": 30000}, "ZIP_INVALID")


def github_client(
    private: object,
    handler: Callable[[httpx.Request], httpx.Response],
    now: Callable[[], float] = time.time,
) -> GitHubClient:
    key = private.private_bytes(  # type: ignore[attr-defined]
        serialization.Encoding.PEM,
        serialization.PrivateFormat.PKCS8,
        serialization.NoEncryption(),
    )
    return GitHubClient(
        app_id=1,
        installation_id=2,
        private_key=key,
        api_base="http://127.0.0.1",
        connect_timeout=1,
        read_timeout=1,
        max_pages=2,
        client=httpx.Client(transport=httpx.MockTransport(handler)),
        now=now,
    )


def test_github_app_token_cache_get_retry_pagination_and_dispatch(
    rsa_material: tuple[Path, object, object],
) -> None:
    _, private, _ = rsa_material
    calls: list[tuple[str, str]] = []
    get_attempt = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal get_attempt
        calls.append((request.method, request.url.path))
        if request.url.path == "/app/installations/2/access_tokens":
            return httpx.Response(
                201,
                json={"token": "opaque", "expires_at": "2099-01-01T00:00:00Z"},
            )
        if request.method == "POST":
            assert request.url.path.endswith("/publish-release.yml/dispatches")
            assert request.headers["authorization"] == "Bearer opaque"
            return httpx.Response(204)
        get_attempt += 1
        if get_attempt == 1:
            return httpx.Response(401, json={})
        page = int(request.url.params["page"])
        return httpx.Response(200, json={"items": [{}] if page == 1 else []})

    client = github_client(private, handler)
    assert client.list_pages(f"/repos/{REPOSITORY}/x", "items") == [{}]
    client.dispatch_publication(
        "pub_" + "a" * 32,
        {
            "candidateId": CANDIDATE,
            "versionBump": "PATCH",
            "description": "d",
            "changelog": "c",
        },
    )
    assert sum(path == "/app/installations/2/access_tokens" for _, path in calls) == 2
    assert calls[-1][0] == "POST"


def test_github_transport_rejects_endpoint_http_and_exhausted_pagination(
    rsa_material: tuple[Path, object, object],
) -> None:
    _, private, _ = rsa_material

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path.startswith("/app/"):
            return httpx.Response(
                201, json={"token": "opaque", "expires_at": "2099-01-01T00:00:00Z"}
            )
        return httpx.Response(403)

    client = github_client(private, handler)
    with pytest.raises(RuntimeFailure, match="GITHUB_ENDPOINT_INVALID"):
        client.get_json("/user")
    with pytest.raises(RemoteHttpFailure):
        client.get_json(f"/repos/{REPOSITORY}/x")

    def full(request: httpx.Request) -> httpx.Response:
        if request.url.path.startswith("/app/"):
            return httpx.Response(
                201, json={"token": "opaque", "expires_at": "2099-01-01T00:00:00Z"}
            )
        return httpx.Response(200, json=[{}] * 100)

    client = github_client(private, full)
    with pytest.raises(RuntimeFailure, match="GITHUB_PAGINATION_EXHAUSTED"):
        client.list_pages(f"/repos/{REPOSITORY}/x", None)


@pytest.mark.parametrize(
    ("response", "method", "expected"),
    [
        (httpx.Response(200, content=b"{"), "get_json", RuntimeFailure),
        (httpx.Response(200, json=[]), "get_json", RuntimeFailure),
        (httpx.Response(503), "get_bytes", RemoteHttpFailure),
        (httpx.Response(200, content=b"too-big"), "get_bytes", RuntimeFailure),
        (httpx.Response(422), "dispatch", RemoteHttpFailure),
    ],
)
def test_github_response_mutants(
    rsa_material: tuple[Path, object, object],
    response: httpx.Response,
    method: str,
    expected: type[Exception],
) -> None:
    _, private, _ = rsa_material

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path.startswith("/app/"):
            return httpx.Response(
                201, json={"token": "opaque", "expires_at": "2099-01-01T00:00:00Z"}
            )
        return response

    client = github_client(private, handler)
    with pytest.raises(expected):
        if method == "get_json":
            client.get_json(f"/repos/{REPOSITORY}/x")
        elif method == "get_bytes":
            client.get_bytes(
                f"/repos/{REPOSITORY}/x",
                limit=1 if response.status_code == 200 else 100,
            )
        else:
            client.dispatch_publication(
                "pub_" + "a" * 32,
                {
                    "candidateId": CANDIDATE,
                    "versionBump": "PATCH",
                    "description": "d",
                    "changelog": "c",
                },
            )


@pytest.mark.parametrize(
    ("phase", "expected"),
    [
        ("private-key", PreDispatchFailure),
        ("token-transport", PreDispatchFailure),
        ("token-shape", PreDispatchFailure),
        ("token-http", PreDispatchFailure),
        ("dispatch-transport", RemoteTransportFailure),
        ("dispatch-http", RemoteHttpFailure),
        ("dispatch-ok", None),
    ],
)
def test_dispatch_phase_is_causal_and_never_retried(
    rsa_material: tuple[Path, object, object],
    phase: str,
    expected: type[BaseException] | None,
) -> None:
    _, private, _ = rsa_material
    calls: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append(request.url.path)
        if request.url.path.startswith("/app/"):
            if phase == "token-transport":
                raise httpx.ConnectError("token failed", request=request)
            if phase == "token-shape":
                return httpx.Response(201, json={})
            if phase == "token-http":
                return httpx.Response(403)
            return httpx.Response(
                201, json={"token": "opaque", "expires_at": "2099-01-01T00:00:00Z"}
            )
        if phase == "dispatch-transport":
            raise httpx.ConnectError("dispatch failed", request=request)
        if phase == "dispatch-http":
            return httpx.Response(403)
        return httpx.Response(204)

    client = github_client(private, handler)
    if phase == "private-key":
        client.private_key = b"invalid"
    request = {
        "candidateId": CANDIDATE,
        "versionBump": "PATCH",
        "description": "d",
        "changelog": "c",
    }
    if expected is None:
        client.dispatch_publication("pub_" + "a" * 32, request)
    else:
        with pytest.raises(expected) as caught:
            client.dispatch_publication("pub_" + "a" * 32, request)
        if phase == "dispatch-transport":
            assert isinstance(caught.value, RemoteTransportFailure)
            assert caught.value.uncertain
    token_posts = sum(path.startswith("/app/") for path in calls)
    dispatch_posts = sum(path.endswith("/dispatches") for path in calls)
    assert token_posts == (0 if phase == "private-key" else 1)
    assert dispatch_posts == (
        1 if phase in {"dispatch-transport", "dispatch-http", "dispatch-ok"} else 0
    )


@pytest.mark.parametrize(
    "token_response",
    [
        httpx.Response(403),
        httpx.Response(201, json={}),
        httpx.Response(201, json={"token": "x", "expires_at": "invalid"}),
        httpx.Response(
            201, json={"token": "x", "expires_at": "2000-01-01T00:00:00Z"}
        ),
    ],
)
def test_github_app_token_fail_closed(
    rsa_material: tuple[Path, object, object], token_response: httpx.Response
) -> None:
    _, private, _ = rsa_material
    client = github_client(private, lambda _request: token_response)
    with pytest.raises(RuntimeFailure):
        client.get_json(f"/repos/{REPOSITORY}/x")


def test_github_binary_success_and_invalid_page_shape(
    rsa_material: tuple[Path, object, object],
) -> None:
    _, private, _ = rsa_material

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path.startswith("/app/"):
            return httpx.Response(
                201, json={"token": "opaque", "expires_at": "2099-01-01T00:00:00Z"}
            )
        if request.url.path.endswith("/binary"):
            return httpx.Response(200, content=b"safe")
        return httpx.Response(200, json={"items": "not-a-list"})

    client = github_client(private, handler)
    assert client.get_bytes(f"/repos/{REPOSITORY}/binary") == b"safe"
    with pytest.raises(RuntimeFailure, match="GITHUB_RESPONSE_INVALID"):
        client.list_pages(f"/repos/{REPOSITORY}/x", "items")
    with pytest.raises(RuntimeFailure, match="GITHUB_ENDPOINT_INVALID"):
        client.get_bytes("/outside")


def test_loopback_fake_records_exact_transport_and_no_retry(
    rsa_material: tuple[Path, object, object],
) -> None:
    _, private, _ = rsa_material
    requests: list[tuple[str, str, str, str]] = []

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, _format: str, *_args: object) -> None:
            return

        def reply(self, status: int, body: bytes = b"") -> None:
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_POST(self) -> None:  # noqa: N802
            requests.append(
                (
                    "POST",
                    self.path,
                    self.headers.get("Accept", ""),
                    self.headers.get("User-Agent", ""),
                )
            )
            if self.path == "/app/installations/2/access_tokens":
                self.reply(
                    201,
                    b'{"token":"opaque","expires_at":"2099-01-01T00:00:00Z"}',
                )
            else:
                self.reply(422, b"{}")

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        key = private.private_bytes(  # type: ignore[attr-defined]
            serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption(),
        )
        client = GitHubClient(
            app_id=1,
            installation_id=2,
            private_key=key,
            api_base=f"http://127.0.0.1:{server.server_port}",
            connect_timeout=1,
            read_timeout=1,
            max_pages=1,
        )
        with pytest.raises(RemoteHttpFailure):
            client.dispatch_publication(
                "pub_" + "a" * 32,
                {
                    "candidateId": CANDIDATE,
                    "versionBump": "PATCH",
                    "description": "d",
                    "changelog": "c",
                },
            )
        client.client.close()
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)
    assert [item[0] for item in requests] == ["POST", "POST"]
    assert requests[1][1].endswith("/publish-release.yml/dispatches")
    assert all(item[2] == "application/vnd.github+json" for item in requests)
    assert all(item[3] == "emporio-release-control/0.1" for item in requests)
    with socket.socket() as outbound:
        with pytest.raises(OSError, match="non-loopback"):
            outbound.connect(("192.0.2.1", 443))


class SyncGitHub:
    def __init__(self) -> None:
        self.candidate_zip, self.candidate_digest, _ = candidate_bundle()
        self.outcome_zip, self.outcome_digest = candidate_outcome_bundle(
            self.candidate_digest
        )
        self.release_data, self.release_manifest = release_files()

    @staticmethod
    def run() -> dict[str, Any]:
        return {
            "id": 200,
            "run_attempt": 1,
            "name": "Publish Candidate",
            "event": "workflow_run",
            "status": "completed",
            "conclusion": "success",
            "head_branch": "main",
            "head_sha": SHA,
            "created_at": "2026-07-29T12:00:00Z",
            "repository": {"full_name": REPOSITORY},
            "head_repository": {"full_name": REPOSITORY},
        }

    def artifact(self, name: str, artifact_id: int, artifact_digest: str) -> dict[str, Any]:
        return {
            "id": artifact_id,
            "name": name,
            "expired": False,
            "digest": artifact_digest,
            "workflow_run": {"id": 200, "head_sha": SHA},
            "url": f"https://api.github.com/repos/{REPOSITORY}/actions/artifacts/{artifact_id}",
            "archive_download_url": (
                f"https://api.github.com/repos/{REPOSITORY}/actions/artifacts/{artifact_id}/zip"
            ),
        }

    def list_pages(self, path: str, key: str | None) -> list[dict[str, Any]]:
        if "publish-candidate" in path:
            return [self.run()]
        if "/actions/runs/200/artifacts" in path:
            return [
                self.artifact("candidate-outcome", 300, self.outcome_digest),
                self.artifact("candidate-manifest", 301, self.candidate_digest),
            ]
        if path.endswith("/releases"):
            return [
                {
                    "id": 10,
                    "tag_name": "v0.0.1",
                    "name": "v0.0.1",
                    "draft": False,
                    "prerelease": False,
                    "url": f"https://api.github.com/repos/{REPOSITORY}/releases/10",
                    "assets": [
                        {
                            "id": 11 + index,
                            "name": name,
                            "state": "uploaded",
                            "size": len(self.release_data[name]),
                            "digest": digest(self.release_data[name]),
                            "content_type": (
                                "text/plain"
                                if name == "release.json.sha256"
                                else "application/json"
                            ),
                            "url": (
                                f"https://api.github.com/repos/{REPOSITORY}/releases/assets/"
                                f"{11 + index}"
                            ),
                        }
                        for index, name in enumerate(self.release_data)
                    ],
                }
            ]
        if "matching-refs" in path:
            return [
                {
                    "ref": "refs/tags/v0.0.1",
                    "url": (
                        f"https://api.github.com/repos/{REPOSITORY}/git/refs/tags/v0.0.1"
                    ),
                    "object": {
                        "type": "commit",
                        "sha": SHA,
                        "url": (
                            f"https://api.github.com/repos/{REPOSITORY}/git/commits/{SHA}"
                        ),
                    },
                }
            ]
        raise AssertionError(path)

    def get_bytes(self, path: str) -> bytes:
        if path.endswith("/300/zip"):
            return self.outcome_zip
        if path.endswith("/301/zip"):
            return self.candidate_zip
        asset_id = int(path.rsplit("/", 1)[1])
        return self.release_data[list(self.release_data)[asset_id - 11]]

    def get_json(self, path: str) -> dict[str, Any]:
        raise AssertionError(path)


def test_candidate_and_release_sync_green_and_drift(
    factory: sessionmaker[Session],
) -> None:
    github = SyncGitHub()
    sync = Synchronizer(factory, github)  # type: ignore[arg-type]
    sync.sync_candidates()
    sync.sync_releases()
    with factory() as session:
        assert session.get(CandidateSnapshot, CANDIDATE) is not None
        assert session.get(ReleaseSnapshot, "v0.0.1") is not None
        assert all(not item.drift for item in session.scalars(select(SyncState)))
    github.run = lambda: {**SyncGitHub.run(), "conclusion": "failure"}  # type: ignore[method-assign]
    with pytest.raises(RuntimeFailure):
        sync.sync_candidates()
    with factory() as session:
        state = session.get(SyncState, "candidates")
        assert state is not None and state.drift and state.error_code == "WORKFLOW_RUN_INVALID"


def test_inherited_candidate_artifact_is_bound_to_its_own_run(
    factory: sessionmaker[Session],
) -> None:
    github = SyncGitHub()
    github.outcome_zip, github.outcome_digest = candidate_outcome_bundle(
        github.candidate_digest, "already_published"
    )

    def get_json(path: str) -> dict[str, Any]:
        if path.endswith("/actions/artifacts/301"):
            return github.artifact("candidate-manifest", 301, github.candidate_digest)
        if path.endswith("/actions/runs/200"):
            return github.run()
        raise AssertionError(path)

    github.get_json = get_json  # type: ignore[method-assign]
    evidence, _ = Synchronizer(factory, github)._candidate_from_run(github.run())  # type: ignore[arg-type]
    assert evidence.artifact_id == 301


def test_published_candidate_rejects_duplicate_manifest(
    factory: sessionmaker[Session],
) -> None:
    github = SyncGitHub()
    original = github.list_pages

    def duplicate(path: str, key: str | None) -> list[dict[str, Any]]:
        values = original(path, key)
        if "/actions/runs/200/artifacts" in path:
            values.append(github.artifact("candidate-manifest", 302, github.candidate_digest))
        return values

    github.list_pages = duplicate  # type: ignore[method-assign]
    with pytest.raises(RuntimeFailure, match="CANDIDATE_ARTIFACT_INVALID"):
        Synchronizer(factory, github)._candidate_from_run(github.run())  # type: ignore[arg-type]


def test_published_candidate_binds_null_predecessor(
    factory: sessionmaker[Session],
) -> None:
    github = SyncGitHub()
    evidence, _ = Synchronizer(factory, github)._candidate_from_run(github.run())  # type: ignore[arg-type]
    assert evidence.manifest["predecessor"]["candidateId"] is None
    github.outcome_zip, github.outcome_digest = candidate_outcome_bundle(
        github.candidate_digest,
        predecessor="candidate-" + "2" * 40 + "-199-1",
    )
    with pytest.raises(RuntimeFailure, match="CANDIDATE_PREDECESSOR_INVALID"):
        Synchronizer(factory, github)._candidate_from_run(github.run())  # type: ignore[arg-type]


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("name", "Wrong"),
        ("event", "push"),
        ("status", "in_progress"),
        ("conclusion", "failure"),
        ("head_branch", "feature"),
        ("repository", {"full_name": "wrong/repo"}),
        ("head_repository", {"full_name": "wrong/repo"}),
        ("run_attempt", 0),
        ("head_sha", "2" * 40),
    ],
)
def test_inherited_candidate_requires_fully_valid_owner_run(
    factory: sessionmaker[Session], field: str, value: object
) -> None:
    github = SyncGitHub()
    github.outcome_zip, github.outcome_digest = candidate_outcome_bundle(
        github.candidate_digest, "already_published"
    )

    def get_json(path: str) -> dict[str, Any]:
        if path.endswith("/actions/artifacts/301"):
            return github.artifact("candidate-manifest", 301, github.candidate_digest)
        if path.endswith("/actions/runs/200"):
            return {**github.run(), field: value}
        raise AssertionError(path)

    github.get_json = get_json  # type: ignore[method-assign]
    with pytest.raises(RuntimeFailure):
        Synchronizer(factory, github)._candidate_from_run(github.run())  # type: ignore[arg-type]


@pytest.mark.parametrize(
    ("mutation", "expected"),
    [
        ("mime", "RELEASE_ASSETS_INVALID"),
        ("oversize", "RELEASE_ASSETS_INVALID"),
        ("bool-size", "RELEASE_ASSETS_INVALID"),
        ("duplicate-id", "RELEASE_ASSETS_INVALID"),
    ],
)
def test_release_assets_fail_before_any_download(
    factory: sessionmaker[Session], mutation: str, expected: str
) -> None:
    github = SyncGitHub()
    original_list = github.list_pages
    downloads: list[str] = []

    def mutated(path: str, key: str | None) -> list[dict[str, Any]]:
        values = original_list(path, key)
        if path.endswith("/releases"):
            assets = values[0]["assets"]
            if mutation == "mime":
                assets[0]["content_type"] = "text/plain"
            elif mutation == "oversize":
                assets[0]["size"] = 2 * 1024 * 1024 + 1
            elif mutation == "bool-size":
                assets[0]["size"] = True
            else:
                assets[1]["id"] = assets[0]["id"]
                assets[1]["url"] = assets[0]["url"]
        return values

    original_bytes = github.get_bytes

    def recording(path: str) -> bytes:
        downloads.append(path)
        return original_bytes(path)

    github.list_pages = mutated  # type: ignore[method-assign]
    github.get_bytes = recording  # type: ignore[method-assign]
    with pytest.raises(RuntimeFailure, match=expected):
        Synchronizer(factory, github).sync_releases()  # type: ignore[arg-type]
    assert downloads == []


@pytest.mark.parametrize(
    "call",
    [
        lambda: parse_time("not-a-time", "TIME_INVALID"),
        lambda: positive_id(True, "ID_INVALID"),
        lambda: positive_id(0, "ID_INVALID"),
    ],
)
def test_sync_scalar_validators_fail_closed(call: Callable[[], object]) -> None:
    with pytest.raises(RuntimeFailure):
        call()


def test_release_sync_rejects_ref_and_asset_binding(
    factory: sessionmaker[Session],
) -> None:
    github = SyncGitHub()
    original = github.list_pages

    def bad_ref(path: str, key: str | None) -> list[dict[str, Any]]:
        values = original(path, key)
        if "matching-refs" in path:
            values[0]["url"] = "https://api.github.com/wrong"
        return values

    github.list_pages = bad_ref  # type: ignore[method-assign]
    with pytest.raises(RuntimeFailure, match="RELEASE_REF_INVALID"):
        Synchronizer(factory, github).sync_releases()  # type: ignore[arg-type]
