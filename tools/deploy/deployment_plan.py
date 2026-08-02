#!/usr/bin/env python3
"""Deterministic, offline deployment-plan builder for an Emporio global release."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import stat
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, NoReturn

import jsonschema

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/releases"))
import global_release  # noqa: E402

TARGET_SCHEMA = ROOT / "ops/releases/global-release.schema.json"
INSTALLED_SCHEMA = ROOT / "ops/deploy/schemas/installed-state.schema.json"
PLAN_SCHEMA = ROOT / "ops/deploy/schemas/deployment-plan.schema.json"
CANONICAL_COMPOSE = ROOT / "ops/compose/compose.prod.yml"

COMPONENTS = (
    "backend",
    "website_back",
    "frontend",
    "website_front",
    "whatsapp_service",
    "gateway",
)
DATABASES = ("erp", "website")
DATABASE_OWNERS = {"erp": "backend", "website": "website_back"}
REPOSITORIES = {
    "backend": "ghcr.io/greggorio/abaronesa-emporio-backend",
    "website_back": "ghcr.io/greggorio/abaronesa-emporio-website-backend",
    "frontend": "ghcr.io/greggorio/abaronesa-emporio-frontend",
    "website_front": "ghcr.io/greggorio/abaronesa-emporio-website-frontend",
    "whatsapp_service": "ghcr.io/greggorio/abaronesa-emporio-whatsapp-service",
    "gateway": "ghcr.io/greggorio/abaronesa-emporio-gateway",
}
SERVICES = {component: component for component in COMPONENTS}
IMAGE_VARIABLES = {
    "backend": "BACKEND_IMAGE",
    "website_back": "WEBSITE_BACK_IMAGE",
    "frontend": "FRONTEND_IMAGE",
    "website_front": "WEBSITE_FRONT_IMAGE",
    "whatsapp_service": "WHATSAPP_IMAGE",
    "gateway": "GATEWAY_IMAGE",
}
EXECUTION_ORDER = (
    "VALIDATE",
    "PULL",
    "BACKUP",
    "MIGRATE",
    "UPDATE",
    "VERIFY",
    "COMMIT_STATE",
)
BUNDLE_PAYLOADS = (
    "manifest.json",
    "compose.prod.yml",
    "release.env",
    "deployment-plan.json",
    "installed-state.next.json",
)
BUNDLE_FILES = frozenset((*BUNDLE_PAYLOADS, "bundle.sha256"))

MAX_JSON_BYTES = 2 * 1024 * 1024
MAX_COMPOSE_BYTES = 1024 * 1024
DIGEST_RE = re.compile(r"sha256:[0-9a-f]{64}")
RAW_DIGEST_RE = re.compile(r"[0-9a-f]{64}")
TIME_RE = re.compile(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z")


class DeploymentPlanError(ValueError):
    """Stable, sanitized planner failure."""

    def __init__(self, code: str, exit_code: int = 3):
        super().__init__(code)
        self.code = code
        self.exit_code = exit_code


def canonical_bytes(value: Any) -> bytes:
    """Canonical JSON used for manifest identity; deliberately has no final LF."""
    return json.dumps(
        value, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    ).encode("utf-8")


def _json_file_bytes(value: Any) -> bytes:
    return canonical_bytes(value) + b"\n"


def manifest_digest(value: Any) -> str:
    return "sha256:" + hashlib.sha256(canonical_bytes(value)).hexdigest()


def _digest_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _schema(path: Path) -> dict[str, Any]:
    try:
        raw = path.read_bytes()
        value = json.loads(raw.decode("utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise DeploymentPlanError("INVALID_CONTRACT") from exc
    if not isinstance(value, dict):
        raise DeploymentPlanError("INVALID_CONTRACT")
    try:
        jsonschema.Draft202012Validator.check_schema(value)
    except jsonschema.SchemaError as exc:
        raise DeploymentPlanError("INVALID_CONTRACT") from exc
    return value


def _validate_schema(value: Any, path: Path, code: str = "INVALID_CONTRACT") -> None:
    try:
        jsonschema.Draft202012Validator(
            _schema(path), format_checker=jsonschema.FormatChecker()
        ).validate(value)
    except jsonschema.ValidationError as exc:
        raise DeploymentPlanError(code) from exc


def _read_regular(path: Path, limit: int, code: str) -> bytes:
    path = Path(path)
    try:
        details = path.lstat()
        if stat.S_ISLNK(details.st_mode) or not stat.S_ISREG(details.st_mode):
            raise DeploymentPlanError(code)
        if details.st_size > limit:
            raise DeploymentPlanError(code)
        data = path.read_bytes()
    except DeploymentPlanError:
        raise
    except OSError as exc:
        raise DeploymentPlanError(code) from exc
    if len(data) > limit:
        raise DeploymentPlanError(code)
    return data


def _decode_json(raw: bytes, code: str) -> dict[str, Any]:
    if raw.startswith(b"\xef\xbb\xbf"):
        raise DeploymentPlanError(code)
    try:
        value = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise DeploymentPlanError(code) from exc
    if not isinstance(value, dict):
        raise DeploymentPlanError(code)
    return value


def _load_release(path: Path) -> dict[str, Any]:
    raw = _read_regular(path, MAX_JSON_BYTES, "INVALID_CONTRACT")
    value = _decode_json(raw, "INVALID_CONTRACT")
    _validate_target_contract(value)
    return value


def _validate_target_contract(value: dict[str, Any]) -> None:
    _validate_schema(value, TARGET_SCHEMA)
    try:
        errors = global_release.validate_release(value)
    except Exception as exc:
        raise DeploymentPlanError("INVALID_CONTRACT") from exc
    if errors:
        raise DeploymentPlanError("INVALID_CONTRACT")
    _validate_target_invariants(value)


def load_target(path: Path | str) -> dict[str, Any]:
    return _load_release(Path(path))


def _parse_time(value: Any, code: str) -> datetime:
    if not isinstance(value, str) or TIME_RE.fullmatch(value) is None:
        raise DeploymentPlanError(code)
    try:
        parsed = datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(
            tzinfo=timezone.utc
        )
    except ValueError as exc:
        raise DeploymentPlanError(code) from exc
    return parsed


def _ids_in_order(values: Any, expected: tuple[str, ...], code: str) -> None:
    if (
        not isinstance(values, list)
        or len(values) != len(expected)
        or any(not isinstance(item, dict) for item in values)
        or tuple(item.get("id") for item in values) != expected
    ):
        raise DeploymentPlanError(code)


def _migration_set_digest(migrations: list[dict[str, Any]]) -> str:
    # S13's accepted algorithm includes the canonical artifact LF.
    return "sha256:" + hashlib.sha256(global_release.canonical(migrations)).hexdigest()


def _validate_migrations(
    database: dict[str, Any], *, installed: bool, code: str
) -> None:
    migrations = database.get("migrations")
    if not isinstance(migrations, list) or not migrations:
        raise DeploymentPlanError(code)
    paths: set[str] = set()
    versions: set[str] = set()
    for migration in migrations:
        if (
            not isinstance(migration, dict)
            or set(migration) != {"version", "path", "sha256"}
            or not isinstance(migration.get("version"), str)
            or not isinstance(migration.get("path"), str)
            or DIGEST_RE.fullmatch(str(migration.get("sha256"))) is None
            or migration["path"] in paths
            or migration["version"] in versions
        ):
            raise DeploymentPlanError(code)
        paths.add(migration["path"])
        versions.add(migration["version"])
    if not installed and database.get("latestVersion") != migrations[-1]["version"]:
        raise DeploymentPlanError(code)
    if database.get("migrationSetSha256") != _migration_set_digest(migrations):
        raise DeploymentPlanError(code)


def _validate_target_invariants(target: dict[str, Any]) -> None:
    _ids_in_order(target.get("components"), COMPONENTS, "INVALID_CONTRACT")
    for component in target["components"]:
        component_id = component["id"]
        repository = REPOSITORIES[component_id]
        digest = component.get("digest")
        immutable = component.get("immutableRef")
        if (
            component.get("imageRepository") != repository
            or DIGEST_RE.fullmatch(str(digest)) is None
            or immutable != repository + "@" + str(digest)
        ):
            raise DeploymentPlanError("INVALID_CONTRACT")
    _ids_in_order(target.get("databases"), DATABASES, "INVALID_CONTRACT")
    for database in target["databases"]:
        if database.get("ownerComponent") != DATABASE_OWNERS[database["id"]]:
            raise DeploymentPlanError("INVALID_CONTRACT")
        _validate_migrations(database, installed=False, code="INVALID_CONTRACT")


def _validate_installed_invariants(current: dict[str, Any], *, confirmed: bool) -> None:
    _ids_in_order(current.get("components"), COMPONENTS, "CURRENT_STATE_MISMATCH")
    for component in current["components"]:
        repository = REPOSITORIES[component["id"]]
        immutable = component.get("immutableRef")
        if (
            not isinstance(immutable, str)
            or not immutable.startswith(repository + "@")
            or DIGEST_RE.fullmatch(immutable.removeprefix(repository + "@")) is None
        ):
            raise DeploymentPlanError("CURRENT_STATE_MISMATCH")
    _ids_in_order(current.get("databases"), DATABASES, "CURRENT_STATE_MISMATCH")
    for database in current["databases"]:
        if database.get("ownerComponent") != DATABASE_OWNERS[database["id"]]:
            raise DeploymentPlanError("CURRENT_STATE_MISMATCH")
        _validate_migrations(
            database, installed=True, code="CURRENT_STATE_MISMATCH"
        )
    planned = _parse_time(current.get("plannedAt"), "CURRENT_STATE_MISMATCH")
    if confirmed:
        installed = _parse_time(current.get("installedAt"), "CURRENT_STATE_MISMATCH")
        if current.get("reconciled") is not True or installed < planned:
            raise DeploymentPlanError("CURRENT_STATE_MISMATCH")
    elif current.get("reconciled") is not False or current.get("installedAt") is not None:
        raise DeploymentPlanError("INVALID_CONTRACT")


def load_current(path: Path | str) -> dict[str, Any]:
    raw = _read_regular(Path(path), MAX_JSON_BYTES, "CURRENT_STATE_MISMATCH")
    value = _decode_json(raw, "CURRENT_STATE_MISMATCH")
    _validate_schema(value, INSTALLED_SCHEMA, "CURRENT_STATE_MISMATCH")
    _validate_installed_invariants(value, confirmed=True)
    return value


def _load_current_manifest(path: Path | str) -> dict[str, Any]:
    return _load_release(Path(path))


def _component_projection_from_target(target: dict[str, Any]) -> list[dict[str, Any]]:
    return [
        {"id": item["id"], "immutableRef": item["immutableRef"]}
        for item in target["components"]
    ]


def _database_projection_from_target(target: dict[str, Any]) -> list[dict[str, Any]]:
    return [
        {
            "id": item["id"],
            "ownerComponent": item["ownerComponent"],
            "migrationSetSha256": item["migrationSetSha256"],
            "migrations": item["migrations"],
        }
        for item in target["databases"]
    ]


def _validate_current_pair(
    current: dict[str, Any], current_manifest: dict[str, Any]
) -> None:
    if (
        current_manifest.get("release") != current.get("release")
        or current_manifest.get("sourceCommit") != current.get("sourceCommit")
        or manifest_digest(current_manifest) != current.get("manifestSha256")
        or _component_projection_from_target(current_manifest) != current.get("components")
        or _database_projection_from_target(current_manifest) != current.get("databases")
    ):
        raise DeploymentPlanError("CURRENT_STATE_MISMATCH")


def _validate_chain(target: dict[str, Any], current: dict[str, Any] | None) -> None:
    try:
        target_version = global_release.parse_semver(target["release"])
    except Exception as exc:
        raise DeploymentPlanError("INVALID_CONTRACT") from exc
    if current is None:
        if target.get("previousRelease") is not None:
            raise DeploymentPlanError("RELEASE_CHAIN_MISMATCH")
        return
    try:
        current_version = global_release.parse_semver(current["release"])
    except Exception as exc:
        raise DeploymentPlanError("CURRENT_STATE_MISMATCH") from exc
    if current_version >= target_version or target.get("previousRelease") != current["release"]:
        raise DeploymentPlanError("RELEASE_CHAIN_MISMATCH")


def _digest_from_ref(component_id: str, immutable_ref: str) -> str:
    prefix = REPOSITORIES[component_id] + "@"
    if not immutable_ref.startswith(prefix):
        raise DeploymentPlanError("INVALID_CONTRACT")
    value = immutable_ref.removeprefix(prefix)
    if DIGEST_RE.fullmatch(value) is None:
        raise DeploymentPlanError("INVALID_CONTRACT")
    return value


def _pending_migrations(
    current: list[dict[str, Any]], target: list[dict[str, Any]]
) -> list[dict[str, Any]]:
    if len(current) > len(target) or target[: len(current)] != current:
        raise DeploymentPlanError("NON_FORWARD_MIGRATION")
    return target[len(current) :]


def build_plan(
    target: dict[str, Any],
    current: dict[str, Any] | None,
    planned_at: str,
) -> dict[str, Any]:
    _validate_target_contract(target)
    if current is not None:
        _validate_schema(current, INSTALLED_SCHEMA, "CURRENT_STATE_MISMATCH")
        _validate_installed_invariants(current, confirmed=True)
    _validate_chain(target, current)
    _validate_planned_at(target, current, planned_at)

    current_components = (
        {} if current is None else {item["id"]: item for item in current["components"]}
    )
    component_plan: list[dict[str, Any]] = []
    changed_services: list[str] = []
    for target_component in target["components"]:
        component_id = target_component["id"]
        target_ref = target_component["immutableRef"]
        target_digest = _digest_from_ref(component_id, target_ref)
        current_ref = (
            None
            if current is None
            else str(current_components[component_id]["immutableRef"])
        )
        action = "KEEP" if current_ref == target_ref else "UPDATE"
        if action == "UPDATE":
            changed_services.append(SERVICES[component_id])
        component_plan.append(
            {
                "component": component_id,
                "service": SERVICES[component_id],
                "imageVariable": IMAGE_VARIABLES[component_id],
                "action": action,
                "currentDigest": (
                    None
                    if current_ref is None
                    else _digest_from_ref(component_id, current_ref)
                ),
                "targetDigest": target_digest,
                "targetImmutableRef": target_ref,
            }
        )

    current_databases = (
        {} if current is None else {item["id"]: item for item in current["databases"]}
    )
    database_plan: list[dict[str, Any]] = []
    migration_required = False
    for target_database in target["databases"]:
        database_id = target_database["id"]
        current_database = current_databases.get(database_id)
        current_migrations = (
            [] if current_database is None else current_database["migrations"]
        )
        pending = _pending_migrations(
            current_migrations, target_database["migrations"]
        )
        changed = bool(pending)
        migration_required = migration_required or changed
        database_plan.append(
            {
                "id": database_id,
                "ownerComponent": target_database["ownerComponent"],
                "changed": changed,
                "currentMigrationSetSha256": (
                    None
                    if current_database is None
                    else current_database["migrationSetSha256"]
                ),
                "targetMigrationSetSha256": target_database[
                    "migrationSetSha256"
                ],
                "pendingMigrations": pending,
            }
        )

    result = {
        "schemaVersion": 1,
        "kind": "deployment-plan",
        "environment": "production",
        "sourceRelease": None if current is None else current["release"],
        "targetRelease": target["release"],
        "targetSourceCommit": target["sourceCommit"],
        "targetManifestSha256": manifest_digest(target),
        "plannedAt": planned_at,
        "firstInstallation": current is None,
        "components": component_plan,
        "servicesToPull": changed_services,
        "servicesToUpdate": list(changed_services),
        "databases": database_plan,
        "migrationRequired": migration_required,
        "backupRequired": migration_required,
        "executionOrder": list(EXECUTION_ORDER),
    }
    _validate_schema(result, PLAN_SCHEMA)
    return result


def build_release_env(target: dict[str, Any]) -> bytes:
    _validate_target_contract(target)
    components = {item["id"]: item for item in target["components"]}
    lines = [f"RELEASE_ID={target['release']}"]
    lines.extend(
        f"{IMAGE_VARIABLES[component]}={components[component]['immutableRef']}"
        for component in COMPONENTS
    )
    value = "\n".join(lines) + "\n"
    if any(character in value for character in ("\r", "\x00")):
        raise DeploymentPlanError("INVALID_CONTRACT")
    return value.encode("utf-8")


def build_next_state(target: dict[str, Any], planned_at: str) -> dict[str, Any]:
    _validate_target_contract(target)
    _validate_planned_at(target, None, planned_at)
    result = {
        "schemaVersion": 1,
        "kind": "installed-state",
        "environment": "production",
        "release": target["release"],
        "sourceCommit": target["sourceCommit"],
        "manifestSha256": manifest_digest(target),
        "plannedAt": planned_at,
        "installedAt": None,
        "reconciled": False,
        "components": _component_projection_from_target(target),
        "databases": _database_projection_from_target(target),
    }
    _validate_schema(result, INSTALLED_SCHEMA)
    _validate_installed_invariants(result, confirmed=False)
    return result


def _validate_planned_at(
    target: dict[str, Any], current: dict[str, Any] | None, planned_at: str
) -> None:
    planned = _parse_time(planned_at, "INVALID_CONTRACT")
    published = _parse_time(target.get("publishedAt"), "INVALID_CONTRACT")
    if planned < published:
        raise DeploymentPlanError("INVALID_CONTRACT")
    if current is not None:
        installed = _parse_time(
            current.get("installedAt"), "CURRENT_STATE_MISMATCH"
        )
        if planned <= installed:
            raise DeploymentPlanError("INVALID_CONTRACT")


def _load_compose(path: Path | str) -> bytes:
    supplied = Path(path)
    try:
        lexical = _lexical_absolute(supplied)
        if any(component.is_symlink() for component in _path_components(lexical)):
            raise DeploymentPlanError("INVALID_CONTRACT")
        resolved = supplied.resolve(strict=True)
        expected = CANONICAL_COMPOSE.resolve(strict=True)
    except (OSError, RuntimeError) as exc:
        raise DeploymentPlanError("INVALID_CONTRACT") from exc
    if resolved != expected:
        raise DeploymentPlanError("INVALID_CONTRACT")
    return _read_regular(supplied, MAX_COMPOSE_BYTES, "INVALID_CONTRACT")


def _is_relative_to(path: Path, parent: Path) -> bool:
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False


def _lexical_absolute(path: Path) -> Path:
    return Path(os.path.abspath(os.fspath(path)))


def _path_components(path: Path) -> list[Path]:
    result: list[Path] = []
    current = path
    while True:
        result.append(current)
        if current.parent == current:
            return list(reversed(result))
        current = current.parent


def _validate_output_path(path: Path | str) -> Path:
    lexical = _lexical_absolute(Path(path))
    workspace = ROOT.resolve()
    temporary = Path("/tmp").resolve()
    try:
        resolved = lexical.resolve(strict=False)
    except (OSError, RuntimeError) as exc:
        raise DeploymentPlanError("UNSAFE_PATH", 4) from exc
    allowed = (
        (_is_relative_to(resolved, workspace) and resolved != workspace)
        or (_is_relative_to(resolved, temporary) and resolved != temporary)
    )
    home_roots = {Path.home().resolve(), Path("/root").resolve()}
    home_parent = Path("/home")
    if home_parent.is_dir():
        home_roots.update(entry.resolve() for entry in home_parent.iterdir() if entry.is_dir())
    if not allowed or resolved in {Path("/"), temporary, workspace, *home_roots}:
        raise DeploymentPlanError("UNSAFE_PATH", 4)
    for component in _path_components(lexical):
        try:
            if component.exists() or component.is_symlink():
                if component.is_symlink():
                    raise DeploymentPlanError("UNSAFE_PATH", 4)
        except OSError as exc:
            raise DeploymentPlanError("UNSAFE_PATH", 4) from exc
    if lexical.exists() or lexical.is_symlink():
        raise DeploymentPlanError("BUNDLE_CONFLICT", 4)
    if not lexical.parent.is_dir() or lexical.parent.is_symlink():
        raise DeploymentPlanError("UNSAFE_PATH", 4)
    return lexical


def _write_file(path: Path, data: bytes) -> None:
    descriptor: int | None = None
    try:
        descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, "wb") as stream:
            descriptor = None
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.chmod(path, 0o600)
    except Exception:
        if descriptor is not None:
            os.close(descriptor)
        raise


def _fsync_directory(path: Path) -> None:
    descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_DIRECTORY", 0))
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def _checksum_file(payloads: dict[str, bytes]) -> bytes:
    return "".join(
        f"{_digest_bytes(payloads[name])}  {name}\n" for name in BUNDLE_PAYLOADS
    ).encode("ascii")


def _verify_staging(staging: Path, payloads: dict[str, bytes]) -> None:
    if {item.name for item in staging.iterdir()} != BUNDLE_FILES:
        raise DeploymentPlanError("INVALID_CONTRACT")
    for name, expected in payloads.items():
        path = staging / name
        if path.is_symlink() or not path.is_file() or path.read_bytes() != expected:
            raise DeploymentPlanError("INVALID_CONTRACT")
    expected_sidecar = _checksum_file(payloads)
    if (staging / "bundle.sha256").read_bytes() != expected_sidecar:
        raise DeploymentPlanError("INVALID_CONTRACT")


def generate_bundle(
    *,
    target_path: Path | str,
    current_path: Path | str | None,
    current_manifest_path: Path | str | None,
    compose_path: Path | str,
    planned_at: str,
    output_path: Path | str,
) -> dict[str, Any]:
    if (current_path is None) != (current_manifest_path is None):
        raise DeploymentPlanError("CURRENT_STATE_MISMATCH", 2)
    target = load_target(target_path)
    current = None if current_path is None else load_current(current_path)
    if current is not None:
        current_manifest = _load_current_manifest(current_manifest_path)
        _validate_current_pair(current, current_manifest)
    compose = _load_compose(compose_path)
    plan = build_plan(target, current, planned_at)
    next_state = build_next_state(target, planned_at)
    output = _validate_output_path(output_path)
    payloads = {
        "manifest.json": _json_file_bytes(target),
        "compose.prod.yml": compose,
        "release.env": build_release_env(target),
        "deployment-plan.json": _json_file_bytes(plan),
        "installed-state.next.json": _json_file_bytes(next_state),
    }
    staging: Path | None = None
    renamed = False
    try:
        staging = Path(
            tempfile.mkdtemp(prefix=f".{output.name}.stage-", dir=output.parent)
        )
        os.chmod(staging, 0o700)
        for name in BUNDLE_PAYLOADS:
            _write_file(staging / name, payloads[name])
        _write_file(staging / "bundle.sha256", _checksum_file(payloads))
        _fsync_directory(staging)
        _verify_staging(staging, payloads)
        if output.exists() or output.is_symlink():
            raise DeploymentPlanError("BUNDLE_CONFLICT", 4)
        os.replace(staging, output)
        renamed = True
        _fsync_directory(output.parent)
        return plan
    except DeploymentPlanError:
        raise
    except OSError as exc:
        raise DeploymentPlanError("ATOMICITY_FAILED", 5) from exc
    except Exception as exc:
        raise DeploymentPlanError("ATOMICITY_FAILED", 5) from exc
    finally:
        if staging is not None and not renamed and staging.exists():
            shutil.rmtree(staging, ignore_errors=True)


def _read_bundle_file(bundle: Path, name: str, limit: int) -> bytes:
    return _read_regular(bundle / name, limit, "INVALID_CONTRACT")


def _load_bundle_json(bundle: Path, name: str) -> dict[str, Any]:
    raw = _read_bundle_file(bundle, name, MAX_JSON_BYTES)
    value = _decode_json(raw, "INVALID_CONTRACT")
    if raw != _json_file_bytes(value):
        raise DeploymentPlanError("INVALID_CONTRACT")
    return value


def _parse_checksums(raw: bytes) -> dict[str, str]:
    try:
        text = raw.decode("ascii")
    except UnicodeDecodeError as exc:
        raise DeploymentPlanError("INVALID_CONTRACT") from exc
    expected_lines = [
        f"{'0' * 64}  {name}\n" for name in BUNDLE_PAYLOADS
    ]
    lines = text.splitlines(keepends=True)
    if len(lines) != len(expected_lines):
        raise DeploymentPlanError("INVALID_CONTRACT")
    result: dict[str, str] = {}
    for line, name in zip(lines, BUNDLE_PAYLOADS):
        match = re.fullmatch(r"([0-9a-f]{64})  ([^\r\n]+)\n", line)
        if match is None or match.group(2) != name:
            raise DeploymentPlanError("INVALID_CONTRACT")
        result[name] = match.group(1)
    return result


def _validate_plan_bundle_coherence(
    target: dict[str, Any],
    plan: dict[str, Any],
    next_state: dict[str, Any],
    release_env: bytes,
) -> None:
    if (
        plan.get("targetRelease") != target["release"]
        or plan.get("targetSourceCommit") != target["sourceCommit"]
        or plan.get("targetManifestSha256") != manifest_digest(target)
        or plan.get("sourceRelease") != target.get("previousRelease")
        or next_state != build_next_state(target, plan.get("plannedAt"))
        or release_env != build_release_env(target)
        or plan.get("servicesToPull") != plan.get("servicesToUpdate")
        or plan.get("executionOrder") != list(EXECUTION_ORDER)
    ):
        raise DeploymentPlanError("INVALID_CONTRACT")
    first = plan.get("firstInstallation")
    if first is not (plan.get("sourceRelease") is None):
        raise DeploymentPlanError("INVALID_CONTRACT")
    _ids_in_order(
        [
            {"id": item.get("component")}
            for item in plan.get("components", [])
            if isinstance(item, dict)
        ],
        COMPONENTS,
        "INVALID_CONTRACT",
    )
    target_components = {item["id"]: item for item in target["components"]}
    updated: list[str] = []
    for item in plan["components"]:
        component_id = item["component"]
        target_item = target_components[component_id]
        if (
            item.get("service") != SERVICES[component_id]
            or item.get("imageVariable") != IMAGE_VARIABLES[component_id]
            or item.get("targetImmutableRef") != target_item["immutableRef"]
            or item.get("targetDigest")
            != _digest_from_ref(component_id, target_item["immutableRef"])
        ):
            raise DeploymentPlanError("INVALID_CONTRACT")
        if first:
            if item.get("action") != "UPDATE" or item.get("currentDigest") is not None:
                raise DeploymentPlanError("INVALID_CONTRACT")
        elif item.get("action") == "KEEP":
            if item.get("currentDigest") != item.get("targetDigest"):
                raise DeploymentPlanError("INVALID_CONTRACT")
        elif item.get("action") != "UPDATE" or DIGEST_RE.fullmatch(
            str(item.get("currentDigest"))
        ) is None or item.get("currentDigest") == item.get("targetDigest"):
            raise DeploymentPlanError("INVALID_CONTRACT")
        if item["action"] == "UPDATE":
            updated.append(SERVICES[component_id])
    if plan.get("servicesToPull") != updated:
        raise DeploymentPlanError("INVALID_CONTRACT")
    _ids_in_order(plan.get("databases"), DATABASES, "INVALID_CONTRACT")
    target_databases = {item["id"]: item for item in target["databases"]}
    changed_any = False
    for item in plan["databases"]:
        target_database = target_databases[item["id"]]
        pending = item.get("pendingMigrations")
        if (
            item.get("ownerComponent") != target_database["ownerComponent"]
            or item.get("targetMigrationSetSha256")
            != target_database["migrationSetSha256"]
            or not isinstance(pending, list)
        ):
            raise DeploymentPlanError("INVALID_CONTRACT")
        if first:
            if (
                item.get("currentMigrationSetSha256") is not None
                or pending != target_database["migrations"]
                or item.get("changed") is not True
            ):
                raise DeploymentPlanError("INVALID_CONTRACT")
        else:
            target_migrations = target_database["migrations"]
            if len(pending) > len(target_migrations):
                raise DeploymentPlanError("INVALID_CONTRACT")
            prefix = target_migrations[: len(target_migrations) - len(pending)]
            if (
                target_migrations[len(prefix) :] != pending
                or item.get("currentMigrationSetSha256")
                != _migration_set_digest(prefix)
                or item.get("changed") is not bool(pending)
            ):
                raise DeploymentPlanError("INVALID_CONTRACT")
        changed_any = changed_any or bool(pending)
    if (
        plan.get("migrationRequired") is not changed_any
        or plan.get("backupRequired") is not changed_any
    ):
        raise DeploymentPlanError("INVALID_CONTRACT")


def validate_bundle(path: Path | str) -> dict[str, Any]:
    bundle = _lexical_absolute(Path(path))
    try:
        if bundle.is_symlink() or not bundle.is_dir():
            raise DeploymentPlanError("INVALID_CONTRACT")
        for component in _path_components(bundle):
            if component.is_symlink():
                raise DeploymentPlanError("INVALID_CONTRACT")
        entries = list(bundle.iterdir())
    except DeploymentPlanError:
        raise
    except OSError as exc:
        raise DeploymentPlanError("INVALID_CONTRACT") from exc
    if {item.name for item in entries} != BUNDLE_FILES:
        raise DeploymentPlanError("INVALID_CONTRACT")
    if any(item.is_symlink() or not item.is_file() for item in entries):
        raise DeploymentPlanError("INVALID_CONTRACT")
    if stat.S_IMODE(bundle.stat().st_mode) != 0o700 or any(
        stat.S_IMODE(item.stat().st_mode) != 0o600 for item in entries
    ):
        raise DeploymentPlanError("INVALID_CONTRACT")
    checksums = _parse_checksums(
        _read_bundle_file(bundle, "bundle.sha256", 1024)
    )
    for name in BUNDLE_PAYLOADS:
        raw = _read_bundle_file(
            bundle,
            name,
            MAX_COMPOSE_BYTES if name == "compose.prod.yml" else MAX_JSON_BYTES,
        )
        if _digest_bytes(raw) != checksums[name]:
            raise DeploymentPlanError("INVALID_CONTRACT")
    target = _load_bundle_json(bundle, "manifest.json")
    _validate_schema(target, TARGET_SCHEMA)
    try:
        if global_release.validate_release(target):
            raise DeploymentPlanError("INVALID_CONTRACT")
    except DeploymentPlanError:
        raise
    except Exception as exc:
        raise DeploymentPlanError("INVALID_CONTRACT") from exc
    _validate_target_invariants(target)
    plan = _load_bundle_json(bundle, "deployment-plan.json")
    _validate_schema(plan, PLAN_SCHEMA)
    next_state = _load_bundle_json(bundle, "installed-state.next.json")
    _validate_schema(next_state, INSTALLED_SCHEMA)
    _validate_installed_invariants(next_state, confirmed=False)
    compose = _read_bundle_file(bundle, "compose.prod.yml", MAX_COMPOSE_BYTES)
    if compose != _load_compose(CANONICAL_COMPOSE):
        raise DeploymentPlanError("INVALID_CONTRACT")
    release_env = _read_bundle_file(bundle, "release.env", 1024 * 1024)
    _validate_plan_bundle_coherence(target, plan, next_state, release_env)
    return plan


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="deployment_plan.py")
    subparsers = parser.add_subparsers(dest="command", required=True)
    generate = subparsers.add_parser("generate")
    generate.add_argument("--target", required=True, type=Path)
    generate.add_argument("--current", type=Path)
    generate.add_argument("--current-manifest", type=Path)
    generate.add_argument("--compose", required=True, type=Path)
    generate.add_argument("--planned-at", required=True)
    generate.add_argument("--output", required=True, type=Path)
    validate = subparsers.add_parser("validate")
    validate.add_argument("--bundle", required=True, type=Path)
    return parser


def _argument_error(message: str) -> NoReturn:
    del message
    raise DeploymentPlanError("INVALID_ARGUMENT", 2)


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    parser.error = _argument_error  # type: ignore[method-assign]
    try:
        args = parser.parse_args(argv)
        if args.command == "generate":
            if (args.current is None) != (args.current_manifest is None):
                raise DeploymentPlanError("INVALID_ARGUMENT", 2)
            generate_bundle(
                target_path=args.target,
                current_path=args.current,
                current_manifest_path=args.current_manifest,
                compose_path=args.compose,
                planned_at=args.planned_at,
                output_path=args.output,
            )
            print("deployment-plan:generated")
        else:
            validate_bundle(args.bundle)
            print("deployment-plan:valid")
        return 0
    except DeploymentPlanError as exc:
        print(f"deployment-plan:{exc.code}", file=sys.stderr)
        return exc.exit_code
    except (OSError, ValueError, TypeError) as exc:
        del exc
        print("deployment-plan:INVALID_CONTRACT", file=sys.stderr)
        return 3
    except Exception as exc:
        del exc
        print("deployment-plan:ATOMICITY_FAILED", file=sys.stderr)
        return 5


if __name__ == "__main__":
    raise SystemExit(main())
