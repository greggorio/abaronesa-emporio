#!/usr/bin/env python3
"""Transactional, adapter-driven deployment core for an S18 bundle."""

from __future__ import annotations

import copy
import fcntl
import hashlib
import importlib.util
import json
import os
import re
import stat
import sys
import tempfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Literal, Protocol

import jsonschema

ROOT = Path(__file__).resolve().parents[2]
DEPLOYMENT_PLAN_PATH = ROOT / "tools/deploy/deployment_plan.py"
_PLAN_SPEC = importlib.util.spec_from_file_location(
    "_emporio_deployment_plan_s18", DEPLOYMENT_PLAN_PATH
)
if _PLAN_SPEC is None or _PLAN_SPEC.loader is None:
    raise ImportError("deployment plan contract unavailable")
deployment_plan = importlib.util.module_from_spec(_PLAN_SPEC)
sys.modules[_PLAN_SPEC.name] = deployment_plan
_PLAN_SPEC.loader.exec_module(deployment_plan)

JOURNAL_SCHEMA = ROOT / "ops/deploy/schemas/deployment-journal.schema.json"
INSTALLED_SCHEMA = ROOT / "ops/deploy/schemas/installed-state.schema.json"

OPERATION_RE = re.compile(r"^[A-Za-z0-9_-]{20,128}$")
EVIDENCE_RE = re.compile(r"^[A-Za-z0-9_.:-]{8,128}$")
TIME_RE = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$")
DIGEST_RE = re.compile(r"^sha256:[0-9a-f]{64}$")
MAX_JSON_BYTES = 2 * 1024 * 1024

STEPS = (
    "PULL",
    "BACKUP",
    "MIGRATE",
    "UPDATE",
    "VERIFY",
    "COMMIT_STATE",
    "ROLLBACK",
)
TERMINAL_STATES = frozenset(("SUCCEEDED", "ROLLED_BACK", "FAILED"))
TRANSITIONS = frozenset(
    (
        (None, "QUEUED"),
        ("QUEUED", "PULLING"),
        ("PULLING", "BACKING_UP"),
        ("BACKING_UP", "MIGRATING"),
        ("MIGRATING", "UPDATING"),
        ("UPDATING", "VERIFYING"),
        ("VERIFYING", "SUCCEEDED"),
        ("QUEUED", "FAILED"),
        ("PULLING", "FAILED"),
        ("BACKING_UP", "FAILED"),
        ("MIGRATING", "ROLLING_BACK"),
        ("UPDATING", "ROLLING_BACK"),
        ("VERIFYING", "ROLLING_BACK"),
        ("ROLLING_BACK", "ROLLED_BACK"),
        ("ROLLING_BACK", "FAILED"),
    )
)
STATE_ACTION = {
    "PULLING": "PULL",
    "BACKING_UP": "BACKUP",
    "MIGRATING": "MIGRATE",
    "UPDATING": "UPDATE",
}
ACTION_FAILURE = {
    "PULL": "PULL_FAILED",
    "BACKUP": "BACKUP_FAILED",
    "MIGRATE": "MIGRATION_FAILED",
    "UPDATE": "UPDATE_FAILED",
    "VERIFY": "VERIFY_FAILED",
    "COMMIT_STATE": "COMMIT_STATE_FAILED",
    "ROLLBACK": "ROLLBACK_FAILED",
}
COMPENSATED_STATES = frozenset(("MIGRATING", "UPDATING", "VERIFYING"))
COMPONENTS = (
    "backend",
    "website_back",
    "frontend",
    "website_front",
    "whatsapp_service",
    "gateway",
)
DATABASES = ("erp", "website")


class DeploymentExecutionError(Exception):
    """Stable public failure that carries no internal payload."""

    def __init__(self, code: str, exit_code: int = 3):
        super().__init__(code)
        self.code = code
        self.exit_code = exit_code

    def __str__(self) -> str:
        return self.code


@dataclass(frozen=True)
class ProbeResult:
    status: Literal["ABSENT", "SUCCEEDED", "FAILED", "UNKNOWN"]
    observed_at: str
    evidence_id: str | None


@dataclass(frozen=True)
class ActionContext:
    operation_id: str
    action: Literal["PULL", "BACKUP", "MIGRATE", "UPDATE", "VERIFY", "ROLLBACK"]
    bundle: Path
    source_release: str | None
    target_release: str
    services: tuple[str, ...]
    databases: tuple[str, ...]
    database_restore_required: bool


class DeploymentAdapter(Protocol):
    def probe(self, context: ActionContext) -> ProbeResult: ...

    def execute(self, context: ActionContext) -> None: ...


class DeploymentClock(Protocol):
    def now(self) -> str: ...


class _ActionFailure(Exception):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


def _canonical(value: Any) -> bytes:
    return (
        json.dumps(
            value, ensure_ascii=False, sort_keys=True, separators=(",", ":")
        ).encode("utf-8")
        + b"\n"
    )


def _digest(data: bytes) -> str:
    return "sha256:" + hashlib.sha256(data).hexdigest()


def _parse_time(value: Any, code: str) -> datetime:
    if not isinstance(value, str) or TIME_RE.fullmatch(value) is None:
        raise DeploymentExecutionError(code)
    try:
        return datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(
            tzinfo=timezone.utc
        )
    except ValueError as exc:
        raise DeploymentExecutionError(code) from exc


def _clock_now(clock: DeploymentClock, previous: str | None) -> str:
    try:
        value = clock.now()
    except Exception as exc:
        raise DeploymentExecutionError("INVALID_CLOCK") from exc
    parsed = _parse_time(value, "INVALID_CLOCK")
    if previous is not None and parsed < _parse_time(previous, "INVALID_CLOCK"):
        raise DeploymentExecutionError("INVALID_CLOCK")
    return value


def _schema(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
        jsonschema.Draft202012Validator.check_schema(value)
    except Exception as exc:
        raise DeploymentExecutionError("INVALID_CONTRACT") from exc
    if not isinstance(value, dict):
        raise DeploymentExecutionError("INVALID_CONTRACT")
    return value


def _validate_schema(value: Any, schema_path: Path, code: str) -> None:
    try:
        jsonschema.Draft202012Validator(
            _schema(schema_path), format_checker=jsonschema.FormatChecker()
        ).validate(value)
    except DeploymentExecutionError:
        raise
    except jsonschema.ValidationError as exc:
        raise DeploymentExecutionError(code) from exc


def _lexical_absolute(path: Path) -> Path:
    return Path(os.path.abspath(os.fspath(path)))


def _is_relative_to(path: Path, parent: Path) -> bool:
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False


def _path_components(path: Path) -> list[Path]:
    result: list[Path] = []
    current = path
    while True:
        result.append(current)
        if current.parent == current:
            return list(reversed(result))
        current = current.parent


def _safe_directory(path: Path, trusted_root: Path | None = None) -> Path:
    """Confine the journal and installed-state directories to a trusted tree.

    The tree used to be inferred from this module's own location, which silently
    assumed that the code and the deploy root share a workspace. That holds for a
    checkout and for /tmp, but never in production: the control root is installed
    at <deploy_root>/shared/control, so the journal directory at
    <deploy_root>/shared/deploy/journals is its sibling and could never be
    accepted. Callers that already validated a deploy root pass it here; the
    inferred workspace and /tmp stay allowed so checkouts and tests are
    unaffected. Every other protection is unchanged.
    """
    lexical = _lexical_absolute(path)
    try:
        resolved = lexical.resolve(strict=True)
        workspace = ROOT.resolve(strict=True)
        temporary = Path("/tmp").resolve(strict=True)
        trusted = (
            None if trusted_root is None else Path(trusted_root).resolve(strict=True)
        )
    except (OSError, RuntimeError) as exc:
        raise DeploymentExecutionError("UNSAFE_PATH", 4) from exc
    if resolved != lexical:
        raise DeploymentExecutionError("UNSAFE_PATH", 4)
    allowed = (
        (_is_relative_to(resolved, workspace) and resolved != workspace)
        or (_is_relative_to(resolved, temporary) and resolved != temporary)
        or (
            trusted is not None
            and _is_relative_to(resolved, trusted)
            and resolved != trusted
        )
    )
    home_roots = {Path.home().resolve(), Path("/root").resolve()}
    home_parent = Path("/home")
    if home_parent.is_dir():
        try:
            home_roots.update(
                entry.resolve() for entry in home_parent.iterdir() if entry.is_dir()
            )
        except OSError as exc:
            raise DeploymentExecutionError("UNSAFE_PATH", 4) from exc
    try:
        unsafe_component = any(
            component.is_symlink() for component in _path_components(lexical)
        )
        mode = stat.S_IMODE(lexical.stat().st_mode)
    except OSError as exc:
        raise DeploymentExecutionError("UNSAFE_PATH", 4) from exc
    if (
        not allowed
        or resolved in {Path("/"), temporary, workspace, *home_roots}
        or unsafe_component
        or not lexical.is_dir()
        or mode != 0o700
    ):
        raise DeploymentExecutionError("UNSAFE_PATH", 4)
    return lexical


def _validate_paths(
    journal_dir: Path,
    installed_state_path: Path,
    trusted_root: Path | None = None,
) -> tuple[Path, Path]:
    journal = _safe_directory(journal_dir, trusted_root)
    state_input = _lexical_absolute(installed_state_path)
    state_parent = _safe_directory(state_input.parent, trusted_root)
    if (
        state_input.parent != state_parent
        or state_input.name.endswith("installed-state.json") is False
    ):
        raise DeploymentExecutionError("UNSAFE_PATH", 4)
    try:
        if state_input.is_symlink():
            raise DeploymentExecutionError("UNSAFE_PATH", 4)
        if state_input.exists():
            details = state_input.stat()
            if (
                not stat.S_ISREG(details.st_mode)
                or stat.S_IMODE(details.st_mode) != 0o600
                or details.st_size > MAX_JSON_BYTES
            ):
                raise DeploymentExecutionError("UNSAFE_PATH", 4)
    except DeploymentExecutionError:
        raise
    except OSError as exc:
        raise DeploymentExecutionError("UNSAFE_PATH", 4) from exc
    return journal, state_input


def _read_regular(path: Path, limit: int, code: str) -> bytes:
    try:
        details = path.lstat()
        if (
            stat.S_ISLNK(details.st_mode)
            or not stat.S_ISREG(details.st_mode)
            or details.st_size > limit
        ):
            raise DeploymentExecutionError(code)
        data = path.read_bytes()
    except DeploymentExecutionError:
        raise
    except OSError as exc:
        raise DeploymentExecutionError(code) from exc
    if len(data) > limit:
        raise DeploymentExecutionError(code)
    return data


def _decode_object(data: bytes, code: str) -> dict[str, Any]:
    if data.startswith(b"\xef\xbb\xbf"):
        raise DeploymentExecutionError(code)
    try:
        value = json.loads(data.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise DeploymentExecutionError(code) from exc
    if not isinstance(value, dict):
        raise DeploymentExecutionError(code)
    return value


def _validate_installed_semantics(value: dict[str, Any], *, confirmed: bool) -> None:
    try:
        components = value["components"]
        databases = value["databases"]
        if tuple(item["id"] for item in components) != COMPONENTS:
            raise KeyError
        if tuple(item["id"] for item in databases) != DATABASES:
            raise KeyError
        planned = _parse_time(value["plannedAt"], "CURRENT_STATE_CONFLICT")
        if confirmed:
            installed = _parse_time(
                value["installedAt"], "CURRENT_STATE_CONFLICT"
            )
            if value["reconciled"] is not True or installed < planned:
                raise KeyError
        elif value["reconciled"] is not False or value["installedAt"] is not None:
            raise KeyError
    except (KeyError, TypeError):
        raise DeploymentExecutionError("CURRENT_STATE_CONFLICT") from None


def _load_installed(path: Path, *, confirmed: bool) -> tuple[dict[str, Any], bytes]:
    data = _read_regular(path, MAX_JSON_BYTES, "CURRENT_STATE_CONFLICT")
    value = _decode_object(data, "CURRENT_STATE_CONFLICT")
    _validate_schema(value, INSTALLED_SCHEMA, "CURRENT_STATE_CONFLICT")
    _validate_installed_semantics(value, confirmed=confirmed)
    return value, data


def _validate_source(
    plan: dict[str, Any], installed_state_path: Path
) -> tuple[dict[str, Any] | None, str | None]:
    if plan["firstInstallation"]:
        if plan["sourceRelease"] is not None:
            raise DeploymentExecutionError("CURRENT_STATE_CONFLICT")
        if installed_state_path.exists() or installed_state_path.is_symlink():
            raise DeploymentExecutionError("CURRENT_STATE_CONFLICT")
        return None, None
    if not installed_state_path.exists():
        raise DeploymentExecutionError("CURRENT_STATE_CONFLICT")
    current, data = _load_installed(installed_state_path, confirmed=True)
    if current.get("release") != plan.get("sourceRelease"):
        raise DeploymentExecutionError("CURRENT_STATE_CONFLICT")
    if tuple(item.get("id") for item in current["components"]) != tuple(
        item.get("component") for item in plan["components"]
    ):
        raise DeploymentExecutionError("CURRENT_STATE_CONFLICT")
    for current_component, planned_component in zip(
        current["components"], plan["components"]
    ):
        immutable = current_component.get("immutableRef")
        digest = immutable.rsplit("@", 1)[-1] if isinstance(immutable, str) else None
        if digest != planned_component.get("currentDigest"):
            raise DeploymentExecutionError("CURRENT_STATE_CONFLICT")
    if tuple(item.get("id") for item in current["databases"]) != tuple(
        item.get("id") for item in plan["databases"]
    ):
        raise DeploymentExecutionError("CURRENT_STATE_CONFLICT")
    for current_database, planned_database in zip(
        current["databases"], plan["databases"]
    ):
        if current_database.get("migrationSetSha256") != planned_database.get(
            "currentMigrationSetSha256"
        ):
            raise DeploymentExecutionError("CURRENT_STATE_CONFLICT")
    return current, _digest(data)


def _validate_evidence(value: Any) -> None:
    if (
        not isinstance(value, dict)
        or set(value) != {"status", "evidenceId", "observedAt"}
        or value.get("status") != "SUCCEEDED"
        or EVIDENCE_RE.fullmatch(str(value.get("evidenceId"))) is None
    ):
        raise DeploymentExecutionError("JOURNAL_CORRUPT")
    _parse_time(value.get("observedAt"), "JOURNAL_CORRUPT")


def _validate_journal_semantics(value: dict[str, Any]) -> None:
    try:
        transitions = value["transitions"]
        steps = value["steps"]
        if value["sequence"] != len(transitions) or not transitions:
            raise KeyError
        if tuple(item.get("name") for item in steps) != STEPS:
            raise KeyError
        previous_time: datetime | None = None
        previous_state: str | None = None
        transition_events: list[
            tuple[str | None, str, datetime]
        ] = []
        for index, transition in enumerate(transitions, 1):
            if (
                transition.get("sequence") != index
                or transition.get("from") != previous_state
                or (transition.get("from"), transition.get("to")) not in TRANSITIONS
            ):
                raise KeyError
            moment = _parse_time(transition.get("at"), "JOURNAL_CORRUPT")
            if previous_time is not None and moment < previous_time:
                raise KeyError
            previous_time = moment
            previous_state = transition["to"]
            transition_events.append(
                (transition.get("from"), transition["to"], moment)
            )
        if previous_state != value["state"]:
            raise KeyError
        created = _parse_time(value["createdAt"], "JOURNAL_CORRUPT")
        updated = _parse_time(value["updatedAt"], "JOURNAL_CORRUPT")
        if created != _parse_time(transitions[0]["at"], "JOURNAL_CORRUPT"):
            raise KeyError
        if previous_time is not None and updated < previous_time:
            raise KeyError
        latest = previous_time
        for step in steps:
            status = step.get("status")
            attempts = step.get("attempts")
            started_at = step.get("startedAt")
            finished_at = step.get("finishedAt")
            evidence = step.get("evidence")
            error_code = step.get("errorCode")
            if not isinstance(attempts, int) or isinstance(attempts, bool) or attempts < 0:
                raise KeyError
            if status == "PENDING":
                if (
                    attempts != 0
                    or started_at is not None
                    or finished_at is not None
                    or evidence is not None
                    or error_code is not None
                ):
                    raise KeyError
            elif status == "RUNNING":
                if (
                    attempts < 1
                    or started_at is None
                    or finished_at is not None
                    or evidence is not None
                    or error_code is not None
                ):
                    raise KeyError
            elif status in {"SUCCEEDED", "SKIPPED", "FAILED"}:
                if finished_at is None or started_at is None:
                    raise KeyError
                if _parse_time(
                    finished_at, "JOURNAL_CORRUPT"
                ) < _parse_time(started_at, "JOURNAL_CORRUPT"):
                    raise KeyError
                if status == "SUCCEEDED":
                    _validate_evidence(evidence)
                    if error_code is not None:
                        raise KeyError
                    observed = _parse_time(
                        evidence["observedAt"], "JOURNAL_CORRUPT"
                    )
                    if not (
                        _parse_time(started_at, "JOURNAL_CORRUPT")
                        <= observed
                        <= _parse_time(finished_at, "JOURNAL_CORRUPT")
                    ):
                        raise KeyError
                elif evidence is not None:
                    raise KeyError
                if status == "SKIPPED" and error_code is not None:
                    raise KeyError
                if status == "FAILED" and not isinstance(error_code, str):
                    raise KeyError
            else:
                raise KeyError
            for timestamp in (started_at, finished_at):
                if timestamp is not None:
                    parsed = _parse_time(timestamp, "JOURNAL_CORRUPT")
                    if parsed < created:
                        raise KeyError
                    if latest is None or parsed > latest:
                        latest = parsed
        if latest is not None and updated != latest:
            raise KeyError
        terminal = value["state"] in TERMINAL_STATES
        if terminal != (value.get("finishedAt") is not None):
            raise KeyError
        if value.get("finishedAt") is not None:
            finished = _parse_time(value["finishedAt"], "JOURNAL_CORRUPT")
            if (
                finished != updated
                or previous_time is None
                or finished != previous_time
            ):
                raise KeyError
        step_map = {item["name"]: item for item in steps}
        step_windows = {
            "PULL": ("PULLING", {"BACKING_UP", "FAILED"}),
            "BACKUP": ("BACKING_UP", {"MIGRATING", "FAILED"}),
            "MIGRATE": ("MIGRATING", {"UPDATING", "ROLLING_BACK"}),
            "UPDATE": ("UPDATING", {"VERIFYING", "ROLLING_BACK"}),
            "VERIFY": ("VERIFYING", {"SUCCEEDED", "ROLLING_BACK"}),
            "COMMIT_STATE": (
                "VERIFYING",
                {"SUCCEEDED", "ROLLING_BACK"},
            ),
            "ROLLBACK": ("ROLLING_BACK", {"ROLLED_BACK", "FAILED"}),
        }
        for name, (window_state, exit_states) in step_windows.items():
            step = step_map[name]
            if step["status"] == "PENDING":
                continue
            entries = [
                (index, moment)
                for index, (_source, destination, moment) in enumerate(
                    transition_events
                )
                if destination == window_state
            ]
            if len(entries) != 1:
                raise KeyError
            entry_index, lower = entries[0]
            exits = [
                moment
                for source, destination, moment in transition_events[
                    entry_index + 1 :
                ]
                if source == window_state and destination in exit_states
            ]
            if len(exits) > 1:
                raise KeyError
            upper = exits[0] if exits else updated
            started = _parse_time(step["startedAt"], "JOURNAL_CORRUPT")
            if not lower <= started <= upper:
                raise KeyError
            if step["finishedAt"] is not None:
                step_finished = _parse_time(
                    step["finishedAt"], "JOURNAL_CORRUPT"
                )
                if not lower <= step_finished <= upper:
                    raise KeyError
        commit_started = step_map["COMMIT_STATE"]["startedAt"]
        if commit_started is not None:
            verify_finished = step_map["VERIFY"]["finishedAt"]
            if (
                verify_finished is None
                or _parse_time(
                    commit_started, "JOURNAL_CORRUPT"
                )
                < _parse_time(verify_finished, "JOURNAL_CORRUPT")
            ):
                raise KeyError
        if any(
            step_map[name]["status"] == "SKIPPED"
            for name in ("VERIFY", "COMMIT_STATE", "ROLLBACK")
        ):
            raise KeyError
        migrate_status = step_map["MIGRATE"]["status"]
        # The flag answers "must a rollback also restore the database", so it is
        # raised the moment migrations begin and stays raised while the outcome is
        # still open. A deployment that reached SUCCEEDED has no rollback to plan
        # for and the migrated schema is the intended state, so the flag must be
        # lowered: the control plane refuses a confirmed SUCCEEDED that still
        # demands a restore, and every release carrying migrations would otherwise
        # need a manual adjudication to reconcile.
        if migrate_status in {"RUNNING", "SUCCEEDED", "FAILED"}:
            expected_restore = value["state"] != "SUCCEEDED"
            legacy_first_install_success = (
                value["state"] == "SUCCEEDED"
                and value.get("sourceRelease") is None
                and value.get("databaseRestoreRequired") is True
            )
            if (
                value.get("databaseRestoreRequired") is not expected_restore
                and not legacy_first_install_success
            ):
                raise KeyError
        active_steps = {
            "QUEUED": None,
            "PULLING": "PULL",
            "BACKING_UP": "BACKUP",
            "MIGRATING": "MIGRATE",
            "UPDATING": "UPDATE",
            "VERIFYING": "VERIFY",
        }
        if value["state"] in active_steps:
            active = active_steps[value["state"]]
            boundary = 0 if active is None else STEPS.index(active)
            for name in STEPS[:boundary]:
                if step_map[name]["status"] not in {"SUCCEEDED", "SKIPPED"}:
                    raise KeyError
            if active is None:
                if any(step_map[name]["status"] != "PENDING" for name in STEPS):
                    raise KeyError
            else:
                if step_map[active]["status"] not in {
                    "PENDING",
                    "RUNNING",
                    "SKIPPED",
                    "SUCCEEDED",
                    "FAILED",
                }:
                    raise KeyError
                later_start = boundary + 1
                if value["state"] == "VERIFYING":
                    if step_map["VERIFY"]["status"] == "SUCCEEDED":
                        later_start = STEPS.index("COMMIT_STATE") + 1
                    elif step_map["COMMIT_STATE"]["status"] != "PENDING":
                        raise KeyError
                if any(
                    step_map[name]["status"] != "PENDING"
                    for name in STEPS[later_start:]
                ):
                    raise KeyError
        if value["state"] == "SUCCEEDED":
            if (
                step_map["COMMIT_STATE"]["status"] != "SUCCEEDED"
                or any(
                    step_map[name]["status"] not in {"SUCCEEDED", "SKIPPED"}
                    for name in STEPS[:6]
                )
                or step_map["VERIFY"]["status"] != "SUCCEEDED"
                or step_map["ROLLBACK"]["status"] != "PENDING"
                or value.get("errorCode") is not None
                or value.get("rollbackErrorCode") is not None
                or DIGEST_RE.fullmatch(str(value.get("confirmedStateSha256")))
                is None
            ):
                raise KeyError
        elif value["state"] == "ROLLING_BACK":
            if (
                not isinstance(value.get("errorCode"), str)
                or step_map["ROLLBACK"]["status"]
                not in {"PENDING", "RUNNING", "SUCCEEDED", "FAILED"}
            ):
                raise KeyError
        elif value["state"] == "ROLLED_BACK":
            if (
                step_map["ROLLBACK"]["status"] != "SUCCEEDED"
                or not isinstance(value.get("errorCode"), str)
            ):
                raise KeyError
        elif value["state"] == "FAILED":
            if not isinstance(value.get("errorCode"), str) and not isinstance(
                value.get("rollbackErrorCode"), str
            ):
                raise KeyError
        if value.get("sourceRelease") is None:
            if value.get("sourceStateSha256") is not None:
                raise KeyError
        elif DIGEST_RE.fullmatch(str(value.get("sourceStateSha256"))) is None:
            raise KeyError
    except DeploymentExecutionError:
        raise
    except (KeyError, TypeError, ValueError):
        raise DeploymentExecutionError("JOURNAL_CORRUPT") from None


def _validate_journal(value: dict[str, Any]) -> None:
    _validate_schema(value, JOURNAL_SCHEMA, "JOURNAL_CORRUPT")
    _validate_journal_semantics(value)


def _write_bytes(path: Path, data: bytes) -> None:
    descriptor: int | None = None
    try:
        descriptor = os.open(
            path,
            os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0),
            0o600,
        )
        with os.fdopen(descriptor, "wb") as stream:
            descriptor = None
            stream.write(data)
            stream.flush()
            _fsync_file(stream.fileno())
        os.chmod(path, 0o600)
    except Exception:
        if descriptor is not None:
            os.close(descriptor)
        raise


def _fsync_file(descriptor: int) -> None:
    os.fsync(descriptor)


def _fsync_directory(path: Path) -> None:
    descriptor = os.open(
        path, os.O_RDONLY | getattr(os, "O_DIRECTORY", 0) | getattr(os, "O_NOFOLLOW", 0)
    )
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def _verify_staged_json(
    path: Path,
    expected: bytes,
    validator: Callable[[dict[str, Any]], None],
) -> None:
    observed = _read_regular(path, MAX_JSON_BYTES, "JOURNAL_IO_FAILED")
    if observed != expected:
        raise DeploymentExecutionError("JOURNAL_IO_FAILED", 5)
    value = _decode_object(observed, "JOURNAL_IO_FAILED")
    try:
        validator(value)
    except DeploymentExecutionError as exc:
        raise DeploymentExecutionError("JOURNAL_IO_FAILED", 5) from exc


def _replace_atomic(source: Path, destination: Path) -> None:
    os.replace(source, destination)


def _atomic_write_json(
    path: Path,
    value: dict[str, Any],
    validator: Callable[[dict[str, Any]], None],
) -> bytes:
    data = _canonical(value)
    temporary: Path | None = None
    replaced = False
    try:
        descriptor, raw_name = tempfile.mkstemp(
            prefix=f".{path.name}.tmp-", dir=path.parent
        )
        os.close(descriptor)
        temporary = Path(raw_name)
        temporary.unlink()
        _write_bytes(temporary, data)
        _verify_staged_json(temporary, data, validator)
        _replace_atomic(temporary, path)
        replaced = True
        _fsync_directory(path.parent)
        return data
    except DeploymentExecutionError as exc:
        if exc.code == "JOURNAL_IO_FAILED" and exc.exit_code == 5:
            raise
        raise DeploymentExecutionError("JOURNAL_IO_FAILED", 5) from exc
    except Exception as exc:
        raise DeploymentExecutionError("JOURNAL_IO_FAILED", 5) from exc
    finally:
        if temporary is not None and not replaced:
            try:
                if temporary.exists() or temporary.is_symlink():
                    temporary.unlink()
            except OSError:
                pass


def _persist_journal(path: Path, journal: dict[str, Any]) -> bytes:
    return _atomic_write_json(path, journal, _validate_journal)


def _persist_installed_state(path: Path, value: dict[str, Any]) -> bytes:
    def validate(candidate: dict[str, Any]) -> None:
        _validate_schema(candidate, INSTALLED_SCHEMA, "CURRENT_STATE_CONFLICT")
        _validate_installed_semantics(candidate, confirmed=True)

    return _atomic_write_json(path, value, validate)


def _journal_step(journal: dict[str, Any], name: str) -> dict[str, Any]:
    return next(item for item in journal["steps"] if item["name"] == name)


def _mutate_time(journal: dict[str, Any], clock: DeploymentClock) -> str:
    value = _clock_now(clock, journal["updatedAt"])
    journal["updatedAt"] = value
    return value


def _transition(
    journal: dict[str, Any],
    destination: str,
    clock: DeploymentClock,
    journal_path: Path,
) -> None:
    source = journal["state"]
    if (source, destination) not in TRANSITIONS or source in TERMINAL_STATES:
        raise DeploymentExecutionError("JOURNAL_CORRUPT")
    moment = _mutate_time(journal, clock)
    journal["sequence"] += 1
    journal["state"] = destination
    journal["transitions"].append(
        {
            "sequence": journal["sequence"],
            "from": source,
            "to": destination,
            "at": moment,
        }
    )
    if destination in TERMINAL_STATES:
        journal["finishedAt"] = moment
    _persist_journal(journal_path, journal)


def _start_step(
    journal: dict[str, Any],
    name: str,
    clock: DeploymentClock,
    journal_path: Path,
) -> None:
    step = _journal_step(journal, name)
    if step["status"] == "RUNNING":
        return
    if step["status"] != "PENDING":
        raise DeploymentExecutionError("JOURNAL_CORRUPT")
    moment = _mutate_time(journal, clock)
    step["status"] = "RUNNING"
    step["attempts"] += 1
    step["startedAt"] = moment
    if name == "MIGRATE":
        journal["databaseRestoreRequired"] = True
    _persist_journal(journal_path, journal)


def _skip_step(
    journal: dict[str, Any],
    name: str,
    clock: DeploymentClock,
    journal_path: Path,
) -> None:
    step = _journal_step(journal, name)
    if step["status"] in {"SUCCEEDED", "SKIPPED"}:
        return
    if step["status"] != "PENDING":
        raise DeploymentExecutionError("JOURNAL_CORRUPT")
    moment = _mutate_time(journal, clock)
    step["status"] = "SKIPPED"
    step["startedAt"] = moment
    step["finishedAt"] = moment
    _persist_journal(journal_path, journal)


def _succeed_step(
    journal: dict[str, Any],
    name: str,
    probe: ProbeResult,
    persisted_at: str,
    journal_path: Path,
) -> None:
    step = _journal_step(journal, name)
    if step["status"] == "SUCCEEDED":
        return
    if step["status"] != "RUNNING":
        raise DeploymentExecutionError("JOURNAL_CORRUPT")
    journal["updatedAt"] = persisted_at
    step["status"] = "SUCCEEDED"
    step["finishedAt"] = persisted_at
    step["evidence"] = {
        "status": "SUCCEEDED",
        "evidenceId": probe.evidence_id,
        "observedAt": probe.observed_at,
    }
    _persist_journal(journal_path, journal)


def _fail_step(
    journal: dict[str, Any],
    name: str,
    code: str,
    clock: DeploymentClock,
    journal_path: Path,
) -> None:
    step = _journal_step(journal, name)
    if step["status"] == "FAILED":
        return
    if step["status"] == "PENDING":
        _start_step(journal, name, clock, journal_path)
    if step["status"] != "RUNNING":
        raise DeploymentExecutionError("JOURNAL_CORRUPT")
    moment = _mutate_time(journal, clock)
    step["status"] = "FAILED"
    step["finishedAt"] = moment
    step["errorCode"] = code
    _persist_journal(journal_path, journal)


def _action_context(
    action: str,
    operation_id: str,
    bundle: Path,
    plan: dict[str, Any],
    journal: dict[str, Any],
) -> ActionContext:
    changed_databases = tuple(
        item["id"] for item in plan["databases"] if item["changed"]
    )
    services: tuple[str, ...] = ()
    databases: tuple[str, ...] = ()
    if action == "PULL":
        services = tuple(plan["servicesToPull"])
    elif action in {"BACKUP", "MIGRATE"}:
        databases = changed_databases
    elif action == "UPDATE":
        services = tuple(plan["servicesToUpdate"])
    elif action in {"VERIFY", "ROLLBACK"}:
        services = COMPONENTS
        databases = DATABASES if action == "VERIFY" else changed_databases
    return ActionContext(
        operation_id=operation_id,
        action=action,  # type: ignore[arg-type]
        bundle=bundle,
        source_release=plan["sourceRelease"],
        target_release=plan["targetRelease"],
        services=services,
        databases=databases,
        database_restore_required=journal["databaseRestoreRequired"],
    )


def _validated_probe(
    result: Any,
    clock: DeploymentClock,
    journal: dict[str, Any],
) -> tuple[ProbeResult, str]:
    if not isinstance(result, ProbeResult):
        raise _ActionFailure("INVALID_ADAPTER_RESULT")
    if result.status not in {"ABSENT", "SUCCEEDED", "FAILED", "UNKNOWN"}:
        raise _ActionFailure("INVALID_ADAPTER_RESULT")
    try:
        observed = _parse_time(result.observed_at, "INVALID_ADAPTER_RESULT")
    except DeploymentExecutionError as exc:
        raise _ActionFailure("INVALID_ADAPTER_RESULT") from exc
    if result.status == "SUCCEEDED":
        if (
            not isinstance(result.evidence_id, str)
            or EVIDENCE_RE.fullmatch(result.evidence_id) is None
        ):
            raise _ActionFailure("INVALID_ADAPTER_RESULT")
    elif result.evidence_id is not None:
        raise _ActionFailure("INVALID_ADAPTER_RESULT")
    persisted_at = _clock_now(clock, journal["updatedAt"])
    if observed > _parse_time(persisted_at, "INVALID_CLOCK"):
        raise _ActionFailure("INVALID_ADAPTER_RESULT")
    return result, persisted_at


def _probe(
    adapter: DeploymentAdapter,
    context: ActionContext,
    clock: DeploymentClock,
    journal: dict[str, Any],
) -> tuple[ProbeResult, str]:
    try:
        result = adapter.probe(context)
    except Exception as exc:
        raise _ActionFailure(ACTION_FAILURE[context.action]) from exc
    return _validated_probe(result, clock, journal)


def _run_adapter_action(
    journal: dict[str, Any],
    action: str,
    context: ActionContext,
    adapter: DeploymentAdapter,
    clock: DeploymentClock,
    journal_path: Path,
) -> None:
    step = _journal_step(journal, action)
    if step["status"] in {"SUCCEEDED", "SKIPPED"}:
        return
    _start_step(journal, action, clock, journal_path)
    try:
        before, persisted_at = _probe(adapter, context, clock, journal)
        if before.status == "SUCCEEDED":
            _succeed_step(journal, action, before, persisted_at, journal_path)
            return
        if before.status != "ABSENT":
            raise _ActionFailure(ACTION_FAILURE[action])
        try:
            returned = adapter.execute(context)
        except Exception as exc:
            raise _ActionFailure(ACTION_FAILURE[action]) from exc
        if returned is not None:
            raise _ActionFailure("INVALID_ADAPTER_RESULT")
        after, persisted_at = _probe(adapter, context, clock, journal)
        if after.status != "SUCCEEDED":
            raise _ActionFailure(ACTION_FAILURE[action])
        _succeed_step(journal, action, after, persisted_at, journal_path)
    except _ActionFailure as exc:
        _fail_step(journal, action, exc.code, clock, journal_path)
        raise


def _is_noop(action: str, plan: dict[str, Any]) -> bool:
    if action == "PULL":
        return not plan["servicesToPull"]
    if action == "BACKUP":
        return not plan["backupRequired"]
    if action == "MIGRATE":
        return not plan["migrationRequired"]
    if action == "UPDATE":
        return not plan["servicesToUpdate"]
    return False


def _target_state_match(
    value: dict[str, Any], next_state: dict[str, Any], plan: dict[str, Any]
) -> bool:
    try:
        _validate_schema(value, INSTALLED_SCHEMA, "CURRENT_STATE_CONFLICT")
        _validate_installed_semantics(value, confirmed=True)
    except DeploymentExecutionError:
        return False
    expected = copy.deepcopy(next_state)
    installed_at = value.get("installedAt")
    expected["reconciled"] = True
    expected["installedAt"] = installed_at
    if value != expected:
        return False
    return _parse_time(installed_at, "CURRENT_STATE_CONFLICT") >= _parse_time(
        plan["plannedAt"], "CURRENT_STATE_CONFLICT"
    )


def _state_classification(
    installed_state_path: Path,
    journal: dict[str, Any],
    next_state: dict[str, Any],
    plan: dict[str, Any],
) -> tuple[str, dict[str, Any] | None, bytes | None]:
    if not installed_state_path.exists():
        if journal["sourceStateSha256"] is None:
            return "SOURCE", None, None
        raise _ActionFailure("CURRENT_STATE_CONFLICT")
    try:
        value, data = _load_installed(installed_state_path, confirmed=True)
    except DeploymentExecutionError as exc:
        raise _ActionFailure("CURRENT_STATE_CONFLICT") from exc
    observed = _digest(data)
    if observed == journal["sourceStateSha256"]:
        return "SOURCE", value, data
    is_canonical = data == _canonical(value)
    if journal["confirmedStateSha256"] is not None:
        if (
            observed == journal["confirmedStateSha256"]
            and is_canonical
            and _target_state_match(value, next_state, plan)
        ):
            return "TARGET", value, data
        raise _ActionFailure("CURRENT_STATE_CONFLICT")
    if is_canonical and _target_state_match(value, next_state, plan):
        return "TARGET", value, data
    raise _ActionFailure("CURRENT_STATE_CONFLICT")


def _commit_state(
    journal: dict[str, Any],
    plan: dict[str, Any],
    next_state: dict[str, Any],
    installed_state_path: Path,
    clock: DeploymentClock,
    journal_path: Path,
) -> None:
    step = _journal_step(journal, "COMMIT_STATE")
    if step["status"] == "SUCCEEDED":
        classification, _, data = _state_classification(
            installed_state_path, journal, next_state, plan
        )
        if (
            classification != "TARGET"
            or data is None
            or _digest(data) != journal["confirmedStateSha256"]
        ):
            raise _ActionFailure("CURRENT_STATE_CONFLICT")
        return
    _start_step(journal, "COMMIT_STATE", clock, journal_path)
    classification, existing, existing_data = _state_classification(
        installed_state_path, journal, next_state, plan
    )
    if classification == "TARGET":
        assert existing is not None and existing_data is not None
        installed_at = existing["installedAt"]
        confirmed_data = existing_data
    else:
        installed_at = _clock_now(clock, journal["updatedAt"])
        if _parse_time(installed_at, "INVALID_CLOCK") < _parse_time(
            plan["plannedAt"], "INVALID_CLOCK"
        ):
            raise DeploymentExecutionError("INVALID_CLOCK")
        confirmed = copy.deepcopy(next_state)
        confirmed["reconciled"] = True
        confirmed["installedAt"] = installed_at
        try:
            confirmed_data = _persist_installed_state(
                installed_state_path, confirmed
            )
        except DeploymentExecutionError:
            try:
                reconciled, candidate, candidate_data = _state_classification(
                    installed_state_path, journal, next_state, plan
                )
            except _ActionFailure:
                raise
            if reconciled != "TARGET" or candidate is None or candidate_data is None:
                raise
            installed_at = candidate["installedAt"]
            confirmed_data = candidate_data
    confirmed_hash = _digest(confirmed_data)
    moment = _clock_now(clock, journal["updatedAt"])
    if _parse_time(moment, "INVALID_CLOCK") < _parse_time(
        installed_at, "INVALID_CLOCK"
    ):
        raise DeploymentExecutionError("INVALID_CLOCK")
    journal["confirmedStateSha256"] = confirmed_hash
    probe = ProbeResult(
        status="SUCCEEDED",
        observed_at=moment,
        evidence_id="state:" + confirmed_hash.removeprefix("sha256:"),
    )
    _succeed_step(journal, "COMMIT_STATE", probe, moment, journal_path)


def _complete_failure(
    journal: dict[str, Any],
    primary_code: str,
    plan: dict[str, Any],
    operation_id: str,
    bundle: Path,
    adapter: DeploymentAdapter,
    clock: DeploymentClock,
    journal_path: Path,
) -> dict[str, Any]:
    failure_state = journal["state"]
    if journal["errorCode"] is None:
        journal["errorCode"] = primary_code
    if failure_state not in COMPENSATED_STATES:
        _transition(journal, "FAILED", clock, journal_path)
        return copy.deepcopy(journal)
    _transition(journal, "ROLLING_BACK", clock, journal_path)
    context = _action_context(
        "ROLLBACK", operation_id, bundle, plan, journal
    )
    try:
        _run_adapter_action(
            journal,
            "ROLLBACK",
            context,
            adapter,
            clock,
            journal_path,
        )
    except _ActionFailure:
        journal["rollbackErrorCode"] = "ROLLBACK_FAILED"
        _transition(journal, "FAILED", clock, journal_path)
        return copy.deepcopy(journal)
    _transition(journal, "ROLLED_BACK", clock, journal_path)
    return copy.deepcopy(journal)


def _new_journal(
    operation_id: str,
    bundle_identity: str,
    plan: dict[str, Any],
    source_hash: str | None,
    clock: DeploymentClock,
) -> dict[str, Any]:
    moment = _clock_now(clock, None)
    return {
        "schemaVersion": 1,
        "kind": "deployment-journal",
        "operationId": operation_id,
        "operationType": "deployment",
        "bundleIdentity": bundle_identity,
        "sourceRelease": plan["sourceRelease"],
        "sourceStateSha256": source_hash,
        "targetRelease": plan["targetRelease"],
        "state": "QUEUED",
        "createdAt": moment,
        "updatedAt": moment,
        "finishedAt": None,
        "sequence": 1,
        "steps": [
            {
                "name": name,
                "status": "PENDING",
                "attempts": 0,
                "startedAt": None,
                "finishedAt": None,
                "evidence": None,
                "errorCode": None,
            }
            for name in STEPS
        ],
        "transitions": [
            {"sequence": 1, "from": None, "to": "QUEUED", "at": moment}
        ],
        "databaseRestoreRequired": False,
        "errorCode": None,
        "rollbackErrorCode": None,
        "confirmedStateSha256": None,
    }


def _load_journal(path: Path) -> dict[str, Any]:
    try:
        details = path.lstat()
        if (
            stat.S_ISLNK(details.st_mode)
            or not stat.S_ISREG(details.st_mode)
            or stat.S_IMODE(details.st_mode) != 0o600
            or details.st_size > MAX_JSON_BYTES
        ):
            raise DeploymentExecutionError("JOURNAL_CORRUPT")
        data = path.read_bytes()
    except DeploymentExecutionError:
        raise
    except OSError as exc:
        raise DeploymentExecutionError("JOURNAL_CORRUPT") from exc
    value = _decode_object(data, "JOURNAL_CORRUPT")
    if data != _canonical(value):
        raise DeploymentExecutionError("JOURNAL_CORRUPT")
    _validate_journal(value)
    if path.name != f"{value['operationId']}.json":
        raise DeploymentExecutionError("JOURNAL_CORRUPT")
    return value


def _audit_journals(
    journal_dir: Path, operation_id: str
) -> tuple[Path, dict[str, Any] | None]:
    own_path = journal_dir / f"{operation_id}.json"
    own: dict[str, Any] | None = None
    try:
        entries = list(journal_dir.iterdir())
    except OSError as exc:
        raise DeploymentExecutionError("JOURNAL_CORRUPT") from exc
    for path in entries:
        if path.name == ".production.lock":
            continue
        if path.suffix != ".json":
            continue
        journal = _load_journal(path)
        if journal["operationId"] == operation_id:
            if own is not None:
                raise DeploymentExecutionError("JOURNAL_CORRUPT")
            own = journal
        elif journal["state"] not in TERMINAL_STATES:
            raise DeploymentExecutionError("PRODUCTION_OPERATION_ACTIVE", 4)
    if own_path.exists() and own is None:
        raise DeploymentExecutionError("JOURNAL_CORRUPT")
    return own_path, own


def _open_lock(journal_dir: Path) -> int:
    path = journal_dir / ".production.lock"
    flags = os.O_RDWR | os.O_CREAT | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(path, flags, 0o600)
        details = os.fstat(descriptor)
        if (
            not stat.S_ISREG(details.st_mode)
            or stat.S_IMODE(details.st_mode) != 0o600
        ):
            raise DeploymentExecutionError("UNSAFE_PATH", 4)
        try:
            fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as exc:
            raise DeploymentExecutionError(
                "PRODUCTION_OPERATION_ACTIVE", 4
            ) from exc
        return descriptor
    except DeploymentExecutionError:
        try:
            os.close(descriptor)
        except (OSError, UnboundLocalError):
            pass
        raise
    except OSError as exc:
        try:
            os.close(descriptor)
        except (OSError, UnboundLocalError):
            pass
        if exc.errno in {11, 13}:
            raise DeploymentExecutionError(
                "PRODUCTION_OPERATION_ACTIVE", 4
            ) from exc
        raise DeploymentExecutionError("UNSAFE_PATH", 4) from exc


def _next_state(bundle: Path) -> dict[str, Any]:
    data = _read_regular(
        bundle / "installed-state.next.json",
        MAX_JSON_BYTES,
        "INVALID_CONTRACT",
    )
    value = _decode_object(data, "INVALID_CONTRACT")
    if data != _canonical(value):
        raise DeploymentExecutionError("INVALID_CONTRACT")
    _validate_schema(value, INSTALLED_SCHEMA, "INVALID_CONTRACT")
    _validate_installed_semantics(value, confirmed=False)
    return value


def _run_transaction(
    journal: dict[str, Any],
    journal_path: Path,
    plan: dict[str, Any],
    next_state: dict[str, Any],
    operation_id: str,
    bundle: Path,
    installed_state_path: Path,
    adapter: DeploymentAdapter,
    clock: DeploymentClock,
) -> dict[str, Any]:
    while journal["state"] not in TERMINAL_STATES:
        state = journal["state"]
        try:
            if state == "QUEUED":
                _transition(journal, "PULLING", clock, journal_path)
                continue
            if state in STATE_ACTION:
                action = STATE_ACTION[state]
                failed_step = _journal_step(journal, action)
                if failed_step["status"] == "FAILED":
                    return _complete_failure(
                        journal,
                        failed_step["errorCode"],
                        plan,
                        operation_id,
                        bundle,
                        adapter,
                        clock,
                        journal_path,
                    )
                if _is_noop(action, plan):
                    _skip_step(journal, action, clock, journal_path)
                else:
                    context = _action_context(
                        action, operation_id, bundle, plan, journal
                    )
                    _run_adapter_action(
                        journal,
                        action,
                        context,
                        adapter,
                        clock,
                        journal_path,
                    )
                destination = {
                    "PULLING": "BACKING_UP",
                    "BACKING_UP": "MIGRATING",
                    "MIGRATING": "UPDATING",
                    "UPDATING": "VERIFYING",
                }[state]
                _transition(journal, destination, clock, journal_path)
                continue
            if state == "VERIFYING":
                verify_step = _journal_step(journal, "VERIFY")
                commit_step = _journal_step(journal, "COMMIT_STATE")
                for failed_step in (verify_step, commit_step):
                    if failed_step["status"] == "FAILED":
                        return _complete_failure(
                            journal,
                            failed_step["errorCode"],
                            plan,
                            operation_id,
                            bundle,
                            adapter,
                            clock,
                            journal_path,
                        )
                if verify_step["status"] not in {
                    "SUCCEEDED",
                    "SKIPPED",
                }:
                    context = _action_context(
                        "VERIFY", operation_id, bundle, plan, journal
                    )
                    _run_adapter_action(
                        journal,
                        "VERIFY",
                        context,
                        adapter,
                        clock,
                        journal_path,
                    )
                try:
                    _commit_state(
                        journal,
                        plan,
                        next_state,
                        installed_state_path,
                        clock,
                        journal_path,
                    )
                except _ActionFailure as exc:
                    commit_step = _journal_step(journal, "COMMIT_STATE")
                    if commit_step["status"] == "RUNNING":
                        _fail_step(
                            journal,
                            "COMMIT_STATE",
                            exc.code,
                            clock,
                            journal_path,
                        )
                    raise
                # Lowered before the terminal transition so the persisted journal
                # and the outcome derived from it agree: nothing is left to
                # restore once the deployment is committed.
                journal["databaseRestoreRequired"] = False
                _transition(journal, "SUCCEEDED", clock, journal_path)
                continue
            if state == "ROLLING_BACK":
                rollback_step = _journal_step(journal, "ROLLBACK")
                if rollback_step["status"] == "FAILED":
                    journal["rollbackErrorCode"] = "ROLLBACK_FAILED"
                    _transition(journal, "FAILED", clock, journal_path)
                    continue
                context = _action_context(
                    "ROLLBACK", operation_id, bundle, plan, journal
                )
                try:
                    _run_adapter_action(
                        journal,
                        "ROLLBACK",
                        context,
                        adapter,
                        clock,
                        journal_path,
                    )
                except _ActionFailure:
                    journal["rollbackErrorCode"] = "ROLLBACK_FAILED"
                    _transition(journal, "FAILED", clock, journal_path)
                else:
                    _transition(journal, "ROLLED_BACK", clock, journal_path)
                continue
            raise DeploymentExecutionError("JOURNAL_CORRUPT")
        except _ActionFailure as exc:
            return _complete_failure(
                journal,
                exc.code,
                plan,
                operation_id,
                bundle,
                adapter,
                clock,
                journal_path,
            )
    return copy.deepcopy(journal)


def execute_deployment(
    *,
    bundle: Path,
    operation_id: str,
    journal_dir: Path,
    installed_state_path: Path,
    adapter: DeploymentAdapter,
    clock: DeploymentClock,
    trusted_root: Path | None = None,
) -> dict[str, Any]:
    """Create or resume one deployment transaction without real side effects."""
    if not isinstance(operation_id, str) or OPERATION_RE.fullmatch(operation_id) is None:
        raise DeploymentExecutionError("INVALID_CONTRACT")
    bundle_path = _lexical_absolute(Path(bundle))
    try:
        plan = deployment_plan.validate_bundle(bundle_path)
    except Exception as exc:
        raise DeploymentExecutionError("INVALID_CONTRACT") from exc
    journal_root, state_path = _validate_paths(
        Path(journal_dir), Path(installed_state_path), trusted_root
    )
    try:
        identity_bytes = _read_regular(
            bundle_path / "bundle.sha256", MAX_JSON_BYTES, "INVALID_CONTRACT"
        )
        bundle_identity = _digest(identity_bytes)
        next_state = _next_state(bundle_path)
    except DeploymentExecutionError:
        raise
    except Exception as exc:
        raise DeploymentExecutionError("INVALID_CONTRACT") from exc

    lock_descriptor = _open_lock(journal_root)
    try:
        journal_path, journal = _audit_journals(journal_root, operation_id)
        if journal is None:
            _, source_hash = _validate_source(plan, state_path)
            journal = _new_journal(
                operation_id, bundle_identity, plan, source_hash, clock
            )
            _persist_journal(journal_path, journal)
        else:
            if (
                journal["bundleIdentity"] != bundle_identity
                or journal["sourceRelease"] != plan["sourceRelease"]
                or journal["targetRelease"] != plan["targetRelease"]
            ):
                raise DeploymentExecutionError("OPERATION_CONFLICT", 4)
            if journal["state"] == "SUCCEEDED":
                try:
                    classification, _, data = _state_classification(
                        state_path, journal, next_state, plan
                    )
                except _ActionFailure as exc:
                    raise DeploymentExecutionError(
                        "CURRENT_STATE_CONFLICT"
                    ) from exc
                if (
                    classification != "TARGET"
                    or data is None
                    or _digest(data) != journal["confirmedStateSha256"]
                ):
                    raise DeploymentExecutionError("CURRENT_STATE_CONFLICT")
                return copy.deepcopy(journal)
            if journal["state"] in {"FAILED", "ROLLED_BACK"}:
                return copy.deepcopy(journal)
            try:
                _state_classification(state_path, journal, next_state, plan)
            except _ActionFailure:
                return _complete_failure(
                    journal,
                    "CURRENT_STATE_CONFLICT",
                    plan,
                    operation_id,
                    bundle_path,
                    adapter,
                    clock,
                    journal_path,
                )
        return _run_transaction(
            journal,
            journal_path,
            plan,
            next_state,
            operation_id,
            bundle_path,
            state_path,
            adapter,
            clock,
        )
    finally:
        try:
            fcntl.flock(lock_descriptor, fcntl.LOCK_UN)
        finally:
            os.close(lock_descriptor)
