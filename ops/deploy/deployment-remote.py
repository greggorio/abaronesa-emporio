#!/usr/bin/env python3
"""Closed remote transport boundary for Emporio production deployments.

The program deliberately owns every remote path and executable.  Its command
line carries identifiers only; it never accepts a host, path or command.
"""

from __future__ import annotations

import argparse
import fcntl
import hashlib
import json
import os
import pwd
import re
import selectors
import stat
import subprocess
import sys
import tarfile
import tempfile
import time
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterator, NoReturn


ROOT = Path(__file__).resolve().parents[2]
# The vendored runtime must win over the global interpreter's site-packages,
# which on the production host is Ubuntu 22.04's jsonschema 3.2.0 — proven to
# silently accept invalid Draft 2020-12 prefixItems. Inserted first so it
# shadows anything Python already put on sys.path at startup.
sys.path.insert(0, str(ROOT / "vendor"))
sys.path.insert(0, str(ROOT / "tools" / "deploy"))
# The installed control root is an immutable, manifest-bound package.  Direct
# shebang execution must not add __pycache__ files to that tree.
sys.dont_write_bytecode = True
import deployment_plan  # noqa: E402


DEPLOY_ROOT = Path("/opt/sistemas/emporio")
CONTROL_ROOT = DEPLOY_ROOT / "shared/control"
REMOTE_HELPER = CONTROL_ROOT / "ops/deploy/deployment-remote.py"
DEPLOY_SCRIPT = CONTROL_ROOT / "ops/deploy/deploy-release.sh"
INCOMING_ROOT = DEPLOY_ROOT / "shared/deploy/incoming"
SNAPSHOT_ROOT = DEPLOY_ROOT / "shared/deploy/snapshots"
RELEASES_ROOT = DEPLOY_ROOT / "releases"
INSTALLED_STATE = DEPLOY_ROOT / "shared/deploy/installed-state.json"
SNAPSHOT_SCHEMA = ROOT / "ops/deploy/schemas/production-snapshot.schema.json"
EXPECTED_USER = "deploy-emporio"

OPERATION_RE = re.compile(r"^[A-Za-z0-9_-]{20,128}$")
RELEASE_RE = re.compile(
    r"^v(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)$"
)
DIGEST_RE = re.compile(r"^sha256:[0-9a-f]{64}$")
RAW_DIGEST_RE = re.compile(r"^[0-9a-f]{64}$")
TIME_RE = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$")

ARCHIVE_LIMIT = 16 * 1024 * 1024
STDOUT_LIMIT = 65_536
# Must stay strictly below the transport's client-side execute timeout, which in
# turn must stay well below the deploy job ceiling. When the remote budget is the
# first to expire the helper still returns a sanitized code over a live channel,
# so the transport can persist evidence. If the job ceiling expired first the run
# would be cancelled, no artifact would be uploaded, and the only possible verdict
# would be an evidence-free INDETERMINATE.
EXECUTE_TIMEOUT = 3_600
BUNDLE_FILES = frozenset(
    {
        "manifest.json",
        "compose.prod.yml",
        "release.env",
        "deployment-plan.json",
        "installed-state.next.json",
        "bundle.sha256",
    }
)
SNAPSHOT_COMMON = frozenset(
    {
        "schemaVersion",
        "kind",
        "operationId",
        "targetRelease",
        "mode",
        "capturedAt",
        "currentRelease",
        "installedStateSha256",
        "currentManifestSha256",
    }
)
TERMINAL_EXIT = {"SUCCEEDED": 0, "ROLLED_BACK": 20, "FAILED": 21}


class RemoteError(Exception):
    """Stable public failure without raw operational details."""

    def __init__(self, code: str, exit_code: int = 6):
        super().__init__(code)
        self.code = code
        self.exit_code = exit_code

    def __str__(self) -> str:
        return self.code


def _canonical_bytes(value: Any) -> bytes:
    return json.dumps(
        value, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    ).encode("utf-8")


def _json_file_bytes(value: Any) -> bytes:
    return _canonical_bytes(value) + b"\n"


def _digest(data: bytes) -> str:
    return "sha256:" + hashlib.sha256(data).hexdigest()


def _now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def _fail(code: str, exit_code: int = 6) -> NoReturn:
    raise RemoteError(code, exit_code)


def _validate_operation(value: str) -> str:
    if OPERATION_RE.fullmatch(value) is None:
        _fail("BUNDLE_INVALID", 3)
    return value


def _validate_release(value: str) -> str:
    if RELEASE_RE.fullmatch(value) is None:
        _fail("BUNDLE_INVALID", 3)
    return value


def _validate_digest(value: str) -> str:
    if DIGEST_RE.fullmatch(value):
        return value
    if RAW_DIGEST_RE.fullmatch(value):
        return "sha256:" + value
    _fail("BUNDLE_INVALID", 3)


def _validate_identity() -> None:
    try:
        uid = os.geteuid()
        username = pwd.getpwuid(uid).pw_name
    except (KeyError, OSError) as exc:
        raise RemoteError("REMOTE_CAPABILITY_MISMATCH", 4) from exc
    if uid == 0 or username != EXPECTED_USER:
        _fail("REMOTE_CAPABILITY_MISMATCH", 4)


def _path_components(path: Path) -> list[Path]:
    result: list[Path] = []
    current = Path(os.path.abspath(os.fspath(path)))
    while True:
        result.append(current)
        if current.parent == current:
            return list(reversed(result))
        current = current.parent


def _check_component(path: Path, code: str, *, owned: bool = False) -> os.stat_result:
    try:
        details = path.lstat()
    except OSError as exc:
        raise RemoteError(code, 4) from exc
    if stat.S_ISLNK(details.st_mode) or not stat.S_ISDIR(details.st_mode):
        _fail(code, 4)
    if details.st_mode & 0o022:
        _fail(code, 4)
    if owned and details.st_uid != os.geteuid():
        _fail(code, 4)
    return details


def _validate_root() -> None:
    root = Path(os.path.abspath(os.fspath(DEPLOY_ROOT)))
    if root == Path("/") or root != DEPLOY_ROOT:
        _fail("REMOTE_CAPABILITY_MISMATCH", 4)
    for component in _path_components(root):
        _check_component(component, "REMOTE_CAPABILITY_MISMATCH", owned=component == root)


def _secure_directory(path: Path, code: str, *, mode: int = 0o700) -> None:
    try:
        relative = path.relative_to(DEPLOY_ROOT)
    except ValueError:
        _fail(code, 4)
    current = DEPLOY_ROOT
    for part in relative.parts:
        current = current / part
        _check_component(current, code, owned=True)
    details = _check_component(path, code, owned=True)
    if stat.S_IMODE(details.st_mode) != mode:
        _fail(code, 4)


def _validate_executable(path: Path) -> None:
    try:
        relative = path.relative_to(DEPLOY_ROOT)
    except ValueError:
        _fail("REMOTE_RESULT_UNAVAILABLE", 4)
    current = DEPLOY_ROOT
    for part in relative.parts[:-1]:
        current = current / part
        _check_component(current, "REMOTE_RESULT_UNAVAILABLE")
    try:
        details = path.lstat()
    except OSError as exc:
        raise RemoteError("REMOTE_RESULT_UNAVAILABLE", 4) from exc
    if (
        stat.S_ISLNK(details.st_mode)
        or not stat.S_ISREG(details.st_mode)
        or details.st_uid not in {0, os.geteuid()}
        or details.st_mode & 0o022
        or details.st_mode & 0o111 == 0
    ):
        _fail("REMOTE_RESULT_UNAVAILABLE", 4)


def _regular_bytes(path: Path, code: str, limit: int, *, mode: int = 0o600) -> bytes:
    try:
        details = path.lstat()
        if (
            stat.S_ISLNK(details.st_mode)
            or not stat.S_ISREG(details.st_mode)
            or details.st_uid != os.geteuid()
            or stat.S_IMODE(details.st_mode) != mode
            or details.st_size > limit
        ):
            _fail(code, 4)
        with path.open("rb") as stream:
            data = stream.read(limit + 1)
    except RemoteError:
        raise
    except OSError as exc:
        raise RemoteError(code, 4) from exc
    if len(data) > limit:
        _fail(code, 4)
    return data


def _load_canonical_json(path: Path, code: str) -> tuple[dict[str, Any], bytes]:
    raw = _regular_bytes(path, code, deployment_plan.MAX_JSON_BYTES)
    try:
        value = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise RemoteError(code, 3) from exc
    if not isinstance(value, dict) or raw != _json_file_bytes(value):
        _fail(code, 3)
    return value, raw


def _fsync_directory(path: Path, code: str) -> None:
    try:
        descriptor = os.open(
            path,
            os.O_RDONLY | getattr(os, "O_DIRECTORY", 0) | getattr(os, "O_NOFOLLOW", 0),
        )
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)
    except OSError as exc:
        raise RemoteError(code) from exc


def _write_exclusive(path: Path, data: bytes, code: str) -> None:
    descriptor = -1
    try:
        descriptor = os.open(
            path,
            os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0),
            0o600,
        )
        offset = 0
        while offset < len(data):
            offset += os.write(descriptor, data[offset:])
        os.fsync(descriptor)
    except OSError as exc:
        raise RemoteError(code) from exc
    finally:
        if descriptor >= 0:
            os.close(descriptor)


def _installed_control_sha() -> str:
    """Prove that the bytes running here are the commit they claim to be.

    Without this the workflow would transport a controlSha it can never check
    against the helper actually answering, so a tampered or stale control root
    would look identical to a healthy one.
    """
    manifest_path = CONTROL_ROOT / "control-root.manifest.json"
    sidecar_path = CONTROL_ROOT / "control-root.manifest.json.sha256"
    try:
        payload = manifest_path.read_bytes()
        sidecar = sidecar_path.read_bytes()
    except OSError as exc:
        raise RemoteError("REMOTE_CONTROL_MANIFEST_MISSING", 4) from exc
    if sidecar != hashlib.sha256(payload).hexdigest().encode() + b"\n":
        _fail("REMOTE_CONTROL_MANIFEST_INVALID", 4)
    try:
        manifest = json.loads(payload)
    except ValueError as exc:
        raise RemoteError("REMOTE_CONTROL_MANIFEST_INVALID", 4) from exc
    if not isinstance(manifest, dict):
        _fail("REMOTE_CONTROL_MANIFEST_INVALID", 4)
    source_sha = manifest.get("sourceSha")
    files = manifest.get("files")
    if (
        not isinstance(source_sha, str)
        or re.fullmatch(r"[0-9a-f]{40}", source_sha) is None
        or not isinstance(files, list)
        or not files
    ):
        _fail("REMOTE_CONTROL_MANIFEST_INVALID", 4)
    for entry in files:
        if not isinstance(entry, dict):
            _fail("REMOTE_CONTROL_MANIFEST_INVALID", 4)
        relative, digest = entry.get("path"), entry.get("sha256")
        if not isinstance(relative, str) or not isinstance(digest, str):
            _fail("REMOTE_CONTROL_MANIFEST_INVALID", 4)
        if relative.startswith("/") or ".." in relative.split("/"):
            _fail("REMOTE_CONTROL_MANIFEST_INVALID", 4)
        target = CONTROL_ROOT / relative
        try:
            details = target.lstat()
            if stat.S_ISLNK(details.st_mode) or not stat.S_ISREG(details.st_mode):
                _fail("REMOTE_CONTROL_TAMPERED", 4)
            if hashlib.sha256(target.read_bytes()).hexdigest() != digest:
                _fail("REMOTE_CONTROL_TAMPERED", 4)
        except OSError as exc:
            raise RemoteError("REMOTE_CONTROL_TAMPERED", 4) from exc
    return source_sha


def capabilities() -> dict[str, Any]:
    _validate_identity()
    _validate_root()
    return {
        "controlSha": _installed_control_sha(),
        "deployRoot": "/opt/sistemas/emporio",
        "protocol": "emporio-deployment-transport",
        "schemaVersion": 1,
        "user": EXPECTED_USER,
    }


def _link_release(name: str, code: str) -> str | None:
    link = DEPLOY_ROOT / name
    try:
        details = link.lstat()
    except FileNotFoundError:
        return None
    except OSError as exc:
        raise RemoteError(code, 4) from exc
    if not stat.S_ISLNK(details.st_mode):
        _fail(code, 4)
    try:
        target = os.readlink(link)
    except OSError as exc:
        raise RemoteError(code, 4) from exc
    match = re.fullmatch(
        r"releases/(v(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*))",
        target,
    )
    if match is None:
        _fail(code, 4)
    expected = RELEASES_ROOT / match.group(1)
    try:
        if link.resolve(strict=True) != expected.resolve(strict=True):
            _fail(code, 4)
    except (OSError, RuntimeError) as exc:
        raise RemoteError(code, 4) from exc
    return match.group(1)


def _installed_snapshot(release: str) -> tuple[dict[str, Any], bytes, dict[str, Any], bytes]:
    state, state_raw = _load_canonical_json(INSTALLED_STATE, "REMOTE_SNAPSHOT_INVALID")
    current = _link_release("current", "REMOTE_SNAPSHOT_INVALID")
    if current is None or current != state.get("release"):
        _fail("REMOTE_SNAPSHOT_INVALID", 3)
    bundle = RELEASES_ROOT / current
    try:
        deployment_plan.validate_bundle(bundle)
        deployment_plan.load_current(INSTALLED_STATE)
        manifest, manifest_raw = _load_canonical_json(
            bundle / "manifest.json", "REMOTE_SNAPSHOT_INVALID"
        )
        deployment_plan._validate_current_pair(state, manifest)
    except (deployment_plan.DeploymentPlanError, KeyError, TypeError, ValueError) as exc:
        raise RemoteError("REMOTE_SNAPSHOT_INVALID", 3) from exc
    if release == current:
        _fail("SNAPSHOT_CONFLICT", 3)
    return state, state_raw, manifest, manifest_raw


def _snapshot_value(operation: str, release: str) -> tuple[dict[str, Any], dict[str, bytes]]:
    state_present = INSTALLED_STATE.exists() or INSTALLED_STATE.is_symlink()
    current = _link_release("current", "REMOTE_SNAPSHOT_INVALID")
    previous = _link_release("previous", "REMOTE_SNAPSHOT_INVALID")
    captured = _now()
    if not state_present and current is None and previous is None:
        value = {
            "schemaVersion": 1,
            "kind": "production-snapshot",
            "operationId": operation,
            "targetRelease": release,
            "mode": "FIRST_INSTALL",
            "capturedAt": captured,
            "currentRelease": None,
            "installedStateSha256": None,
            "currentManifestSha256": None,
        }
        return value, {"production-snapshot.json": _json_file_bytes(value)}
    if not state_present or current is None:
        _fail("REMOTE_SNAPSHOT_INVALID", 3)
    state, state_raw, _manifest, manifest_raw = _installed_snapshot(release)
    value = {
        "schemaVersion": 1,
        "kind": "production-snapshot",
        "operationId": operation,
        "targetRelease": release,
        "mode": "UPDATE",
        "capturedAt": captured,
        "currentRelease": state["release"],
        "installedStateSha256": _digest(state_raw),
        "currentManifestSha256": _digest(manifest_raw),
    }
    return value, {
        "production-snapshot.json": _json_file_bytes(value),
        "installed-state.json": state_raw,
        "current-manifest.json": manifest_raw,
    }


def _validate_snapshot_directory(path: Path, operation: str, release: str) -> dict[str, Any]:
    _secure_directory(path, "REMOTE_SNAPSHOT_INVALID")
    try:
        entries = list(path.iterdir())
    except OSError as exc:
        raise RemoteError("REMOTE_SNAPSHOT_INVALID", 4) from exc
    names = {entry.name for entry in entries}
    value, raw = _load_canonical_json(
        path / "production-snapshot.json", "REMOTE_SNAPSHOT_INVALID"
    )
    if (
        set(value) != SNAPSHOT_COMMON
        or value.get("schemaVersion") != 1
        or value.get("kind") != "production-snapshot"
        or value.get("operationId") != operation
        or value.get("targetRelease") != release
        or not isinstance(value.get("capturedAt"), str)
        or TIME_RE.fullmatch(value["capturedAt"]) is None
    ):
        _fail("REMOTE_SNAPSHOT_INVALID", 3)
    try:
        deployment_plan._validate_schema(value, SNAPSHOT_SCHEMA, "REMOTE_SNAPSHOT_INVALID")
    except deployment_plan.DeploymentPlanError as exc:
        raise RemoteError("REMOTE_SNAPSHOT_INVALID", 3) from exc
    mode = value.get("mode")
    if mode == "FIRST_INSTALL":
        if names != {"production-snapshot.json"} or any(
            value.get(field) is not None
            for field in ("currentRelease", "installedStateSha256", "currentManifestSha256")
        ):
            _fail("REMOTE_SNAPSHOT_INVALID", 3)
    elif mode == "UPDATE":
        if names != {
            "production-snapshot.json",
            "installed-state.json",
            "current-manifest.json",
        }:
            _fail("REMOTE_SNAPSHOT_INVALID", 3)
        state, state_raw = _load_canonical_json(
            path / "installed-state.json", "REMOTE_SNAPSHOT_INVALID"
        )
        manifest, manifest_raw = _load_canonical_json(
            path / "current-manifest.json", "REMOTE_SNAPSHOT_INVALID"
        )
        try:
            deployment_plan.load_current(path / "installed-state.json")
            deployment_plan.load_target(path / "current-manifest.json")
            deployment_plan._validate_current_pair(state, manifest)
        except deployment_plan.DeploymentPlanError as exc:
            raise RemoteError("REMOTE_SNAPSHOT_INVALID", 3) from exc
        if (
            value.get("currentRelease") != state.get("release")
            or value.get("installedStateSha256") != _digest(state_raw)
            or value.get("currentManifestSha256") != _digest(manifest_raw)
        ):
            _fail("REMOTE_SNAPSHOT_INVALID", 3)
    else:
        _fail("REMOTE_SNAPSHOT_INVALID", 3)
    if raw != _json_file_bytes(value):
        _fail("REMOTE_SNAPSHOT_INVALID", 3)
    return value


def _validate_snapshot_replay(value: dict[str, Any], release: str) -> None:
    """Prove that a persisted snapshot still describes the live read model."""
    state_present = INSTALLED_STATE.exists() or INSTALLED_STATE.is_symlink()
    current = _link_release("current", "SNAPSHOT_CONFLICT")
    previous = _link_release("previous", "SNAPSHOT_CONFLICT")
    if value["mode"] == "FIRST_INSTALL":
        if state_present or current is not None or previous is not None:
            _fail("SNAPSHOT_CONFLICT", 3)
        return
    if not state_present or current is None:
        _fail("SNAPSHOT_CONFLICT", 3)
    try:
        state, state_raw, _manifest, manifest_raw = _installed_snapshot(release)
    except RemoteError as exc:
        raise RemoteError("SNAPSHOT_CONFLICT", 3) from exc
    if (
        value.get("currentRelease") != state.get("release")
        or value.get("installedStateSha256") != _digest(state_raw)
        or value.get("currentManifestSha256") != _digest(manifest_raw)
    ):
        _fail("SNAPSHOT_CONFLICT", 3)


@contextmanager
def _snapshot_lock() -> Iterator[None]:
    lock = SNAPSHOT_ROOT / ".snapshot.lock"
    descriptor = -1
    try:
        descriptor = os.open(
            lock,
            os.O_RDWR | os.O_CREAT | getattr(os, "O_NOFOLLOW", 0),
            0o600,
        )
        details = os.fstat(descriptor)
        if not stat.S_ISREG(details.st_mode) or details.st_uid != os.geteuid():
            _fail("REMOTE_SNAPSHOT_INVALID", 4)
        fcntl.flock(descriptor, fcntl.LOCK_EX)
        yield
    except RemoteError:
        raise
    except OSError as exc:
        raise RemoteError("REMOTE_SNAPSHOT_INVALID", 4) from exc
    finally:
        if descriptor >= 0:
            try:
                fcntl.flock(descriptor, fcntl.LOCK_UN)
            finally:
                os.close(descriptor)


def snapshot(operation_id: str, release: str) -> dict[str, Any]:
    _validate_identity()
    _validate_root()
    operation = _validate_operation(operation_id)
    target = _validate_release(release)
    _secure_directory(SNAPSHOT_ROOT, "REMOTE_SNAPSHOT_INVALID")
    final = SNAPSHOT_ROOT / operation
    staging = SNAPSHOT_ROOT / f"{operation}.staging"
    with _snapshot_lock():
        if final.exists() or final.is_symlink():
            observed = _validate_snapshot_directory(final, operation, target)
            _validate_snapshot_replay(observed, target)
            return observed
        if staging.exists() or staging.is_symlink():
            # A partial snapshot is evidence of an interrupted write.  It is
            # deliberately preserved for diagnosis/explicit cleanup; snapshot
            # must never silently turn that state into a new observation.
            _fail("REMOTE_SNAPSHOT_INVALID", 3)
        value, files = _snapshot_value(operation, target)
        try:
            staging.mkdir(mode=0o700)
            for name, data in files.items():
                _write_exclusive(staging / name, data, "REMOTE_SNAPSHOT_INVALID")
            _fsync_directory(staging, "REMOTE_SNAPSHOT_INVALID")
            os.replace(staging, final)
            _fsync_directory(SNAPSHOT_ROOT, "REMOTE_SNAPSHOT_INVALID")
        except RemoteError:
            raise
        except OSError as exc:
            raise RemoteError("REMOTE_SNAPSHOT_INVALID") from exc
        observed = _validate_snapshot_directory(final, operation, target)
        if {key: observed[key] for key in observed} != value:
            _fail("SNAPSHOT_CONFLICT", 3)
        return observed


def _archive_file(operation: str) -> Path:
    return INCOMING_ROOT / f"{operation}.tar.part"


def _materialize_database_initializer() -> None:
    """Install the control-root database initializer outside release bundles."""
    source = CONTROL_ROOT / "ops/db/init-databases.sh"
    payload = _regular_bytes(
        source, "REMOTE_CAPABILITY_MISMATCH", ARCHIVE_LIMIT, mode=0o755
    )
    support = RELEASES_ROOT / "db"
    try:
        support.mkdir(mode=0o700)
    except FileExistsError:
        pass
    except OSError as exc:
        raise RemoteError("BUNDLE_INVALID", 4) from exc
    _secure_directory(support, "BUNDLE_INVALID")
    destination = support / "init-databases.sh"
    if destination.exists() or destination.is_symlink():
        if _regular_bytes(
            destination, "BUNDLE_INVALID", ARCHIVE_LIMIT, mode=0o755
        ) == payload:
            return
    staging = support / ".init-databases.sh.staging"
    if staging.exists() or staging.is_symlink():
        _fail("BUNDLE_INVALID", 4)
    try:
        _write_exclusive(staging, payload, "BUNDLE_INVALID")
        staging.chmod(0o755)
        if _regular_bytes(
            staging, "BUNDLE_INVALID", ARCHIVE_LIMIT, mode=0o755
        ) != payload:
            _fail("BUNDLE_INVALID", 4)
        os.replace(staging, destination)
        _fsync_directory(support, "BUNDLE_INVALID")
    except RemoteError:
        raise
    except OSError as exc:
        raise RemoteError("BUNDLE_INVALID", 4) from exc
    if _regular_bytes(
        destination, "BUNDLE_INVALID", ARCHIVE_LIMIT, mode=0o755
    ) != payload:
        _fail("BUNDLE_INVALID", 4)


def _hash_archive(path: Path) -> str:
    try:
        details = path.lstat()
        if (
            stat.S_ISLNK(details.st_mode)
            or not stat.S_ISREG(details.st_mode)
            or details.st_uid != os.geteuid()
            or stat.S_IMODE(details.st_mode) != 0o600
            or details.st_size > ARCHIVE_LIMIT
        ):
            _fail("BUNDLE_INVALID", 4)
        digest = hashlib.sha256()
        total = 0
        descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
        try:
            with os.fdopen(descriptor, "rb") as stream:
                while chunk := stream.read(1024 * 1024):
                    total += len(chunk)
                    if total > ARCHIVE_LIMIT:
                        _fail("BUNDLE_INVALID", 4)
                    digest.update(chunk)
        except Exception:
            raise
    except RemoteError:
        raise
    except OSError as exc:
        raise RemoteError("BUNDLE_INVALID", 4) from exc
    return "sha256:" + digest.hexdigest()


def _validate_members(archive: tarfile.TarFile) -> dict[str, tarfile.TarInfo]:
    result: dict[str, tarfile.TarInfo] = {}
    try:
        members = archive.getmembers()
    except (tarfile.TarError, OSError) as exc:
        raise RemoteError("BUNDLE_INVALID", 3) from exc
    for member in members:
        name = member.name
        if (
            not name.isascii()
            or name not in BUNDLE_FILES
            or name in result
            or member.type not in {tarfile.REGTYPE, tarfile.AREGTYPE}
            or not member.isfile()
            or member.pax_headers
            or stat.S_IMODE(member.mode) != 0o600
            or member.size < 0
            or member.size > ARCHIVE_LIMIT
        ):
            _fail("BUNDLE_INVALID", 3)
        result[name] = member
    if set(result) != BUNDLE_FILES:
        _fail("BUNDLE_INVALID", 3)
    return result


def _extract_archive(path: Path, staging: Path) -> None:
    try:
        with tarfile.open(path, mode="r:") as archive:
            if archive.pax_headers:
                _fail("BUNDLE_INVALID", 3)
            members = _validate_members(archive)
            staging.mkdir(mode=0o700)
            for name in sorted(members):
                source = archive.extractfile(members[name])
                if source is None:
                    _fail("BUNDLE_INVALID", 3)
                destination = staging / name
                descriptor = os.open(
                    destination,
                    os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0),
                    0o600,
                )
                try:
                    total = 0
                    while chunk := source.read(1024 * 1024):
                        total += len(chunk)
                        if total > members[name].size or total > ARCHIVE_LIMIT:
                            _fail("BUNDLE_INVALID", 3)
                        offset = 0
                        while offset < len(chunk):
                            written = os.write(descriptor, chunk[offset:])
                            if written <= 0:
                                _fail("BUNDLE_INVALID")
                            offset += written
                    if total != members[name].size:
                        _fail("BUNDLE_INVALID", 3)
                    os.fsync(descriptor)
                finally:
                    os.close(descriptor)
            _fsync_directory(staging, "BUNDLE_INVALID")
    except RemoteError:
        raise
    except (tarfile.TarError, OSError) as exc:
        raise RemoteError("BUNDLE_INVALID", 3) from exc


def _bundles_identical(left: Path, right: Path) -> bool:
    for name in BUNDLE_FILES:
        try:
            left_raw = _regular_bytes(left / name, "BUNDLE_INVALID", ARCHIVE_LIMIT)
            right_raw = _regular_bytes(right / name, "BUNDLE_INVALID", ARCHIVE_LIMIT)
        except RemoteError:
            raise
        if left_raw != right_raw:
            return False
    return True


def _archive_matches_bundle(archive_path: Path, bundle: Path) -> bool:
    """Validate the upload again and compare every payload byte to a bundle."""
    try:
        deployment_plan.validate_bundle(bundle)
        with tarfile.open(archive_path, mode="r:") as archive:
            if archive.pax_headers:
                _fail("BUNDLE_INVALID", 3)
            members = _validate_members(archive)
            for name in sorted(BUNDLE_FILES):
                expected = _regular_bytes(
                    bundle / name, "BUNDLE_INVALID", ARCHIVE_LIMIT
                )
                source = archive.extractfile(members[name])
                if source is None:
                    _fail("BUNDLE_INVALID", 3)
                observed = source.read(len(expected) + 1)
                if observed != expected or source.read(1):
                    return False
    except RemoteError:
        raise
    except (deployment_plan.DeploymentPlanError, tarfile.TarError, OSError) as exc:
        raise RemoteError("BUNDLE_INVALID", 3) from exc
    return True


def _bundle_release(path: Path) -> str:
    try:
        deployment_plan.validate_bundle(path)
        value, _raw = _load_canonical_json(path / "manifest.json", "BUNDLE_INVALID")
    except deployment_plan.DeploymentPlanError as exc:
        raise RemoteError("BUNDLE_INVALID", 3) from exc
    release = value.get("release")
    if not isinstance(release, str):
        _fail("BUNDLE_INVALID", 3)
    return release


def install(operation_id: str, release: str, archive_sha256: str) -> dict[str, Any]:
    _validate_identity()
    _validate_root()
    operation = _validate_operation(operation_id)
    target = _validate_release(release)
    expected = _validate_digest(archive_sha256)
    _secure_directory(INCOMING_ROOT, "BUNDLE_INVALID")
    _secure_directory(RELEASES_ROOT, "BUNDLE_INVALID")
    archive = _archive_file(operation)
    if _hash_archive(archive) != expected:
        _fail("BUNDLE_INVALID", 3)
    _materialize_database_initializer()
    destination = RELEASES_ROOT / target
    staging = RELEASES_ROOT / f".{operation}.installing"
    if destination.exists() or destination.is_symlink():
        if destination.is_symlink() or not destination.is_dir():
            _fail("BUNDLE_CONFLICT", 3)
        if _bundle_release(destination) != target:
            _fail("BUNDLE_CONFLICT", 3)
        if staging.exists() or staging.is_symlink():
            # A destination and staging for the same operation is ambiguous;
            # neither is mutated automatically.
            _fail("BUNDLE_CONFLICT", 3)
        if not _archive_matches_bundle(archive, destination):
            _fail("BUNDLE_CONFLICT", 3)
        return {"installed": True, "operationId": operation, "release": target}
    if staging.exists() or staging.is_symlink():
        if (
            _bundle_release(staging) != target
            or not _archive_matches_bundle(archive, staging)
        ):
            _fail("BUNDLE_CONFLICT", 3)
    else:
        _extract_archive(archive, staging)
    if _bundle_release(staging) != target:
        _fail("BUNDLE_INVALID", 3)
    try:
        os.replace(staging, destination)
        _fsync_directory(RELEASES_ROOT, "BUNDLE_INVALID")
    except OSError as exc:
        raise RemoteError("BUNDLE_INVALID") from exc
    if _bundle_release(destination) != target:
        _fail("BUNDLE_INVALID", 3)
    return {"installed": True, "operationId": operation, "release": target}


def _validate_remote_result(raw: bytes, returncode: int, operation: str) -> dict[str, Any]:
    if len(raw) > STDOUT_LIMIT or raw.count(b"\n") != 1 or not raw.endswith(b"\n"):
        _fail("REMOTE_RESULT_INVALID", 3)
    try:
        value = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise RemoteError("REMOTE_RESULT_INVALID", 3) from exc
    if (
        not isinstance(value, dict)
        or raw != _json_file_bytes(value)
        or set(value) != {"databaseRestoreRequired", "errorCode", "operationId", "state"}
        or value.get("operationId") != operation
        or value.get("state") not in TERMINAL_EXIT
        or TERMINAL_EXIT[value["state"]] != returncode
        or not isinstance(value.get("databaseRestoreRequired"), bool)
        or (value["state"] == "SUCCEEDED" and value.get("errorCode") is not None)
        or (
            value["state"] != "SUCCEEDED"
            and (not isinstance(value.get("errorCode"), str) or not value["errorCode"])
        )
    ):
        _fail("REMOTE_RESULT_INVALID", 3)
    return value


def _stop_process(process: subprocess.Popen[bytes]) -> None:
    if process.poll() is not None:
        return
    try:
        process.terminate()
        process.wait(timeout=5)
    except (OSError, subprocess.TimeoutExpired):
        try:
            process.kill()
        except OSError:
            pass
        try:
            process.wait(timeout=5)
        except (OSError, subprocess.TimeoutExpired):
            pass


CAUSE_RE = re.compile(r"^[A-Z][A-Z0-9_]{2,63}$")


def _cli_cause(raw: bytes) -> str | None:
    """Extract the CLI's own sanitized failure code from its stderr.

    deployment_cli.py answers every pre-journal failure with a single canonical
    object of one key on stderr. Discarding it left an operator with a transport
    verdict and no cause: the deployment could fail on the env file mode, the
    Docker config, the compose model or a binary guard and every one of them
    looked identical from the runner. Only a closed code shape is accepted, so a
    corrupted or chatty stream degrades to no cause instead of leaking bytes.
    """
    if not raw or len(raw) > STDOUT_LIMIT:
        return None
    line = raw.strip().rsplit(b"\n", 1)[-1]
    try:
        value = json.loads(line.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        return None
    if not isinstance(value, dict) or set(value) != {"errorCode"}:
        return None
    code = value["errorCode"]
    if not isinstance(code, str) or CAUSE_RE.fullmatch(code) is None:
        return None
    return code


def _run_bounded(argv: list[str]) -> tuple[bytes, int, bytes]:
    """Capture the S20 line incrementally and reap on overflow or timeout.

    The child's stderr goes to a temporary file rather than a second pipe: a file
    write never blocks, so the single-stream select loop below stays exactly as
    it was and cannot deadlock on a chatty child.
    """
    diagnostic = tempfile.TemporaryFile()
    try:
        process = subprocess.Popen(
            argv,
            shell=False,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=diagnostic,
            env={"LANG": "C.UTF-8", "LC_ALL": "C.UTF-8", "PATH": "/usr/bin:/bin"},
            close_fds=True,
        )
    except OSError as exc:
        diagnostic.close()
        raise RemoteError("REMOTE_RESULT_UNAVAILABLE") from exc
    if process.stdout is None:  # pragma: no cover - PIPE guarantees a stream.
        _stop_process(process)
        _fail("REMOTE_RESULT_UNAVAILABLE")
    captured = bytearray()
    deadline = time.monotonic() + EXECUTE_TIMEOUT
    selector = selectors.DefaultSelector()
    try:
        selector.register(process.stdout, selectors.EVENT_READ)
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                _stop_process(process)
                _fail("REMOTE_RESULT_UNAVAILABLE")
            events = selector.select(min(remaining, 1.0))
            if events:
                chunk = os.read(process.stdout.fileno(), min(8192, STDOUT_LIMIT + 1))
                if chunk:
                    captured.extend(chunk)
                    if len(captured) > STDOUT_LIMIT:
                        _stop_process(process)
                        _fail("REMOTE_RESULT_INVALID", 3)
                elif process.poll() is not None:
                    break
            elif process.poll() is not None:
                # Drain the final pipe bytes after observing process exit.
                chunk = os.read(process.stdout.fileno(), min(8192, STDOUT_LIMIT + 1))
                if chunk:
                    captured.extend(chunk)
                    if len(captured) > STDOUT_LIMIT:
                        _fail("REMOTE_RESULT_INVALID", 3)
                    continue
                break
        returncode = process.wait(timeout=1)
        try:
            diagnostic.seek(0)
            captured_diagnostic = diagnostic.read(STDOUT_LIMIT + 1)
        except OSError:
            captured_diagnostic = b""
        return bytes(captured), returncode, captured_diagnostic
    except RemoteError:
        raise
    except (OSError, subprocess.TimeoutExpired) as exc:
        _stop_process(process)
        raise RemoteError("REMOTE_RESULT_UNAVAILABLE") from exc
    finally:
        selector.close()
        process.stdout.close()
        diagnostic.close()


def execute(operation_id: str, release: str) -> tuple[dict[str, Any], int]:
    _validate_identity()
    _validate_root()
    operation = _validate_operation(operation_id)
    target = _validate_release(release)
    destination = RELEASES_ROOT / target
    if _bundle_release(destination) != target:
        _fail("REMOTE_RESULT_INVALID", 3)
    _validate_executable(DEPLOY_SCRIPT)
    argv = [
        os.fspath(DEPLOY_SCRIPT),
        "deploy",
        "--operation-id",
        operation,
        "--release",
        target,
    ]
    stdout, returncode, diagnostic = _run_bounded(argv)
    if returncode not in TERMINAL_EXIT.values():
        cause = _cli_cause(diagnostic)
        if cause is not None:
            # Re-emitted on the helper's own stderr, never on stdout: stdout stays
            # the strict result channel the transport parses. The transport
            # captures this stream over SSH, so the cause reaches the job log
            # without touching the outcome contract.
            print(json.dumps({"errorCode": cause}, separators=(",", ":")), file=sys.stderr)
    value = _validate_remote_result(stdout, returncode, operation)
    return value, returncode


def _safe_remove_tree(path: Path, code: str) -> None:
    try:
        details = path.lstat()
    except FileNotFoundError:
        return
    except OSError as exc:
        raise RemoteError(code) from exc
    if stat.S_ISLNK(details.st_mode):
        _fail(code, 4)
    if stat.S_ISREG(details.st_mode):
        if details.st_uid != os.geteuid() or details.st_mode & 0o022:
            _fail(code, 4)
        try:
            path.unlink()
            return
        except OSError as exc:
            raise RemoteError(code) from exc
    if not stat.S_ISDIR(details.st_mode):
        _fail(code, 4)
    if details.st_uid != os.geteuid() or stat.S_IMODE(details.st_mode) != 0o700:
        _fail(code, 4)
    try:
        entries = list(path.iterdir())
    except OSError as exc:
        raise RemoteError(code) from exc
    for entry in entries:
        _safe_remove_tree(entry, code)
    try:
        path.rmdir()
    except OSError as exc:
        raise RemoteError(code) from exc


def cleanup(operation_id: str) -> dict[str, Any]:
    _validate_identity()
    _validate_root()
    operation = _validate_operation(operation_id)
    failures = False
    for path in (
        _archive_file(operation),
        SNAPSHOT_ROOT / f"{operation}.staging",
        SNAPSHOT_ROOT / operation,
    ):
        try:
            _safe_remove_tree(path, "REMOTE_CLEANUP_FAILED")
        except RemoteError:
            failures = True
    if failures:
        _fail("REMOTE_CLEANUP_FAILED")
    return {"cleaned": True, "operationId": operation}


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="deployment-remote.py")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("capabilities")
    for name in ("snapshot", "execute"):
        command = commands.add_parser(name)
        command.add_argument("--operation-id", required=True)
        command.add_argument("--release", required=True)
    install_command = commands.add_parser("install")
    install_command.add_argument("--operation-id", required=True)
    install_command.add_argument("--release", required=True)
    install_command.add_argument("--archive-sha256", required=True)
    cleanup_command = commands.add_parser("cleanup")
    cleanup_command.add_argument("--operation-id", required=True)
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    try:
        args = parser.parse_args(argv)
        exit_code = 0
        if args.command == "capabilities":
            result = capabilities()
        elif args.command == "snapshot":
            result = snapshot(args.operation_id, args.release)
        elif args.command == "install":
            result = install(args.operation_id, args.release, args.archive_sha256)
        elif args.command == "execute":
            result, exit_code = execute(args.operation_id, args.release)
        elif args.command == "cleanup":
            result = cleanup(args.operation_id)
        else:  # pragma: no cover - argparse owns the closed command set.
            _fail("INTERNAL_ERROR")
        sys.stdout.buffer.write(_json_file_bytes(result))
        return exit_code
    except RemoteError as exc:
        sys.stdout.buffer.write(_json_file_bytes({"errorCode": exc.code}))
        return exc.exit_code
    except Exception:
        sys.stdout.buffer.write(_json_file_bytes({"errorCode": "INTERNAL_ERROR"}))
        return 6


if __name__ == "__main__":
    raise SystemExit(main())
