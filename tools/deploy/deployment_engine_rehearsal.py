#!/usr/bin/env python3
"""Trusted, isolated rehearsal of the real production deployment transaction."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import secrets
import shutil
import stat
import subprocess
import sys
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/releases"))
sys.path.insert(0, str(ROOT / "tools/deploy"))
import deployment_plan
import global_release
import release_publication

REPOSITORY = "greggorio/abaronesa-emporio"
WORKFLOW = ".github/workflows/verify-deployment-engine.yml"
REF = "refs/heads/main"
RELEASE = "v0.1.1"
PREVIOUS_RELEASE = "v0.1.0"
RELEASE_ID = 365219520
OPERATION = "deployment_engine_rehearsal_v011"
PROJECT = "abaronesa-emporio"
ROOT_PREFIX = ".s46-engine-"
EXPECTED_STEPS = ("PULL", "BACKUP", "MIGRATE", "UPDATE", "VERIFY", "COMMIT_STATE", "ROLLBACK")
POSTGRES_IMAGE = "postgres:16.10-alpine3.22@sha256:029660641a0cfc575b14f336ba448fb8a75fd595d42e1fa316b9fb4378742297"
TRUST_FILE = "deployment-engine-trust.json"
REHEARSAL_FILE = "deployment-engine-rehearsal.json"
OUTCOME_FILE = "deployment-engine-rehearsal-outcome.json"
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
POSITIVE_RE = re.compile(r"^[1-9][0-9]*$")
MAX_OUTPUT = 128 * 1024
CLI_TIMEOUT = 3600
CLEANUP_DEADLINE = 600
CLI_EXITS = frozenset({0, 2, 3, 4, 6, 20, 21})
CLI_CAUSE_RE = re.compile(r"^[A-Z][A-Z0-9_]{2,63}$")
CLI_CAUSE_UNAVAILABLE = "CLI_CAUSE_UNAVAILABLE"
CLI_CAUSES = frozenset(
    {
        "ATOMICITY_FAILED",
        "BACKUP_CONFLICT",
        "BACKUP_DUMP_FAILED",
        "BACKUP_FAILED",
        "BACKUP_INVALID",
        "BACKUP_IO_FAILED",
        "BACKUP_POSTGRES_FAILED",
        "BINARY_UNAVAILABLE",
        "BUNDLE_CONFLICT",
        "COMMAND_FAILED",
        "COMMAND_TIMEOUT",
        "COMMIT_STATE_FAILED",
        "COMPOSE_CONFIG_FAILED",
        "CONTAINER_PROBE_FAILED",
        "CURRENT_STATE_CONFLICT",
        "CURRENT_STATE_MISMATCH",
        "DEPENDENCY_UNAVAILABLE",
        "DOCKER_CONFIG_INVALID",
        "INVALID_ACTION_CONTEXT",
        "INVALID_ADAPTER_RESULT",
        "INVALID_ARGUMENT",
        "INVALID_BUNDLE",
        "INVALID_CLOCK",
        "INVALID_COMMAND",
        "INVALID_CONTRACT",
        "INVALID_GATEWAY_PORT",
        "INVALID_PROCESS_OUTPUT",
        "INVALID_PROCESS_RESULT",
        "INVALID_SMOKE_TARGET",
        "JOURNAL_CORRUPT",
        "JOURNAL_IO_FAILED",
        "LINK_RECONCILIATION_FAILED",
        "MIGRATION_COMMAND_FAILED",
        "MIGRATION_FAILED",
        "NON_FORWARD_MIGRATION",
        "OPERATION_CONFLICT",
        "OPERATIONAL_FAILURE",
        "OPERATIONAL_IO_FAILED",
        "PRODUCTION_OPERATION_ACTIVE",
        "PULL_COMMAND_FAILED",
        "PULL_FAILED",
        "RELEASE_CHAIN_MISMATCH",
        "RELEASE_MISMATCH",
        "ROLLBACK_COMMAND_FAILED",
        "ROLLBACK_FAILED",
        "SOURCE_BUNDLE_INVALID",
        "UNSAFE_BINARY",
        "UNSAFE_LINK_STATE",
        "UNSAFE_PATH",
        "UPDATE_COMMAND_FAILED",
        "UPDATE_FAILED",
        "VERIFY_FAILED",
    }
)
JOURNAL_STATES = frozenset({"QUEUED", "PULLING", "BACKING_UP", "MIGRATING", "UPDATING", "VERIFYING", "ROLLING_BACK", "SUCCEEDED", "ROLLED_BACK", "FAILED"})
STEP_STATUSES = frozenset({"PENDING", "RUNNING", "SUCCEEDED", "FAILED", "SKIPPED"})
FAILED_STAGES = frozenset(
    {
        "PREPARE_ROOT",
        "BUNDLE_GENERATION",
        "DEPLOYMENT_CLI",
        "TRANSACTION_EVIDENCE",
        "CLEANUP",
    }
)
STAGE_ERRORS = {
    "PREPARE_ROOT": "PREPARE_ROOT_FAILED",
    "BUNDLE_GENERATION": "BUNDLE_GENERATION_FAILED",
    "DEPLOYMENT_CLI": "DEPLOYMENT_CLI_FAILED",
    "TRANSACTION_EVIDENCE": "TRANSACTION_EVIDENCE_FAILED",
    "CLEANUP": "CLEANUP_INCOMPLETE",
}


class RehearsalError(RuntimeError):
    pass


class CleanupError(RehearsalError):
    def __init__(self, code: str, counts: dict[str, int]):
        super().__init__(code)
        self.counts = counts


def canonical(value: Any) -> bytes:
    return (json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False) + "\n").encode()


def digest(payload: bytes) -> str:
    return "sha256:" + hashlib.sha256(payload).hexdigest()


def _required(name: str) -> str:
    value = os.environ.get(name)
    if not isinstance(value, str) or not value:
        raise RehearsalError(f"MISSING_{name}")
    return value


def _positive(name: str) -> int:
    value = _required(name)
    if POSITIVE_RE.fullmatch(value) is None:
        raise RehearsalError(f"INVALID_{name}")
    return int(value)


def binding() -> dict[str, Any]:
    sha = _required("TRUSTED_SHA")
    if SHA_RE.fullmatch(sha) is None:
        raise RehearsalError("INVALID_SHA")
    return {
        "repository": _required("TRUSTED_REPOSITORY"),
        "workflow": WORKFLOW,
        "workflowRef": _required("TRUSTED_WORKFLOW_REF"),
        "runId": _positive("TRUSTED_RUN_ID"),
        "runAttempt": _positive("TRUSTED_RUN_ATTEMPT"),
        "sha": sha,
        "actorId": _positive("TRUSTED_ACTOR_ID"),
    }


def _write_exclusive(path: Path, payload: bytes, mode: int = 0o600) -> None:
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0), mode)
    with os.fdopen(descriptor, "wb") as stream:
        stream.write(payload)
        stream.flush()
        os.fsync(stream.fileno())
    details = path.lstat()
    if not stat.S_ISREG(details.st_mode) or stat.S_IMODE(details.st_mode) != mode:
        raise RehearsalError("OUTPUT_INVALID")


def _write_bundle(directory: Path, name: str, value: dict[str, Any]) -> None:
    if directory.exists() or directory.is_symlink():
        raise RehearsalError("OUTPUT_EXISTS")
    directory.mkdir(parents=True, mode=0o700)
    payload = canonical(value)
    _write_exclusive(directory / name, payload)
    _write_exclusive(directory / f"{name}.sha256", (digest(payload) + "\n").encode("ascii"))


def _load_bundle(directory: Path, name: str) -> dict[str, Any]:
    if directory.is_symlink() or not directory.is_dir():
        raise RehearsalError("ARTIFACT_INVALID")
    expected = {name, f"{name}.sha256"}
    entries = list(directory.iterdir())
    if {item.name for item in entries} != expected or any(item.is_symlink() or not item.is_file() for item in entries):
        raise RehearsalError("ARTIFACT_INVALID")
    payload = (directory / name).read_bytes()
    if (directory / f"{name}.sha256").read_text(encoding="ascii") != digest(payload) + "\n":
        raise RehearsalError("ARTIFACT_DIGEST_INVALID")
    try:
        value = json.loads(payload)
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise RehearsalError("ARTIFACT_INVALID") from exc
    if not isinstance(value, dict) or payload != canonical(value):
        raise RehearsalError("ARTIFACT_INVALID")
    return value


def trust(output: Path) -> None:
    bound = binding()
    allowlist = _required("DEPLOYER_ACTOR_IDS").split(",")
    if (
        _required("TRUSTED_EVENT") != "workflow_dispatch"
        or _required("TRUSTED_REF") != REF
        or bound["repository"] != REPOSITORY
        or bound["workflowRef"] != f"{REPOSITORY}/{WORKFLOW}@{REF}"
        or _required("TRUSTED_ACTOR") != _required("TRUSTED_TRIGGERING_ACTOR")
        or _positive("TRUSTED_SENDER_ID") != bound["actorId"]
        or str(bound["actorId"]) not in allowlist
        or any(POSITIVE_RE.fullmatch(item) is None for item in allowlist)
    ):
        raise RehearsalError("UNTRUSTED_DISPATCH")
    completed = subprocess.run(
        ("/usr/bin/git", "rev-parse", "HEAD"), check=False,
        stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
        env={"PATH": "/usr/bin:/bin", "LANG": "C.UTF-8", "LC_ALL": "C.UTF-8"},
    )
    if completed.returncode != 0 or completed.stdout != (bound["sha"] + "\n").encode("ascii"):
        raise RehearsalError("CHECKOUT_SHA_MISMATCH")
    _write_bundle(output, TRUST_FILE, {"schemaVersion": 1, "kind": "deployment-engine-trust", **bound, "status": "TRUSTED"})


def _validate_trust(value: dict[str, Any]) -> None:
    expected = {"schemaVersion", "kind", "repository", "workflow", "workflowRef", "runId", "runAttempt", "sha", "actorId", "status"}
    if (
        set(value) != expected
        or value.get("schemaVersion") != 1
        or value.get("kind") != "deployment-engine-trust"
        or value.get("repository") != REPOSITORY
        or value.get("workflow") != WORKFLOW
        or value.get("workflowRef") != f"{REPOSITORY}/{WORKFLOW}@{REF}"
        or value.get("status") != "TRUSTED"
        or not isinstance(value.get("sha"), str)
        or SHA_RE.fullmatch(value["sha"]) is None
    ):
        raise RehearsalError("TRUST_INVALID")


def _release(assets: Path) -> dict[str, Any]:
    expected = {*release_publication.ASSETS, "release-record.json", "tag-ref.json"}
    if assets.is_symlink() or not assets.is_dir() or {item.name for item in assets.iterdir()} != expected:
        raise RehearsalError("RELEASE_INVALID")
    try:
        record = json.loads((assets / "release-record.json").read_text(encoding="utf-8"))
        tag_ref = json.loads((assets / "tag-ref.json").read_text(encoding="utf-8"))
        release_assets = release_publication.validate_release_assets(record.get("assets"))
        payloads = {name: (assets / name).read_bytes() for name in release_publication.ASSETS}
        manifest_raw = payloads["release.json"]
        manifest = json.loads(manifest_raw)
        metadata = json.loads(payloads["metadata.json"])
        release_publication.validate_release_identity(record)
        release_publication.validate_tag_ref(tag_ref, RELEASE, manifest["sourceCommit"])
        release_publication.validate_release_state(
            record, release_id=RELEASE_ID, tag=RELEASE, sha=manifest["sourceCommit"],
            notes_bytes=record["body"].encode(), draft=False, assets_required=True,
        )
        if (
            record.get("id") != RELEASE_ID
            or record.get("tag_name") != RELEASE
            or record.get("name") != RELEASE
            or len(release_assets) != 3
            or manifest_raw != global_release.canonical(manifest)
            or payloads["release.json.sha256"] != (digest(manifest_raw).removeprefix("sha256:") + "\n").encode()
            or metadata != global_release.metadata_for(manifest, manifest_raw)
            or global_release.validate_release(manifest)
            or manifest.get("release") != RELEASE
            or manifest.get("previousRelease") != PREVIOUS_RELEASE
            or len(manifest.get("components", [])) != 6
        ):
            raise ValueError
    except Exception as exc:
        raise RehearsalError("RELEASE_INVALID") from exc
    return manifest


def _run(argv: tuple[str, ...], *, environment: dict[str, str] | None = None, timeout: int = 2700) -> subprocess.CompletedProcess[bytes]:
    completed = subprocess.run(
        argv, check=False, stdin=subprocess.DEVNULL, capture_output=True,
        timeout=timeout, env=environment,
    )
    if len(completed.stdout) > MAX_OUTPUT or len(completed.stderr) > MAX_OUTPUT:
        raise RehearsalError("OUTPUT_LIMIT_EXCEEDED")
    return completed


def _closed_cause(value: Any) -> str | None:
    if value is None:
        return None
    if (
        not isinstance(value, str)
        or CLI_CAUSE_RE.fullmatch(value) is None
        or value not in CLI_CAUSES
    ):
        raise RehearsalError("CLI_EVIDENCE_INVALID")
    return value


def _closed_json(raw: bytes) -> dict[str, Any]:
    if not raw or len(raw) > MAX_OUTPUT or raw.startswith(b"\xef\xbb\xbf"):
        raise RehearsalError("CLI_EVIDENCE_INVALID")
    try:
        value = json.loads(raw.decode("utf-8"))
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise RehearsalError("CLI_EVIDENCE_INVALID") from exc
    if not isinstance(value, dict) or raw != canonical(value):
        raise RehearsalError("CLI_EVIDENCE_INVALID")
    return value


def _journal_projection(path: Path) -> tuple[dict[str, Any], bytes]:
    raw = path.read_bytes()
    value = _closed_json(raw)
    state = value.get("state")
    restore = value.get("databaseRestoreRequired")
    rollback_code = value.get("rollbackErrorCode")
    error_code = value.get("errorCode")
    steps = value.get("steps")
    if (
        state not in JOURNAL_STATES
        or not isinstance(restore, bool)
        or not isinstance(steps, list)
        or len(steps) != len(EXPECTED_STEPS)
    ):
        raise RehearsalError("CLI_EVIDENCE_INVALID")
    projected_steps: list[dict[str, Any]] = []
    for expected_name, step in zip(EXPECTED_STEPS, steps, strict=True):
        if not isinstance(step, dict):
            raise RehearsalError("CLI_EVIDENCE_INVALID")
        name = step.get("name")
        status = step.get("status")
        step_code = step.get("errorCode")
        if name != expected_name or status not in STEP_STATUSES:
            raise RehearsalError("CLI_EVIDENCE_INVALID")
        projected_steps.append(
            {"name": name, "status": status, "errorCode": _closed_cause(step_code)}
        )
    projection = {
        "state": state,
        "errorCode": _closed_cause(error_code),
        "rollbackErrorCode": _closed_cause(rollback_code),
        "databaseRestoreRequired": restore,
        "steps": projected_steps,
    }
    return projection, raw


def _cli_evidence(
    completed: subprocess.CompletedProcess[bytes], journal_path: Path
) -> dict[str, Any]:
    exit_code = completed.returncode if completed.returncode in CLI_EXITS else 6
    unavailable = {
        "cliExit": exit_code,
        "causeCode": CLI_CAUSE_UNAVAILABLE,
        "journal": None,
    }
    try:
        if completed.returncode not in CLI_EXITS:
            raise RehearsalError("CLI_EVIDENCE_INVALID")
        if completed.returncode in {0, 20, 21}:
            if completed.stderr:
                raise RehearsalError("CLI_EVIDENCE_INVALID")
            summary = _closed_json(completed.stdout)
            if set(summary) != {
                "databaseRestoreRequired",
                "errorCode",
                "operationId",
                "state",
            } or summary.get("operationId") != OPERATION:
                raise RehearsalError("CLI_EVIDENCE_INVALID")
            projection, _ = _journal_projection(journal_path)
            if (
                summary.get("state") != projection["state"]
                or summary.get("databaseRestoreRequired")
                != projection["databaseRestoreRequired"]
                or summary.get("errorCode") != projection["errorCode"]
            ):
                raise RehearsalError("CLI_EVIDENCE_INVALID")
            cause = projection["errorCode"]
            if completed.returncode == 0 and (
                projection["state"] != "SUCCEEDED" or cause is not None
            ):
                raise RehearsalError("CLI_EVIDENCE_INVALID")
            if completed.returncode != 0 and cause is None:
                raise RehearsalError("CLI_EVIDENCE_INVALID")
            return {"cliExit": completed.returncode, "causeCode": cause, "journal": projection}
        if completed.stdout:
            raise RehearsalError("CLI_EVIDENCE_INVALID")
        failure = _closed_json(completed.stderr)
        if set(failure) != {"errorCode"}:
            raise RehearsalError("CLI_EVIDENCE_INVALID")
        return {
            "cliExit": completed.returncode,
            "causeCode": _closed_cause(failure["errorCode"]),
            "journal": None,
        }
    except (OSError, RehearsalError):
        return unavailable


def _validate_journal_projection(value: Any) -> None:
    if value is None:
        return
    if not isinstance(value, dict) or set(value) != {
        "state",
        "errorCode",
        "rollbackErrorCode",
        "databaseRestoreRequired",
        "steps",
    }:
        raise RehearsalError("REHEARSAL_ARTIFACT_INVALID")
    if (
        value.get("state") not in JOURNAL_STATES
        or not isinstance(value.get("databaseRestoreRequired"), bool)
        or not isinstance(value.get("steps"), list)
        or len(value["steps"]) != len(EXPECTED_STEPS)
    ):
        raise RehearsalError("REHEARSAL_ARTIFACT_INVALID")
    try:
        _closed_cause(value.get("errorCode"))
        _closed_cause(value.get("rollbackErrorCode"))
        for name, step in zip(EXPECTED_STEPS, value["steps"], strict=True):
            if (
                not isinstance(step, dict)
                or set(step) != {"name", "status", "errorCode"}
                or step.get("name") != name
                or step.get("status") not in STEP_STATUSES
            ):
                raise RehearsalError("REHEARSAL_ARTIFACT_INVALID")
            _closed_cause(step.get("errorCode"))
    except RehearsalError as exc:
        raise RehearsalError("REHEARSAL_ARTIFACT_INVALID") from exc


def _transaction_valid(
    journal: dict[str, Any],
    state: dict[str, Any],
    backup_manifest: dict[str, Any],
    root: Path,
) -> bool:
    steps = journal.get("steps", [])
    return bool(
        journal.get("state") == "SUCCEEDED"
        and journal.get("errorCode") is None
        and journal.get("databaseRestoreRequired") is True
        and tuple(step.get("name") for step in steps) == EXPECTED_STEPS
        and all(step.get("status") == "SUCCEEDED" for step in steps[:6])
        and len(steps) == 7
        and steps[6].get("status") == "PENDING"
        and state.get("release") == RELEASE
        and state.get("reconciled") is True
        and os.readlink(root / "current") == f"releases/{RELEASE}"
        and not (root / "previous").exists()
        and tuple(item.get("id") for item in backup_manifest.get("databases", []))
        == ("erp", "website")
        and all(item.get("size", 0) > 0 for item in backup_manifest["databases"])
    )


def _resolve_postgres_manifest() -> bool:
    try:
        result = _run(
            ("/usr/bin/docker", "manifest", "inspect", POSTGRES_IMAGE),
            timeout=120,
        )
    except (OSError, RehearsalError, subprocess.SubprocessError):
        return False
    return result.returncode == 0


def _overall_status(transaction_status: str, cleanup_status: str) -> str:
    return (
        "SUCCESS"
        if transaction_status == "SUCCESS" and cleanup_status == "SUCCESS"
        else "FAILED"
    )


def _mask(value: str) -> None:
    print(f"::add-mask::{value}")


def _env(root: Path, manifest: dict[str, Any], run_id: int) -> tuple[Path, Path, dict[str, str]]:
    shared = root / "shared"
    shared.mkdir(mode=0o700)
    identity = shared / "synthetic-rsa.pem"
    result = _run(("/usr/bin/openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out", os.fspath(identity)), timeout=60)
    if result.returncode != 0:
        raise RehearsalError("SYNTHETIC_IDENTITY_FAILED")
    identity.chmod(0o600)
    sensitive = {
        "POSTGRES_ADMIN_PASSWORD": secrets.token_hex(24),
        "ERP_DB_PASSWORD": secrets.token_hex(24),
        "WEBSITE_DB_PASSWORD": secrets.token_hex(24),
        "INTEGRATION_SYSTEM_TOKEN_SECRET": secrets.token_hex(64),
        "ERP_WEBSITE_SYNC_KEY": secrets.token_hex(32),
        "GOOGLE_CLIENT_SECRET": secrets.token_hex(24),
    }
    for value in sensitive.values():
        _mask(value)
    resource_names = _resource_names(run_id)
    values = {
        "POSTGRES_IMAGE": POSTGRES_IMAGE,
        "POSTGRES_ADMIN_USER": "rehearsal_admin",
        "ERP_DB_NAME": "rehearsal_erp",
        "ERP_DB_USER": "rehearsal_erp_user",
        "WEBSITE_DB_NAME": "rehearsal_website",
        "WEBSITE_DB_USER": "rehearsal_website_user",
        "GOOGLE_CLIENT_ID": "rehearsal.invalid",
        "GATEWAY_LOOPBACK_PORT": "8120",
        **resource_names,
        "RELEASE_CONTROL_MODE": "disabled",
        "RELEASE_CONTROL_DEPLOYER_IDENTITY_ENABLED": "false",
        "RELEASE_CONTROL_DEPLOYER_IDENTITY_PRIVATE_KEY_FILE": os.fspath(identity),
        **sensitive,
    }
    env_file = shared / ".env"
    _write_exclusive(env_file, "".join(f"{key}={value}\n" for key, value in sorted(values.items())).encode())
    return env_file, identity, values


def _docker_project_ids(*, timeout: int = 30) -> tuple[str, ...]:
    result = _run(("/usr/bin/docker", "ps", "-aq", "--filter", f"label=com.docker.compose.project={PROJECT}"), timeout=timeout)
    if result.returncode != 0:
        raise RehearsalError("DOCKER_INSPECT_FAILED")
    return tuple(sorted(line for line in result.stdout.decode("ascii").splitlines() if line))


def _shred(path: Path, *, timeout: int = 30) -> None:
    if not path.exists():
        return
    result = _run(("/usr/bin/shred", "-u", os.fspath(path)), timeout=timeout)
    if result.returncode != 0 or path.exists():
        raise RehearsalError("SECRET_CLEANUP_FAILED")


def _validate_ephemeral_root(root: Path, *, run_id: int) -> Path:
    trusted_root = ROOT.resolve(strict=True)
    try:
        candidate = root.absolute()
        details = candidate.lstat()
        resolved = candidate.resolve(strict=True)
    except (OSError, RuntimeError) as exc:
        raise RehearsalError("PREPARE_ROOT_FAILED") from exc
    expected_prefix = f"{ROOT_PREFIX}{run_id}-"
    if (
        resolved != candidate
        or resolved.parent != trusted_root
        or not resolved.name.startswith(expected_prefix)
        or len(resolved.name) <= len(expected_prefix)
        or not stat.S_ISDIR(details.st_mode)
        or stat.S_IMODE(details.st_mode) != 0o700
        or details.st_uid != os.geteuid()
        or details.st_gid != os.getegid()
        or stat.S_IMODE(details.st_mode) & 0o077
    ):
        raise RehearsalError("PREPARE_ROOT_FAILED")
    components: list[Path] = []
    component = resolved
    while True:
        components.append(component)
        if component.parent == component:
            break
        component = component.parent
    for component in reversed(components):
        component_details = component.lstat()
        if (
            component.is_symlink()
            or not stat.S_ISDIR(component_details.st_mode)
            or stat.S_IMODE(component_details.st_mode) & 0o022
        ):
            raise RehearsalError("PREPARE_ROOT_FAILED")
    return resolved


def _prepare_root(run_id: int) -> Path:
    trusted_root = ROOT.resolve(strict=True)
    created = Path(
        tempfile.mkdtemp(prefix=f"{ROOT_PREFIX}{run_id}-", dir=trusted_root)
    )
    try:
        created.chmod(0o700)
        return _validate_ephemeral_root(created, run_id=run_id)
    except Exception:
        if created.exists() and not created.is_symlink():
            shutil.rmtree(created)
        raise


def _remove_ephemeral_root(root: Path, *, run_id: int) -> None:
    validated = _validate_ephemeral_root(root, run_id=run_id)
    shutil.rmtree(validated)
    if validated.exists() or validated.is_symlink():
        raise RehearsalError("CLEANUP_INCOMPLETE")


def _materialize_database_initializer(releases: Path) -> Path:
    support = releases / "db"
    support.mkdir(mode=0o700)
    destination = support / "init-databases.sh"
    shutil.copyfile(ROOT / "ops/db/init-databases.sh", destination)
    destination.chmod(0o755)
    return destination


def _resource_names(run_id: int) -> dict[str, str]:
    suffix = f"s46-engine-{run_id}"
    return {
        "APP_NETWORK_NAME": suffix + "-app",
        "DB_NETWORK_NAME": suffix + "-db",
        "POSTGRES_VOLUME_NAME": suffix + "-postgres",
        "BACKEND_UPLOADS_VOLUME_NAME": suffix + "-backend-uploads",
        "WEBSITE_UPLOADS_VOLUME_NAME": suffix + "-website-uploads",
        "WHATSAPP_SESSION_VOLUME_NAME": suffix + "-whatsapp-session",
    }


def _docker_exists(kind: str, value: str, *, timeout: int = 30) -> bool:
    result = _run(("/usr/bin/docker", kind, "inspect", value), timeout=timeout)
    if result.returncode not in {0, 1}:
        raise RehearsalError("DOCKER_INSPECT_FAILED")
    return result.returncode == 0


def _capture_baseline(image_refs: list[str], names: dict[str, str]) -> dict[str, Any]:
    return {
        "containers": frozenset(_docker_project_ids()),
        "images": {image: _docker_exists("image", image) for image in image_refs},
        "volumes": {
            value: _docker_exists("volume", value)
            for key, value in names.items()
            if key.endswith("VOLUME_NAME")
        },
        "networks": {
            value: _docker_exists("network", value)
            for key, value in names.items()
            if key.endswith("NETWORK_NAME")
        },
    }


def _cleanup(
    root: Path | None,
    bundle: Path | None,
    env_file: Path | None,
    identity: Path | None,
    image_refs: list[str],
    names: dict[str, str],
    baseline: dict[str, Any],
    *,
    run_id: int,
) -> dict[str, int]:
    deadline = time.monotonic() + CLEANUP_DEADLINE
    failed = False

    def remaining(cap: int) -> int:
        available = int(deadline - time.monotonic())
        if available <= 0:
            raise RehearsalError("CLEANUP_DEADLINE_EXCEEDED")
        return min(cap, available)

    if bundle is not None and env_file is not None and bundle.is_dir() and env_file.is_file():
        try:
            down = _run((
                "/usr/bin/docker", "compose", "--project-name", PROJECT,
                "--env-file", os.fspath(env_file), "--env-file", os.fspath(bundle / "release.env"),
                "-f", os.fspath(bundle / "compose.prod.yml"), "down", "-v", "--remove-orphans",
            ), timeout=remaining(300))
            failed = down.returncode != 0
        except (OSError, RehearsalError, subprocess.SubprocessError):
            failed = True
    for image in image_refs:
        if baseline.get("images", {}).get(image, False):
            continue
        try:
            if _docker_exists("image", image, timeout=remaining(30)):
                removed = _run(
                    ("/usr/bin/docker", "image", "rm", image),
                    timeout=remaining(120),
                )
                failed = failed or removed.returncode != 0
        except (OSError, RehearsalError, subprocess.SubprocessError):
            failed = True
    if identity is not None:
        try:
            _shred(identity, timeout=remaining(30))
        except (OSError, RehearsalError, subprocess.SubprocessError):
            failed = True
    if env_file is not None:
        try:
            _shred(env_file, timeout=remaining(30))
        except (OSError, RehearsalError, subprocess.SubprocessError):
            failed = True
    counts = {"containers": -1, "volumes": -1, "networks": -1, "images": -1}
    try:
        counts = {
            "containers": len(
                set(_docker_project_ids(timeout=remaining(30)))
                - set(baseline.get("containers", ()))
            ),
            "volumes": 0,
            "networks": 0,
            "images": 0,
        }
    except (OSError, RehearsalError, subprocess.SubprocessError):
        failed = True
    for kind, values in (("volume", [value for key, value in names.items() if key.endswith("VOLUME_NAME")]), ("network", [value for key, value in names.items() if key.endswith("NETWORK_NAME")])):
        for value in values:
            try:
                existed = baseline.get(kind + "s", {}).get(value, False)
                if not existed and _docker_exists(kind, value, timeout=remaining(30)):
                    counts[kind + "s"] += 1
            except (OSError, RehearsalError, subprocess.SubprocessError):
                counts[kind + "s"] = -1
                failed = True
    for image in image_refs:
        if baseline.get("images", {}).get(image, False):
            continue
        try:
            if _docker_exists("image", image, timeout=remaining(30)):
                counts["images"] += 1
        except (OSError, RehearsalError, subprocess.SubprocessError):
            counts["images"] = -1
            failed = True
    if root is not None and (root.exists() or root.is_symlink()):
        try:
            remaining(1)
            _remove_ephemeral_root(root, run_id=run_id)
        except (OSError, RehearsalError):
            failed = True
    if any(counts.values()) or (
        root is not None and (root.exists() or root.is_symlink())
    ) or failed:
        raise CleanupError("CLEANUP_INCOMPLETE", counts)
    return counts


def rehearse(trust_directory: Path, assets: Path, output: Path) -> None:
    trust_value = _load_bundle(trust_directory, TRUST_FILE)
    _validate_trust(trust_value)
    bound = binding()
    if any(trust_value.get(key) != value for key, value in bound.items()):
        raise RehearsalError("TRUST_BINDING_INVALID")
    manifest = _release(assets)
    root: Path | None = None
    bundle: Path | None = None
    env_file: Path | None = None
    identity: Path | None = None
    names = _resource_names(bound["runId"])
    image_refs = [item["immutableRef"] for item in manifest["components"]] + [POSTGRES_IMAGE]
    baseline: dict[str, Any] | None = None
    error_code: str | None = None
    failed_stage: str | None = "PREPARE_ROOT"
    cleanup_counts = {"containers": -1, "volumes": -1, "networks": -1, "images": -1}
    transaction_status = "FAILED"
    cleanup_status = "FAILED"
    postgres_manifest_resolved = False
    cli_evidence: dict[str, Any] = {
        "cliExit": 6,
        "causeCode": CLI_CAUSE_UNAVAILABLE,
        "journal": None,
    }
    journal_sha: str | None = None
    state_sha: str | None = None
    receipt_steps: list[dict[str, Any]] = []
    receipt_backup: list[dict[str, Any]] = []
    receipt_services: list[dict[str, Any]] = []
    current: str | None = None
    previous: str | None = None
    replay_evidence = {
        "journalUnchanged": False,
        "backupUnchanged": False,
        "containersUnchanged": False,
    }
    try:
        baseline = _capture_baseline(image_refs, names)
        if (
            baseline["containers"]
            or any(baseline["volumes"].values())
            or any(baseline["networks"].values())
        ):
            raise RehearsalError("RESOURCE_BASELINE_CONFLICT")
        root = _prepare_root(bound["runId"])
        failed_stage = "BUNDLE_GENERATION"
        releases = root / "releases"
        releases.mkdir(mode=0o700)
        _materialize_database_initializer(releases)
        env_file, identity, environment_values = _env(root, manifest, bound["runId"])
        names = {
            key: value
            for key, value in environment_values.items()
            if key.endswith("VOLUME_NAME") or key.endswith("NETWORK_NAME")
        }
        target = root / "release.json"
        _write_exclusive(target, global_release.canonical(manifest))
        bundle = releases / RELEASE
        planned_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        deployment_plan.generate_bundle(
            target_path=target, current_path=None, current_manifest_path=None,
            compose_path=ROOT / "ops/compose/compose.prod.yml", planned_at=planned_at,
            output_path=bundle,
        )
        failed_stage = "DEPLOYMENT_CLI"
        cli_env = {
            "PATH": "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "LANG": "C.UTF-8", "LC_ALL": "C.UTF-8", "EMPORIO_DEPLOY_ROOT": os.fspath(root),
        }
        command = (sys.executable, os.fspath(ROOT / "tools/deploy/deployment_cli.py"), "deploy", "--operation-id", OPERATION, "--release", RELEASE)
        postgres_manifest_resolved = _resolve_postgres_manifest()
        journal_path = root / "shared/deploy/journals" / f"{OPERATION}.json"
        first = _run(command, environment=cli_env, timeout=CLI_TIMEOUT)
        cli_evidence = _cli_evidence(first, journal_path)
        if first.returncode != 0:
            raise RehearsalError("DEPLOYMENT_CLI_FAILED")
        failed_stage = "TRANSACTION_EVIDENCE"
        state_path = root / "shared/deploy/installed-state.json"
        backup = root / "shared/backups" / OPERATION
        journal_raw = journal_path.read_bytes()
        state_raw = state_path.read_bytes()
        journal = json.loads(journal_raw)
        state = json.loads(state_raw)
        backup_manifest = json.loads((backup / "backup-manifest.json").read_bytes())
        if (
            journal_raw != canonical(journal)
            or state_raw != canonical(state)
            or cli_evidence["journal"] is None
            or not _transaction_valid(journal, state, backup_manifest, root)
        ):
            raise RehearsalError("TRANSACTION_INVALID")
        containers_before = _docker_project_ids()
        backup_before = sorted((item.name, digest(item.read_bytes())) for item in backup.iterdir() if item.is_file())
        replay = _run(command, environment=cli_env)
        if replay.returncode != 0:
            raise RehearsalError("REPLAY_FAILED")
        containers_after = _docker_project_ids()
        backup_after = sorted((item.name, digest(item.read_bytes())) for item in backup.iterdir() if item.is_file())
        if journal_path.read_bytes() != journal_raw or containers_after != containers_before or backup_after != backup_before:
            raise RehearsalError("REPLAY_EFFECT_DETECTED")
        transaction_status = "SUCCESS"
        journal_sha = digest(journal_raw)
        state_sha = digest(state_raw)
        receipt_steps = [
            {"name": item["name"], "status": item["status"]}
            for item in journal["steps"]
        ]
        receipt_backup = [
            {"id": item["id"], "size": item["size"], "sha256": item["sha256"]}
            for item in backup_manifest["databases"]
        ]
        receipt_services = [
            {"id": item["id"], "immutableRef": item["immutableRef"]}
            for item in manifest["components"]
        ] + [{"id": "postgresql", "immutableRef": POSTGRES_IMAGE}]
        current = RELEASE
        replay_evidence = {
            "journalUnchanged": True,
            "backupUnchanged": True,
            "containersUnchanged": True,
        }
        failed_stage = None
    except Exception:
        if failed_stage not in FAILED_STAGES:
            failed_stage = "TRANSACTION_EVIDENCE"
        error_code = STAGE_ERRORS[failed_stage]
    finally:
        try:
            if baseline is None and root is None:
                cleanup_counts = {
                    "containers": 0, "volumes": 0, "networks": 0, "images": 0
                }
            else:
                assert baseline is not None
                cleanup_counts = _cleanup(
                    root, bundle, env_file, identity, image_refs, names, baseline,
                    run_id=bound["runId"],
                )
            cleanup_status = "SUCCESS"
        except CleanupError as exc:
            cleanup_counts = exc.counts
            failed_stage = "CLEANUP"
            error_code = STAGE_ERRORS[failed_stage]
        except (OSError, RehearsalError, subprocess.SubprocessError):
            failed_stage = "CLEANUP"
            error_code = STAGE_ERRORS[failed_stage]
        status = _overall_status(transaction_status, cleanup_status)
        receipt = {
            "schemaVersion": 1,
            "kind": "deployment-engine-rehearsal",
            **bound,
            "release": RELEASE,
            "releaseId": RELEASE_ID,
            "previousRelease": PREVIOUS_RELEASE,
            "operationId": OPERATION,
            "transactionStatus": transaction_status,
            "cleanupStatus": cleanup_status,
            "status": status,
            "errorCode": None if status == "SUCCESS" else (
                error_code or STAGE_ERRORS["TRANSACTION_EVIDENCE"]
            ),
            "failedStage": None if status == "SUCCESS" else (
                failed_stage or "TRANSACTION_EVIDENCE"
            ),
            "cliExit": cli_evidence["cliExit"],
            "causeCode": None if status == "SUCCESS" else (
                cli_evidence["causeCode"] or CLI_CAUSE_UNAVAILABLE
            ),
            "journal": cli_evidence["journal"],
            "postgresManifestResolved": postgres_manifest_resolved,
            "journalSha256": journal_sha,
            "installedStateSha256": state_sha,
            "steps": receipt_steps,
            "backup": receipt_backup,
            "services": receipt_services,
            "current": current,
            "previous": previous,
            "replay": replay_evidence,
            "cleanup": cleanup_counts,
        }
        _write_bundle(output, REHEARSAL_FILE, receipt)
    if receipt.get("status") != "SUCCESS":
        raise RehearsalError(str(receipt["errorCode"]))


def _validate_rehearsal(value: dict[str, Any], *, success: bool) -> None:
    bound = binding()
    expected_keys = {
        "schemaVersion", "kind", "repository", "workflow", "workflowRef",
        "runId", "runAttempt", "sha", "actorId", "release", "releaseId",
        "previousRelease", "operationId", "transactionStatus", "cleanupStatus",
        "status", "errorCode", "failedStage", "cliExit", "causeCode", "journal",
        "postgresManifestResolved", "journalSha256", "installedStateSha256",
        "steps", "backup", "services", "current", "previous", "replay", "cleanup",
    }
    _validate_journal_projection(value.get("journal"))
    if (
        set(value) != expected_keys
        or value.get("schemaVersion") != 1
        or value.get("kind") != "deployment-engine-rehearsal"
        or any(value.get(key) != item for key, item in bound.items())
        or value.get("release") != RELEASE
        or value.get("releaseId") != RELEASE_ID
        or value.get("previousRelease") != PREVIOUS_RELEASE
        or value.get("operationId") != OPERATION
        or value.get("transactionStatus") not in {"SUCCESS", "FAILED"}
        or value.get("cleanupStatus") not in {"SUCCESS", "FAILED"}
        or value.get("status") != ("SUCCESS" if success else "FAILED")
        or (value.get("status") == "SUCCESS")
        != (
            value.get("transactionStatus") == "SUCCESS"
            and value.get("cleanupStatus") == "SUCCESS"
        )
        or value.get("cliExit") not in CLI_EXITS
        or not isinstance(value.get("postgresManifestResolved"), bool)
        or value.get("failedStage")
        not in ({None} if success else FAILED_STAGES)
    ):
        raise RehearsalError("REHEARSAL_ARTIFACT_INVALID")
    if success and (
        value.get("errorCode") is not None
        or value.get("causeCode") is not None
        or value.get("cliExit") != 0
        or not isinstance(value.get("journal"), dict)
        or value.get("journal", {}).get("state") != "SUCCEEDED"
        or value.get("journal", {}).get("databaseRestoreRequired") is not True
        or value.get("journal", {}).get("errorCode") is not None
        or tuple(
            step.get("name") for step in value.get("journal", {}).get("steps", [])
        ) != EXPECTED_STEPS
        or any(
            step.get("status") != "SUCCEEDED"
            for step in value["journal"]["steps"][:6]
        )
        or value["journal"]["steps"][6].get("status") != "PENDING"
        or value.get("current") != RELEASE
        or value.get("previous") is not None
        or len(value.get("services", [])) != 7
        or len(value.get("backup", [])) != 2
        or any(value.get("cleanup", {}).values())
        or value.get("replay") != {"journalUnchanged": True, "backupUnchanged": True, "containersUnchanged": True}
    ):
        raise RehearsalError("REHEARSAL_ARTIFACT_INVALID")
    if not success:
        cause = value.get("causeCode")
        if (
            value.get("errorCode") != STAGE_ERRORS[value["failedStage"]]
            or cause not in CLI_CAUSES | {CLI_CAUSE_UNAVAILABLE}
            or (isinstance(cause, str) and CLI_CAUSE_RE.fullmatch(cause) is None)
            or (
                value.get("cleanupStatus") == "FAILED"
                and value.get("failedStage") != "CLEANUP"
            )
            or (
                value.get("failedStage") == "CLEANUP"
                and value.get("cleanupStatus") != "FAILED"
            )
        ):
            raise RehearsalError("REHEARSAL_ARTIFACT_INVALID")


def outcome(trust_directory: Path, rehearsal_directory: Path, output: Path) -> None:
    trust_value = _load_bundle(trust_directory, TRUST_FILE)
    _validate_trust(trust_value)
    rehearsal_value = _load_bundle(rehearsal_directory, REHEARSAL_FILE)
    success = _required("TRUST_RESULT") == "success" and _required("REHEARSAL_RESULT") == "success"
    _validate_rehearsal(rehearsal_value, success=success)
    _write_bundle(output, OUTCOME_FILE, {
        "schemaVersion": 1, "kind": "deployment-engine-rehearsal-outcome", **binding(),
        "release": RELEASE, "operationId": OPERATION,
        "status": "SUCCESS" if success else "FAILED",
        "errorCode": rehearsal_value.get("errorCode"),
        "failedStage": rehearsal_value.get("failedStage"),
        "transactionStatus": rehearsal_value.get("transactionStatus"),
        "cleanupStatus": rehearsal_value.get("cleanupStatus"),
        "causeCode": rehearsal_value.get("causeCode"),
        "trustResult": _required("TRUST_RESULT"), "rehearsalResult": _required("REHEARSAL_RESULT"),
        "rehearsalSha256": digest(canonical(rehearsal_value)),
        "cleanup": rehearsal_value.get("cleanup"),
    })


def validate(rehearsal_directory: Path, outcome_directory: Path) -> None:
    rehearsal_value = _load_bundle(rehearsal_directory, REHEARSAL_FILE)
    outcome_value = _load_bundle(outcome_directory, OUTCOME_FILE)
    _validate_rehearsal(rehearsal_value, success=True)
    if (
        outcome_value.get("kind") != "deployment-engine-rehearsal-outcome"
        or outcome_value.get("status") != "SUCCESS"
        or outcome_value.get("errorCode") is not None
        or outcome_value.get("failedStage") is not None
        or outcome_value.get("transactionStatus") != "SUCCESS"
        or outcome_value.get("cleanupStatus") != "SUCCESS"
        or outcome_value.get("causeCode") is not None
        or outcome_value.get("trustResult") != "success"
        or outcome_value.get("rehearsalResult") != "success"
        or outcome_value.get("rehearsalSha256") != digest(canonical(rehearsal_value))
        or any(outcome_value.get("cleanup", {}).values())
    ):
        raise RehearsalError("OUTCOME_INVALID")


def parser() -> argparse.ArgumentParser:
    value = argparse.ArgumentParser()
    commands = value.add_subparsers(dest="command", required=True)
    trust_command = commands.add_parser("trust"); trust_command.add_argument("--output", required=True, type=Path)
    rehearse_command = commands.add_parser("rehearse"); rehearse_command.add_argument("--trust", required=True, type=Path); rehearse_command.add_argument("--assets", required=True, type=Path); rehearse_command.add_argument("--output", required=True, type=Path)
    outcome_command = commands.add_parser("outcome"); outcome_command.add_argument("--trust", required=True, type=Path); outcome_command.add_argument("--rehearsal", required=True, type=Path); outcome_command.add_argument("--output", required=True, type=Path)
    validate_command = commands.add_parser("validate"); validate_command.add_argument("--rehearsal", required=True, type=Path); validate_command.add_argument("--outcome", required=True, type=Path)
    return value


def main() -> int:
    args = parser().parse_args()
    try:
        if args.command == "trust": trust(args.output)
        elif args.command == "rehearse": rehearse(args.trust, args.assets, args.output)
        elif args.command == "outcome": outcome(args.trust, args.rehearsal, args.output)
        else: validate(args.rehearsal, args.outcome)
    except (OSError, RehearsalError, subprocess.SubprocessError):
        print("deployment-engine-rehearsal:failed", file=sys.stderr)
        return 3
    print(f"deployment-engine-rehearsal:{args.command}:ok")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
