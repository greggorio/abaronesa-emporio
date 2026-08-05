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
EXPECTED_STEPS = ("PULL", "BACKUP", "MIGRATE", "UPDATE", "VERIFY", "COMMIT_STATE")
POSTGRES_IMAGE = "postgres@sha256:589f3b24f30e60a2b33f79543ed51c8f897589bcde5c59f4dc0e814551eeeb0f"
TRUST_FILE = "deployment-engine-trust.json"
REHEARSAL_FILE = "deployment-engine-rehearsal.json"
OUTCOME_FILE = "deployment-engine-rehearsal-outcome.json"
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
POSITIVE_RE = re.compile(r"^[1-9][0-9]*$")
MAX_OUTPUT = 128 * 1024
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
    suffix = f"s46-engine-{run_id}"
    values = {
        "POSTGRES_IMAGE": POSTGRES_IMAGE,
        "POSTGRES_ADMIN_USER": "rehearsal_admin",
        "ERP_DB_NAME": "rehearsal_erp",
        "ERP_DB_USER": "rehearsal_erp_user",
        "WEBSITE_DB_NAME": "rehearsal_website",
        "WEBSITE_DB_USER": "rehearsal_website_user",
        "GOOGLE_CLIENT_ID": "rehearsal.invalid",
        "GATEWAY_LOOPBACK_PORT": "8120",
        "APP_NETWORK_NAME": suffix + "-app",
        "DB_NETWORK_NAME": suffix + "-db",
        "POSTGRES_VOLUME_NAME": suffix + "-postgres",
        "BACKEND_UPLOADS_VOLUME_NAME": suffix + "-backend-uploads",
        "WEBSITE_UPLOADS_VOLUME_NAME": suffix + "-website-uploads",
        "WHATSAPP_SESSION_VOLUME_NAME": suffix + "-whatsapp-session",
        "RELEASE_CONTROL_MODE": "disabled",
        "RELEASE_CONTROL_DEPLOYER_IDENTITY_ENABLED": "false",
        "RELEASE_CONTROL_DEPLOYER_IDENTITY_PRIVATE_KEY_FILE": os.fspath(identity),
        **sensitive,
    }
    env_file = shared / ".env"
    _write_exclusive(env_file, "".join(f"{key}={value}\n" for key, value in sorted(values.items())).encode())
    return env_file, identity, values


def _docker_project_ids() -> tuple[str, ...]:
    result = _run(("/usr/bin/docker", "ps", "-aq", "--filter", f"label=com.docker.compose.project={PROJECT}"), timeout=30)
    if result.returncode != 0:
        raise RehearsalError("DOCKER_INSPECT_FAILED")
    return tuple(sorted(line for line in result.stdout.decode("ascii").splitlines() if line))


def _shred(path: Path) -> None:
    if not path.exists():
        return
    result = _run(("/usr/bin/shred", "-u", os.fspath(path)), timeout=30)
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


def _cleanup(root: Path | None, bundle: Path | None, env_file: Path | None, identity: Path | None, image_refs: list[str], names: dict[str, str], *, run_id: int) -> dict[str, int]:
    if bundle is not None and env_file is not None and bundle.is_dir() and env_file.is_file():
        _run((
            "/usr/bin/docker", "compose", "--project-name", PROJECT,
            "--env-file", os.fspath(env_file), "--env-file", os.fspath(bundle / "release.env"),
            "-f", os.fspath(bundle / "compose.prod.yml"), "down", "-v", "--remove-orphans",
        ), timeout=300)
    for image in image_refs:
        _run(("/usr/bin/docker", "image", "rm", image), timeout=120)
    if identity is not None:
        _shred(identity)
    if env_file is not None:
        _shred(env_file)
    counts = {"containers": len(_docker_project_ids()), "volumes": 0, "networks": 0, "images": 0}
    for kind, values in (("volume", [value for key, value in names.items() if key.endswith("VOLUME_NAME")]), ("network", [value for key, value in names.items() if key.endswith("NETWORK_NAME")])):
        for value in values:
            result = _run(("/usr/bin/docker", kind, "inspect", value), timeout=30)
            if result.returncode == 0:
                counts[kind + "s"] += 1
    for image in image_refs:
        if _run(("/usr/bin/docker", "image", "inspect", image), timeout=30).returncode == 0:
            counts["images"] += 1
    if root is not None and (root.exists() or root.is_symlink()):
        _remove_ephemeral_root(root, run_id=run_id)
    if any(counts.values()) or (
        root is not None and (root.exists() or root.is_symlink())
    ):
        raise RehearsalError("CLEANUP_INCOMPLETE")
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
    names: dict[str, str] = {}
    image_refs = [item["immutableRef"] for item in manifest["components"]] + [POSTGRES_IMAGE]
    receipt: dict[str, Any] | None = None
    error_code: str | None = None
    failed_stage: str | None = "PREPARE_ROOT"
    cleanup_counts = {"containers": -1, "volumes": -1, "networks": -1, "images": -1}
    try:
        root = _prepare_root(bound["runId"])
        failed_stage = "BUNDLE_GENERATION"
        releases = root / "releases"
        releases.mkdir(mode=0o700)
        support = releases / "db"
        support.mkdir(mode=0o700)
        shutil.copyfile(ROOT / "ops/db/init-databases.sh", support / "init-databases.sh")
        (support / "init-databases.sh").chmod(0o700)
        env_file, identity, names = _env(root, manifest, bound["runId"])
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
        first = _run(command, environment=cli_env)
        if first.returncode != 0:
            raise RehearsalError("DEPLOYMENT_CLI_FAILED")
        failed_stage = "TRANSACTION_EVIDENCE"
        journal_path = root / "shared/deploy/journals" / f"{OPERATION}.json"
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
            or journal.get("state") != "SUCCEEDED"
            or journal.get("databaseRestoreRequired") is not False
            or journal.get("errorCode") is not None
            or tuple(step.get("name") for step in journal.get("steps", [])) != EXPECTED_STEPS
            or any(step.get("status") != "SUCCEEDED" for step in journal["steps"])
            or state.get("release") != RELEASE
            or state.get("reconciled") is not True
            or os.readlink(root / "current") != f"releases/{RELEASE}"
            or (root / "previous").exists()
            or tuple(item.get("id") for item in backup_manifest.get("databases", [])) != ("erp", "website")
            or any(item.get("size", 0) <= 0 for item in backup_manifest["databases"])
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
        receipt = {
            "schemaVersion": 1,
            "kind": "deployment-engine-rehearsal",
            **bound,
            "release": RELEASE,
            "releaseId": RELEASE_ID,
            "previousRelease": PREVIOUS_RELEASE,
            "operationId": OPERATION,
            "status": "SUCCESS",
            "errorCode": None,
            "failedStage": None,
            "journalSha256": digest(journal_raw),
            "installedStateSha256": digest(state_raw),
            "steps": [{"name": item["name"], "status": item["status"]} for item in journal["steps"]],
            "backup": [{"id": item["id"], "size": item["size"], "sha256": item["sha256"]} for item in backup_manifest["databases"]],
            "services": [{"id": item["id"], "immutableRef": item["immutableRef"]} for item in manifest["components"]] + [{"id": "postgresql", "immutableRef": POSTGRES_IMAGE}],
            "current": RELEASE,
            "previous": None,
            "replay": {"journalUnchanged": True, "backupUnchanged": True, "containersUnchanged": True},
            "cleanup": cleanup_counts,
        }
        failed_stage = None
    except Exception:
        if failed_stage not in FAILED_STAGES:
            failed_stage = "TRANSACTION_EVIDENCE"
        error_code = STAGE_ERRORS[failed_stage]
    finally:
        cleanup_failure: RehearsalError | None = None
        try:
            cleanup_counts = _cleanup(
                root, bundle, env_file, identity, image_refs, names,
                run_id=bound["runId"],
            )
        except RehearsalError as exc:
            cleanup_failure = exc
            failed_stage = "CLEANUP"
            error_code = STAGE_ERRORS[failed_stage]
        if receipt is None:
            receipt = {
                "schemaVersion": 1, "kind": "deployment-engine-rehearsal", **bound,
                "release": RELEASE, "releaseId": RELEASE_ID, "previousRelease": PREVIOUS_RELEASE,
                "operationId": OPERATION, "status": "FAILED",
                "errorCode": error_code or STAGE_ERRORS["TRANSACTION_EVIDENCE"],
                "failedStage": failed_stage or "TRANSACTION_EVIDENCE",
                "journalSha256": None, "installedStateSha256": None, "steps": [], "backup": [],
                "services": [], "current": None, "previous": None,
                "replay": {"journalUnchanged": False, "backupUnchanged": False, "containersUnchanged": False},
                "cleanup": cleanup_counts,
            }
        else:
            receipt["cleanup"] = cleanup_counts
            if cleanup_failure is not None:
                receipt["status"] = "FAILED"
                receipt["errorCode"] = error_code
                receipt["failedStage"] = failed_stage
        _write_bundle(output, REHEARSAL_FILE, receipt)
    if receipt.get("status") != "SUCCESS":
        raise RehearsalError(str(receipt["errorCode"]))


def _validate_rehearsal(value: dict[str, Any], *, success: bool) -> None:
    bound = binding()
    if (
        value.get("schemaVersion") != 1
        or value.get("kind") != "deployment-engine-rehearsal"
        or any(value.get(key) != item for key, item in bound.items())
        or value.get("release") != RELEASE
        or value.get("releaseId") != RELEASE_ID
        or value.get("previousRelease") != PREVIOUS_RELEASE
        or value.get("operationId") != OPERATION
        or value.get("status") != ("SUCCESS" if success else "FAILED")
        or value.get("failedStage")
        not in ({None} if success else FAILED_STAGES)
    ):
        raise RehearsalError("REHEARSAL_ARTIFACT_INVALID")
    if success and (
        value.get("errorCode") is not None
        or value.get("current") != RELEASE
        or value.get("previous") is not None
        or len(value.get("services", [])) != 7
        or len(value.get("backup", [])) != 2
        or any(value.get("cleanup", {}).values())
        or value.get("replay") != {"journalUnchanged": True, "backupUnchanged": True, "containersUnchanged": True}
    ):
        raise RehearsalError("REHEARSAL_ARTIFACT_INVALID")
    if not success and value.get("errorCode") != STAGE_ERRORS[value["failedStage"]]:
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
