#!/usr/bin/env python3
"""Runner-side, fail-closed production deployment transport for S21."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import selectors
import shutil
import stat
import subprocess
import sys
import tarfile
import tempfile
from contextlib import contextmanager
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Any, Callable, Iterator, Protocol

import jsonschema

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/releases"))
sys.path.insert(0, str(ROOT / "tools/deploy"))
import deployment_plan  # noqa: E402
import global_release  # noqa: E402
import release_publication  # noqa: E402
import ssh_material  # noqa: E402

REPOSITORY = "greggorio/abaronesa-emporio"
OWNER = "greggorio"
REMOTE_USER = "deploy-emporio"
DEPLOY_ROOT = "/opt/sistemas/emporio"
REMOTE_HELPER = DEPLOY_ROOT + "/shared/control/ops/deploy/deployment-remote.py"
INCOMING_ROOT = DEPLOY_ROOT + "/shared/deploy/incoming"
SNAPSHOT_ROOT = DEPLOY_ROOT + "/shared/deploy/snapshots"
PROTOCOL = "emporio-deployment-transport"
OPERATION_RE = re.compile(r"[A-Za-z0-9_-]{20,128}")
SHA_RE = re.compile(r"[0-9a-f]{40}")
DIGEST_RE = re.compile(r"sha256:[0-9a-f]{64}")
ERROR_CODE_RE = re.compile(r"[A-Z][A-Z0-9_]{2,63}")
TIME_RE = re.compile(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z")
MAX_STDOUT = 65536
MAX_ARCHIVE = 16 * 1024 * 1024
BUNDLE_FILES = (
    "manifest.json",
    "compose.prod.yml",
    "release.env",
    "deployment-plan.json",
    "installed-state.next.json",
    "bundle.sha256",
)
REQUEST_SCHEMA = ROOT / "ops/deploy/schemas/deployment-request.schema.json"
SNAPSHOT_SCHEMA = ROOT / "ops/deploy/schemas/production-snapshot.schema.json"
OUTCOME_SCHEMA = ROOT / "ops/deploy/schemas/deployment-workflow-outcome.schema.json"
PUBLIC_ERRORS = frozenset(
    {
        "INVALID_DISPATCH", "ACTOR_NOT_ALLOWED", "RELEASE_NOT_FOUND",
        "RELEASE_NOT_ELIGIBLE", "RELEASE_ASSETS_INVALID",
        "REMOTE_CAPABILITY_MISMATCH", "REMOTE_SNAPSHOT_INVALID",
        "SNAPSHOT_CONFLICT", "BUNDLE_GENERATION_FAILED", "BUNDLE_INVALID",
        "BUNDLE_CONFLICT", "SSH_CONFIGURATION_INVALID", "SSH_UNAVAILABLE",
        "SSH_KEY_FORMAT_INVALID", "SSH_KEY_FINGERPRINT_MISMATCH",
        "SSH_KNOWN_HOSTS_INVALID", "SSH_CONNECTION_FAILED",
        "SSH_AUTHENTICATION_FAILED",
        "REMOTE_RESULT_UNAVAILABLE", "REMOTE_RESULT_INVALID",
        "REMOTE_CLEANUP_FAILED", "INTERNAL_ERROR",
    }
)


class DeploymentTransportError(ValueError):
    def __init__(self, code: str):
        if code not in PUBLIC_ERRORS:
            code = "INTERNAL_ERROR"
        super().__init__(code)
        self.code = code


def canonical(value: Any) -> bytes:
    return (
        json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        + "\n"
    ).encode("utf-8")


def digest(data: bytes) -> str:
    return "sha256:" + hashlib.sha256(data).hexdigest()


def _validate_schema(value: dict[str, Any], schema_path: Path, code: str) -> None:
    try:
        schema = json.loads(schema_path.read_text(encoding="utf-8"))
        jsonschema.Draft202012Validator(schema).validate(value)
    except (OSError, UnicodeError, json.JSONDecodeError, jsonschema.ValidationError,
            jsonschema.SchemaError) as exc:
        raise DeploymentTransportError(code) from exc


def _positive_decimal(value: Any, code: str = "INVALID_DISPATCH") -> int:
    if not isinstance(value, str) or re.fullmatch(r"[1-9][0-9]*", value) is None:
        raise DeploymentTransportError(code)
    return int(value)


def _load_canonical(path: Path, code: str, limit: int = 2 * 1024 * 1024) -> dict[str, Any]:
    path = Path(path)
    try:
        details = path.lstat()
        if stat.S_ISLNK(details.st_mode) or not stat.S_ISREG(details.st_mode) or details.st_size > limit:
            raise DeploymentTransportError(code)
        raw = path.read_bytes()
        value = json.loads(raw.decode("utf-8"))
    except DeploymentTransportError:
        raise
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise DeploymentTransportError(code) from exc
    if not isinstance(value, dict) or raw != canonical(value):
        raise DeploymentTransportError(code)
    return value


def _write_private(path: Path, data: bytes) -> None:
    descriptor = os.open(
        path,
        os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0),
        0o600,
    )
    try:
        with os.fdopen(descriptor, "wb") as stream:
            descriptor = -1
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.chmod(path, 0o600)
    finally:
        if descriptor >= 0:
            os.close(descriptor)


def validate_trust_environment(environment: dict[str, str]) -> dict[str, Any]:
    required = {
        "TRUSTED_REPOSITORY", "TRUSTED_OWNER", "TRUSTED_EVENT", "TRUSTED_REF",
        "TRUSTED_SHA", "TRUSTED_RUN_ID", "TRUSTED_RUN_ATTEMPT",
        "TRUSTED_ACTOR_ID", "TRUSTED_OPERATION_ID", "TRUSTED_RELEASE",
        "DEPLOYER_ACTOR_IDS",
    }
    if set(environment) != required:
        raise DeploymentTransportError("INVALID_DISPATCH")
    if (
        environment["TRUSTED_REPOSITORY"] != REPOSITORY
        or environment["TRUSTED_OWNER"] != OWNER
        or environment["TRUSTED_EVENT"] != "workflow_dispatch"
        or environment["TRUSTED_REF"] != "refs/heads/main"
        or SHA_RE.fullmatch(environment["TRUSTED_SHA"]) is None
        or OPERATION_RE.fullmatch(environment["TRUSTED_OPERATION_ID"]) is None
        or global_release.SEMVER_RE.fullmatch(environment["TRUSTED_RELEASE"]) is None
    ):
        raise DeploymentTransportError("INVALID_DISPATCH")
    run_id = _positive_decimal(environment["TRUSTED_RUN_ID"])
    attempt = _positive_decimal(environment["TRUSTED_RUN_ATTEMPT"])
    actor = _positive_decimal(environment["TRUSTED_ACTOR_ID"], "ACTOR_NOT_ALLOWED")
    allowlist_raw = environment["DEPLOYER_ACTOR_IDS"]
    if re.fullmatch(r"[1-9][0-9]*(?:,[1-9][0-9]*)*", allowlist_raw) is None:
        raise DeploymentTransportError("ACTOR_NOT_ALLOWED")
    allowlist = {int(item) for item in allowlist_raw.split(",")}
    if actor not in allowlist:
        raise DeploymentTransportError("ACTOR_NOT_ALLOWED")
    return {
        "schemaVersion": 1,
        "kind": "deployment-trust",
        "repository": REPOSITORY,
        "operationId": environment["TRUSTED_OPERATION_ID"],
        "targetRelease": environment["TRUSTED_RELEASE"],
        "controlSha": environment["TRUSTED_SHA"],
        "workflowRunId": run_id,
        "workflowRunAttempt": attempt,
        "requestedActorId": actor,
    }


def build_deployment_request(trust: dict[str, Any], run_started_at: str) -> dict[str, Any]:
    if set(trust) != {
        "schemaVersion", "kind", "repository", "operationId", "targetRelease",
        "controlSha", "workflowRunId", "workflowRunAttempt", "requestedActorId",
    } or trust.get("kind") != "deployment-trust" or trust.get("repository") != REPOSITORY:
        raise DeploymentTransportError("INVALID_DISPATCH")
    if not isinstance(run_started_at, str) or TIME_RE.fullmatch(run_started_at) is None:
        raise DeploymentTransportError("INVALID_DISPATCH")
    request = {
        "schemaVersion": 1,
        "kind": "deployment-request",
        "repository": REPOSITORY,
        "operationId": trust["operationId"],
        "targetRelease": trust["targetRelease"],
        "controlSha": trust["controlSha"],
        "workflowRunId": trust["workflowRunId"],
        "workflowRunAttempt": trust["workflowRunAttempt"],
        "requestedActorId": trust["requestedActorId"],
        "plannedAt": run_started_at,
    }
    _validate_schema(request, REQUEST_SCHEMA, "INVALID_DISPATCH")
    return request


def validate_release_artifacts(
    *,
    requested_release: str,
    release_record: dict[str, Any],
    tag_ref: dict[str, Any],
    assets: list[dict[str, Any]],
    payloads: dict[str, bytes],
) -> dict[str, Any]:
    try:
        validated_assets = release_publication.validate_release_assets(assets)
        if set(payloads) != set(release_publication.ASSETS):
            raise ValueError
        for asset in validated_assets:
            raw = payloads[asset["name"]]
            if not isinstance(raw, bytes) or len(raw) != asset["size"]:
                raise ValueError
        manifest_raw = payloads["release.json"]
        manifest = json.loads(manifest_raw.decode("utf-8"))
        metadata = json.loads(payloads["metadata.json"].decode("utf-8"))
        if (
            manifest_raw != global_release.canonical(manifest)
            or payloads["metadata.json"] != global_release.canonical(metadata)
            or payloads["release.json.sha256"]
            != (digest(manifest_raw).removeprefix("sha256:") + "\n").encode("ascii")
            or metadata != global_release.metadata_for(manifest, manifest_raw)
            or global_release.validate_release(manifest)
            or manifest.get("release") != requested_release
        ):
            raise ValueError
        source_sha = manifest["sourceCommit"]
        release_publication.validate_tag_ref(tag_ref, requested_release, source_sha)
        release_id = release_publication.validate_release_identity(release_record)
        body = release_record.get("body")
        if not isinstance(body, str):
            raise ValueError
        release_publication.validate_release_state(
            release_record, release_id=release_id, tag=requested_release,
            sha=source_sha, notes_bytes=body.encode("utf-8"), draft=False,
            assets_required=True,
        )
        if (
            release_record.get("tag_name") != requested_release
            or release_record.get("name") != requested_release
            or release_record.get("target_commitish") != source_sha
            or release_record.get("draft") is not False
            or release_record.get("prerelease") is not False
            or release_record.get("published_at") is None
            or release_record.get("deleted_at") not in (None,)
            or release_id < 1
        ):
            raise ValueError
        return manifest
    except Exception as exc:
        raise DeploymentTransportError("RELEASE_ASSETS_INVALID") from exc


class GithubPrepareTransport(Protocol):
    def api(self, method: str, endpoint: str, body: Path | None = None,
            expected_status: int = 200) -> Any: ...
    def bytes(self, endpoint: str, limit: int, *headers: str) -> bytes: ...


def _trust_file(path: Path) -> Path:
    path = Path(path)
    return path / "deployment-trust.json" if path.is_dir() else path


HANDOFF_FILES = frozenset(
    {
        "deployment-request.json",
        "release.json",
        "release.json.sha256",
        "metadata.json",
    }
)
HANDOFF_LIMITS = {
    "deployment-request.json": 2 * 1024 * 1024,
    "release.json": 2 * 1024 * 1024,
    "release.json.sha256": 128,
    "metadata.json": 16 * 1024,
}


def _same_file(left: os.stat_result, right: os.stat_result) -> bool:
    return (
        left.st_dev,
        left.st_ino,
        left.st_mode,
        left.st_nlink,
        left.st_uid,
        left.st_gid,
        left.st_size,
        left.st_mtime_ns,
        left.st_ctime_ns,
    ) == (
        right.st_dev,
        right.st_ino,
        right.st_mode,
        right.st_nlink,
        right.st_uid,
        right.st_gid,
        right.st_size,
        right.st_mtime_ns,
        right.st_ctime_ns,
    )


def _read_handoff_file(
    directory_fd: int, name: str, *, allowed_modes: frozenset[int]
) -> bytes:
    code = "INVALID_DISPATCH"
    try:
        before = os.stat(name, dir_fd=directory_fd, follow_symlinks=False)
        if (
            stat.S_ISLNK(before.st_mode)
            or not stat.S_ISREG(before.st_mode)
            or stat.S_IMODE(before.st_mode) not in allowed_modes
            or before.st_mode & 0o022
            or before.st_size > HANDOFF_LIMITS[name]
        ):
            raise DeploymentTransportError(code)
        descriptor = os.open(
            name,
            os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0),
            dir_fd=directory_fd,
        )
        try:
            opened = os.fstat(descriptor)
            if not _same_file(before, opened):
                raise DeploymentTransportError(code)
            chunks: list[bytes] = []
            total = 0
            while True:
                chunk = os.read(descriptor, min(64 * 1024, HANDOFF_LIMITS[name] + 1 - total))
                if not chunk:
                    break
                chunks.append(chunk)
                total += len(chunk)
                if total > HANDOFF_LIMITS[name]:
                    raise DeploymentTransportError(code)
            after_read = os.fstat(descriptor)
            if not _same_file(opened, after_read):
                raise DeploymentTransportError(code)
        finally:
            os.close(descriptor)
        after_path = os.stat(name, dir_fd=directory_fd, follow_symlinks=False)
        if not _same_file(before, after_path):
            raise DeploymentTransportError(code)
        return b"".join(chunks)
    except DeploymentTransportError:
        raise
    except OSError as exc:
        raise DeploymentTransportError(code) from exc


def _read_handoff_directory(
    path: Path,
    *,
    directory_modes: frozenset[int],
    file_modes: frozenset[int],
) -> dict[str, bytes]:
    path = Path(path)
    descriptor = -1
    try:
        before = path.lstat()
        if (
            stat.S_ISLNK(before.st_mode)
            or not stat.S_ISDIR(before.st_mode)
            or stat.S_IMODE(before.st_mode) not in directory_modes
            or before.st_mode & 0o022
        ):
            raise DeploymentTransportError("INVALID_DISPATCH")
        descriptor = os.open(
            path,
            os.O_RDONLY
            | getattr(os, "O_DIRECTORY", 0)
            | getattr(os, "O_NOFOLLOW", 0),
        )
        opened = os.fstat(descriptor)
        if not _same_file(before, opened):
            raise DeploymentTransportError("INVALID_DISPATCH")
        names = os.listdir(descriptor)
        if len(names) != len(HANDOFF_FILES) or set(names) != HANDOFF_FILES:
            raise DeploymentTransportError("INVALID_DISPATCH")
        payloads = {
            name: _read_handoff_file(descriptor, name, allowed_modes=file_modes)
            for name in sorted(HANDOFF_FILES)
        }
        if set(os.listdir(descriptor)) != HANDOFF_FILES:
            raise DeploymentTransportError("INVALID_DISPATCH")
        if not _same_file(opened, os.fstat(descriptor)):
            raise DeploymentTransportError("INVALID_DISPATCH")
        return payloads
    except DeploymentTransportError:
        raise
    except OSError as exc:
        raise DeploymentTransportError("INVALID_DISPATCH") from exc
    finally:
        if descriptor >= 0:
            os.close(descriptor)


def _canonical_from_bytes(raw: bytes, code: str) -> dict[str, Any]:
    try:
        value = json.loads(raw.decode("utf-8"))
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise DeploymentTransportError(code) from exc
    if not isinstance(value, dict) or raw != canonical(value):
        raise DeploymentTransportError(code)
    return value


def _validate_handoff_payloads(
    payloads: dict[str, bytes],
) -> tuple[dict[str, Any], dict[str, Any]]:
    if set(payloads) != HANDOFF_FILES:
        raise DeploymentTransportError("INVALID_DISPATCH")
    request = _canonical_from_bytes(
        payloads["deployment-request.json"], "INVALID_DISPATCH"
    )
    _validate_schema(request, REQUEST_SCHEMA, "INVALID_DISPATCH")
    release_raw = payloads["release.json"]
    release = _canonical_from_bytes(release_raw, "RELEASE_ASSETS_INVALID")
    if (
        request.get("repository") != REPOSITORY
        or request.get("operationId") is None
        or request.get("targetRelease") != release.get("release")
        or global_release.validate_release(release)
        or payloads["release.json.sha256"]
        != (digest(release_raw).removeprefix("sha256:") + "\n").encode("ascii")
    ):
        raise DeploymentTransportError("RELEASE_ASSETS_INVALID")
    metadata = _canonical_from_bytes(
        payloads["metadata.json"], "RELEASE_ASSETS_INVALID"
    )
    if metadata != global_release.metadata_for(release, release_raw):
        raise DeploymentTransportError("RELEASE_ASSETS_INVALID")
    return request, release


def _read_and_validate_handoff(
    path: Path,
    *,
    directory_modes: frozenset[int],
    file_modes: frozenset[int],
) -> tuple[dict[str, Any], dict[str, Any], dict[str, bytes]]:
    payloads = _read_handoff_directory(
        path, directory_modes=directory_modes, file_modes=file_modes
    )
    request, release = _validate_handoff_payloads(payloads)
    return request, release, payloads


def validate_handoff(path: Path) -> tuple[dict[str, Any], dict[str, Any]]:
    request, release, _payloads = _read_and_validate_handoff(
        path,
        directory_modes=frozenset({0o700, 0o755}),
        file_modes=frozenset({0o600, 0o644}),
    )
    return request, release


def _fsync_private_directory(path: Path) -> None:
    descriptor = -1
    try:
        descriptor = os.open(
            path,
            os.O_RDONLY
            | getattr(os, "O_DIRECTORY", 0)
            | getattr(os, "O_NOFOLLOW", 0),
        )
        os.fsync(descriptor)
    except OSError as exc:
        raise DeploymentTransportError("INVALID_DISPATCH") from exc
    finally:
        if descriptor >= 0:
            os.close(descriptor)


@contextmanager
def private_handoff(ingress: Path, parent: Path) -> Iterator[Path]:
    """Validate an artifact ingress and expose only a private, stable copy."""
    _request, _release, ingress_payloads = _read_and_validate_handoff(
        ingress,
        directory_modes=frozenset({0o700, 0o755}),
        file_modes=frozenset({0o600, 0o644}),
    )
    private = Path(tempfile.mkdtemp(prefix=".deployment-handoff-", dir=Path(parent)))
    try:
        if stat.S_IMODE(private.lstat().st_mode) != 0o700 or private.is_symlink():
            raise DeploymentTransportError("INVALID_DISPATCH")
        for name in sorted(HANDOFF_FILES):
            _write_private(private / name, ingress_payloads[name])
        _fsync_private_directory(private)
        _private_request, _private_release, private_payloads = _read_and_validate_handoff(
            private,
            directory_modes=frozenset({0o700}),
            file_modes=frozenset({0o600}),
        )
        if private_payloads != ingress_payloads:
            raise DeploymentTransportError("INVALID_DISPATCH")
        yield private
    finally:
        if private.exists() or private.is_symlink():
            shutil.rmtree(private)


def prepare_handoff(
    *, trust_path: Path, output: Path, remote: GithubPrepareTransport,
) -> dict[str, Any]:
    trust = _load_canonical(_trust_file(trust_path), "INVALID_DISPATCH")
    run_id = trust.get("workflowRunId")
    release_name = trust.get("targetRelease")
    run_title = f"deploy-production-{trust.get('operationId')}"
    try:
        run = remote.api(
            "GET", f"/repos/{REPOSITORY}/actions/runs/{run_id}", expected_status=200
        )
        if (
            not isinstance(run, dict) or run.get("id") != run_id
            or run.get("run_attempt") != trust.get("workflowRunAttempt")
            or run.get("event") != "workflow_dispatch"
            or run.get("head_branch") != "main"
            or run.get("head_sha") != trust.get("controlSha")
            or run.get("name") != run_title
            or run.get("display_title") != run_title
            or run.get("path") != ".github/workflows/deploy-production.yml"
            or run.get("html_url")
            != f"https://github.com/{REPOSITORY}/actions/runs/{run_id}"
            or run.get("repository", {}).get("full_name") != REPOSITORY
            or run.get("head_repository", {}).get("full_name") != REPOSITORY
            or run.get("actor", {}).get("id") != trust.get("requestedActorId")
        ):
            raise DeploymentTransportError("INVALID_DISPATCH")
        request = build_deployment_request(trust, run.get("run_started_at"))
        release_record = remote.api(
            "GET", f"/repos/{REPOSITORY}/releases/tags/{release_name}",
            expected_status=200,
        )
        tag_ref = remote.api(
            "GET", f"/repos/{REPOSITORY}/git/ref/tags/{release_name}",
            expected_status=200,
        )
        assets = release_publication.validate_release_assets(release_record.get("assets"))
        payloads: dict[str, bytes] = {}
        for asset in assets:
            endpoint = asset["url"].removeprefix("https://api.github.com")
            limit = release_publication.ASSETS[asset["name"]][0]
            payloads[asset["name"]] = remote.bytes(
                endpoint, limit, "Accept: application/octet-stream"
            )
        validate_release_artifacts(
            requested_release=release_name, release_record=release_record,
            tag_ref=tag_ref, assets=assets, payloads=payloads,
        )
    except DeploymentTransportError:
        raise
    except release_publication.RemoteHttpError as exc:
        code = "RELEASE_NOT_FOUND" if exc.status == 404 else "RELEASE_ASSETS_INVALID"
        raise DeploymentTransportError(code) from exc
    except Exception as exc:
        raise DeploymentTransportError("RELEASE_ASSETS_INVALID") from exc
    output = Path(output)
    output.mkdir(mode=0o700, parents=False, exist_ok=False)
    _write_private(output / "deployment-request.json", canonical(request))
    for name in release_publication.ASSETS:
        _write_private(output / name, payloads[name])
    validate_handoff(output)
    return request


def validate_snapshot(directory: Path, request: dict[str, Any]) -> dict[str, Any]:
    directory = Path(directory)
    try:
        entries = list(directory.iterdir())
    except OSError as exc:
        raise DeploymentTransportError("REMOTE_SNAPSHOT_INVALID") from exc
    if any(item.is_symlink() or not item.is_file() for item in entries):
        raise DeploymentTransportError("REMOTE_SNAPSHOT_INVALID")
    snapshot = _load_canonical(directory / "production-snapshot.json", "REMOTE_SNAPSHOT_INVALID")
    _validate_schema(snapshot, SNAPSHOT_SCHEMA, "REMOTE_SNAPSHOT_INVALID")
    if (
        snapshot.get("operationId") != request.get("operationId")
        or snapshot.get("targetRelease") != request.get("targetRelease")
        or snapshot.get("mode") not in {"FIRST_INSTALL", "UPDATE"}
    ):
        raise DeploymentTransportError("REMOTE_SNAPSHOT_INVALID")
    if snapshot["mode"] == "FIRST_INSTALL":
        if {item.name for item in entries} != {"production-snapshot.json"} or any(
            snapshot.get(key) is not None
            for key in ("currentRelease", "installedStateSha256", "currentManifestSha256")
        ):
            raise DeploymentTransportError("REMOTE_SNAPSHOT_INVALID")
    else:
        if {item.name for item in entries} != {
            "production-snapshot.json", "installed-state.json", "current-manifest.json"
        }:
            raise DeploymentTransportError("REMOTE_SNAPSHOT_INVALID")
        state_raw = (directory / "installed-state.json").read_bytes()
        manifest_raw = (directory / "current-manifest.json").read_bytes()
        try:
            state = deployment_plan.load_current(directory / "installed-state.json")
            manifest = deployment_plan.load_target(directory / "current-manifest.json")
            if (
                state_raw != deployment_plan._json_file_bytes(state)
                or manifest_raw != deployment_plan._json_file_bytes(manifest)
            ):
                raise ValueError
            deployment_plan._validate_current_pair(state, manifest)
        except Exception as exc:
            raise DeploymentTransportError("REMOTE_SNAPSHOT_INVALID") from exc
        if (
            snapshot.get("currentRelease") != state["release"]
            or snapshot.get("installedStateSha256") != digest(state_raw)
            or snapshot.get("currentManifestSha256") != digest(manifest_raw)
        ):
            raise DeploymentTransportError("REMOTE_SNAPSHOT_INVALID")
    return snapshot


def create_bundle_archive(bundle: Path, archive: Path) -> str:
    bundle = Path(bundle)
    archive = Path(archive)
    try:
        deployment_plan.validate_bundle(bundle)
        if archive.exists() or archive.is_symlink():
            raise DeploymentTransportError("BUNDLE_CONFLICT")
        descriptor = os.open(archive, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, "wb") as raw:
            with tarfile.open(fileobj=raw, mode="w", format=tarfile.USTAR_FORMAT) as tar:
                for name in BUNDLE_FILES:
                    path = bundle / name
                    details = path.lstat()
                    if stat.S_ISLNK(details.st_mode) or not stat.S_ISREG(details.st_mode):
                        raise DeploymentTransportError("BUNDLE_INVALID")
                    info = tarfile.TarInfo(name)
                    info.size = details.st_size
                    info.mode = 0o600
                    info.uid = info.gid = 0
                    info.uname = info.gname = ""
                    info.mtime = 0
                    with path.open("rb") as stream:
                        tar.addfile(info, stream)
            raw.flush()
            os.fsync(raw.fileno())
        os.chmod(archive, 0o600)
        if archive.stat().st_size > MAX_ARCHIVE:
            archive.unlink()
            raise DeploymentTransportError("BUNDLE_INVALID")
        validate_bundle_archive(archive)
        deployment_plan.validate_bundle(bundle)
        hasher = hashlib.sha256()
        with archive.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                hasher.update(chunk)
        return "sha256:" + hasher.hexdigest()
    except DeploymentTransportError:
        raise
    except Exception as exc:
        if archive.exists() and archive.is_file():
            archive.unlink()
        raise DeploymentTransportError("BUNDLE_INVALID") from exc


def validate_bundle_archive(archive: Path) -> None:
    archive = Path(archive)
    try:
        if archive.is_symlink() or not archive.is_file() or archive.stat().st_size > MAX_ARCHIVE:
            raise ValueError
        with tarfile.open(archive, "r:") as tar:
            members = tar.getmembers()
            names = [member.name for member in members]
            if names != list(BUNDLE_FILES) or len(names) != len(set(names)):
                raise ValueError
            for member in members:
                name = PurePosixPath(member.name)
                if (
                    name.is_absolute() or ".." in name.parts or len(name.parts) != 1
                    or not member.isfile() or member.pax_headers or member.mode != 0o600
                ):
                    raise ValueError
    except (OSError, tarfile.TarError, ValueError) as exc:
        raise DeploymentTransportError("BUNDLE_INVALID") from exc


@dataclass(frozen=True)
class ProcessResult:
    return_code: int
    stdout: bytes
    stderr: bytes = b""


class ProcessRunner(Protocol):
    def run(self, argv: tuple[str, ...], *, timeout_seconds: int) -> ProcessResult: ...


class SubprocessRunner:
    def run(self, argv: tuple[str, ...], *, timeout_seconds: int) -> ProcessResult:
        if not isinstance(argv, tuple) or not argv or timeout_seconds < 1:
            raise DeploymentTransportError("SSH_CONFIGURATION_INVALID")
        process = subprocess.Popen(
            argv, shell=False, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, close_fds=True,
            env={"PATH": "/usr/bin:/bin", "LANG": "C.UTF-8", "LC_ALL": "C.UTF-8"},
        )
        output = bytearray()
        diagnostic = bytearray()
        selector = selectors.DefaultSelector()
        try:
            assert process.stdout is not None
            assert process.stderr is not None
            selector.register(process.stdout, selectors.EVENT_READ, "stdout")
            selector.register(process.stderr, selectors.EVENT_READ, "stderr")
            import time
            deadline = time.monotonic() + timeout_seconds
            while selector.get_map():
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    process.kill(); process.wait()
                    raise DeploymentTransportError("SSH_UNAVAILABLE")
                events = selector.select(min(remaining, 1.0))
                for key, _ in events:
                    chunk = os.read(key.fileobj.fileno(), 8192)
                    if not chunk:
                        selector.unregister(key.fileobj)
                    else:
                        target = output if key.data == "stdout" else diagnostic
                        target.extend(chunk)
                        if len(target) > MAX_STDOUT:
                            process.kill(); process.wait()
                            raise DeploymentTransportError("SSH_UNAVAILABLE")
            return_code = process.wait(timeout=max(0.1, deadline - time.monotonic()))
            return ProcessResult(return_code, bytes(output), bytes(diagnostic))
        except subprocess.TimeoutExpired as exc:
            process.kill(); process.wait()
            raise DeploymentTransportError("SSH_UNAVAILABLE") from exc
        finally:
            selector.close()
            if process.stdout is not None:
                process.stdout.close()
            if process.stderr is not None:
                process.stderr.close()


SshConfiguration = ssh_material.SshConfiguration


def resolve_openssh(name: str) -> Path:
    try:
        return ssh_material.resolve_openssh(name)
    except ssh_material.SshMaterialError as exc:
        raise DeploymentTransportError(exc.code) from exc


def materialize_ssh_configuration(**values: Any) -> SshConfiguration:
    try:
        return ssh_material.materialize_ssh_configuration(**values)
    except ssh_material.SshMaterialError as exc:
        raise DeploymentTransportError(exc.code) from exc


class OpenSshTransport:
    def __init__(self, configuration: SshConfiguration, runner: ProcessRunner):
        self.configuration = configuration
        self.runner = runner

    def _remote(self, tokens: tuple[str, ...], timeout: int = 60) -> ProcessResult:
        argv = (
            os.fspath(self.configuration.ssh), "-F", os.fspath(self.configuration.config),
            self.configuration.destination, REMOTE_HELPER, *tokens,
        )
        return self.runner.run(argv, timeout_seconds=timeout)

    def capabilities(self, control_sha: str) -> None:
        """Refuse to proceed unless the installed control root is that commit.

        This runs before snapshot, upload and every other remote mutation, so a
        control root that is missing its manifest, tampered with, or simply a
        different commit stops the deployment while nothing has changed yet. The
        expected sha comes from the trusted request, never from the VPS.
        """
        if not isinstance(control_sha, str) or re.fullmatch(r"[0-9a-f]{40}", control_sha) is None:
            raise DeploymentTransportError("REMOTE_CAPABILITY_MISMATCH")
        result = self._remote(("capabilities",))
        if result.return_code != 0:
            raise DeploymentTransportError(
                ssh_material.classify_ssh_failure(result.stderr, stage="capabilities")
            )
        value = _parse_single_json(result, "REMOTE_CAPABILITY_MISMATCH")
        if value != {
            "controlSha": control_sha,
            "deployRoot": DEPLOY_ROOT, "protocol": PROTOCOL,
            "schemaVersion": 1, "user": REMOTE_USER,
        }:
            raise DeploymentTransportError("REMOTE_CAPABILITY_MISMATCH")

    def snapshot(self, operation: str, release: str) -> dict[str, Any]:
        result = self._remote(("snapshot", "--operation-id", operation, "--release", release))
        return _parse_single_json(result, "REMOTE_SNAPSHOT_INVALID")

    def download_snapshot(
        self, operation: str, mode: str, destination: Path
    ) -> None:
        names = ["production-snapshot.json"]
        if mode == "UPDATE":
            names.extend(("installed-state.json", "current-manifest.json"))
        elif mode != "FIRST_INSTALL":
            raise DeploymentTransportError("REMOTE_SNAPSHOT_INVALID")
        destination = Path(destination)
        destination.mkdir(mode=0o700, parents=False, exist_ok=False)
        for name in names:
            remote = (
                f"{self.configuration.destination}:{SNAPSHOT_ROOT}/"
                f"{operation}/{name}"
            )
            local = destination / name
            result = self.runner.run(
                (
                    os.fspath(self.configuration.scp), "-F",
                    os.fspath(self.configuration.config), remote, os.fspath(local),
                ),
                timeout_seconds=120,
            )
            if result.return_code != 0 or result.stdout or not local.is_file():
                raise DeploymentTransportError("REMOTE_SNAPSHOT_INVALID")
            os.chmod(local, 0o600)

    def upload(self, archive: Path, operation: str) -> None:
        remote = f"{self.configuration.destination}:{INCOMING_ROOT}/{operation}.tar.part"
        result = self.runner.run(
            (os.fspath(self.configuration.scp), "-F", os.fspath(self.configuration.config),
             os.fspath(archive), remote), timeout_seconds=300,
        )
        if result.return_code != 0 or result.stdout:
            raise DeploymentTransportError("SSH_UNAVAILABLE")

    def install(self, operation: str, release: str, archive_sha256: str) -> None:
        result = self._remote((
            "install", "--operation-id", operation, "--release", release,
            "--archive-sha256", archive_sha256,
        ), 300)
        value = _parse_single_json(result, "BUNDLE_CONFLICT")
        if value.get("operationId") != operation or value.get("release") != release or value.get("installed") is not True:
            raise DeploymentTransportError("BUNDLE_CONFLICT")

    def execute(self, operation: str, release: str) -> dict[str, Any]:
        result = self._remote(("execute", "--operation-id", operation, "--release", release), 2700)
        value = _parse_single_json(result, "REMOTE_RESULT_INVALID", allowed=(0, 20, 21))
        expected_state = {0: "SUCCEEDED", 20: "ROLLED_BACK", 21: "FAILED"}[result.return_code]
        if (
            set(value) != {"databaseRestoreRequired", "errorCode", "operationId", "state"}
            or value.get("operationId") != operation or value.get("state") != expected_state
            or not isinstance(value.get("databaseRestoreRequired"), bool)
            or (expected_state == "SUCCEEDED" and value.get("errorCode") is not None)
            or (
                expected_state != "SUCCEEDED"
                and (
                    not isinstance(value.get("errorCode"), str)
                    or ERROR_CODE_RE.fullmatch(value["errorCode"]) is None
                )
            )
        ):
            raise DeploymentTransportError("REMOTE_RESULT_INVALID")
        return value

    def cleanup(self, operation: str) -> None:
        result = self._remote(("cleanup", "--operation-id", operation))
        value = _parse_single_json(result, "REMOTE_CLEANUP_FAILED")
        if value != {"cleaned": True, "operationId": operation}:
            raise DeploymentTransportError("REMOTE_CLEANUP_FAILED")


def _parse_single_json(result: ProcessResult, code: str, allowed: tuple[int, ...] = (0,)) -> dict[str, Any]:
    if not isinstance(result, ProcessResult) or result.return_code not in allowed or len(result.stdout) > MAX_STDOUT:
        raise DeploymentTransportError(code)
    try:
        value = json.loads(result.stdout.decode("utf-8"))
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise DeploymentTransportError(code) from exc
    if not isinstance(value, dict) or result.stdout != canonical(value):
        raise DeploymentTransportError(code)
    return value


def build_outcome(
    request: dict[str, Any], *, result: dict[str, Any] | None,
    transport_status: str, error_code: str | None,
) -> dict[str, Any]:
    base = {
        "schemaVersion": 1, "kind": "deployment-workflow-outcome",
        "operationId": request["operationId"], "targetRelease": request["targetRelease"],
        "workflowRunId": request["workflowRunId"],
        "workflowRunAttempt": request["workflowRunAttempt"],
        "controlSha": request["controlSha"],
    }
    if transport_status == "INDETERMINATE":
        if error_code not in PUBLIC_ERRORS or error_code is None:
            raise DeploymentTransportError("INTERNAL_ERROR")
        base.update(transportStatus="INDETERMINATE", deploymentState=None,
                    databaseRestoreRequired=None, errorCode=error_code)
    elif transport_status == "CONFIRMED" and result is not None:
        base.update(
            transportStatus="CONFIRMED", deploymentState=result["state"],
            databaseRestoreRequired=result["databaseRestoreRequired"],
            errorCode=error_code if error_code is not None else result.get("errorCode"),
        )
    else:
        raise DeploymentTransportError("INTERNAL_ERROR")
    _validate_schema(base, OUTCOME_SCHEMA, "INTERNAL_ERROR")
    return base


def execute_remote(
    *, request: dict[str, Any], transport: OpenSshTransport, archive: Path,
    archive_sha256: str,
) -> dict[str, Any]:
    operation = request["operationId"]
    release = request["targetRelease"]
    mutation_possible = False
    execute_started = False
    result: dict[str, Any] | None = None
    error: str | None = None
    outcome_status = "CONFIRMED"
    try:
        transport.capabilities(request["controlSha"])
        transport.upload(archive, operation)
        mutation_possible = True
        transport.install(operation, release, archive_sha256)
        execute_started = True
        result = transport.execute(operation, release)
    except DeploymentTransportError as exc:
        error = exc.code
        if execute_started or mutation_possible:
            outcome_status = "INDETERMINATE"
            error = "REMOTE_RESULT_UNAVAILABLE" if execute_started else error
        else:
            result = {
                "state": "FAILED", "databaseRestoreRequired": False,
                "errorCode": error,
            }
    finally:
        if mutation_possible:
            try:
                transport.cleanup(operation)
            except DeploymentTransportError:
                error = "REMOTE_CLEANUP_FAILED"
    if error == "REMOTE_CLEANUP_FAILED" and result is not None:
        return build_outcome(request, result=result, transport_status="CONFIRMED", error_code=error)
    if outcome_status == "INDETERMINATE":
        return build_outcome(
            request, result=None, transport_status="INDETERMINATE",
            error_code=error or "REMOTE_RESULT_UNAVAILABLE",
        )
    return build_outcome(request, result=result, transport_status="CONFIRMED", error_code=None)


def deploy_handoff(
    *, handoff: Path, output: Path, configuration: SshConfiguration,
    runner: ProcessRunner,
) -> dict[str, Any]:
    request, _release = validate_handoff(handoff)
    output = Path(output)
    output.mkdir(mode=0o700, parents=False, exist_ok=False)
    client = OpenSshTransport(configuration, runner)
    operation = request["operationId"]
    target = request["targetRelease"]
    mutation_possible = False
    snapshot_validated = False
    result: dict[str, Any] | None = None
    error: str | None = None
    with tempfile.TemporaryDirectory(prefix=".deployment-work-", dir=output.parent) as temporary:
        work = Path(temporary)
        try:
            client.capabilities(request["controlSha"])
            snapshot_summary = client.snapshot(operation, target)
            snapshot_dir = work / "snapshot"
            client.download_snapshot(operation, snapshot_summary.get("mode"), snapshot_dir)
            snapshot = validate_snapshot(snapshot_dir, request)
            if snapshot != snapshot_summary:
                raise DeploymentTransportError("REMOTE_SNAPSHOT_INVALID")
            snapshot_validated = True
            bundle = work / "bundle"
            current = None
            current_manifest = None
            if snapshot["mode"] == "UPDATE":
                current = snapshot_dir / "installed-state.json"
                current_manifest = snapshot_dir / "current-manifest.json"
            try:
                deployment_plan.generate_bundle(
                    target_path=Path(handoff) / "release.json",
                    current_path=current,
                    current_manifest_path=current_manifest,
                    compose_path=ROOT / "ops/compose/compose.prod.yml",
                    planned_at=request["plannedAt"],
                    output_path=bundle,
                )
                deployment_plan.validate_bundle(bundle)
            except Exception as exc:
                raise DeploymentTransportError("BUNDLE_GENERATION_FAILED") from exc
            archive = work / f"{operation}.tar"
            archive_sha = create_bundle_archive(bundle, archive)
            client.upload(archive, operation)
            mutation_possible = True
            client.install(operation, target, archive_sha)
            try:
                result = client.execute(operation, target)
            except DeploymentTransportError as exc:
                raise DeploymentTransportError("REMOTE_RESULT_UNAVAILABLE") from exc
        except DeploymentTransportError as exc:
            error = exc.code
        finally:
            if snapshot_validated:
                try:
                    client.cleanup(operation)
                except DeploymentTransportError:
                    error = "REMOTE_CLEANUP_FAILED"
    if result is not None and error is None:
        outcome = build_outcome(
            request, result=result, transport_status="CONFIRMED", error_code=None
        )
    elif result is not None and error == "REMOTE_CLEANUP_FAILED":
        outcome = build_outcome(
            request, result=result, transport_status="CONFIRMED", error_code=error
        )
    elif mutation_possible:
        outcome = build_outcome(
            request, result=None, transport_status="INDETERMINATE",
            error_code=error or "REMOTE_RESULT_UNAVAILABLE",
        )
    else:
        outcome = build_outcome(
            request,
            result={
                "state": "FAILED", "databaseRestoreRequired": False,
                "errorCode": error or "INTERNAL_ERROR",
            },
            transport_status="CONFIRMED", error_code=error or "INTERNAL_ERROR",
        )
    _write_private(output / "deployment-result.json", canonical(outcome))
    return outcome


def _write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    _write_private(path, canonical(value))


def _persist_command_outcome(output: Path, filename: str, value: dict[str, Any]) -> None:
    """Persist a reconciled command artifact before its terminal exit is applied."""
    output = Path(output)
    output.mkdir(mode=0o700, parents=False, exist_ok=False)
    _write_private(output / filename, canonical(value))


def _clean_success(value: dict[str, Any]) -> bool:
    return (
        value.get("transportStatus") == "CONFIRMED"
        and value.get("deploymentState") == "SUCCEEDED"
        and value.get("errorCode") is None
    )


def _result_file(argument: Path | None) -> tuple[Path | None, bool]:
    """Resolve the fixed result filename and distinguish absence from invalidity."""
    if argument is None:
        return None, True
    argument = Path(argument)
    try:
        details = argument.lstat()
    except FileNotFoundError:
        return None, True
    except OSError:
        return argument, False
    if stat.S_ISLNK(details.st_mode):
        return argument, False
    if stat.S_ISDIR(details.st_mode):
        candidate = argument / "deployment-result.json"
        try:
            candidate.lstat()
        except FileNotFoundError:
            return None, True
        except OSError:
            return candidate, False
        return candidate, False
    return argument, False


def reconcile_workflow_outcome(
    request: dict[str, Any], result_argument: Path | None,
) -> dict[str, Any]:
    result_file, absent = _result_file(result_argument)
    if absent:
        return build_outcome(
            request, result=None, transport_status="INDETERMINATE",
            error_code="REMOTE_RESULT_UNAVAILABLE",
        )
    try:
        if result_file is None:
            raise DeploymentTransportError("REMOTE_RESULT_INVALID")
        value = _load_canonical(result_file, "REMOTE_RESULT_INVALID")
        _validate_schema(value, OUTCOME_SCHEMA, "REMOTE_RESULT_INVALID")
        bindings = (
            "operationId", "targetRelease", "workflowRunId",
            "workflowRunAttempt", "controlSha",
        )
        if any(value.get(key) != request.get(key) for key in bindings):
            raise DeploymentTransportError("REMOTE_RESULT_INVALID")
        return value
    except DeploymentTransportError:
        return build_outcome(
            request, result=None, transport_status="INDETERMINATE",
            error_code="REMOTE_RESULT_INVALID",
        )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="deployment_transport.py")
    commands = parser.add_subparsers(dest="command", required=True)
    trust = commands.add_parser("trust")
    trust.add_argument("--output", type=Path, required=True)
    prepare = commands.add_parser("prepare")
    prepare.add_argument("--trust", type=Path, required=True)
    prepare.add_argument("--output", type=Path, required=True)
    deploy = commands.add_parser("deploy")
    deploy.add_argument("--handoff", type=Path, required=True)
    deploy.add_argument("--output", type=Path, required=True)
    outcome = commands.add_parser("outcome")
    outcome.add_argument("--handoff", type=Path, required=True)
    outcome.add_argument("--result", type=Path)
    outcome.add_argument("--output", type=Path, required=True)
    return parser


def main(argv: list[str] | None = None) -> int:
    try:
        args = build_parser().parse_args(argv)
        final_outcome: dict[str, Any] | None = None
        if args.command == "trust":
            keys = [key for key in os.environ if key.startswith("TRUSTED_") or key == "DEPLOYER_ACTOR_IDS"]
            args.output.mkdir(mode=0o700, parents=False, exist_ok=False)
            _write_private(
                args.output / "deployment-trust.json",
                canonical(validate_trust_environment({key: os.environ[key] for key in keys})),
            )
        elif args.command == "prepare":
            prepare_handoff(
                trust_path=args.trust,
                output=args.output,
                remote=release_publication.GhTransport(),
            )
        elif args.command == "deploy":
            with private_handoff(args.handoff, args.output.parent) as handoff:
                request, _release = validate_handoff(handoff)
                ssh_directory: Path | None = None
                try:
                    try:
                        host = os.environ.get("PRODUCTION_SSH_HOST", "")
                        port = int(os.environ.get("PRODUCTION_SSH_PORT", ""))
                    except ValueError as exc:
                        raise DeploymentTransportError("SSH_CONFIGURATION_INVALID") from exc
                    ssh_directory = Path(
                        tempfile.mkdtemp(prefix=".deployment-ssh-", dir=args.output.parent)
                    )
                    ssh_directory.rmdir()
                    configuration = materialize_ssh_configuration(
                        directory=ssh_directory,
                        host=host,
                        port=port,
                        private_key=os.environ.get("PRODUCTION_SSH_PRIVATE_KEY", "").encode(),
                        known_hosts=os.environ.get("PRODUCTION_SSH_KNOWN_HOSTS", "").encode(),
                        expected_fingerprint=os.environ.get(
                            "PRODUCTION_SSH_PUBLIC_KEY_SHA256", ""
                        ),
                        ssh_binary=resolve_openssh("ssh"),
                        scp_binary=resolve_openssh("scp"),
                        keygen_binary=resolve_openssh("ssh-keygen"),
                    )
                except OSError:
                    local_error = DeploymentTransportError(
                        "SSH_CONFIGURATION_INVALID"
                    )
                    final_outcome = build_outcome(
                        request,
                        result={
                            "state": "FAILED",
                            "databaseRestoreRequired": False,
                            "errorCode": local_error.code,
                        },
                        transport_status="CONFIRMED",
                        error_code=local_error.code,
                    )
                    _persist_command_outcome(
                        args.output, "deployment-result.json", final_outcome
                    )
                except DeploymentTransportError as exc:
                    if exc.code not in {
                        "SSH_CONFIGURATION_INVALID", "SSH_UNAVAILABLE",
                        "SSH_KEY_FORMAT_INVALID", "SSH_KEY_FINGERPRINT_MISMATCH",
                        "SSH_KNOWN_HOSTS_INVALID",
                    }:
                        raise
                    final_outcome = build_outcome(
                        request,
                        result={
                            "state": "FAILED", "databaseRestoreRequired": False,
                            "errorCode": exc.code,
                        },
                        transport_status="CONFIRMED", error_code=exc.code,
                    )
                    _persist_command_outcome(
                        args.output, "deployment-result.json", final_outcome
                    )
                else:
                    final_outcome = deploy_handoff(
                        handoff=handoff,
                        output=args.output,
                        configuration=configuration,
                        runner=SubprocessRunner(),
                    )
                finally:
                    if ssh_directory is not None and ssh_directory.exists():
                        try:
                            ssh_material.cleanup_ssh_configuration(ssh_directory)
                        except ssh_material.SshMaterialError as exc:
                            raise DeploymentTransportError(exc.code) from exc
        else:
            with private_handoff(args.handoff, args.output.parent) as handoff:
                request, _release = validate_handoff(handoff)
                final_outcome = reconcile_workflow_outcome(request, args.result)
                _persist_command_outcome(
                    args.output, "deployment-workflow-outcome.json", final_outcome
                )
        if final_outcome is not None and not _clean_success(final_outcome):
            return 4
        return 0
    except DeploymentTransportError as exc:
        print(f"deployment-transport:{exc.code}", file=sys.stderr)
        return 3
    except Exception:
        print("deployment-transport:INTERNAL_ERROR", file=sys.stderr)
        return 3


if __name__ == "__main__":
    raise SystemExit(main())
