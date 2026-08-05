#!/usr/bin/env python3
"""Hardened Docker Compose adapter for the S19 transactional core."""

from __future__ import annotations

import hashlib
import json
import os
import pwd
import re
import select
import shutil
import stat
import subprocess
import sys
import tempfile
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Protocol

import jsonschema

ROOT = Path(__file__).resolve().parents[2]
DEPLOY_TOOLS = ROOT / "tools/deploy"
if __package__:
    from . import deployment_executor, deployment_plan  # type: ignore[attr-defined]
else:
    sys.path.insert(0, str(DEPLOY_TOOLS))
    import deployment_executor  # type: ignore[no-redef]  # noqa: E402
    import deployment_plan  # type: ignore[no-redef]  # noqa: E402

ActionContext = deployment_executor.ActionContext
ProbeResult = deployment_executor.ProbeResult
DeploymentClock = deployment_executor.DeploymentClock

BACKUP_SCHEMA = ROOT / "ops/deploy/schemas/backup-manifest.schema.json"
MAX_OUTPUT_BYTES = 65536
MAX_JSON_BYTES = 2 * 1024 * 1024
PROJECT_NAME = "abaronesa-emporio"
GATEWAY_LOOPBACK_PORT_DEFAULT = 8120
COMPONENTS = (
    "backend",
    "website_back",
    "frontend",
    "website_front",
    "whatsapp_service",
    "gateway",
)
ALL_SERVICES = (*COMPONENTS, "postgresql")
DATABASES = ("erp", "website")
DATABASE_SERVICE = {"erp": "backend", "website": "website_back"}
MIGRATION_MARKERS = frozenset(
    ("MIGRATIONS_APPLIED", "MIGRATIONS_PENDING", "MIGRATIONS_FAILED")
)
HOSTS = (
    "erp-emporio.abaronesa.net.br",
    "emporio.abaronesa.net.br",
)
SMOKES = (
    ("erp-emporio.abaronesa.net.br", "/healthz"),
    ("erp-emporio.abaronesa.net.br", "/"),
    ("emporio.abaronesa.net.br", "/healthz"),
    ("emporio.abaronesa.net.br", "/"),
)
EVIDENCE_PREFIX = {
    "PULL": "pull:",
    "BACKUP": "backup:",
    "MIGRATE": "migrate:",
    "UPDATE": "update:",
    "VERIFY": "verify:",
    "ROLLBACK": "rollback:",
}
TIME_RE = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$")
OPERATION_RE = re.compile(r"^[A-Za-z0-9_-]{20,128}$")
SEMVER_RE = re.compile(r"^v(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)$")
SAFE_TOKEN_RE = re.compile(r"^[A-Za-z0-9_.:-]{1,128}$")
DIGEST_RE = re.compile(r"^sha256:[0-9a-f]{64}$")
IMMUTABLE_RE = re.compile(
    r"^ghcr\.io/greggorio/abaronesa-emporio-[a-z-]+@sha256:[0-9a-f]{64}$"
)
MINIMUM_ENV = {
    "PATH": "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
    "LANG": "C.UTF-8",
    "LC_ALL": "C.UTF-8",
}


class ProductionAdapterError(Exception):
    """Stable operational error; internal process details are never rendered."""

    def __init__(self, code: str, exit_code: int = 6):
        super().__init__(code)
        self.code = code
        self.exit_code = exit_code

    def __str__(self) -> str:
        return self.code


@dataclass(frozen=True)
class ProcessResult:
    return_code: int
    stdout: bytes


class ProcessRunner(Protocol):
    def run(
        self,
        argv: tuple[str, ...],
        *,
        timeout_seconds: int,
        stdout_file: Path | None = None,
    ) -> ProcessResult: ...


def _is_safe_text(value: str) -> bool:
    return (
        bool(value)
        and "\x00" not in value
        and "\n" not in value
        and "\r" not in value
    )


def _validate_binary_path(path: Path) -> Path:
    if not path.is_absolute() or not _is_safe_text(os.fspath(path)):
        raise ProductionAdapterError("UNSAFE_BINARY")
    try:
        resolved = path.resolve(strict=True)
        details = resolved.lstat()
    except (OSError, RuntimeError) as exc:
        raise ProductionAdapterError("UNSAFE_BINARY") from exc
    if (
        resolved.is_symlink()
        or not stat.S_ISREG(details.st_mode)
        or details.st_uid != 0
        or stat.S_IMODE(details.st_mode) & 0o022
        or not os.access(resolved, os.X_OK)
    ):
        raise ProductionAdapterError("UNSAFE_BINARY")
    return resolved


def resolve_binary(name: str) -> Path:
    """Resolve one explicit host dependency to a root-owned immutable path."""
    if name not in {"docker", "curl"}:
        raise ProductionAdapterError("UNSAFE_BINARY")
    located = shutil.which(name, path=MINIMUM_ENV["PATH"])
    if located is None:
        raise ProductionAdapterError("BINARY_UNAVAILABLE")
    return _validate_binary_path(Path(located))


def _effective_docker_config() -> Path:
    """Resolve Docker credentials from the effective OS identity, never env."""
    uid = os.geteuid()
    try:
        identity = pwd.getpwuid(uid)
        home = Path(identity.pw_dir)
    except (KeyError, OSError, TypeError) as exc:
        raise ProductionAdapterError("DOCKER_CONFIG_INVALID") from exc
    if not home.is_absolute() or not _is_safe_text(os.fspath(home)):
        raise ProductionAdapterError("DOCKER_CONFIG_INVALID")
    docker_config = home / ".docker"
    config_file = docker_config / "config.json"
    try:
        home_details = home.lstat()
        docker_details = docker_config.lstat()
        config_details = config_file.lstat()
        home_resolved = home.resolve(strict=True)
        docker_resolved = docker_config.resolve(strict=True)
        config_resolved = config_file.resolve(strict=True)
    except (OSError, RuntimeError) as exc:
        raise ProductionAdapterError("DOCKER_CONFIG_INVALID") from exc
    if (
        home_resolved != home
        or docker_resolved != docker_config
        or config_resolved != config_file
        or docker_resolved.parent != home_resolved
        or config_resolved.parent != docker_resolved
        or home.is_symlink()
        or docker_config.is_symlink()
        or config_file.is_symlink()
        or not stat.S_ISDIR(home_details.st_mode)
        or not stat.S_ISDIR(docker_details.st_mode)
        or not stat.S_ISREG(config_details.st_mode)
        or home_details.st_uid != uid
        or docker_details.st_uid != uid
        or config_details.st_uid != uid
        or stat.S_IMODE(home_details.st_mode) & 0o022
        or stat.S_IMODE(docker_details.st_mode) != 0o700
        or stat.S_IMODE(config_details.st_mode) != 0o600
    ):
        raise ProductionAdapterError("DOCKER_CONFIG_INVALID")
    return docker_resolved


def _command_environment(argv: tuple[str, ...]) -> dict[str, str]:
    environment = dict(MINIMUM_ENV)
    if Path(argv[0]).name == "docker":
        environment["DOCKER_CONFIG"] = os.fspath(_effective_docker_config())
    return environment


class SubprocessRunner:
    """Real runner with bounded capture, fixed environment and no shell."""

    def run(
        self,
        argv: tuple[str, ...],
        *,
        timeout_seconds: int,
        stdout_file: Path | None = None,
    ) -> ProcessResult:
        if (
            not isinstance(argv, tuple)
            or not argv
            or not isinstance(timeout_seconds, int)
            or isinstance(timeout_seconds, bool)
            or timeout_seconds <= 0
            or any(not isinstance(item, str) or not _is_safe_text(item) for item in argv)
        ):
            raise ProductionAdapterError("INVALID_COMMAND")
        executable = _validate_binary_path(Path(argv[0]))
        if executable.name not in {"docker", "curl"}:
            raise ProductionAdapterError("UNSAFE_BINARY")
        normalized_argv = (os.fspath(executable), *argv[1:])
        if stdout_file is None:
            return self._run_captured(normalized_argv, timeout_seconds)
        return self._run_to_file(normalized_argv, timeout_seconds, stdout_file)

    def _run_to_file(
        self, argv: tuple[str, ...], timeout_seconds: int, stdout_file: Path
    ) -> ProcessResult:
        destination = _safe_output_destination(stdout_file)
        descriptor = os.open(
            destination,
            os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0),
            0o600,
        )
        output_stream = os.fdopen(descriptor, "wb")
        try:
            completed = subprocess.run(
                argv,
                shell=False,
                stdin=subprocess.DEVNULL,
                stdout=output_stream,
                stderr=subprocess.DEVNULL,
                close_fds=True,
                timeout=timeout_seconds,
                env=_command_environment(argv),
                check=False,
            )
            output_stream.flush()
            os.fsync(output_stream.fileno())
            return ProcessResult(completed.returncode, b"")
        except subprocess.TimeoutExpired as exc:
            raise ProductionAdapterError("COMMAND_TIMEOUT") from exc
        except OSError as exc:
            raise ProductionAdapterError("COMMAND_FAILED") from exc
        finally:
            output_stream.close()

    def _run_captured(
        self, argv: tuple[str, ...], timeout_seconds: int
    ) -> ProcessResult:
        try:
            process = subprocess.Popen(
                argv,
                shell=False,
                stdin=subprocess.DEVNULL,
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                close_fds=True,
                env=_command_environment(argv),
            )
        except OSError as exc:
            raise ProductionAdapterError("COMMAND_FAILED") from exc
        stdout = process.stdout
        assert stdout is not None
        deadline = time.monotonic() + timeout_seconds
        buffer = bytearray()
        outcome: str | None = None
        try:
            while True:
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    outcome = "COMMAND_TIMEOUT"
                    break
                ready, _, _ = select.select([stdout], [], [], min(remaining, 1.0))
                if not ready:
                    continue
                chunk = os.read(stdout.fileno(), 65536)
                if chunk == b"":
                    break
                buffer.extend(chunk)
                if len(buffer) > MAX_OUTPUT_BYTES:
                    outcome = "OUTPUT_LIMIT_EXCEEDED"
                    break
        except OSError as exc:
            outcome = outcome or "COMMAND_FAILED"
        finally:
            try:
                stdout.close()
            except OSError:
                pass
        if outcome is not None:
            self._terminate(process)
            raise ProductionAdapterError(outcome)
        try:
            return_code = process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            self._terminate(process)
            raise ProductionAdapterError("COMMAND_TIMEOUT")
        return ProcessResult(return_code, bytes(buffer))

    @staticmethod
    def _terminate(process: subprocess.Popen) -> None:
        try:
            process.terminate()
        except OSError:
            pass
        try:
            process.wait(timeout=5)
            return
        except subprocess.TimeoutExpired:
            pass
        try:
            process.kill()
        except OSError:
            pass
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            pass


def _safe_output_destination(path: Path) -> Path:
    destination = Path(path)
    if (
        not destination.is_absolute()
        or not _is_safe_text(os.fspath(destination))
        or destination.name in {"", ".", ".."}
    ):
        raise ProductionAdapterError("UNSAFE_PATH")
    try:
        parent = destination.parent.resolve(strict=True)
        if (
            parent.is_symlink()
            or not parent.is_dir()
            or stat.S_IMODE(parent.stat().st_mode) != 0o700
            or parent.stat().st_uid != os.geteuid()
            or destination.exists()
            or destination.is_symlink()
        ):
            raise ProductionAdapterError("UNSAFE_PATH")
    except ProductionAdapterError:
        raise
    except (OSError, RuntimeError) as exc:
        raise ProductionAdapterError("UNSAFE_PATH") from exc
    return parent / destination.name


def _canonical(value: Any) -> bytes:
    return (
        json.dumps(
            value, ensure_ascii=False, sort_keys=True, separators=(",", ":")
        ).encode("utf-8")
        + b"\n"
    )


def _digest(data: bytes) -> str:
    return "sha256:" + hashlib.sha256(data).hexdigest()


def _evidence_suffix(value: Any) -> str:
    return hashlib.sha256(_canonical(value)).hexdigest()[:12]


def _parse_time(value: Any) -> datetime:
    if not isinstance(value, str) or TIME_RE.fullmatch(value) is None:
        raise ProductionAdapterError("INVALID_CLOCK")
    try:
        return datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(
            tzinfo=timezone.utc
        )
    except ValueError as exc:
        raise ProductionAdapterError("INVALID_CLOCK") from exc


def _clock_now(clock: DeploymentClock) -> str:
    try:
        value = clock.now()
    except Exception as exc:
        raise ProductionAdapterError("INVALID_CLOCK") from exc
    _parse_time(value)
    return value


def _decode_json(data: bytes, code: str) -> Any:
    if len(data) > MAX_OUTPUT_BYTES or data.startswith(b"\xef\xbb\xbf"):
        raise ProductionAdapterError(code)
    try:
        return json.loads(data.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ProductionAdapterError(code) from exc


def _safe_directory(path: Path, *, mode: int | None = 0o700) -> Path:
    if (
        not path.is_absolute()
        or not _is_safe_text(os.fspath(path))
        or ".." in path.parts
    ):
        raise ProductionAdapterError("UNSAFE_PATH")
    try:
        resolved = path.resolve(strict=True)
        details = resolved.lstat()
        if (
            resolved != path
            or resolved.is_symlink()
            or not stat.S_ISDIR(details.st_mode)
            or (
                stat.S_IMODE(details.st_mode) != mode
                if mode is not None
                else bool(details.st_mode & 0o022)
            )
            or details.st_uid != os.geteuid()
        ):
            raise ProductionAdapterError("UNSAFE_PATH")
    except ProductionAdapterError:
        raise
    except (OSError, RuntimeError) as exc:
        raise ProductionAdapterError("UNSAFE_PATH") from exc
    return resolved


def _safe_regular(path: Path, *, mode: int, limit: int) -> bytes:
    try:
        details = path.lstat()
        if (
            stat.S_ISLNK(details.st_mode)
            or not stat.S_ISREG(details.st_mode)
            or details.st_uid != os.geteuid()
            or stat.S_IMODE(details.st_mode) != mode
            or details.st_mode & 0o022
            or details.st_size > limit
        ):
            raise ProductionAdapterError("UNSAFE_PATH")
        data = path.read_bytes()
    except ProductionAdapterError:
        raise
    except OSError as exc:
        raise ProductionAdapterError("UNSAFE_PATH") from exc
    if len(data) > limit:
        raise ProductionAdapterError("UNSAFE_PATH")
    return data


DUMP_CHUNK_BYTES = 1024 * 1024


def _dump_metadata(path: Path, *, code: str = "BACKUP_INVALID") -> tuple[int, str]:
    """Stream-hash a `.dump` artifact without ever materializing it in memory."""
    try:
        initial = path.lstat()
        if (
            stat.S_ISLNK(initial.st_mode)
            or not stat.S_ISREG(initial.st_mode)
            or initial.st_uid != os.geteuid()
            or stat.S_IMODE(initial.st_mode) != 0o600
        ):
            raise ProductionAdapterError(code)
        initial_size = initial.st_size
        if initial_size <= 0:
            raise ProductionAdapterError(code)
        digest = hashlib.sha256()
        read_total = 0
        with open(path, "rb") as stream:
            while True:
                chunk = stream.read(DUMP_CHUNK_BYTES)
                if not chunk:
                    break
                digest.update(chunk)
                read_total += len(chunk)
                if read_total > initial_size:
                    raise ProductionAdapterError(code)
        final = path.lstat()
        if (
            stat.S_ISLNK(final.st_mode)
            or not stat.S_ISREG(final.st_mode)
            or final.st_uid != os.geteuid()
            or stat.S_IMODE(final.st_mode) != 0o600
            or final.st_size != initial_size
            or read_total != initial_size
        ):
            raise ProductionAdapterError(code)
    except ProductionAdapterError:
        raise
    except OSError as exc:
        raise ProductionAdapterError(code) from exc
    return initial_size, "sha256:" + digest.hexdigest()


def _fsync_directory(path: Path) -> None:
    descriptor = os.open(
        path,
        os.O_RDONLY | getattr(os, "O_DIRECTORY", 0) | getattr(os, "O_NOFOLLOW", 0),
    )
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def _atomic_json(path: Path, value: dict[str, Any]) -> bytes:
    data = _canonical(value)
    temporary: Path | None = None
    replaced = False
    try:
        descriptor, raw_name = tempfile.mkstemp(
            prefix=f".{path.name}.tmp-", dir=path.parent
        )
        temporary = Path(raw_name)
        os.fchmod(descriptor, 0o600)
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        if temporary.read_bytes() != data:
            raise ProductionAdapterError("BACKUP_IO_FAILED")
        os.replace(temporary, path)
        replaced = True
        _fsync_directory(path.parent)
        return data
    except ProductionAdapterError:
        raise
    except OSError as exc:
        raise ProductionAdapterError("BACKUP_IO_FAILED") from exc
    finally:
        if temporary is not None and not replaced:
            try:
                temporary.unlink()
            except FileNotFoundError:
                pass
            except OSError:
                pass


def _parse_json_lines(data: bytes) -> list[dict[str, Any]]:
    stripped = data.strip()
    if not stripped:
        return []
    try:
        parsed = json.loads(stripped.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        parsed = None
    if isinstance(parsed, dict):
        return [parsed]
    if isinstance(parsed, list) and all(
        isinstance(item, dict) for item in parsed
    ):
        return parsed
    result: list[dict[str, Any]] = []
    for line in stripped.splitlines():
        item = _decode_json(line, "INVALID_PROCESS_OUTPUT")
        if not isinstance(item, dict):
            raise ProductionAdapterError("INVALID_PROCESS_OUTPUT")
        result.append(item)
    return result


class ProductionDeploymentAdapter:
    """Production action adapter; all effects are delegated to ProcessRunner."""

    def __init__(
        self,
        *,
        root: Path,
        bundle: Path,
        plan: dict[str, Any],
        runner: ProcessRunner,
        clock: DeploymentClock,
        docker_binary: Path,
        curl_binary: Path,
        gateway_loopback_port: int = GATEWAY_LOOPBACK_PORT_DEFAULT,
    ):
        self.root = _safe_directory(Path(root), mode=None)
        self.shared = _safe_directory(self.root / "shared", mode=None)
        self.backups = _safe_directory(self.shared / "backups")
        self.releases = _safe_directory(self.root / "releases", mode=None)
        self.bundle = _safe_directory(Path(bundle))
        if self.bundle.parent != self.releases:
            raise ProductionAdapterError("UNSAFE_PATH")
        self.env_file = self.shared / ".env"
        _safe_regular(self.env_file, mode=0o600, limit=MAX_JSON_BYTES)
        try:
            validated_plan = deployment_plan.validate_bundle(self.bundle)
        except Exception as exc:
            raise ProductionAdapterError("INVALID_BUNDLE") from exc
        if validated_plan != plan:
            raise ProductionAdapterError("INVALID_BUNDLE")
        self.plan = validated_plan
        self.manifest = self._bundle_json(self.bundle, "manifest.json")
        self.target_refs = {
            item["id"]: item["immutableRef"] for item in self.manifest["components"]
        }
        self.runner = runner
        self.clock = clock
        self.docker = _validate_binary_path(Path(docker_binary))
        self.curl = _validate_binary_path(Path(curl_binary))
        if self.docker.name != "docker" or self.curl.name != "curl":
            raise ProductionAdapterError("UNSAFE_BINARY")
        if (
            not isinstance(gateway_loopback_port, int)
            or isinstance(gateway_loopback_port, bool)
            or not 1024 <= gateway_loopback_port <= 65535
        ):
            raise ProductionAdapterError("INVALID_GATEWAY_PORT")
        self.gateway_loopback_port = gateway_loopback_port
        self._source_bundle_cache: Path | None = None
        self._source_refs_cache: dict[str, str] | None = None
        self._source_migration_cache: dict[str, str] | None = None

    def validate_compose(self) -> None:
        result = self._run(
            self._compose_base() + ("config", "--quiet"), 30
        )
        if result.return_code != 0 or result.stdout:
            raise ProductionAdapterError("COMPOSE_CONFIG_FAILED")

    def _bundle_json(
        self, bundle: Path, name: str
    ) -> dict[str, Any]:
        data = _safe_regular(bundle / name, mode=0o600, limit=MAX_JSON_BYTES)
        value = _decode_json(data, "INVALID_BUNDLE")
        if not isinstance(value, dict) or data != _canonical(value):
            raise ProductionAdapterError("INVALID_BUNDLE")
        return value

    def _compose_base(self, bundle: Path | None = None) -> tuple[str, ...]:
        selected = self.bundle if bundle is None else bundle
        return (
            os.fspath(self.docker),
            "compose",
            "--project-name",
            PROJECT_NAME,
            "--env-file",
            os.fspath(self.env_file),
            "--env-file",
            os.fspath(selected / "release.env"),
            "-f",
            os.fspath(selected / "compose.prod.yml"),
        )

    def _run(
        self,
        argv: tuple[str, ...],
        timeout: int,
        *,
        stdout_file: Path | None = None,
    ) -> ProcessResult:
        try:
            result = self.runner.run(
                argv, timeout_seconds=timeout, stdout_file=stdout_file
            )
        except ProductionAdapterError:
            raise
        except Exception as exc:
            raise ProductionAdapterError("COMMAND_FAILED") from exc
        if (
            not isinstance(result, ProcessResult)
            or not isinstance(result.return_code, int)
            or isinstance(result.return_code, bool)
            or not isinstance(result.stdout, bytes)
            or len(result.stdout) > MAX_OUTPUT_BYTES
        ):
            raise ProductionAdapterError("INVALID_PROCESS_RESULT")
        return result

    def _result(
        self, action: str, status: str, evidence: Any = None
    ) -> ProbeResult:
        observed = _clock_now(self.clock)
        evidence_id = (
            None
            if status != "SUCCEEDED"
            else EVIDENCE_PREFIX[action] + _evidence_suffix(evidence)
        )
        return ProbeResult(status, observed, evidence_id)

    def _validate_context(self, context: ActionContext) -> None:
        if not isinstance(context, ActionContext):
            raise ProductionAdapterError("INVALID_ACTION_CONTEXT")
        try:
            valid = (
                context.bundle.resolve(strict=True) == self.bundle
                and OPERATION_RE.fullmatch(context.operation_id) is not None
                and context.target_release == self.plan["targetRelease"]
                and context.source_release == self.plan["sourceRelease"]
                and context.action
                in {
                    "PULL",
                    "BACKUP",
                    "MIGRATE",
                    "UPDATE",
                    "VERIFY",
                    "ROLLBACK",
                }
                and tuple(context.services)
                == self._expected_services(context.action)
                and tuple(context.databases)
                == self._expected_databases(context.action)
                and isinstance(context.database_restore_required, bool)
            )
        except (OSError, RuntimeError, TypeError, ValueError):
            valid = False
        if not valid:
            raise ProductionAdapterError("INVALID_ACTION_CONTEXT")

    def _expected_services(self, action: str) -> tuple[str, ...]:
        if action == "PULL":
            return tuple(self.plan["servicesToPull"])
        if action == "UPDATE":
            return tuple(self.plan["servicesToUpdate"])
        if action in {"VERIFY", "ROLLBACK"}:
            return COMPONENTS
        return ()

    def _expected_databases(self, action: str) -> tuple[str, ...]:
        changed = tuple(
            item["id"] for item in self.plan["databases"] if item["changed"]
        )
        if action in {"BACKUP", "MIGRATE", "ROLLBACK"}:
            return changed
        if action == "VERIFY":
            return DATABASES
        return ()

    def probe(self, context: ActionContext) -> ProbeResult:
        self._validate_context(context)
        handlers = {
            "PULL": self._probe_pull,
            "BACKUP": self._probe_backup,
            "MIGRATE": self._probe_migrate,
            "UPDATE": self._probe_update,
            "VERIFY": self._probe_verify,
            "ROLLBACK": self._probe_rollback,
        }
        try:
            return handlers[context.action](context)
        except ProductionAdapterError:
            return self._result(context.action, "FAILED")

    def execute(self, context: ActionContext) -> None:
        self._validate_context(context)
        handlers = {
            "PULL": self._execute_pull,
            "BACKUP": self._execute_backup,
            "MIGRATE": self._execute_migrate,
            "UPDATE": self._execute_update,
            "VERIFY": self._execute_verify,
            "ROLLBACK": self._execute_rollback,
        }
        handlers[context.action](context)
        return None

    def _image_probe(self, immutable_ref: str) -> str:
        if IMMUTABLE_RE.fullmatch(immutable_ref) is None and not immutable_ref.startswith(
            "postgres:"
        ):
            return "UNKNOWN"
        result = self._run(
            (
                os.fspath(self.docker),
                "image",
                "inspect",
                "--format",
                "{{json .RepoDigests}}",
                immutable_ref,
            ),
            30,
        )
        if result.return_code == 1:
            return "ABSENT"
        if result.return_code != 0:
            return "UNKNOWN"
        value = _decode_json(result.stdout.strip(), "INVALID_PROCESS_OUTPUT")
        if not isinstance(value, list) or not all(
            isinstance(item, str) for item in value
        ):
            return "UNKNOWN"
        parsed_digests: list[str] = []
        for item in value:
            if item.count("@") != 1:
                return "UNKNOWN"
            repository, digest = item.split("@", 1)
            if (
                not repository
                or any(
                    character.isspace()
                    or ord(character) < 32
                    or ord(character) == 127
                    for character in repository
                )
                or DIGEST_RE.fullmatch(digest) is None
            ):
                return "UNKNOWN"
            parsed_digests.append(digest)
        expected_digest = immutable_ref.rsplit("@", 1)[-1]
        if expected_digest not in parsed_digests:
            return "UNKNOWN"
        return "SUCCEEDED"

    def _postgres_image(self) -> str:
        result = self._run(
            self._compose_base() + ("config", "--format", "json"), 30
        )
        if result.return_code != 0:
            raise ProductionAdapterError("COMPOSE_CONFIG_FAILED")
        value = _decode_json(result.stdout, "INVALID_PROCESS_OUTPUT")
        try:
            image = value["services"]["postgresql"]["image"]
        except (KeyError, TypeError):
            raise ProductionAdapterError("INVALID_PROCESS_OUTPUT") from None
        if (
            not isinstance(image, str)
            or "@sha256:" not in image
            or DIGEST_RE.fullmatch(image.rsplit("@", 1)[-1]) is None
        ):
            raise ProductionAdapterError("INVALID_PROCESS_OUTPUT")
        return image

    def _probe_pull(self, context: ActionContext) -> ProbeResult:
        refs = [self.target_refs[service] for service in context.services]
        if context.source_release is None:
            refs.insert(0, self._postgres_image())
        states = [self._image_probe(reference) for reference in refs]
        if states and all(state == "SUCCEEDED" for state in states):
            return self._result("PULL", "SUCCEEDED", {"refs": refs})
        if all(state == "ABSENT" for state in states):
            return self._result("PULL", "ABSENT")
        return self._result("PULL", "UNKNOWN")

    def _execute_pull(self, context: ActionContext) -> None:
        services = list(context.services)
        if context.source_release is None:
            services.insert(0, "postgresql")
        result = self._run(
            self._compose_base() + ("pull", "--quiet", *services), 600
        )
        if result.return_code != 0:
            raise ProductionAdapterError("PULL_COMMAND_FAILED")

    def _backup_paths(
        self, operation_id: str
    ) -> tuple[Path, Path]:
        return (
            self.backups / operation_id,
            self.backups / f"{operation_id}.staging",
        )

    def _validate_backup(
        self, path: Path, context: ActionContext
    ) -> dict[str, Any]:
        try:
            directory = _safe_directory(path)
            expected = {
                *(f"{database}.dump" for database in context.databases),
                "backup-manifest.json",
            }
            if {item.name for item in directory.iterdir()} != expected:
                raise ProductionAdapterError("BACKUP_INVALID")
            manifest_data = _safe_regular(
                directory / "backup-manifest.json",
                mode=0o600,
                limit=MAX_JSON_BYTES,
            )
            manifest = _decode_json(manifest_data, "BACKUP_INVALID")
            if (
                not isinstance(manifest, dict)
                or manifest_data != _canonical(manifest)
            ):
                raise ProductionAdapterError("BACKUP_INVALID")
            schema = _decode_json(
                BACKUP_SCHEMA.read_bytes(), "BACKUP_INVALID"
            )
            jsonschema.Draft202012Validator(
                schema, format_checker=jsonschema.FormatChecker()
            ).validate(manifest)
            if (
                manifest["operationId"] != context.operation_id
                or manifest["sourceRelease"] != context.source_release
                or manifest["targetRelease"] != context.target_release
                or tuple(item["id"] for item in manifest["databases"])
                != tuple(context.databases)
                or manifest["complete"] is not True
            ):
                raise ProductionAdapterError("BACKUP_INVALID")
            for item in manifest["databases"]:
                size, digest = _dump_metadata(directory / item["file"])
                if (
                    item["file"] != f"{item['id']}.dump"
                    or item["size"] != size
                    or item["sha256"] != digest
                ):
                    raise ProductionAdapterError("BACKUP_INVALID")
            return manifest
        except (
            OSError,
            jsonschema.ValidationError,
            jsonschema.SchemaError,
            KeyError,
            TypeError,
        ) as exc:
            raise ProductionAdapterError("BACKUP_INVALID") from exc

    def _validate_backup_staging(
        self, path: Path, context: ActionContext
    ) -> None:
        directory = _safe_directory(path)
        allowed = {
            *(f"{database}.dump" for database in context.databases),
            *(f".{database}.dump.tmp" for database in context.databases),
            "backup-manifest.json",
        }
        try:
            entries = list(directory.iterdir())
        except OSError as exc:
            raise ProductionAdapterError("BACKUP_INVALID") from exc
        if any(item.name not in allowed for item in entries):
            raise ProductionAdapterError("BACKUP_INVALID")
        for item in entries:
            try:
                details = item.lstat()
            except OSError as exc:
                raise ProductionAdapterError("BACKUP_INVALID") from exc
            if (
                stat.S_ISLNK(details.st_mode)
                or not stat.S_ISREG(details.st_mode)
                or stat.S_IMODE(details.st_mode) != 0o600
                or details.st_uid != os.geteuid()
            ):
                raise ProductionAdapterError("BACKUP_INVALID")
        manifest_path = directory / "backup-manifest.json"
        if manifest_path.exists():
            self._validate_backup(directory, context)

    def _probe_backup(self, context: ActionContext) -> ProbeResult:
        final, staging = self._backup_paths(context.operation_id)
        if final.exists() or final.is_symlink():
            if staging.exists() or staging.is_symlink():
                return self._result("BACKUP", "FAILED")
            try:
                manifest = self._validate_backup(final, context)
            except ProductionAdapterError:
                return self._result("BACKUP", "FAILED")
            return self._result(
                "BACKUP",
                "SUCCEEDED",
                {
                    "operationId": manifest["operationId"],
                    "databases": manifest["databases"],
                },
            )
        if staging.exists() or staging.is_symlink():
            try:
                self._validate_backup_staging(staging, context)
            except ProductionAdapterError:
                return self._result("BACKUP", "FAILED")
        return self._result("BACKUP", "ABSENT")

    def _database_dump_command(self, database: str) -> str:
        if database == "erp":
            prefix = "ERP"
        elif database == "website":
            prefix = "WEBSITE"
        else:
            raise ProductionAdapterError("INVALID_ACTION_CONTEXT")
        return (
            f'PGPASSWORD="${prefix}_DB_PASSWORD" exec pg_dump '
            "--format=custom --no-owner --no-acl "
            f'--username="${prefix}_DB_USER" --dbname="${prefix}_DB_NAME"'
        )

    def _execute_backup(self, context: ActionContext) -> None:
        final, staging = self._backup_paths(context.operation_id)
        if final.exists() or final.is_symlink():
            raise ProductionAdapterError("BACKUP_CONFLICT")
        postgres = self._run(
            self._compose_base()
            + (
                "up",
                "-d",
                "--no-build",
                "--wait",
                "--wait-timeout",
                "180",
                "postgresql",
            ),
            180,
        )
        if postgres.return_code != 0:
            raise ProductionAdapterError("BACKUP_POSTGRES_FAILED")
        if staging.exists() or staging.is_symlink():
            self._validate_backup_staging(staging, context)
        else:
            try:
                staging.mkdir(mode=0o700)
            except OSError as exc:
                raise ProductionAdapterError("BACKUP_IO_FAILED") from exc
            _safe_directory(staging)
        existing_manifest = staging / "backup-manifest.json"
        if existing_manifest.exists() or existing_manifest.is_symlink():
            self._validate_backup(staging, context)
            _fsync_directory(staging)
            if final.exists() or final.is_symlink():
                raise ProductionAdapterError("BACKUP_CONFLICT")
            try:
                os.replace(staging, final)
                _fsync_directory(self.backups)
            except OSError as exc:
                raise ProductionAdapterError("BACKUP_IO_FAILED") from exc
            return
        database_entries: list[dict[str, Any]] = []
        for database in context.databases:
            dump_path = staging / f"{database}.dump"
            if dump_path.exists() or dump_path.is_symlink():
                dump_size, dump_digest = _dump_metadata(dump_path)
            else:
                temp_dump = staging / f".{database}.dump.tmp"
                try:
                    if temp_dump.is_symlink():
                        raise ProductionAdapterError("BACKUP_INVALID")
                    if temp_dump.exists():
                        details = temp_dump.stat()
                        if (
                            not stat.S_ISREG(details.st_mode)
                            or details.st_uid != os.geteuid()
                        ):
                            raise ProductionAdapterError("BACKUP_INVALID")
                        temp_dump.unlink()
                    result = self._run(
                        self._compose_base()
                        + (
                            "exec",
                            "-T",
                            "postgresql",
                            "sh",
                            "-eu",
                            "-c",
                            self._database_dump_command(database),
                        ),
                        600,
                        stdout_file=temp_dump,
                    )
                    if result.return_code != 0:
                        raise ProductionAdapterError("BACKUP_DUMP_FAILED")
                    dump_size, dump_digest = _dump_metadata(
                        temp_dump, code="BACKUP_DUMP_FAILED"
                    )
                    try:
                        os.replace(temp_dump, dump_path)
                        _fsync_directory(staging)
                    except OSError as exc:
                        raise ProductionAdapterError(
                            "BACKUP_IO_FAILED"
                        ) from exc
                finally:
                    try:
                        if temp_dump.exists() or temp_dump.is_symlink():
                            temp_dump.unlink()
                    except OSError:
                        pass
            database_entries.append(
                {
                    "id": database,
                    "file": f"{database}.dump",
                    "sha256": dump_digest,
                    "size": dump_size,
                }
            )
        manifest = {
            "schemaVersion": 1,
            "kind": "deployment-backup",
            "operationId": context.operation_id,
            "sourceRelease": context.source_release,
            "targetRelease": context.target_release,
            "createdAt": _clock_now(self.clock),
            "databases": database_entries,
            "complete": True,
        }
        manifest_path = staging / "backup-manifest.json"
        if manifest_path.exists() or manifest_path.is_symlink():
            existing = self._validate_backup(staging, context)
            if existing != manifest:
                raise ProductionAdapterError("BACKUP_CONFLICT")
        else:
            _atomic_json(manifest_path, manifest)
            self._validate_backup(staging, context)
        _fsync_directory(staging)
        if final.exists() or final.is_symlink():
            raise ProductionAdapterError("BACKUP_CONFLICT")
        try:
            os.replace(staging, final)
            _fsync_directory(self.backups)
        except OSError as exc:
            raise ProductionAdapterError("BACKUP_IO_FAILED") from exc

    def _migration_command(
        self, bundle: Path, database: str, mode: str
    ) -> tuple[str, ...]:
        if database not in DATABASES or mode not in {"probe", "migrate"}:
            raise ProductionAdapterError("INVALID_ACTION_CONTEXT")
        return self._compose_base(bundle) + (
            "run",
            "--rm",
            "--no-deps",
            "--entrypoint",
            "/app/bin/migrate",
            DATABASE_SERVICE[database],
            mode,
        )

    def _migration_result(
        self, bundle: Path, database: str, mode: str
    ) -> str:
        result = self._run(
            self._migration_command(bundle, database, mode), 600
        )
        lines = result.stdout.rstrip(b"\r\n").splitlines()
        try:
            marker = lines[-1].decode("ascii")
        except (IndexError, UnicodeDecodeError):
            return "FAILED"
        if marker not in MIGRATION_MARKERS:
            return "FAILED"
        if mode == "probe":
            if result.return_code == 0 and marker == "MIGRATIONS_APPLIED":
                return "APPLIED"
            if result.return_code == 10 and marker == "MIGRATIONS_PENDING":
                return "PENDING"
            return "FAILED"
        if result.return_code == 0 and marker == "MIGRATIONS_APPLIED":
            return "APPLIED"
        return "FAILED"

    def _probe_migrate(self, context: ActionContext) -> ProbeResult:
        states = [
            self._migration_result(self.bundle, database, "probe")
            for database in context.databases
        ]
        if states and all(state == "APPLIED" for state in states):
            return self._result(
                "MIGRATE",
                "SUCCEEDED",
                {"databases": list(context.databases), "states": states},
            )
        if states and all(state in {"APPLIED", "PENDING"} for state in states):
            return self._result("MIGRATE", "ABSENT")
        return self._result("MIGRATE", "FAILED")

    def _execute_migrate(self, context: ActionContext) -> None:
        for database in context.databases:
            if (
                self._migration_result(self.bundle, database, "migrate")
                != "APPLIED"
            ):
                raise ProductionAdapterError("MIGRATION_COMMAND_FAILED")

    def _container(self, bundle: Path, service: str) -> dict[str, Any] | None:
        if service not in ALL_SERVICES:
            raise ProductionAdapterError("INVALID_ACTION_CONTEXT")
        selected = self._compose_base(bundle)
        ids = self._run(selected + ("ps", "--all", "-q", service), 30)
        if ids.return_code != 0:
            raise ProductionAdapterError("CONTAINER_PROBE_FAILED")
        values = [
            item.decode("ascii")
            for item in ids.stdout.splitlines()
            if item
        ]
        if not values:
            return None
        if len(values) != 1 or SAFE_TOKEN_RE.fullmatch(values[0]) is None:
            raise ProductionAdapterError("CONTAINER_PROBE_FAILED")
        inspected = self._run(
            (
                os.fspath(self.docker),
                "container",
                "inspect",
                "--format",
                "{{json .}}",
                values[0],
            ),
            30,
        )
        if inspected.return_code != 0:
            raise ProductionAdapterError("CONTAINER_PROBE_FAILED")
        value = _decode_json(inspected.stdout, "INVALID_PROCESS_OUTPUT")
        if not isinstance(value, dict):
            raise ProductionAdapterError("INVALID_PROCESS_OUTPUT")
        try:
            image = value["Config"]["Image"]
            running = value["State"]["Status"] == "running"
            healthy = value["State"]["Health"]["Status"] == "healthy"
        except (KeyError, TypeError):
            raise ProductionAdapterError("INVALID_PROCESS_OUTPUT") from None
        if not isinstance(image, str):
            raise ProductionAdapterError("INVALID_PROCESS_OUTPUT")
        return {"image": image, "running": running, "healthy": healthy}

    def _source_bundle(self) -> Path:
        source = self.plan["sourceRelease"]
        if not isinstance(source, str) or SEMVER_RE.fullmatch(source) is None:
            raise ProductionAdapterError("SOURCE_BUNDLE_INVALID")
        if self._source_bundle_cache is None:
            candidate = self.releases / source
            try:
                candidate = _safe_directory(candidate)
                source_plan = deployment_plan.validate_bundle(candidate)
            except Exception as exc:
                raise ProductionAdapterError("SOURCE_BUNDLE_INVALID") from exc
            if source_plan["targetRelease"] != source:
                raise ProductionAdapterError("SOURCE_BUNDLE_INVALID")
            manifest = self._bundle_json(candidate, "manifest.json")
            component_refs = {
                item["id"]: item["immutableRef"] for item in manifest["components"]
            }
            database_shas = {
                item["id"]: item["migrationSetSha256"]
                for item in manifest["databases"]
            }
            if tuple(component_refs) != COMPONENTS or tuple(
                database_shas
            ) != DATABASES:
                raise ProductionAdapterError("SOURCE_BUNDLE_INVALID")
            self._source_bundle_cache = candidate
            self._source_refs_cache = component_refs
            self._source_migration_cache = database_shas
        # Re-validated on every call, including cache hits: the historical
        # bundle must stay cryptographically linked to this plan's declared
        # current-state projection, not merely self-consistent in isolation.
        self._validate_source_linkage()
        return self._source_bundle_cache

    def _validate_source_linkage(self) -> None:
        assert self._source_refs_cache is not None
        assert self._source_migration_cache is not None
        plan_components = {
            item["component"]: item for item in self.plan["components"]
        }
        for component_id in COMPONENTS:
            expected_digest = plan_components.get(component_id, {}).get(
                "currentDigest"
            )
            if not isinstance(expected_digest, str) or DIGEST_RE.fullmatch(
                expected_digest
            ) is None:
                raise ProductionAdapterError("SOURCE_BUNDLE_INVALID")
            source_ref = self._source_refs_cache.get(component_id)
            if (
                not isinstance(source_ref, str)
                or "@" not in source_ref
                or source_ref.rsplit("@", 1)[-1] != expected_digest
            ):
                raise ProductionAdapterError("SOURCE_BUNDLE_INVALID")
        plan_databases = {item["id"]: item for item in self.plan["databases"]}
        for database_id in DATABASES:
            expected_sha = plan_databases.get(database_id, {}).get(
                "currentMigrationSetSha256"
            )
            if not isinstance(expected_sha, str) or DIGEST_RE.fullmatch(
                expected_sha
            ) is None:
                raise ProductionAdapterError("SOURCE_BUNDLE_INVALID")
            if self._source_migration_cache.get(database_id) != expected_sha:
                raise ProductionAdapterError("SOURCE_BUNDLE_INVALID")

    def _source_refs(self) -> dict[str, str]:
        self._source_bundle()
        assert self._source_refs_cache is not None
        return self._source_refs_cache

    def _probe_update(self, context: ActionContext) -> ProbeResult:
        source_refs = (
            {} if context.source_release is None else self._source_refs()
        )
        states: list[str] = []
        observations: list[dict[str, str]] = []
        for service in context.services:
            container = self._container(self.bundle, service)
            if container is None:
                states.append("SOURCE")
                observations.append({"service": service, "state": "absent"})
                continue
            if not container["running"] or not container["healthy"]:
                return self._result("UPDATE", "UNKNOWN")
            if container["image"] == self.target_refs[service]:
                states.append("TARGET")
                observations.append(
                    {"service": service, "state": "target"}
                )
            elif (
                context.source_release is not None
                and container["image"] == source_refs[service]
            ):
                states.append("SOURCE")
                observations.append(
                    {"service": service, "state": "source"}
                )
            else:
                return self._result("UPDATE", "UNKNOWN")
        if states and all(state == "TARGET" for state in states):
            return self._result("UPDATE", "SUCCEEDED", observations)
        if all(state == "SOURCE" for state in states):
            return self._result("UPDATE", "ABSENT")
        return self._result("UPDATE", "UNKNOWN")

    def _up_services(
        self,
        bundle: Path,
        services: tuple[str, ...],
        timeout: int,
        *,
        remove_orphans: bool = False,
    ) -> None:
        flags = (
            "up",
            "-d",
            "--no-build",
            "--no-deps",
            *(("--remove-orphans",) if remove_orphans else ()),
            "--wait",
            "--wait-timeout",
            "180",
        )
        result = self._run(
            self._compose_base(bundle)
            + flags
            + services,
            timeout,
        )
        if result.return_code != 0:
            raise ProductionAdapterError("UPDATE_COMMAND_FAILED")

    def _execute_update(self, context: ActionContext) -> None:
        self._up_services(
            self.bundle,
            context.services,
            300,
            remove_orphans=True,
        )

    def _project_service_names(self, bundle: Path) -> tuple[str, ...]:
        result = self._run(
            self._compose_base(bundle) + ("ps", "--all", "--format", "json"),
            30,
        )
        if result.return_code != 0:
            raise ProductionAdapterError("CONTAINER_PROBE_FAILED")
        rows = _parse_json_lines(result.stdout)
        try:
            names = tuple(row["Service"] for row in rows)
        except (KeyError, TypeError):
            raise ProductionAdapterError("INVALID_PROCESS_OUTPUT") from None
        if any(not isinstance(name, str) for name in names) or len(set(names)) != len(
            names
        ):
            raise ProductionAdapterError("INVALID_PROCESS_OUTPUT")
        return names

    def _smokes(self) -> bool:
        for host, path in SMOKES:
            if host not in HOSTS or path not in {"/healthz", "/"}:
                raise ProductionAdapterError("INVALID_SMOKE_TARGET")
            result = self._run(
                (
                    os.fspath(self.curl),
                    "--fail",
                    "--silent",
                    "--show-error",
                    "--max-time",
                    "15",
                    "--header",
                    f"Host: {host}",
                    f"http://127.0.0.1:{self.gateway_loopback_port}{path}",
                ),
                15,
            )
            if result.return_code != 0:
                return False
        return True

    def _verify_stack(
        self,
        bundle: Path,
        refs: dict[str, str],
    ) -> tuple[bool, list[dict[str, str]]]:
        try:
            names = self._project_service_names(bundle)
            if set(names) != set(ALL_SERVICES) or len(names) != len(ALL_SERVICES):
                return False, []
            observations: list[dict[str, str]] = []
            for service in ALL_SERVICES:
                container = self._container(bundle, service)
                if (
                    container is None
                    or not container["running"]
                    or not container["healthy"]
                ):
                    return False, []
                if service != "postgresql" and container["image"] != refs[service]:
                    return False, []
                observations.append({"service": service, "state": "healthy"})
            if not self._smokes():
                return False, []
            return True, observations
        except ProductionAdapterError:
            return False, []

    def _probe_verify(self, context: ActionContext) -> ProbeResult:
        complete, observations = self._verify_stack(
            self.bundle, self.target_refs
        )
        if complete:
            return self._result(
                "VERIFY",
                "SUCCEEDED",
                {
                    "release": context.target_release,
                    "services": observations,
                    "smokes": [
                        {"host": host, "path": path} for host, path in SMOKES
                    ],
                },
            )
        return self._result("VERIFY", "ABSENT")

    def _execute_verify(self, context: ActionContext) -> None:
        self._up_services(self.bundle, COMPONENTS, 300)

    def _probe_rollback(self, context: ActionContext) -> ProbeResult:
        if context.source_release is None:
            states = [
                self._container(self.bundle, service) is None
                for service in COMPONENTS
            ]
            if all(states):
                return self._result(
                    "ROLLBACK",
                    "SUCCEEDED",
                    {
                        "release": context.target_release,
                        "services": list(COMPONENTS),
                        "databaseRestoreRequired": context.database_restore_required,
                    },
                )
            if not any(states):
                return self._result("ROLLBACK", "ABSENT")
            return self._result("ROLLBACK", "UNKNOWN")
        source_bundle = self._source_bundle()
        complete, observations = self._verify_stack(
            source_bundle, self._source_refs()
        )
        if complete:
            return self._result(
                "ROLLBACK",
                "SUCCEEDED",
                {
                    "release": context.source_release,
                    "services": observations,
                    "databaseRestoreRequired": context.database_restore_required,
                },
            )
        return self._result("ROLLBACK", "ABSENT")

    def _execute_rollback(self, context: ActionContext) -> None:
        if context.source_release is None:
            result = self._run(
                self._compose_base()
                + ("rm", "-f", "-s", *COMPONENTS),
                300,
            )
            if result.return_code != 0:
                raise ProductionAdapterError("ROLLBACK_COMMAND_FAILED")
            return
        source_bundle = self._source_bundle()
        try:
            self._up_services(source_bundle, COMPONENTS, 300)
        except ProductionAdapterError as exc:
            raise ProductionAdapterError("ROLLBACK_COMMAND_FAILED") from exc
