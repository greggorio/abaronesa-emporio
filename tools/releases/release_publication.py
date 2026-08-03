#!/usr/bin/env python3
"""Fail-closed release publication primitives and workflow CLI.

Network access is isolated behind an injected transport.  Local tests use only
the in-memory transport; the CLI's remote subcommands use explicit ``gh api``
argument arrays and inherit GH_TOKEN through the environment.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
import re
import selectors
import shutil
import subprocess
import sys
import tempfile
import tarfile
import time
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable

import jsonschema
import yaml

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/candidates"))
import artifact_io
import candidate_manifest
import global_release
import outcome as candidate_outcome

REPOSITORY = "greggorio/abaronesa-emporio"
PLAN_SCHEMA = ROOT / "ops/releases/release-publication-plan.schema.json"
OUTCOME_SCHEMA = ROOT / "ops/releases/release-publication-outcome.schema.json"
CANDIDATE_RE = re.compile(r"candidate-([0-9a-f]{40})-([1-9][0-9]*)-([1-9][0-9]*)")
OPERATION_RE = re.compile(r"[A-Za-z0-9_-]{20,128}")
DIGEST_RE = re.compile(r"sha256:[0-9a-f]{64}")
ASSETS = {
    "release.json": (2 * 1024 * 1024, "application/json"),
    "release.json.sha256": (128, "text/plain"),
    "metadata.json": (16 * 1024, "application/json"),
}
HISTORY_KEYS = {
    "releaseId", "tagName", "tagCommitSha", "manifestSha256",
    "releaseAssetId", "sidecarAssetId", "metadataAssetId",
}
MAX_HTTP_HEADERS = 64 * 1024
MAX_HTTP_JSON_BODY = 4 * 1024 * 1024
REMOTE_TIMEOUT_SECONDS = 60


class PublicationError(ValueError):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


class DraftCreationError(PublicationError):
    def __init__(self, code: str, owned_id: int | None = None):
        super().__init__(code)
        self.owned_id = owned_id


class RemoteHttpError(PublicationError):
    def __init__(self, status: int):
        super().__init__("REMOTE_HTTP_ERROR")
        self.status = status


class RemoteResponseError(PublicationError):
    def __init__(self, status: int):
        super().__init__("REMOTE_RESPONSE_INVALID")
        self.status = status


def parse_http_response(raw: bytes, returncode: int) -> tuple[int, Any]:
    """Parse one bounded ``gh api --include`` response without exposing bytes."""
    if not isinstance(raw, bytes) or not isinstance(returncode, int):
        raise PublicationError("REMOTE_TRANSPORT_FAILED")
    first_line_end = raw.find(b"\n")
    if first_line_end < 0 or first_line_end > 256:
        raise PublicationError("REMOTE_TRANSPORT_FAILED")
    first_line = raw[:first_line_end].removesuffix(b"\r")
    match = re.fullmatch(
        rb"HTTP/[0-9]+(?:\.[0-9]+)? ([0-9]{3})(?: [^\r\n]*)?", first_line
    )
    if match is None:
        raise PublicationError("REMOTE_TRANSPORT_FAILED")
    status = int(match.group(1))
    if returncode < 0:
        raise PublicationError("REMOTE_TRANSPORT_FAILED")
    if 200 <= status <= 299:
        if returncode != 0:
            raise PublicationError("REMOTE_TRANSPORT_FAILED")
    elif returncode > 0:
        raise RemoteHttpError(status)
    else:
        raise PublicationError("REMOTE_TRANSPORT_FAILED")
    separators = [
        (position, separator)
        for separator in (b"\r\n\r\n", b"\n\n")
        if (position := raw.find(separator)) >= 0
    ]
    if not separators:
        raise RemoteResponseError(status)
    header_end, separator = min(separators, key=lambda item: item[0])
    if header_end > MAX_HTTP_HEADERS:
        raise RemoteResponseError(status)
    header = raw[:header_end]
    body = raw[header_end + len(separator):]
    if len(body) > MAX_HTTP_JSON_BODY:
        raise RemoteResponseError(status)
    lines = header.splitlines()
    if not lines or sum(line.startswith(b"HTTP/") for line in lines) != 1:
        raise RemoteResponseError(status)
    if status == 204:
        if body:
            raise RemoteResponseError(status)
        return status, None
    try:
        return status, json.loads(body)
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        raise RemoteResponseError(status) from exc


def _positive_int(value: Any, code: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < 1:
        raise PublicationError(code)
    return value


def _positive_decimal(value: Any, code: str) -> int:
    if not isinstance(value, str) or not re.fullmatch(r"[1-9][0-9]*", value):
        raise PublicationError(code)
    return int(value)


def _identity(value: Any, code: str) -> tuple[str, int]:
    if not isinstance(value, dict):
        raise PublicationError(code)
    login = value.get("login")
    if not isinstance(login, str) or not login:
        raise PublicationError(code)
    return login, _positive_int(value.get("id"), code)


def _repository(value: Any, code: str) -> int:
    if not isinstance(value, dict) or value.get("full_name") != REPOSITORY:
        raise PublicationError(code)
    owner = value.get("owner")
    if not isinstance(owner, dict) or owner.get("login") != "greggorio":
        raise PublicationError(code)
    return _positive_int(value.get("id"), code)


def validate_workflow_run(
    run: Any,
    *,
    kind: str,
    run_id: int,
    attempt: int,
    sha: str,
    actor: tuple[str, int] | None = None,
    expected_name: str | None = None,
) -> int:
    """Validate the real top-level workflow-run REST shape."""
    if not isinstance(run, dict):
        raise PublicationError(f"{kind.upper()}_RUN_INVALID")
    code = f"{kind.upper()}_RUN_INVALID"
    expected = {
        # publish-release.yml declares run-name, so the REST `name` of the current
        # run is the display title, not the workflow name; `path` carries no @ref.
        "current": {
            "name": expected_name,
            "path": ".github/workflows/publish-release.yml",
            "event": "workflow_dispatch",
            "status": "in_progress",
        },
        "candidate": {
            "name": "Publish Candidate",
            "path": ".github/workflows/publish-candidate.yml",
            "event": "workflow_run",
            "status": "completed",
        },
    }[kind]
    if (
        _positive_int(run.get("id"), code) != run_id
        or _positive_int(run.get("run_attempt"), code) != attempt
        or _positive_int(run.get("workflow_id"), code) < 1
        or run.get("name") != expected["name"]
        or run.get("path") != expected["path"]
        or run.get("event") != expected["event"]
        or run.get("status") != expected["status"]
        or run.get("head_branch") != "main"
        or run.get("head_sha") != sha
    ):
        raise PublicationError(code)
    if kind == "candidate" and run.get("conclusion") != "success":
        raise PublicationError(code)
    repository_id = _repository(run.get("repository"), code)
    head_repository_id = _repository(run.get("head_repository"), code)
    if repository_id != head_repository_id:
        raise PublicationError(code)
    run_actor = _identity(run.get("actor"), code)
    _identity(run.get("triggering_actor"), code)
    if actor is not None and run_actor != actor:
        raise PublicationError(code)
    return repository_id


def validate_actions_artifact(
    value: Any,
    *,
    expected_name: str,
    candidate_run_id: int,
    candidate_sha: str,
    repository_id: int,
) -> dict[str, Any]:
    code = "CANDIDATE_ARTIFACT_INVALID"
    if not isinstance(value, dict):
        raise PublicationError(code)
    artifact_id = _positive_int(value.get("id"), code)
    if (
        value.get("name") != expected_name
        or value.get("expired") is not False
        or not isinstance(value.get("size_in_bytes"), int)
        or isinstance(value.get("size_in_bytes"), bool)
        or not 0 < value["size_in_bytes"] <= artifact_io.MAX_ZIP
        or value.get("url") != f"https://api.github.com/repos/{REPOSITORY}/actions/artifacts/{artifact_id}"
        or value.get("archive_download_url")
        != f"https://api.github.com/repos/{REPOSITORY}/actions/artifacts/{artifact_id}/zip"
        or not isinstance(value.get("digest"), str)
        or not DIGEST_RE.fullmatch(value["digest"])
    ):
        raise PublicationError(code)
    workflow_run = value.get("workflow_run")
    if (
        not isinstance(workflow_run, dict)
        or _positive_int(workflow_run.get("id"), code) != candidate_run_id
        or workflow_run.get("head_sha") != candidate_sha
    ):
        raise PublicationError(code)
    for key in ("repository_id", "head_repository_id"):
        if key in workflow_run and _positive_int(workflow_run[key], code) != repository_id:
            raise PublicationError(code)
    return value


def validate_release_identity(value: Any, expected_id: int | None = None) -> int:
    code = "HISTORY_RELEASE_INVALID"
    if not isinstance(value, dict):
        raise PublicationError(code)
    release_id = _positive_int(value.get("id"), code)
    if expected_id is not None and release_id != expected_id:
        raise PublicationError(code)
    if value.get("url") != f"https://api.github.com/repos/{REPOSITORY}/releases/{release_id}":
        raise PublicationError(code)
    return release_id


def validate_release_assets(value: Any) -> list[dict[str, Any]]:
    """Validate the complete asset set before any asset endpoint is used."""
    code = "HISTORY_ASSETS_INVALID"
    if not isinstance(value, list) or len(value) != 3:
        raise PublicationError(code)
    by_name: dict[str, dict[str, Any]] = {}
    for asset in value:
        if not isinstance(asset, dict):
            raise PublicationError(code)
        asset_id = _positive_int(asset.get("id"), code)
        name = asset.get("name")
        if name not in ASSETS or name in by_name:
            raise PublicationError(code)
        limit, content_type = ASSETS[name]
        size = asset.get("size")
        if (
            asset.get("url")
            != f"https://api.github.com/repos/{REPOSITORY}/releases/assets/{asset_id}"
            or asset.get("state") != "uploaded"
            or asset.get("content_type") != content_type
            or isinstance(size, bool)
            or not isinstance(size, int)
            or not 0 < size <= limit
        ):
            raise PublicationError(code)
        by_name[name] = asset
    if set(by_name) != set(ASSETS):
        raise PublicationError(code)
    return [by_name[name] for name in ASSETS]


def validate_notes(notes_bytes: bytes) -> str:
    if not isinstance(notes_bytes, bytes) or not 1 <= len(notes_bytes) <= 16 * 1024:
        raise PublicationError("RELEASE_NOTES_INVALID")
    try:
        return notes_bytes.decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise PublicationError("RELEASE_NOTES_INVALID") from exc


def validate_tag_ref(value: Any, tag: str, expected_sha: str | None = None) -> str:
    """Validate the complete canonical lightweight Git-reference REST shape."""
    code = "HISTORY_TAG_INVALID"
    if (
        not isinstance(tag, str)
        or global_release.SEMVER_RE.fullmatch(tag) is None
        or not isinstance(value, dict)
    ):
        raise PublicationError(code)
    ref = f"refs/tags/{tag}"
    api_base = f"https://api.github.com/repos/{REPOSITORY}/git"
    object_value = value.get("object")
    if (
        value.get("ref") != ref
        or value.get("url") != f"{api_base}/refs/tags/{tag}"
        or not isinstance(object_value, dict)
        or object_value.get("type") != "commit"
    ):
        raise PublicationError(code)
    sha = object_value.get("sha")
    if (
        not isinstance(sha, str)
        or re.fullmatch(r"[0-9a-f]{40}", sha) is None
        or object_value.get("url") != f"{api_base}/commits/{sha}"
        or (expected_sha is not None and sha != expected_sha)
    ):
        raise PublicationError(code)
    return sha


def validate_release_state(
    value: Any,
    *,
    release_id: int,
    tag: str,
    sha: str,
    notes_bytes: bytes,
    draft: bool,
    assets_required: bool | None = True,
) -> list[dict[str, Any]]:
    validate_release_identity(value, release_id)
    notes = validate_notes(notes_bytes)
    if (
        value.get("tag_name") != tag
        or value.get("name") != tag
        or value.get("target_commitish") != sha
        or value.get("body") != notes
        or value.get("draft") is not draft
        or value.get("prerelease") is not False
    ):
        raise PublicationError("RELEASE_STATE_INVALID")
    if assets_required is None:
        return []
    if not assets_required:
        if value.get("assets") != []:
            raise PublicationError("RELEASE_STATE_INVALID")
        return []
    return validate_release_assets(value.get("assets"))


def canonical(value: Any) -> bytes:
    return artifact_io.canonical(value)


def digest(data: bytes) -> str:
    return artifact_io.digest(data)


def load_schema(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise PublicationError("SCHEMA_INVALID")
    return value


def validate_schema(value: Any, path: Path) -> None:
    validator = jsonschema.Draft202012Validator(
        load_schema(path), format_checker=jsonschema.FormatChecker()
    )
    if next(validator.iter_errors(value), None):
        raise PublicationError("SCHEMA_INVALID")


def parse_candidate_id(value: str) -> tuple[str, str, int]:
    match = CANDIDATE_RE.fullmatch(str(value))
    if not match:
        raise PublicationError("CANDIDATE_ID_INVALID")
    return match.group(1), match.group(2), int(match.group(3))


def parse_allowlist(value: str | None) -> set[str]:
    if not value or len(value) > 512 or "*" in value:
        raise PublicationError("ACTOR_ALLOWLIST_INVALID")
    parts = value.split(",")
    if not 1 <= len(parts) <= 20 or any(not re.fullmatch(r"[1-9][0-9]*", part) for part in parts):
        raise PublicationError("ACTOR_ALLOWLIST_INVALID")
    if len(set(parts)) != len(parts):
        raise PublicationError("ACTOR_ALLOWLIST_INVALID")
    return set(parts)


def read_event(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise PublicationError("EVENT_INVALID")
    return value


def request_from_event(event: dict[str, Any]) -> tuple[str, dict[str, str]]:
    inputs = event.get("inputs")
    expected = {"operation_id", "candidate_id", "version_bump", "description", "changelog"}
    if not isinstance(inputs, dict) or set(inputs) != expected:
        raise PublicationError("REQUEST_INPUTS_INVALID")
    operation = inputs["operation_id"]
    candidate = inputs["candidate_id"]
    bump = inputs["version_bump"]
    description = inputs["description"]
    changelog = inputs["changelog"]
    if not isinstance(operation, str) or not OPERATION_RE.fullmatch(operation):
        raise PublicationError("OPERATION_ID_INVALID")
    parse_candidate_id(candidate)
    request = {
        "candidateId": candidate, "versionBump": bump,
        "description": description, "changelog": changelog,
    }
    errors = global_release.validate_request(request)
    if errors:
        raise PublicationError(errors[0])
    return operation, request


def validate_identity(
    env: dict[str, str],
    event: dict[str, Any],
    current_run: dict[str, Any],
    operation: str,
) -> None:
    required = {
        "GITHUB_REPOSITORY": REPOSITORY, "GITHUB_REPOSITORY_OWNER": "greggorio",
        "GITHUB_EVENT_NAME": "workflow_dispatch", "GITHUB_REF": "refs/heads/main",
    }
    if any(env.get(key) != value for key, value in required.items()):
        raise PublicationError("WORKFLOW_IDENTITY_INVALID")
    actor_id = env.get("GITHUB_ACTOR_ID", "")
    if actor_id not in parse_allowlist(env.get("RELEASE_PUBLISHER_ACTOR_IDS")):
        raise PublicationError("ACTOR_NOT_AUTHORIZED")
    sender = event.get("sender")
    if not isinstance(sender, dict) or sender.get("login") != env.get("GITHUB_ACTOR") or str(sender.get("id")) != actor_id:
        raise PublicationError("SENDER_INVALID")
    run_id = _positive_decimal(env.get("GITHUB_RUN_ID"), "CURRENT_RUN_INVALID")
    attempt = _positive_decimal(env.get("GITHUB_RUN_ATTEMPT"), "CURRENT_RUN_INVALID")
    actor_id_int = _positive_decimal(actor_id, "CURRENT_RUN_INVALID")
    validate_workflow_run(
        current_run, kind="current", run_id=run_id, attempt=attempt,
        sha=env.get("GITHUB_SHA", ""), actor=(env.get("GITHUB_ACTOR", ""), actor_id_int),
        expected_name=f"publish-release-{operation}",
    )


def history_snapshot(history: list[dict[str, Any]]) -> str:
    normalized = []
    for item in history:
        if not isinstance(item, dict) or set(item) != HISTORY_KEYS:
            raise PublicationError("HISTORY_SNAPSHOT_INVALID")
        normalized.append(copy.deepcopy(item))
    return digest(canonical(normalized))


def validate_history(records: list[dict[str, Any]], tags: dict[str, str]) -> list[dict[str, Any]]:
    if len(records) > 1000 or not isinstance(tags, dict):
        raise PublicationError("HISTORY_INVALID")
    ordered: list[tuple[tuple[int, int, int], dict[str, Any]]] = []
    candidate_ids: set[str] = set()
    run_ids: set[str] = set()
    for record in records:
        if not isinstance(record, dict) or set(record) != {"release", "manifest", "assets", "assetBytes"}:
            raise PublicationError("HISTORY_INVALID")
        remote, manifest = record["release"], record["manifest"]
        validate_release_identity(remote)
        if (
            not isinstance(remote, dict) or remote.get("draft") is not False
            or remote.get("prerelease") is not False or remote.get("name") != remote.get("tag_name")
            or remote.get("tag_name") not in tags
        ):
            raise PublicationError("HISTORY_RELEASE_INVALID")
        try:
            version = global_release.parse_semver(remote["tag_name"])
        except Exception as exc:
            raise PublicationError("HISTORY_SEMVER_INVALID") from exc
        if tags[remote["tag_name"]] != manifest.get("sourceCommit"):
            raise PublicationError("HISTORY_TAG_INVALID")
        assets = validate_release_assets(record["assets"])
        by_name = {asset["name"]: asset for asset in assets}
        blobs = record["assetBytes"]
        if not isinstance(blobs, dict) or set(blobs) != set(ASSETS):
            raise PublicationError("HISTORY_ASSETS_INVALID")
        manifest_bytes = blobs["release.json"]
        if not isinstance(manifest_bytes, bytes) or len(manifest_bytes) > ASSETS["release.json"][0]:
            raise PublicationError("HISTORY_ASSET_BYTES_INVALID")
        if manifest_bytes != canonical(manifest):
            raise PublicationError("HISTORY_ASSET_BYTES_INVALID")
        expected_raw = digest(manifest_bytes).removeprefix("sha256:")
        if blobs["release.json.sha256"] != (expected_raw + "\n").encode():
            raise PublicationError("HISTORY_ASSET_BYTES_INVALID")
        try:
            metadata = json.loads(blobs["metadata.json"])
        except (json.JSONDecodeError, UnicodeDecodeError) as exc:
            raise PublicationError("HISTORY_ASSET_BYTES_INVALID") from exc
        if blobs["metadata.json"] != canonical(metadata) or metadata != global_release.metadata_for(manifest, manifest_bytes):
            raise PublicationError("HISTORY_ASSET_BYTES_INVALID")
        errors = global_release.validate_release(manifest)
        if errors or manifest.get("release") != remote["tag_name"]:
            raise PublicationError("HISTORY_MANIFEST_INVALID")
        candidate_id = manifest["candidate"]["candidateId"]
        run_id = manifest["publication"]["workflowRunId"]
        if candidate_id in candidate_ids or run_id in run_ids:
            raise PublicationError("HISTORY_DUPLICATE_BINDING")
        candidate_ids.add(candidate_id)
        run_ids.add(run_id)
        ordered.append((version, record))
    if set(tags) != {record["release"]["tag_name"] for _, record in ordered}:
        raise PublicationError("HISTORY_TAG_SET_INVALID")
    ordered.sort(key=lambda pair: pair[0])
    previous = None
    for _, record in ordered:
        if record["manifest"]["previousRelease"] != previous:
            raise PublicationError("HISTORY_CHAIN_INVALID")
        previous = record["manifest"]["release"]
    return [record for _, record in ordered]


def snapshot_items(history: list[dict[str, Any]]) -> list[dict[str, Any]]:
    result = []
    for record in history:
        assets = {item["name"]: item for item in record["assets"]}
        result.append({
            "releaseId": str(record["release"]["id"]),
            "tagName": record["manifest"]["release"],
            "tagCommitSha": record["manifest"]["sourceCommit"],
            "manifestSha256": digest(record["assetBytes"]["release.json"]),
            "releaseAssetId": str(assets["release.json"]["id"]),
            "sidecarAssetId": str(assets["release.json.sha256"]["id"]),
            "metadataAssetId": str(assets["metadata.json"]["id"]),
        })
    return result


def build_plan(operation: str, request: dict[str, str], candidate: dict[str, Any],
               candidate_binding: dict[str, Any], history: list[dict[str, Any]],
               workflow: dict[str, Any], release: dict[str, Any] | None) -> dict[str, Any]:
    matches = [item for item in history if item["manifest"]["candidate"]["candidateId"] == request["candidateId"]]
    if len(matches) > 1:
        raise PublicationError("HISTORY_DUPLICATE_BINDING")
    existing = matches[0] if matches else None
    mode = "already_published" if existing else "publish"
    target_manifest = existing["manifest"] if existing else release
    if target_manifest is None:
        raise PublicationError("TARGET_MISSING")
    target_remote = existing["release"] if existing else None
    plan = {
        "schemaVersion": 1, "kind": "release-publication-plan", "mode": mode,
        "repository": REPOSITORY, "operationId": operation,
        "requestSha256": digest(canonical(request)), "workflow": copy.deepcopy(workflow),
        "candidate": {
            "candidateId": request["candidateId"],
            "manifestSha256": candidate_binding["manifestSha256"],
            "artifactId": str(candidate_binding["artifactId"]),
            "artifactDigest": candidate_binding["artifactDigest"],
            "workflowRunId": str(candidate_binding["workflowRunId"]),
            "workflowAttempt": candidate_binding["workflowAttempt"],
        },
        "historySnapshotSha256": history_snapshot(snapshot_items(history)),
        "target": {
            "release": target_manifest["release"], "sourceCommit": target_manifest["sourceCommit"],
            "previousRelease": target_manifest["previousRelease"],
            "manifestSha256": digest(canonical(target_manifest)),
            "existingReleaseId": str(target_remote["id"]) if target_remote else None,
            "existingReleaseUrl": (
                f"https://github.com/{REPOSITORY}/releases/tag/{target_manifest['release']}"
                if target_remote else None
            ),
        },
    }
    validate_schema(plan, PLAN_SCHEMA)
    return plan


def notes_for(release: dict[str, Any], operation: str) -> bytes:
    text = (
        f"# {release['release']}\n\n{release['description']}\n\n## Changelog\n\n"
        f"{release['changelog']}\n\n## Proveniencia\n\n"
        f"- Commit: `{release['sourceCommit']}`\n"
        f"- Candidato: `{release['candidate']['candidateId']}`\n"
        f"- Manifesto: `{digest(canonical(release))}`\n"
        f"- Operacao: `{operation}`\n"
    )
    return text.encode("utf-8")


def metadata(kind: str, operation: str, run_id: str, attempt: int, data: bytes) -> dict[str, Any]:
    field = "planSha256" if kind == "release-publication-plan" else "outcomeSha256"
    return {
        "schemaVersion": 1, "stage": "final", "kind": kind, "repository": REPOSITORY,
        "operationId": operation, "workflowRunId": run_id,
        "workflowAttempt": attempt, field: digest(data),
    }


def write_bundle(directory: Path, name: str, value: dict[str, Any], metadata_value: dict[str, Any]) -> None:
    directory = Path(directory)
    if directory.exists():
        raise PublicationError("BUNDLE_EXISTS")
    stage = Path(tempfile.mkdtemp(prefix=".release-stage-", dir=directory.parent))
    try:
        data = canonical(value)
        (stage / name).write_bytes(data)
        (stage / f"{name}.sha256").write_text(digest(data).removeprefix("sha256:") + "\n", encoding="ascii")
        (stage / "metadata.json").write_bytes(canonical(metadata_value))
        os.replace(stage, directory)
    except Exception:
        shutil.rmtree(stage, ignore_errors=True)
        if directory.exists():
            shutil.rmtree(directory, ignore_errors=True)
        raise


def build_outcome(plan: dict[str, Any], release_id: str, current: dict[str, Any],
                  recorded_at: str, status: str) -> dict[str, Any]:
    target = plan["target"]
    tag = target["release"]
    outcome = {
        "schemaVersion": 1, "kind": "release-publication-outcome", "status": status,
        "repository": REPOSITORY, "operationId": plan["operationId"],
        "candidateId": plan["candidate"]["candidateId"], "release": tag,
        "sourceCommit": target["sourceCommit"],
        "workflow": {
            "runId": str(current["runId"]), "attempt": current["attempt"],
            "url": f"https://github.com/{REPOSITORY}/actions/runs/{current['runId']}",
            "actor": current["actor"], "actorId": str(current["actorId"]),
        },
        "githubRelease": {
            "id": str(release_id),
            "url": f"https://github.com/{REPOSITORY}/releases/tag/{tag}", "tagName": tag,
        },
        "manifestSha256": target["manifestSha256"], "recordedAt": recorded_at,
    }
    validate_schema(outcome, OUTCOME_SCHEMA)
    return outcome


def publish_transaction(
    transport: Any,
    plan: dict[str, Any],
    bundle: dict[str, bytes],
    snapshot: str,
    notes_bytes: bytes,
) -> dict[str, Any]:
    """Publish draft/assets/tag, reconciling and compensating on every failure."""
    validate_schema(plan, PLAN_SCHEMA)
    validate_notes(notes_bytes)
    if plan["mode"] != "publish":
        raise PublicationError("PLAN_MODE_INVALID")
    tag, sha = plan["target"]["release"], plan["target"]["sourceCommit"]
    draft_id: int | None = None
    tag_owned = False
    tag_attempted = False
    try:
        if transport.snapshot() != snapshot:
            raise PublicationError("HISTORY_SNAPSHOT_CHANGED")
        if transport.lookup(tag) is not None:
            raise PublicationError("TARGET_ALREADY_EXISTS")
        draft = transport.create_draft(tag, sha, notes_bytes)
        draft_id = _positive_int(draft.get("id") if isinstance(draft, dict) else None, "DRAFT_CREATE_INVALID")
        for name, (_, content_type) in ASSETS.items():
            transport.upload(draft_id, name, content_type, bundle[name])
        if transport.download_assets(draft_id, tag, sha, notes_bytes, True) != bundle:
            raise PublicationError("PUBLICATION_ASSET_MISMATCH")
        if transport.snapshot(ignore_draft=(draft_id, tag, sha, notes_bytes)) != snapshot:
            raise PublicationError("HISTORY_SNAPSHOT_CHANGED")
        tag_attempted = True
        transport.create_tag(tag, sha)
        tag_owned = True
        transport.publish_draft(draft_id)
        final = transport.final_state(draft_id, tag, sha, notes_bytes)
        if (
            not final.get("valid")
            or transport.download_assets(draft_id, tag, sha, notes_bytes, False) != bundle
        ):
            raise PublicationError("PUBLICATION_RECONCILIATION_FAILED")
        return final
    except Exception as original:
        if isinstance(original, DraftCreationError):
            draft_id = original.owned_id
        failures = []
        if draft_id is not None:
            try:
                transport.delete_owned_draft(draft_id, tag, sha, notes_bytes)
            except Exception:
                failures.append("draft")
        if tag_owned:
            try:
                transport.delete_owned_tag(tag, sha)
            except Exception:
                failures.append("tag")
        try:
            if (
                draft_id is not None
                and hasattr(transport, "release_lookup")
                and transport.release_lookup(draft_id) is not None
            ):
                failures.append("release-proof")
            if transport.lookup(tag) is not None:
                failures.append("proof")
            if hasattr(transport, "tag_lookup") and transport.tag_lookup(tag) is not None:
                failures.append("tag-proof")
        except Exception:
            failures.append("proof")
        if failures:
            raise PublicationError("PUBLICATION_COMPENSATION_FAILED") from original
        raise


class GhTransport:
    """Explicit REST transport. It is never used by local validation/tests."""
    def api(
        self,
        method: str,
        endpoint: str,
        body: Path | None = None,
        expected_status: int = 200,
    ) -> Any:
        if not endpoint.startswith(f"/repos/{REPOSITORY}/"):
            raise PublicationError("REMOTE_ENDPOINT_INVALID")
        command = ["gh", "api", "--include", "--method", method, endpoint]
        if body is not None:
            command += ["--input", str(body)]
        try:
            completed = subprocess.run(
                command, check=False, capture_output=True,
                timeout=REMOTE_TIMEOUT_SECONDS,
            )
        except (OSError, ValueError, subprocess.TimeoutExpired) as exc:
            raise PublicationError("REMOTE_TRANSPORT_FAILED") from exc
        status, value = parse_http_response(completed.stdout, completed.returncode)
        if status != expected_status:
            raise RemoteResponseError(status)
        return value

    def optional_get(self, endpoint: str) -> Any | None:
        try:
            return self.api("GET", endpoint, expected_status=200)
        except RemoteHttpError as exc:
            if exc.status == 404:
                return None
            raise

    def bytes(self, endpoint: str, limit: int, *headers: str) -> bytes:
        if not endpoint.startswith(f"/repos/{REPOSITORY}/"):
            raise PublicationError("REMOTE_ENDPOINT_INVALID")
        command = ["gh", "api", endpoint]
        for header in headers:
            command += ["-H", header]
        process: subprocess.Popen[bytes] | None = None
        selector = selectors.DefaultSelector()
        chunks: list[bytes] = []
        total = 0
        deadline = time.monotonic() + REMOTE_TIMEOUT_SECONDS
        try:
            process = subprocess.Popen(
                command, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL
            )
            assert process.stdout is not None
            selector.register(process.stdout, selectors.EVENT_READ)
            while selector.get_map():
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise subprocess.TimeoutExpired(command, REMOTE_TIMEOUT_SECONDS)
                events = selector.select(remaining)
                if not events:
                    raise subprocess.TimeoutExpired(command, REMOTE_TIMEOUT_SECONDS)
                for key, _ in events:
                    chunk = os.read(key.fileobj.fileno(), 65536)
                    if not chunk:
                        selector.unregister(key.fileobj)
                        continue
                    total += len(chunk)
                    if total > limit:
                        raise PublicationError("REMOTE_DOWNLOAD_LIMIT")
                    chunks.append(chunk)
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise subprocess.TimeoutExpired(command, REMOTE_TIMEOUT_SECONDS)
            if process.wait(timeout=remaining) != 0:
                raise PublicationError("REMOTE_DOWNLOAD_FAILED")
            return b"".join(chunks)
        except PublicationError:
            if process is not None and process.poll() is None:
                process.kill()
                process.wait()
            raise
        except (OSError, ValueError, subprocess.TimeoutExpired) as exc:
            if process is not None and process.poll() is None:
                try:
                    process.kill()
                    process.wait()
                except OSError:
                    pass
            raise PublicationError("REMOTE_DOWNLOAD_FAILED") from exc
        finally:
            selector.close()

    def current_run(self, run_id: str) -> dict[str, Any]:
        run_id_int = _positive_decimal(run_id, "CURRENT_RUN_INVALID")
        value = self.api("GET", f"/repos/{REPOSITORY}/actions/runs/{run_id_int}")
        if not isinstance(value, dict):
            raise PublicationError("CURRENT_RUN_INVALID")
        return value

    def list_pages(self, endpoint: str, key: str | None = None) -> list[Any]:
        result: list[Any] = []
        for page in range(1, 11):
            separator = "&" if "?" in endpoint else "?"
            value = self.api("GET", f"{endpoint}{separator}per_page=100&page={page}")
            page_items = value.get(key) if key and isinstance(value, dict) else value
            if not isinstance(page_items, list):
                raise PublicationError("REMOTE_RESPONSE_INVALID")
            result.extend(page_items)
            if len(page_items) < 100:
                return result
        raise PublicationError("REMOTE_PAGINATION_EXHAUSTED")

    def snapshot(
        self, ignore_draft: tuple[int, str, str, bytes] | None = None
    ) -> str:
        history, tags = load_remote_history(self, ignore_draft)
        validated = validate_history(history, tags)
        return history_snapshot(snapshot_items(validated))

    def lookup(self, tag: str) -> dict[str, Any] | None:
        value = self.optional_get(f"/repos/{REPOSITORY}/releases/tags/{tag}")
        if value is None:
            return None
        if not isinstance(value, dict):
            raise PublicationError("REMOTE_RESPONSE_INVALID")
        validate_release_identity(value)
        return value

    def release_lookup(self, release_id: int) -> dict[str, Any] | None:
        _positive_int(release_id, "HISTORY_RELEASE_INVALID")
        value = self.optional_get(f"/repos/{REPOSITORY}/releases/{release_id}")
        if value is None:
            return None
        validate_release_identity(value, release_id)
        return value

    def tag_lookup(self, tag: str) -> dict[str, Any] | None:
        value = self.optional_get(f"/repos/{REPOSITORY}/git/ref/tags/{tag}")
        if value is None:
            return None
        validate_tag_ref(value, tag)
        return value

    def tag_points_to(self, tag: str, sha: str) -> bool:
        value = self.tag_lookup(tag)
        return value is not None and validate_tag_ref(value, tag, sha) == sha

    def create_draft(self, tag: str, sha: str, notes_bytes: bytes) -> dict[str, Any]:
        body = validate_notes(notes_bytes)
        payload = {"tag_name": tag, "target_commitish": sha, "name": tag,
                   "body": body, "draft": True, "prerelease": False}
        with tempfile.TemporaryDirectory() as raw:
            path = Path(raw) / "request.json"
            path.write_bytes(canonical(payload))
            value = self.api(
                "POST", f"/repos/{REPOSITORY}/releases", path,
                expected_status=201,
            )
        try:
            release_id = _positive_int(
                value.get("id") if isinstance(value, dict) else None,
                "DRAFT_CREATE_INVALID",
            )
        except PublicationError as exc:
            raise PublicationError("DRAFT_CREATE_INVALID")
        endpoint = f"/repos/{REPOSITORY}/releases/{release_id}"
        try:
            confirmed = self.api("GET", endpoint)
            validate_release_state(
                confirmed, release_id=release_id, tag=tag, sha=sha,
                notes_bytes=notes_bytes, draft=True, assets_required=False,
            )
        except Exception as first:
            owned_id = None
            try:
                proof = self.api("GET", endpoint)
                validate_release_state(
                    proof, release_id=release_id, tag=tag, sha=sha,
                    notes_bytes=notes_bytes, draft=True, assets_required=False,
                )
                owned_id = release_id
            except Exception:
                pass
            raise DraftCreationError("DRAFT_RECONCILIATION_INVALID", owned_id) from first
        return {"id": release_id}

    def upload(self, release_id: int, name: str, content_type: str, data: bytes) -> None:
        _positive_int(release_id, "UPLOAD_BINDING_INVALID")
        if name not in ASSETS or ASSETS[name][1] != content_type:
            raise PublicationError("UPLOAD_BINDING_INVALID")
        with tempfile.TemporaryDirectory() as raw:
            path = Path(raw) / name
            path.write_bytes(data)
            command = [
                "gh", "api", "--hostname", "uploads.github.com", "--method", "POST",
                f"/repos/{REPOSITORY}/releases/{release_id}/assets?name={name}",
                "-H", f"Content-Type: {content_type}", "--input", str(path),
            ]
            try:
                completed = subprocess.run(
                    command, check=False, stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL, timeout=REMOTE_TIMEOUT_SECONDS,
                )
            except (OSError, subprocess.TimeoutExpired) as exc:
                raise PublicationError("REMOTE_UPLOAD_FAILED") from exc
            if completed.returncode != 0:
                raise PublicationError("REMOTE_UPLOAD_FAILED")

    def download_assets(
        self,
        release_id: int,
        tag: str,
        sha: str,
        notes_bytes: bytes,
        draft: bool,
    ) -> dict[str, bytes]:
        _positive_int(release_id, "HISTORY_RELEASE_INVALID")
        release = self.api("GET", f"/repos/{REPOSITORY}/releases/{release_id}")
        assets = validate_release_state(
            release, release_id=release_id, tag=tag, sha=sha,
            notes_bytes=notes_bytes, draft=draft,
        )
        result = {}
        for asset in assets:
            limit, _ = ASSETS[asset["name"]]
            result[asset["name"]] = self.bytes(
                f"/repos/{REPOSITORY}/releases/assets/{asset['id']}", limit,
                "Accept: application/octet-stream",
            )
        return result

    def create_tag(self, tag: str, sha: str) -> None:
        try:
            with tempfile.TemporaryDirectory() as raw:
                path = Path(raw) / "request.json"
                path.write_bytes(canonical({"ref": f"refs/tags/{tag}", "sha": sha}))
                value = self.api(
                    "POST", f"/repos/{REPOSITORY}/git/refs", path,
                    expected_status=201,
                )
            try:
                validate_tag_ref(value, tag, sha)
            except PublicationError as exc:
                raise RemoteResponseError(201) from exc
        except RemoteResponseError as exc:
            if exc.status != 201:
                raise
            reconciled = self.tag_lookup(tag)
            if reconciled is None:
                raise
            validate_tag_ref(reconciled, tag, sha)

    def publish_draft(self, release_id: int) -> None:
        _positive_int(release_id, "HISTORY_RELEASE_INVALID")
        with tempfile.TemporaryDirectory() as raw:
            path = Path(raw) / "request.json"
            path.write_bytes(canonical({"draft": False}))
            self.api(
                "PATCH", f"/repos/{REPOSITORY}/releases/{release_id}", path,
                expected_status=200,
            )

    def final_state(
        self, release_id: int, tag: str, sha: str, notes_bytes: bytes
    ) -> dict[str, Any]:
        _positive_int(release_id, "HISTORY_RELEASE_INVALID")
        release = self.api("GET", f"/repos/{REPOSITORY}/releases/{release_id}")
        validate_release_state(
            release, release_id=release_id, tag=tag, sha=sha,
            notes_bytes=notes_bytes, draft=False,
        )
        ref = self.api("GET", f"/repos/{REPOSITORY}/git/ref/tags/{tag}")
        validate_tag_ref(ref, tag, sha)
        return {"valid": True, "id": release_id}

    def delete_owned_draft(
        self, release_id: int, tag: str, sha: str, notes_bytes: bytes
    ) -> None:
        _positive_int(release_id, "COMPENSATION_BINDING_INVALID")
        release = self.api("GET", f"/repos/{REPOSITORY}/releases/{release_id}")
        validate_release_identity(release, release_id)
        if (
            release.get("tag_name") != tag
            or release.get("name") != tag
            or release.get("draft") not in {True, False}
            or release.get("prerelease") is not False
        ):
            raise PublicationError("COMPENSATION_BINDING_INVALID")
        self.api(
            "DELETE", f"/repos/{REPOSITORY}/releases/{release_id}",
            expected_status=204,
        )

    def delete_owned_tag(self, tag: str, sha: str) -> None:
        ref = self.api("GET", f"/repos/{REPOSITORY}/git/ref/tags/{tag}")
        try:
            validate_tag_ref(ref, tag, sha)
        except PublicationError as exc:
            raise PublicationError("COMPENSATION_BINDING_INVALID")
        self.api(
            "DELETE", f"/repos/{REPOSITORY}/git/refs/tags/{tag}",
            expected_status=204,
        )


def _artifact_digest(value: Any) -> str:
    digest_value = value.get("digest") if isinstance(value, dict) else None
    if not isinstance(digest_value, str) or not DIGEST_RE.fullmatch(digest_value):
        raise PublicationError("ARTIFACT_DIGEST_INVALID")
    return digest_value


def resolve_candidate(transport: GhTransport, candidate_id: str, directory: Path) -> tuple[dict[str, Any], dict[str, Any]]:
    sha, run_id, attempt = parse_candidate_id(candidate_id)
    run = transport.current_run(run_id)
    run_id_int = _positive_decimal(run_id, "CANDIDATE_RUN_INVALID")
    repository_id = validate_workflow_run(
        run, kind="candidate", run_id=run_id_int, attempt=attempt, sha=sha
    )
    artifacts = transport.list_pages(f"/repos/{REPOSITORY}/actions/runs/{run_id}/artifacts", "artifacts")
    selected = {}
    for name in ("candidate-manifest", "candidate-outcome"):
        matches = [item for item in artifacts if item.get("name") == name]
        if len(matches) != 1:
            raise PublicationError("CANDIDATE_ARTIFACT_INVALID")
        selected[name] = validate_actions_artifact(
            matches[0], expected_name=name, candidate_run_id=run_id_int,
            candidate_sha=sha, repository_id=repository_id,
        )
    # Both records are fully validated before either ID controls a download.
    directory.mkdir(parents=True, exist_ok=False)
    manifest_artifact = selected["candidate-manifest"]
    manifest_zip = directory / "manifest.zip"
    manifest_zip.write_bytes(transport.bytes(
        f"/repos/{REPOSITORY}/actions/artifacts/{manifest_artifact['id']}/zip",
        artifact_io.MAX_ZIP,
    ))
    manifest_dir = directory / "manifest"
    try:
        artifact_io.safe_extract_named(
            manifest_zip, manifest_dir, _artifact_digest(manifest_artifact),
            {"candidate.json": 1024 * 1024, "candidate.json.sha256": 128, "metadata.json": 16 * 1024},
            "candidate.json",
        )
    except (OSError, EOFError, ValueError, zipfile.BadZipFile) as exc:
        raise PublicationError("CANDIDATE_ARTIFACT_INVALID") from exc
    manifest_bytes = (manifest_dir / "candidate.json").read_bytes()
    try:
        manifest = json.loads(manifest_bytes)
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        raise PublicationError("CANDIDATE_MANIFEST_INVALID") from exc
    if manifest_bytes != canonical(manifest) or candidate_manifest.validate_manifest(manifest):
        raise PublicationError("CANDIDATE_MANIFEST_INVALID")
    expected_metadata = {
        "schemaVersion": 1, "stage": "final", "candidateId": candidate_id,
        "repository": REPOSITORY, "commitSha": sha, "workflowRunId": run_id,
        "workflowAttempt": attempt, "manifestSha256": digest(manifest_bytes),
    }
    metadata_bytes = (manifest_dir / "metadata.json").read_bytes()
    try:
        metadata_value = json.loads(metadata_bytes)
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        raise PublicationError("CANDIDATE_METADATA_INVALID") from exc
    if (
        metadata_bytes != canonical(expected_metadata)
        or metadata_value != expected_metadata
    ):
        raise PublicationError("CANDIDATE_METADATA_INVALID")
    outcome_artifact = selected["candidate-outcome"]
    outcome_zip = directory / "outcome.zip"
    outcome_zip.write_bytes(transport.bytes(
        f"/repos/{REPOSITORY}/actions/artifacts/{outcome_artifact['id']}/zip",
        artifact_io.MAX_ZIP,
    ))
    outcome_dir = directory / "outcome"
    try:
        artifact_io.safe_extract_named(
            outcome_zip, outcome_dir, _artifact_digest(outcome_artifact),
            {"outcome.json": 16 * 1024, "outcome.json.sha256": 128}, "outcome.json",
        )
    except (OSError, EOFError, ValueError, zipfile.BadZipFile) as exc:
        raise PublicationError("CANDIDATE_ARTIFACT_INVALID") from exc
    outcome_bytes = (outcome_dir / "outcome.json").read_bytes()
    try:
        outcome_value = json.loads(outcome_bytes)
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        raise PublicationError("CANDIDATE_OUTCOME_INVALID") from exc
    if outcome_bytes != canonical(outcome_value):
        raise PublicationError("CANDIDATE_OUTCOME_INVALID")
    if candidate_outcome.validate(outcome_value) or outcome_value.get("status") != "published":
        raise PublicationError("CANDIDATE_OUTCOME_INVALID")
    if (
        outcome_value.get("candidateId") != candidate_id
        or outcome_value.get("commitSha") != sha
        or outcome_value.get("workflowRunId") != run_id
        or outcome_value.get("workflowAttempt") != attempt
        or outcome_value.get("candidateArtifactId") != str(manifest_artifact["id"])
        or "sha256:" + outcome_value.get("candidateArtifactDigest", "") != _artifact_digest(manifest_artifact)
    ):
        raise PublicationError("CANDIDATE_BINDING_INVALID")
    binding = {
        "manifestSha256": digest(manifest_bytes), "artifactId": manifest_artifact["id"],
        "artifactDigest": _artifact_digest(manifest_artifact), "workflowRunId": run_id,
        "workflowAttempt": attempt,
    }
    return manifest, binding


def load_remote_history(
    transport: GhTransport,
    ignore_draft: tuple[int, str, str, bytes] | None = None,
) -> tuple[list[dict[str, Any]], dict[str, str]]:
    releases = transport.list_pages(f"/repos/{REPOSITORY}/releases")
    records = []
    for release in releases:
        release_id = validate_release_identity(release)
        if ignore_draft is not None and release_id == ignore_draft[0]:
            validate_release_state(
                release, release_id=ignore_draft[0], tag=ignore_draft[1],
                sha=ignore_draft[2], notes_bytes=ignore_draft[3], draft=True,
            )
            continue
        if release.get("draft") is True or release.get("prerelease") is True:
            raise PublicationError("HISTORY_UNPUBLISHED_RELEASE")
        assets = validate_release_assets(release.get("assets"))
        # Validate every asset before allowing the first asset ID into an endpoint.
        blobs = {}
        for asset in assets:
            blobs[asset["name"]] = transport.bytes(
                f"/repos/{REPOSITORY}/releases/assets/{asset['id']}",
                ASSETS[asset["name"]][0], "Accept: application/octet-stream",
            )
        try:
            manifest = json.loads(blobs["release.json"])
        except (KeyError, json.JSONDecodeError) as exc:
            raise PublicationError("HISTORY_MANIFEST_INVALID") from exc
        records.append({"release": release, "manifest": manifest, "assets": assets, "assetBytes": blobs})
    refs = transport.list_pages(f"/repos/{REPOSITORY}/git/matching-refs/tags/v")
    tags = {}
    for ref in refs:
        raw_ref = ref.get("ref") if isinstance(ref, dict) else None
        if not isinstance(raw_ref, str) or not raw_ref.startswith("refs/tags/"):
            raise PublicationError("HISTORY_TAG_INVALID")
        name = raw_ref.removeprefix("refs/tags/")
        sha = validate_tag_ref(ref, name)
        if name in tags:
            raise PublicationError("HISTORY_TAG_INVALID")
        tags[name] = sha
    return records, tags


def revalidate_remote_context(
    plan: dict[str, Any], transport: GhTransport
) -> list[dict[str, Any]]:
    event = read_event(Path(os.environ["GITHUB_EVENT_PATH"]))
    operation, request = request_from_event(event)
    if (
        operation != plan["operationId"]
        or digest(canonical(request)) != plan["requestSha256"]
        or request["candidateId"] != plan["candidate"]["candidateId"]
    ):
        raise PublicationError("PLAN_REQUEST_BINDING_INVALID")
    current = transport.current_run(os.environ["GITHUB_RUN_ID"])
    validate_identity(dict(os.environ), event, current, operation)
    with tempfile.TemporaryDirectory() as raw:
        _, binding = resolve_candidate(
            transport, request["candidateId"], Path(raw) / "candidate"
        )
    expected_binding = {
        "candidateId": request["candidateId"],
        "manifestSha256": binding["manifestSha256"],
        "artifactId": str(binding["artifactId"]),
        "artifactDigest": binding["artifactDigest"],
        "workflowRunId": str(binding["workflowRunId"]),
        "workflowAttempt": binding["workflowAttempt"],
    }
    if plan["candidate"] != expected_binding:
        raise PublicationError("PLAN_CANDIDATE_BINDING_INVALID")
    records, tags = load_remote_history(transport)
    history = validate_history(records, tags)
    if history_snapshot(snapshot_items(history)) != plan["historySnapshotSha256"]:
        raise PublicationError("HISTORY_SNAPSHOT_CHANGED")
    matches = [
        record for record in history
        if record["manifest"]["candidate"]["candidateId"] == request["candidateId"]
    ]
    if (plan["mode"] == "publish" and matches) or (
        plan["mode"] == "already_published" and len(matches) != 1
    ):
        raise PublicationError("PUBLICATION_MODE_STALE")
    return history


def _write_output(name: str, value: str) -> None:
    if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", name) or "\n" in value or "\r" in value:
        raise PublicationError("OUTPUT_INVALID")
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with Path(output).open("a", encoding="utf-8", newline="\n") as stream:
            stream.write(f"{name}={value}\n")


def _read_canonical(path: Path) -> dict[str, Any]:
    data = path.read_bytes()
    try:
        value = json.loads(data)
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        raise PublicationError("BUNDLE_JSON_INVALID") from exc
    if not isinstance(value, dict) or data != canonical(value):
        raise PublicationError("BUNDLE_CANONICAL_INVALID")
    return value


def _verify_pair(path: Path) -> bytes:
    data = path.read_bytes()
    sidecar = path.with_name(path.name + ".sha256").read_bytes()
    if sidecar != (digest(data)[7:] + "\n").encode("ascii"):
        raise PublicationError("BUNDLE_SIDECAR_INVALID")
    return data


def _run_git(
    arguments: list[str],
    *,
    text: bool = False,
    stdout: Any = subprocess.PIPE,
) -> subprocess.CompletedProcess[Any]:
    try:
        completed = subprocess.run(
            ["git", *arguments], check=False, stdout=stdout,
            stderr=subprocess.DEVNULL, text=text,
            timeout=REMOTE_TIMEOUT_SECONDS,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise PublicationError("GIT_CONTEXT_INVALID") from exc
    if completed.returncode != 0:
        raise PublicationError("GIT_CONTEXT_INVALID")
    return completed


def _candidate_workspace(sha: str) -> tuple[Path, Callable[[], None]]:
    _run_git(["cat-file", "-e", f"{sha}^{{commit}}"])
    _run_git(["merge-base", "--is-ancestor", sha, "origin/main"])
    current = _run_git(["rev-parse", "HEAD"], text=True).stdout.strip()
    if current == sha:
        return ROOT, lambda: None
    temporary = Path(tempfile.mkdtemp(prefix="release-source-"))
    archive = temporary / "migrations.tar"
    try:
        with archive.open("wb") as stream:
            _run_git([
                "archive", "--format=tar", sha, "--",
                "backend/src/main/resources/db/migration",
                "website_back/src/main/resources/db/migration",
            ], stdout=stream)
        extract_root = temporary / "workspace"
        extract_root.mkdir()
        allowed_roots = tuple(contract[2] + "/" for contract in global_release.DATABASES)
        with tarfile.open(archive) as bundle:
            for member in bundle.getmembers():
                pure = Path(member.name)
                if pure.is_absolute() or ".." in pure.parts or member.issym() or member.islnk():
                    raise PublicationError("ARCHIVE_ENTRY_INVALID")
                if member.isdir():
                    continue
                if not member.isfile() or not member.name.startswith(allowed_roots):
                    raise PublicationError("ARCHIVE_ENTRY_INVALID")
                target = extract_root / pure
                target.parent.mkdir(parents=True, exist_ok=True)
                source = bundle.extractfile(member)
                if source is None:
                    raise PublicationError("ARCHIVE_ENTRY_INVALID")
                target.write_bytes(source.read())
        return extract_root, lambda: shutil.rmtree(temporary, ignore_errors=True)
    except PublicationError:
        shutil.rmtree(temporary, ignore_errors=True)
        raise
    except (OSError, EOFError, tarfile.TarError) as exc:
        shutil.rmtree(temporary, ignore_errors=True)
        raise PublicationError("ARCHIVE_ENTRY_INVALID") from exc


def _validate_plan_handoff(
    directory: Path,
) -> tuple[dict[str, Any], dict[str, bytes], bytes | None]:
    plan_dir = directory / "plan"
    plan_bytes = _verify_pair(plan_dir / "plan.json")
    plan = _read_canonical(plan_dir / "plan.json")
    validate_schema(plan, PLAN_SCHEMA)
    metadata_value = _read_canonical(plan_dir / "metadata.json")
    if metadata_value != metadata(
        "release-publication-plan", plan["operationId"], plan["workflow"]["runId"],
        plan["workflow"]["attempt"], plan_bytes,
    ):
        raise PublicationError("PLAN_METADATA_INVALID")
    release_dir = directory / "release"
    bundle: dict[str, bytes] = {}
    notes_bytes: bytes | None = None
    if plan["mode"] == "publish":
        if not release_dir.is_dir() or not (release_dir / "release.json").is_file():
            raise PublicationError("RELEASE_BUNDLE_MISSING")
        for name in ASSETS:
            bundle[name] = (release_dir / name).read_bytes()
        release = json.loads(bundle["release.json"])
        if bundle["release.json"] != canonical(release):
            raise PublicationError("RELEASE_CANONICAL_INVALID")
        if bundle["release.json.sha256"] != (digest(bundle["release.json"])[7:] + "\n").encode():
            raise PublicationError("RELEASE_SIDECAR_INVALID")
        if bundle["metadata.json"] != canonical(global_release.metadata_for(release, bundle["release.json"])):
            raise PublicationError("RELEASE_METADATA_INVALID")
        errors = global_release.validate_release(release)
        if errors or plan["target"]["manifestSha256"] != digest(bundle["release.json"]):
            raise PublicationError("RELEASE_MANIFEST_INVALID")
        if {path.name for path in release_dir.iterdir()} != set(ASSETS) | {"notes.md"}:
            raise PublicationError("RELEASE_BUNDLE_FILES_INVALID")
        notes_bytes = (release_dir / "notes.md").read_bytes()
        validate_notes(notes_bytes)
        if notes_bytes != notes_for(release, plan["operationId"]):
            raise PublicationError("RELEASE_NOTES_INVALID")
    elif release_dir.exists():
        raise PublicationError("ALREADY_PUBLISHED_HANDOFF_INVALID")
    expected = {"plan", "release"} if plan["mode"] == "publish" else {"plan"}
    if {path.name for path in directory.iterdir()} != expected:
        raise PublicationError("HANDOFF_FILES_INVALID")
    return plan, bundle, notes_bytes


def _cli_trust(args: argparse.Namespace) -> int:
    event = read_event(Path(os.environ["GITHUB_EVENT_PATH"]))
    operation, request = request_from_event(event)
    transport = GhTransport()
    current = transport.current_run(os.environ["GITHUB_RUN_ID"])
    validate_identity(dict(os.environ), event, current, operation)
    # The workflow checks out with fetch-depth 0 and persist-credentials false,
    # so origin/main is already present and no authenticated fetch is possible.
    _run_git(["merge-base", "--is-ancestor", os.environ["GITHUB_SHA"], "origin/main"])
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=False)
    (output / "event.json").write_bytes(canonical(event))
    (output / "request.json").write_bytes(canonical(request))
    (output / "identity.json").write_bytes(canonical({
        "operationId": operation, "repository": REPOSITORY,
        "runId": os.environ["GITHUB_RUN_ID"], "attempt": int(os.environ["GITHUB_RUN_ATTEMPT"]),
        "actor": os.environ["GITHUB_ACTOR"], "actorId": os.environ["GITHUB_ACTOR_ID"],
        "headSha": os.environ["GITHUB_SHA"], "createdAt": current["created_at"],
    }))
    _write_output("mode", "trusted")
    return 0


def _cli_prepare(args: argparse.Namespace) -> int:
    trust = Path(args.trust)
    request = _read_canonical(trust / "request.json")
    identity = _read_canonical(trust / "identity.json")
    operation = identity["operationId"]
    transport = GhTransport()
    with tempfile.TemporaryDirectory() as raw:
        candidate, binding = resolve_candidate(
            transport, request["candidateId"], Path(raw) / "candidate"
        )
    sha, _, _ = parse_candidate_id(request["candidateId"])
    if candidate.get("commitSha") != sha:
        raise PublicationError("CANDIDATE_BINDING_INVALID")
    workspace, cleanup = _candidate_workspace(sha)
    try:
        records, tags = load_remote_history(transport)
        history = validate_history(records, tags)
        existing = [record["manifest"] for record in history]
        matching = [item for item in existing if item["candidate"]["candidateId"] == request["candidateId"]]
        release = None
        if not matching:
            release = global_release.build_release(
                candidate, request, existing, candidate_artifact_id=str(binding["artifactId"]),
                candidate_artifact_digest=binding["artifactDigest"],
                published_at=identity["createdAt"], workflow_run_id=identity["runId"],
                workflow_attempt=identity["attempt"], actor=identity["actor"],
                actor_id=identity["actorId"], workspace=workspace,
            )
        workflow = {
            "runId": identity["runId"], "attempt": identity["attempt"],
            "actor": identity["actor"], "actorId": identity["actorId"],
            "event": "workflow_dispatch",
        }
        plan = build_plan(operation, request, candidate, binding, history, workflow, release)
        output = Path(args.output)
        output.mkdir(parents=True, exist_ok=False)
        plan_dir = output / "plan"
        plan_dir.mkdir()
        plan_data = canonical(plan)
        (plan_dir / "plan.json").write_bytes(plan_data)
        (plan_dir / "plan.json.sha256").write_text(digest(plan_data)[7:] + "\n", encoding="ascii")
        (plan_dir / "metadata.json").write_bytes(canonical(metadata(
            "release-publication-plan", operation, identity["runId"], identity["attempt"], plan_data
        )))
        if release is not None:
            release_dir = output / "release"
            global_release.write_release_bundle(release_dir, release)
            (release_dir / "notes.md").write_bytes(notes_for(release, operation))
        _validate_plan_handoff(output)
        _write_output("mode", plan["mode"])
        return 0
    finally:
        cleanup()


def _cli_publish(args: argparse.Namespace) -> int:
    plan, bundle, notes_bytes = _validate_plan_handoff(Path(args.handoff))
    if notes_bytes is None:
        raise PublicationError("RELEASE_NOTES_INVALID")
    transport = GhTransport()
    revalidate_remote_context(plan, transport)
    snapshot = plan["historySnapshotSha256"]
    final = publish_transaction(transport, plan, bundle, snapshot, notes_bytes)
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=False)
    (output / "publication.json").write_bytes(canonical({
        "schemaVersion": 1, "releaseId": str(final["id"]),
        "release": plan["target"]["release"], "sourceCommit": plan["target"]["sourceCommit"],
    }))
    return 0


def _cli_outcome(args: argparse.Namespace) -> int:
    plan, bundle, notes_bytes = _validate_plan_handoff(Path(args.handoff))
    expected_mode = os.environ.get("EXPECTED_MODE")
    publish_result = os.environ.get("PUBLISH_RESULT")
    if expected_mode != plan["mode"]:
        raise PublicationError("OUTCOME_MODE_INVALID")
    if (plan["mode"] == "publish" and publish_result != "success") or (
        plan["mode"] == "already_published" and publish_result != "skipped"
    ):
        raise PublicationError("OUTCOME_JOB_RESULT_INVALID")
    if plan["mode"] == "publish":
        publication = _read_canonical(Path(args.publication) / "publication.json")
        if set(publication) != {
            "schemaVersion", "releaseId", "release", "sourceCommit"
        } or publication.get("schemaVersion") != 1:
            raise PublicationError("PUBLICATION_HANDOFF_INVALID")
        release_id_int = _positive_decimal(
            publication.get("releaseId"), "PUBLICATION_HANDOFF_INVALID"
        )
        if (
            publication.get("release") != plan["target"]["release"]
            or publication.get("sourceCommit") != plan["target"]["sourceCommit"]
        ):
            raise PublicationError("PUBLICATION_HANDOFF_INVALID")
        release_id = str(release_id_int)
        status = "published"
        remote = GhTransport()
        if (
            notes_bytes is None
            or not remote.final_state(
                release_id_int, plan["target"]["release"],
                plan["target"]["sourceCommit"], notes_bytes,
            )["valid"]
            or remote.download_assets(
                release_id_int, plan["target"]["release"],
                plan["target"]["sourceCommit"], notes_bytes, False,
            ) != bundle
        ):
            raise PublicationError("PUBLICATION_RECONCILIATION_FAILED")
    else:
        release_id = plan["target"]["existingReleaseId"]
        status = "already_published"
        history, tags = load_remote_history(GhTransport())
        validated = validate_history(history, tags)
        matches = [record for record in validated if str(record["release"]["id"]) == release_id]
        if len(matches) != 1 or matches[0]["manifest"]["candidate"]["candidateId"] != plan["candidate"]["candidateId"]:
            raise PublicationError("ALREADY_PUBLISHED_RECONCILIATION_FAILED")
    current = {
        "runId": os.environ["CURRENT_RUN_ID"], "attempt": int(os.environ["CURRENT_RUN_ATTEMPT"]),
        "actor": os.environ["CURRENT_ACTOR"], "actorId": os.environ["CURRENT_ACTOR_ID"],
    }
    recorded = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    outcome_value = build_outcome(plan, release_id, current, recorded, status)
    data = canonical(outcome_value)
    write_bundle(
        Path(args.output), "outcome.json", outcome_value,
        metadata("release-publication-outcome", plan["operationId"], current["runId"], current["attempt"], data),
    )
    return 0


def _cli_validate(args: argparse.Namespace) -> int:
    value = json.loads(Path(args.path).read_text(encoding="utf-8"))
    validate_schema(value, PLAN_SCHEMA if args.kind == "plan" else OUTCOME_SCHEMA)
    print(f"release-publication-{args.kind}:valid")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    trust = sub.add_parser("trust")
    trust.add_argument("--output", required=True)
    trust.set_defaults(handler=_cli_trust)
    prepare = sub.add_parser("prepare")
    prepare.add_argument("--trust", required=True)
    prepare.add_argument("--output", required=True)
    prepare.set_defaults(handler=_cli_prepare)
    publish = sub.add_parser("publish")
    publish.add_argument("--handoff", required=True)
    publish.add_argument("--output", required=True)
    publish.set_defaults(handler=_cli_publish)
    outcome_command = sub.add_parser("outcome")
    outcome_command.add_argument("--handoff", required=True)
    outcome_command.add_argument("--publication", required=True)
    outcome_command.add_argument("--output", required=True)
    outcome_command.set_defaults(handler=_cli_outcome)
    for kind in ("plan", "outcome"):
        command = sub.add_parser(f"validate-{kind}")
        command.add_argument("--path", required=True)
        command.set_defaults(handler=_cli_validate, kind=kind)
    args = parser.parse_args(argv)
    try:
        return args.handler(args)
    except (PublicationError, OSError, ValueError, json.JSONDecodeError) as exc:
        code = exc.code if isinstance(exc, PublicationError) else "INVALID"
        print(f"release-publication:invalid:{code}", file=sys.stderr)
        return 3
    except Exception:
        print("release-publication:invalid:INVALID", file=sys.stderr)
        return 3


if __name__ == "__main__":
    raise SystemExit(main())
