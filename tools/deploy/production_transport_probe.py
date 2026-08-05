#!/usr/bin/env python3
"""Fail-closed, non-mutating proof of the dedicated production SSH transport."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import stat
import subprocess
import sys
from pathlib import Path
from typing import Any

REPOSITORY = "greggorio/abaronesa-emporio"
WORKFLOW = ".github/workflows/verify-production-transport.yml"
REF = "refs/heads/main"
REMOTE_USER = "deploy-emporio"
REMOTE_HELPER = "/opt/sistemas/emporio/shared/control/ops/deploy/deployment-remote.py"
REMOTE_COMMAND = (REMOTE_HELPER, "capabilities")
PROTOCOL = "emporio-deployment-transport"
DEPLOY_ROOT = "/opt/sistemas/emporio"
TRUST_FILE = "production-transport-trust.json"
PROBE_FILE = "production-transport-probe.json"
OUTCOME_FILE = "production-transport-probe-outcome.json"
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
HOST_RE = re.compile(r"^[A-Za-z0-9.-]{1,253}$")
POSITIVE_RE = re.compile(r"^[1-9][0-9]*$")
MAX_STDOUT = 4096


class ProbeError(RuntimeError):
    pass


def canonical(value: dict[str, Any]) -> bytes:
    return (
        json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
        + "\n"
    ).encode("utf-8")


def _positive(value: str | None, code: str) -> int:
    if value is None or POSITIVE_RE.fullmatch(value) is None:
        raise ProbeError(code)
    return int(value)


def _required(name: str) -> str:
    value = os.environ.get(name)
    if not isinstance(value, str) or not value:
        raise ProbeError(f"MISSING_{name}")
    return value


def _binding() -> dict[str, Any]:
    sha = _required("TRUSTED_SHA")
    if SHA_RE.fullmatch(sha) is None:
        raise ProbeError("INVALID_SHA")
    return {
        "repository": _required("TRUSTED_REPOSITORY"),
        "workflow": WORKFLOW,
        "workflowRef": _required("TRUSTED_WORKFLOW_REF"),
        "runId": _positive(os.environ.get("TRUSTED_RUN_ID"), "INVALID_RUN_ID"),
        "runAttempt": _positive(
            os.environ.get("TRUSTED_RUN_ATTEMPT"), "INVALID_RUN_ATTEMPT"
        ),
        "sha": sha,
        "actorId": _positive(os.environ.get("TRUSTED_ACTOR_ID"), "INVALID_ACTOR_ID"),
    }


def _sidecar_name(name: str) -> str:
    return f"{name}.sha256"


def _create_directory(path: Path) -> None:
    if path.exists() or path.is_symlink():
        raise ProbeError("OUTPUT_EXISTS")
    path.mkdir(parents=True, mode=0o700)
    if stat.S_IMODE(path.stat().st_mode) != 0o700:
        raise ProbeError("OUTPUT_MODE_INVALID")


def _write_exclusive(path: Path, payload: bytes, mode: int = 0o600) -> None:
    descriptor = os.open(
        path,
        os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0),
        mode,
    )
    with os.fdopen(descriptor, "wb") as stream:
        stream.write(payload)
        stream.flush()
        os.fsync(stream.fileno())
    if stat.S_IMODE(path.stat().st_mode) != mode or not path.is_file():
        raise ProbeError("OUTPUT_MODE_INVALID")


def _write_bundle(directory: Path, name: str, value: dict[str, Any]) -> None:
    _create_directory(directory)
    payload = canonical(value)
    _write_exclusive(directory / name, payload)
    _write_exclusive(
        directory / _sidecar_name(name),
        ("sha256:" + hashlib.sha256(payload).hexdigest() + "\n").encode("ascii"),
    )


def _load_bundle(directory: Path, name: str) -> dict[str, Any]:
    if directory.is_symlink() or not directory.is_dir():
        raise ProbeError("ARTIFACT_INVALID")
    expected = {name, _sidecar_name(name)}
    files = {item.name for item in directory.iterdir() if item.is_file()}
    if files != expected or any(item.is_symlink() for item in directory.iterdir()):
        raise ProbeError("ARTIFACT_INVALID")
    raw = (directory / name).read_bytes()
    sidecar = (directory / _sidecar_name(name)).read_text(encoding="ascii")
    if sidecar != "sha256:" + hashlib.sha256(raw).hexdigest() + "\n":
        raise ProbeError("ARTIFACT_DIGEST_INVALID")
    try:
        value = json.loads(raw)
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise ProbeError("ARTIFACT_INVALID") from exc
    if not isinstance(value, dict) or canonical(value) != raw:
        raise ProbeError("ARTIFACT_INVALID")
    return value


def _validate_trust(value: dict[str, Any]) -> None:
    if set(value) != {
        "schemaVersion",
        "kind",
        "repository",
        "workflow",
        "workflowRef",
        "runId",
        "runAttempt",
        "sha",
        "actorId",
        "status",
    }:
        raise ProbeError("TRUST_INVALID")
    if (
        value.get("schemaVersion") != 1
        or value.get("kind") != "production-transport-trust"
        or value.get("repository") != REPOSITORY
        or value.get("workflow") != WORKFLOW
        or value.get("workflowRef") != f"{REPOSITORY}/{WORKFLOW}@{REF}"
        or value.get("status") != "TRUSTED"
        or not isinstance(value.get("runId"), int)
        or not isinstance(value.get("runAttempt"), int)
        or not isinstance(value.get("actorId"), int)
        or not isinstance(value.get("sha"), str)
        or SHA_RE.fullmatch(value["sha"]) is None
    ):
        raise ProbeError("TRUST_INVALID")


def trust(output: Path) -> None:
    binding = _binding()
    actor = _required("TRUSTED_ACTOR")
    triggering_actor = _required("TRUSTED_TRIGGERING_ACTOR")
    sender_id = _positive(os.environ.get("TRUSTED_SENDER_ID"), "INVALID_SENDER_ID")
    allowlist_raw = _required("DEPLOYER_ACTOR_IDS")
    allowlist = {
        int(item)
        for item in allowlist_raw.split(",")
        if POSITIVE_RE.fullmatch(item) is not None
    }
    if (
        _required("TRUSTED_EVENT") != "workflow_dispatch"
        or _required("TRUSTED_REF") != REF
        or binding["repository"] != REPOSITORY
        or binding["workflowRef"] != f"{REPOSITORY}/{WORKFLOW}@{REF}"
        or actor != triggering_actor
        or sender_id != binding["actorId"]
        or binding["actorId"] not in allowlist
        or str(binding["actorId"]) not in allowlist_raw.split(",")
    ):
        raise ProbeError("UNTRUSTED_DISPATCH")
    head = subprocess.run(
        ("/usr/bin/git", "rev-parse", "HEAD"),
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        env={"PATH": "/usr/bin:/bin", "LANG": "C.UTF-8", "LC_ALL": "C.UTF-8"},
    )
    if head.returncode != 0 or head.stdout.decode("ascii").strip() != binding["sha"]:
        raise ProbeError("CHECKOUT_SHA_MISMATCH")
    _write_bundle(
        output,
        TRUST_FILE,
        {
            "schemaVersion": 1,
            "kind": "production-transport-trust",
            **binding,
            "status": "TRUSTED",
        },
    )


def _temporary_directory(binding: dict[str, Any]) -> Path:
    root = Path(_required("RUNNER_TEMP")).resolve()
    if not root.is_dir() or root.is_symlink():
        raise ProbeError("RUNNER_TEMP_INVALID")
    return root / f"emporio-production-transport-{binding['runId']}-{binding['runAttempt']}"


def _remove_regular(path: Path, *, secret: bool = False) -> None:
    if not path.exists():
        return
    if path.is_symlink() or not path.is_file():
        raise ProbeError("CLEANUP_TARGET_INVALID")
    if secret:
        shred = shutil.which("shred")
        if shred is None:
            raise ProbeError("SHRED_UNAVAILABLE")
        completed = subprocess.run(
            (shred, "-u", os.fspath(path)),
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            env={"PATH": "/usr/bin:/bin", "LANG": "C.UTF-8", "LC_ALL": "C.UTF-8"},
        )
        if completed.returncode != 0 or path.exists():
            raise ProbeError("PRIVATE_KEY_CLEANUP_FAILED")
    else:
        path.unlink()


def _cleanup_temporary(directory: Path) -> None:
    if not directory.exists():
        return
    if directory.is_symlink() or not directory.is_dir():
        raise ProbeError("CLEANUP_TARGET_INVALID")
    allowed = {"id_ed25519", "known_hosts", "ssh_config"}
    observed = {item.name for item in directory.iterdir()}
    if not observed.issubset(allowed):
        raise ProbeError("CLEANUP_TARGET_INVALID")
    _remove_regular(directory / "id_ed25519", secret=True)
    _remove_regular(directory / "known_hosts")
    _remove_regular(directory / "ssh_config")
    directory.rmdir()


def probe(trust_directory: Path, output: Path) -> None:
    trust_value = _load_bundle(trust_directory, TRUST_FILE)
    _validate_trust(trust_value)
    binding = _binding()
    if any(trust_value.get(key) != value for key, value in binding.items()):
        raise ProbeError("TRUST_BINDING_INVALID")
    host = _required("PRODUCTION_SSH_HOST")
    port = _positive(os.environ.get("PRODUCTION_SSH_PORT"), "SSH_PORT_INVALID")
    private_key = _required("PRODUCTION_SSH_PRIVATE_KEY")
    known_hosts = _required("PRODUCTION_SSH_KNOWN_HOSTS")
    if (
        HOST_RE.fullmatch(host) is None
        or port > 65535
        or not private_key.startswith("-----BEGIN " + "OPENSSH PRIVATE KEY-----")
        or "\n" not in known_hosts
    ):
        raise ProbeError("SSH_CONFIGURATION_INVALID")

    temporary = _temporary_directory(binding)
    _create_directory(temporary)
    try:
        key_path = temporary / "id_ed25519"
        known_hosts_path = temporary / "known_hosts"
        config_path = temporary / "ssh_config"
        _write_exclusive(key_path, private_key.encode("utf-8"))
        _write_exclusive(known_hosts_path, known_hosts.encode("utf-8"))
        config = (
            "Host production\n"
            f"  HostName {host}\n"
            f"  Port {port}\n"
            f"  User {REMOTE_USER}\n"
            f"  IdentityFile {key_path}\n"
            "  IdentitiesOnly yes\n"
            "  IdentityAgent none\n"
            "  BatchMode yes\n"
            "  StrictHostKeyChecking yes\n"
            f"  UserKnownHostsFile {known_hosts_path}\n"
            "  PasswordAuthentication no\n"
            "  KbdInteractiveAuthentication no\n"
            "  ConnectTimeout 15\n"
            "  LogLevel ERROR\n"
        ).encode("utf-8")
        _write_exclusive(config_path, config)
        completed = subprocess.run(
            ("/usr/bin/ssh", "-F", os.fspath(config_path), "production", *REMOTE_COMMAND),
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            timeout=30,
            env={"PATH": "/usr/bin:/bin", "LANG": "C.UTF-8", "LC_ALL": "C.UTF-8"},
        )
        if completed.returncode != 0 or len(completed.stdout) > MAX_STDOUT:
            raise ProbeError("SSH_PROBE_FAILED")
        try:
            capabilities = json.loads(completed.stdout)
        except (UnicodeError, json.JSONDecodeError) as exc:
            raise ProbeError("CAPABILITIES_INVALID") from exc
        expected = {
            "controlSha": binding["sha"],
            "deployRoot": DEPLOY_ROOT,
            "protocol": PROTOCOL,
            "schemaVersion": 1,
            "user": REMOTE_USER,
        }
        if capabilities != expected or canonical(capabilities) != completed.stdout:
            raise ProbeError("CAPABILITIES_INVALID")
    finally:
        _cleanup_temporary(temporary)

    _write_bundle(
        output,
        PROBE_FILE,
        {
            "schemaVersion": 1,
            "kind": "production-transport-probe",
            **binding,
            "controlSha": binding["sha"],
            "protocol": PROTOCOL,
            "deployRoot": DEPLOY_ROOT,
            "user": REMOTE_USER,
            "status": "SUCCESS",
        },
    )


def cleanup() -> None:
    binding = _binding()
    _cleanup_temporary(_temporary_directory(binding))


def _validate_probe(value: dict[str, Any]) -> None:
    expected_keys = {
        "schemaVersion",
        "kind",
        "repository",
        "workflow",
        "workflowRef",
        "runId",
        "runAttempt",
        "sha",
        "actorId",
        "controlSha",
        "protocol",
        "deployRoot",
        "user",
        "status",
    }
    if (
        set(value) != expected_keys
        or value.get("schemaVersion") != 1
        or value.get("kind") != "production-transport-probe"
        or value.get("repository") != REPOSITORY
        or value.get("workflow") != WORKFLOW
        or value.get("workflowRef") != f"{REPOSITORY}/{WORKFLOW}@{REF}"
        or value.get("controlSha") != value.get("sha")
        or value.get("protocol") != PROTOCOL
        or value.get("deployRoot") != DEPLOY_ROOT
        or value.get("user") != REMOTE_USER
        or value.get("status") != "SUCCESS"
    ):
        raise ProbeError("PROBE_INVALID")


def outcome(trust_directory: Path, probe_directory: Path, output: Path) -> None:
    binding = _binding()
    trust_result = _required("TRUST_RESULT")
    probe_result = _required("PROBE_RESULT")
    success = trust_result == "success" and probe_result == "success"
    if success:
        trust_value = _load_bundle(trust_directory, TRUST_FILE)
        probe_value = _load_bundle(probe_directory, PROBE_FILE)
        _validate_trust(trust_value)
        _validate_probe(probe_value)
        if any(
            trust_value.get(key) != value or probe_value.get(key) != value
            for key, value in binding.items()
        ):
            raise ProbeError("OUTCOME_BINDING_INVALID")
    _write_bundle(
        output,
        OUTCOME_FILE,
        {
            "schemaVersion": 1,
            "kind": "production-transport-probe-outcome",
            **binding,
            "status": "SUCCESS" if success else "FAILED",
            "trustResult": trust_result,
            "probeResult": probe_result,
        },
    )


def validate(probe_directory: Path, outcome_directory: Path) -> None:
    probe_value = _load_bundle(probe_directory, PROBE_FILE)
    outcome_value = _load_bundle(outcome_directory, OUTCOME_FILE)
    _validate_probe(probe_value)
    expected = {
        "schemaVersion",
        "kind",
        "repository",
        "workflow",
        "workflowRef",
        "runId",
        "runAttempt",
        "sha",
        "actorId",
        "status",
        "trustResult",
        "probeResult",
    }
    if (
        set(outcome_value) != expected
        or outcome_value.get("schemaVersion") != 1
        or outcome_value.get("kind") != "production-transport-probe-outcome"
        or outcome_value.get("status") != "SUCCESS"
        or outcome_value.get("trustResult") != "success"
        or outcome_value.get("probeResult") != "success"
        or any(
            outcome_value.get(key) != probe_value.get(key)
            for key in (
                "repository",
                "workflow",
                "workflowRef",
                "runId",
                "runAttempt",
                "sha",
                "actorId",
            )
        )
    ):
        raise ProbeError("OUTCOME_INVALID")


def parser() -> argparse.ArgumentParser:
    value = argparse.ArgumentParser()
    commands = value.add_subparsers(dest="command", required=True)
    trust_parser = commands.add_parser("trust")
    trust_parser.add_argument("--output", required=True, type=Path)
    probe_parser = commands.add_parser("probe")
    probe_parser.add_argument("--trust", required=True, type=Path)
    probe_parser.add_argument("--output", required=True, type=Path)
    commands.add_parser("cleanup")
    outcome_parser = commands.add_parser("outcome")
    outcome_parser.add_argument("--trust", required=True, type=Path)
    outcome_parser.add_argument("--probe", required=True, type=Path)
    outcome_parser.add_argument("--output", required=True, type=Path)
    validate_parser = commands.add_parser("validate")
    validate_parser.add_argument("--probe", required=True, type=Path)
    validate_parser.add_argument("--outcome", required=True, type=Path)
    return value


def main() -> int:
    args = parser().parse_args()
    try:
        if args.command == "trust":
            trust(args.output)
        elif args.command == "probe":
            probe(args.trust, args.output)
        elif args.command == "cleanup":
            cleanup()
        elif args.command == "outcome":
            outcome(args.trust, args.probe, args.output)
        else:
            validate(args.probe, args.outcome)
    except (OSError, ProbeError, subprocess.SubprocessError) as exc:
        code = str(exc) if isinstance(exc, ProbeError) else "PROBE_FAILED"
        print(f"production-transport-probe:{code}", file=sys.stderr)
        return 3
    print(f"production-transport-probe:{args.command}:ok")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
