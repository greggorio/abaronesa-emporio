#!/usr/bin/env python3
"""Sanitized local CLI for the S20 production deployment adapter."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import stat
import sys
import tempfile
from pathlib import Path
from typing import Any, NoReturn


DEPLOY_TOOLS = Path(__file__).resolve().parent
ROOT = DEPLOY_TOOLS.parents[1]
# The vendored runtime must win over the global interpreter's site-packages,
# which on the production host is Ubuntu 22.04's jsonschema 3.2.0 — proven to
# silently accept invalid Draft 2020-12 prefixItems. Inserted first so it
# shadows anything Python already put on sys.path at startup. This module is
# exec'd directly by deploy-release.sh as its own interpreter process, so it
# cannot rely on a parent process having already adjusted sys.path.
sys.path.insert(0, str(ROOT / "vendor"))
sys.path.insert(0, str(DEPLOY_TOOLS))

import deployment_executor  # noqa: E402
import deployment_plan  # noqa: E402
import production_adapter  # noqa: E402


DEFAULT_DEPLOY_ROOT = Path("/opt/sistemas/emporio")
OPERATION_RE = re.compile(r"^[A-Za-z0-9_-]{20,128}$")
RELEASE_RE = re.compile(r"^v(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)$")
DIGEST_RE = re.compile(r"^sha256:[0-9a-f]{64}$")
MAX_JSON_BYTES = 2 * 1024 * 1024
TERMINAL_EXIT = {"SUCCEEDED": 0, "ROLLED_BACK": 20, "FAILED": 21}


class DeploymentCliError(Exception):
    """Stable pre-journal failure with no internal payload."""

    def __init__(self, code: str, exit_code: int = 6):
        super().__init__(code)
        self.code = code
        self.exit_code = exit_code

    def __str__(self) -> str:
        return self.code


class SystemClock:
    """UTC second-precision clock used only by the operational entrypoint."""

    def now(self) -> str:
        from datetime import datetime, timezone

        return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def _canonical(value: Any) -> str:
    return json.dumps(
        value, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    )


def _lexical_absolute(path: Path) -> Path:
    return Path(os.path.abspath(os.fspath(path)))


def _components(path: Path) -> list[Path]:
    result: list[Path] = []
    current = path
    while True:
        result.append(current)
        if current.parent == current:
            return list(reversed(result))
        current = current.parent


def _check_existing_component(path: Path) -> None:
    try:
        details = path.lstat()
    except OSError as exc:
        raise DeploymentCliError("UNSAFE_PATH", 4) from exc
    if stat.S_ISLNK(details.st_mode):
        raise DeploymentCliError("UNSAFE_PATH", 4)
    if details.st_mode & 0o022:
        raise DeploymentCliError("UNSAFE_PATH", 4)


def _validate_root(value: str | Path) -> Path:
    raw = os.fspath(value)
    if (
        not raw
        or "\x00" in raw
        or "\n" in raw
        or "\r" in raw
        or not Path(raw).is_absolute()
        or ".." in Path(raw).parts
    ):
        raise DeploymentCliError("UNSAFE_PATH", 4)
    root = _lexical_absolute(Path(raw))
    if root == Path("/"):
        raise DeploymentCliError("UNSAFE_PATH", 4)
    for component in _components(root):
        if component.exists() or component.is_symlink():
            _check_existing_component(component)
    if (
        not root.exists()
        or not root.is_dir()
        or root.resolve(strict=True) != root
        or root.stat().st_uid != os.geteuid()
    ):
        raise DeploymentCliError("UNSAFE_PATH", 4)
    return root


def _mkdir_secure(path: Path) -> None:
    try:
        if path.exists() or path.is_symlink():
            _check_existing_component(path)
            if not path.is_dir() or stat.S_IMODE(path.stat().st_mode) != 0o700:
                raise DeploymentCliError("UNSAFE_PATH", 4)
            return
        path.mkdir(mode=0o700)
        if stat.S_IMODE(path.stat().st_mode) != 0o700:
            raise DeploymentCliError("UNSAFE_PATH", 4)
    except DeploymentCliError:
        raise
    except OSError as exc:
        raise DeploymentCliError("OPERATIONAL_IO_FAILED") from exc


def _regular_file(
    path: Path, *, mode: int | None = None, limit: int = MAX_JSON_BYTES
) -> bytes:
    try:
        details = path.lstat()
        if (
            stat.S_ISLNK(details.st_mode)
            or not stat.S_ISREG(details.st_mode)
            or details.st_uid != os.geteuid()
            or details.st_mode & 0o022
            or (mode is not None and stat.S_IMODE(details.st_mode) != mode)
            or details.st_size > limit
        ):
            raise DeploymentCliError("UNSAFE_PATH", 4)
        data = path.read_bytes()
    except DeploymentCliError:
        raise
    except OSError as exc:
        raise DeploymentCliError("UNSAFE_PATH", 4) from exc
    if len(data) > limit:
        raise DeploymentCliError("UNSAFE_PATH", 4)
    return data


def _load_json(path: Path) -> dict[str, Any]:
    raw = _regular_file(path)
    try:
        value = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise DeploymentCliError("INVALID_CONTRACT", 3) from exc
    if not isinstance(value, dict):
        raise DeploymentCliError("INVALID_CONTRACT", 3)
    return value


def _link_release(root: Path, name: str) -> str | None:
    path = root / name
    try:
        details = path.lstat()
    except FileNotFoundError:
        return None
    except OSError as exc:
        raise DeploymentCliError("UNSAFE_LINK_STATE") from exc
    if not stat.S_ISLNK(details.st_mode):
        raise DeploymentCliError("UNSAFE_LINK_STATE")
    try:
        target = os.readlink(path)
    except OSError as exc:
        raise DeploymentCliError("UNSAFE_LINK_STATE") from exc
    match = re.fullmatch(
        r"releases/(v(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*))",
        target,
    )
    if match is None:
        raise DeploymentCliError("UNSAFE_LINK_STATE")
    release = match.group(1)
    expected = root / "releases" / release
    try:
        if expected.resolve(strict=True) != (root / name).resolve(strict=True):
            raise DeploymentCliError("UNSAFE_LINK_STATE")
    except (OSError, RuntimeError) as exc:
        raise DeploymentCliError("UNSAFE_LINK_STATE") from exc
    return release


def _journal_state(path: Path) -> str | None:
    if not path.exists() and not path.is_symlink():
        return None
    value = _load_json(path)
    state = value.get("state")
    if not isinstance(state, str):
        raise DeploymentCliError("INVALID_CONTRACT", 3)
    return state


def _validate_link_window(
    *,
    root: Path,
    plan: dict[str, Any],
    journal_state: str | None,
    installed_state: dict[str, Any] | None,
) -> None:
    source = plan["sourceRelease"]
    target = plan["targetRelease"]
    current = _link_release(root, "current")
    previous = _link_release(root, "previous")
    if source is None:
        if journal_state == "SUCCEEDED":
            if current not in {None, target} or previous is not None:
                raise DeploymentCliError("UNSAFE_LINK_STATE")
            if installed_state is None or installed_state.get("release") != target:
                raise DeploymentCliError("CURRENT_STATE_CONFLICT", 3)
        elif current is not None or previous is not None:
            raise DeploymentCliError("UNSAFE_LINK_STATE")
        return
    if journal_state == "SUCCEEDED":
        if current not in {source, target} or previous not in {None, source}:
            raise DeploymentCliError("UNSAFE_LINK_STATE")
        if installed_state is None or installed_state.get("release") != target:
            raise DeploymentCliError("CURRENT_STATE_CONFLICT", 3)
        if current == target and previous != source:
            raise DeploymentCliError("UNSAFE_LINK_STATE")
    elif current != source:
        raise DeploymentCliError("UNSAFE_LINK_STATE")


def _replace_link(root: Path, name: str, release: str) -> None:
    destination = root / name
    temporary: Path | None = None
    replaced = False
    try:
        descriptor, raw_name = tempfile.mkstemp(prefix=f".{name}.tmp-", dir=root)
        os.close(descriptor)
        temporary = Path(raw_name)
        temporary.unlink()
        temporary.symlink_to(Path("releases") / release)
        if os.readlink(temporary) != f"releases/{release}":
            raise DeploymentCliError("LINK_RECONCILIATION_FAILED")
        os.replace(temporary, destination)
        replaced = True
    except DeploymentCliError:
        raise
    except OSError as exc:
        raise DeploymentCliError("LINK_RECONCILIATION_FAILED") from exc
    finally:
        if temporary is not None and not replaced:
            try:
                temporary.unlink(missing_ok=True)
            except OSError:
                pass


def _fsync_directory(path: Path) -> None:
    try:
        descriptor = os.open(
            path,
            os.O_RDONLY
            | getattr(os, "O_DIRECTORY", 0)
            | getattr(os, "O_NOFOLLOW", 0),
        )
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)
    except OSError as exc:
        raise DeploymentCliError("LINK_RECONCILIATION_FAILED") from exc


def _canonical_state_bytes(value: dict[str, Any]) -> bytes:
    return (
        json.dumps(
            value, ensure_ascii=False, sort_keys=True, separators=(",", ":")
        ).encode("utf-8")
        + b"\n"
    )


def _installed_state_digest(data: bytes) -> str:
    return "sha256:" + hashlib.sha256(data).hexdigest()


def _confirmed_installed_state(
    state_path: Path, target: str
) -> tuple[dict[str, Any], str]:
    data = _regular_file(state_path, mode=0o600, limit=MAX_JSON_BYTES)
    try:
        value = json.loads(data.decode("utf-8"))
        if not isinstance(value, dict) or data != _canonical_state_bytes(value):
            raise ValueError("non-canonical installed state")
        deployment_executor._validate_schema(
            value,
            deployment_executor.INSTALLED_SCHEMA,
            "CURRENT_STATE_CONFLICT",
        )
        deployment_executor._validate_installed_semantics(
            value, confirmed=True
        )
        if value.get("release") != target:
            raise ValueError("release mismatch")
    except (
        UnicodeDecodeError,
        json.JSONDecodeError,
        deployment_executor.DeploymentExecutionError,
        KeyError,
        TypeError,
        ValueError,
    ) as exc:
        raise DeploymentCliError("CURRENT_STATE_CONFLICT", 3) from exc
    return value, _installed_state_digest(data)


def _reconcile_links(
    root: Path, plan: dict[str, Any], journal: dict[str, Any], state_path: Path
) -> None:
    if journal["state"] != "SUCCEEDED":
        return
    target = plan["targetRelease"]
    source = plan["sourceRelease"]
    confirmed_sha = journal.get("confirmedStateSha256")
    if not isinstance(confirmed_sha, str) or DIGEST_RE.fullmatch(confirmed_sha) is None:
        raise DeploymentCliError("CURRENT_STATE_CONFLICT", 3)
    _, observed_sha = _confirmed_installed_state(state_path, target)
    if observed_sha != confirmed_sha:
        raise DeploymentCliError("CURRENT_STATE_CONFLICT", 3)
    current = _link_release(root, "current")
    previous = _link_release(root, "previous")
    if source is not None:
        if previous != source:
            _replace_link(root, "previous", source)
        if current != target:
            _replace_link(root, "current", target)
    else:
        if previous is not None:
            raise DeploymentCliError("UNSAFE_LINK_STATE")
        if current != target:
            _replace_link(root, "current", target)
    _fsync_directory(root)
    if _link_release(root, "current") != target:
        raise DeploymentCliError("LINK_RECONCILIATION_FAILED")
    if source is not None and _link_release(root, "previous") != source:
        raise DeploymentCliError("LINK_RECONCILIATION_FAILED")
    _, reconfirmed_sha = _confirmed_installed_state(state_path, target)
    if reconfirmed_sha != confirmed_sha:
        raise DeploymentCliError("CURRENT_STATE_CONFLICT", 3)


def _binary(name: str) -> Path:
    """Resolve docker/curl exclusively through the adapter's fixed PATH; the
    ambient process environment's PATH is never consulted."""
    try:
        return production_adapter.resolve_binary(name)
    except production_adapter.ProductionAdapterError as exc:
        raise DeploymentCliError("DEPENDENCY_UNAVAILABLE") from exc


def _output(journal: dict[str, Any]) -> str:
    value = {
        "databaseRestoreRequired": journal["databaseRestoreRequired"],
        "errorCode": journal["errorCode"],
        "operationId": journal["operationId"],
        "state": journal["state"],
    }
    return _canonical(value)


def execute(
    *,
    operation_id: str,
    release: str,
    deploy_root: Path,
    runner: production_adapter.ProcessRunner | None = None,
    clock: deployment_executor.DeploymentClock | None = None,
    docker_binary: Path | None = None,
    curl_binary: Path | None = None,
) -> tuple[dict[str, Any], int]:
    if OPERATION_RE.fullmatch(operation_id) is None or RELEASE_RE.fullmatch(release) is None:
        raise DeploymentCliError("INVALID_ARGUMENT", 2)
    root = _validate_root(deploy_root)
    shared = root / "shared"
    releases = root / "releases"
    if not shared.is_dir() or not releases.is_dir():
        raise DeploymentCliError("UNSAFE_PATH", 4)
    for existing in (shared, releases):
        _check_existing_component(existing)
        if existing.stat().st_uid != os.geteuid():
            raise DeploymentCliError("UNSAFE_PATH", 4)
    deploy_dir = shared / "deploy"
    journals = deploy_dir / "journals"
    backups = shared / "backups"
    for directory in (deploy_dir, journals, backups):
        _mkdir_secure(directory)
    env_file = shared / ".env"
    _regular_file(env_file, mode=0o600, limit=1024 * 1024)
    bundle = releases / release
    if bundle.is_symlink() or not bundle.is_dir() or bundle.parent != releases:
        raise DeploymentCliError("INVALID_CONTRACT", 3)
    try:
        plan = deployment_plan.validate_bundle(bundle)
    except deployment_plan.DeploymentPlanError:
        raise
    if plan.get("targetRelease") != release:
        raise DeploymentCliError("RELEASE_MISMATCH", 3)
    source = plan.get("sourceRelease")
    if source is not None:
        historical = releases / source
        try:
            historical_plan = deployment_plan.validate_bundle(historical)
        except deployment_plan.DeploymentPlanError as exc:
            raise DeploymentCliError("SOURCE_BUNDLE_INVALID", 3) from exc
        if historical_plan.get("targetRelease") != source:
            raise DeploymentCliError("SOURCE_BUNDLE_INVALID", 3)
    state_path = deploy_dir / "installed-state.json"
    installed = _load_json(state_path) if state_path.exists() else None
    journal_path = journals / f"{operation_id}.json"
    journal_state = _journal_state(journal_path)
    _validate_link_window(
        root=root,
        plan=plan,
        journal_state=journal_state,
        installed_state=installed,
    )
    actual_runner = runner or production_adapter.SubprocessRunner()
    actual_clock = clock or SystemClock()
    docker = docker_binary or _binary("docker")
    curl = curl_binary or _binary("curl")
    adapter = production_adapter.ProductionDeploymentAdapter(
        root=root,
        bundle=bundle,
        plan=plan,
        runner=actual_runner,
        clock=actual_clock,
        docker_binary=docker,
        curl_binary=curl,
    )
    adapter.validate_compose()
    journal = deployment_executor.execute_deployment(
        bundle=bundle,
        operation_id=operation_id,
        journal_dir=journals,
        installed_state_path=state_path,
        adapter=adapter,
        clock=actual_clock,
        # Already validated by _validate_root above: absolute, no "..", no
        # symlinked component, owned by the effective user and writable by nobody
        # else. Without it the executor falls back to inferring a workspace from
        # its own module path, which in production resolves to the control root —
        # a sibling of the deploy root, so the journal directory is refused.
        trusted_root=root,
    )
    _reconcile_links(root, plan, journal, state_path)
    return journal, TERMINAL_EXIT[journal["state"]]


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="deployment_cli.py")
    subparsers = parser.add_subparsers(dest="command", required=True)
    deploy = subparsers.add_parser("deploy")
    deploy.add_argument("--operation-id", required=True)
    deploy.add_argument("--release", required=True)
    return parser


def _argument_error(message: str) -> NoReturn:
    del message
    raise DeploymentCliError("INVALID_ARGUMENT", 2)


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    parser.error = _argument_error  # type: ignore[method-assign]
    try:
        args = parser.parse_args(argv)
        raw_root = os.environ.get("EMPORIO_DEPLOY_ROOT", os.fspath(DEFAULT_DEPLOY_ROOT))
        journal, exit_code = execute(
            operation_id=args.operation_id,
            release=args.release,
            deploy_root=Path(raw_root),
        )
        print(_output(journal))
        return exit_code
    except (
        DeploymentCliError,
        deployment_plan.DeploymentPlanError,
        deployment_executor.DeploymentExecutionError,
        production_adapter.ProductionAdapterError,
    ) as exc:
        print(_canonical({"errorCode": exc.code}), file=sys.stderr)
        return exc.exit_code
    except Exception:
        print(_canonical({"errorCode": "OPERATIONAL_FAILURE"}), file=sys.stderr)
        return 6


if __name__ == "__main__":
    raise SystemExit(main())
