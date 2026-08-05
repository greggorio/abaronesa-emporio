#!/usr/bin/env python3
"""Closed, shared materialisation of the production OpenSSH identity."""

from __future__ import annotations

import base64
import binascii
import ipaddress
import os
import re
import shutil
import stat
import subprocess
from dataclasses import dataclass
from pathlib import Path

REMOTE_USER = "deploy-emporio"
HOST_RE = re.compile(
    r"(?=.{1,253}\Z)(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)"
    r"(?:\.(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?))*"
)
FINGERPRINT_RE = re.compile(r"SHA256:[A-Za-z0-9+/]{43}")
KEY_TYPE_RE = re.compile(r"(?:ssh-ed25519|ecdsa-sha2-nistp(?:256|384|521)|ssh-rsa)")
PRIVATE_BEGIN = b"-----BEGIN " + b"OPENSSH PRIVATE KEY-----"
PRIVATE_END = b"-----END " + b"OPENSSH PRIVATE KEY-----"
MAX_PRIVATE = 64 * 1024
MAX_KNOWN_HOSTS = 1024 * 1024
MAX_DIAGNOSTIC = 16 * 1024

ERRORS = frozenset(
    {
        "SSH_KEY_FORMAT_INVALID",
        "SSH_KEY_FINGERPRINT_MISMATCH",
        "SSH_KNOWN_HOSTS_INVALID",
        "SSH_CONNECTION_FAILED",
        "SSH_AUTHENTICATION_FAILED",
        "REMOTE_CAPABILITY_MISMATCH",
        "SSH_CONFIGURATION_INVALID",
        "SSH_UNAVAILABLE",
    }
)


class SshMaterialError(ValueError):
    def __init__(self, code: str):
        super().__init__(code if code in ERRORS else "SSH_CONFIGURATION_INVALID")
        self.code = str(self)


@dataclass(frozen=True)
class SshConfiguration:
    directory: Path
    config: Path
    destination: str
    ssh: Path
    scp: Path
    fingerprint: str


def _validate_binary(path: Path, expected: str) -> Path:
    try:
        resolved = path.resolve(strict=True)
        source = path.lstat()
        target = resolved.stat()
    except OSError as exc:
        raise SshMaterialError("SSH_UNAVAILABLE") from exc
    if (
        path.is_symlink()
        or not stat.S_ISREG(source.st_mode)
        or not stat.S_ISREG(target.st_mode)
        or resolved.name != expected
        or target.st_uid != 0
        or target.st_mode & (stat.S_IWGRP | stat.S_IWOTH)
        or not os.access(resolved, os.X_OK)
    ):
        raise SshMaterialError("SSH_CONFIGURATION_INVALID")
    return resolved


def resolve_openssh(name: str) -> Path:
    if name not in {"ssh", "scp", "ssh-keygen"}:
        raise SshMaterialError("SSH_CONFIGURATION_INVALID")
    located = shutil.which(name, path="/usr/bin:/bin")
    if located is None:
        raise SshMaterialError("SSH_UNAVAILABLE")
    return _validate_binary(Path(located), name)


def _validate_host(host: str, port: int) -> None:
    if (
        not isinstance(host, str)
        or host != host.strip()
        or host.startswith("-")
        or any(character in host for character in " /\\\t\r\n;|&$`(){}[]*?!'\"")
        or isinstance(port, bool)
        or not isinstance(port, int)
        or not 1 <= port <= 65535
    ):
        raise SshMaterialError("SSH_CONFIGURATION_INVALID")
    try:
        address = ipaddress.ip_address(host)
        if not isinstance(address, ipaddress.IPv4Address):
            raise SshMaterialError("SSH_CONFIGURATION_INVALID")
    except ValueError:
        if HOST_RE.fullmatch(host) is None:
            raise SshMaterialError("SSH_CONFIGURATION_INVALID")


def normalize_private_key(value: bytes | str) -> bytes:
    if isinstance(value, str):
        try:
            raw = value.encode("ascii")
        except UnicodeEncodeError as exc:
            raise SshMaterialError("SSH_KEY_FORMAT_INVALID") from exc
    elif isinstance(value, bytes):
        raw = value
    else:
        raise SshMaterialError("SSH_KEY_FORMAT_INVALID")
    if not 1 <= len(raw) <= MAX_PRIVATE or b"\x00" in raw or b"\r" in raw:
        raise SshMaterialError("SSH_KEY_FORMAT_INVALID")
    if raw.endswith(b"\n\n"):
        raise SshMaterialError("SSH_KEY_FORMAT_INVALID")
    body = raw[:-1] if raw.endswith(b"\n") else raw
    lines = body.split(b"\n")
    if len(lines) < 3 or lines[0] != PRIVATE_BEGIN or lines[-1] != PRIVATE_END:
        raise SshMaterialError("SSH_KEY_FORMAT_INVALID")
    payload_lines = lines[1:-1]
    if not payload_lines or any(not line or re.fullmatch(rb"[A-Za-z0-9+/=]+", line) is None for line in payload_lines):
        raise SshMaterialError("SSH_KEY_FORMAT_INVALID")
    try:
        decoded = base64.b64decode(b"".join(payload_lines), validate=True)
    except (binascii.Error, ValueError) as exc:
        raise SshMaterialError("SSH_KEY_FORMAT_INVALID") from exc
    if not decoded.startswith(b"openssh-key-v1\x00"):
        raise SshMaterialError("SSH_KEY_FORMAT_INVALID")
    return body + b"\n"


def _known_host_token(host: str, port: int) -> str:
    return host if port == 22 else f"[{host}]:{port}"


def normalize_known_hosts(value: bytes | str, *, host: str, port: int) -> bytes:
    if isinstance(value, str):
        try:
            raw = value.encode("ascii")
        except UnicodeEncodeError as exc:
            raise SshMaterialError("SSH_KNOWN_HOSTS_INVALID") from exc
    elif isinstance(value, bytes):
        raw = value
    else:
        raise SshMaterialError("SSH_KNOWN_HOSTS_INVALID")
    if not 1 <= len(raw) <= MAX_KNOWN_HOSTS or b"\x00" in raw or b"\r" in raw:
        raise SshMaterialError("SSH_KNOWN_HOSTS_INVALID")
    lines = raw.rstrip(b"\n").split(b"\n")
    expected = _known_host_token(host, port)
    found = False
    for encoded in lines:
        if not encoded:
            raise SshMaterialError("SSH_KNOWN_HOSTS_INVALID")
        try:
            line = encoded.decode("ascii")
        except UnicodeDecodeError as exc:
            raise SshMaterialError("SSH_KNOWN_HOSTS_INVALID") from exc
        fields = line.split()
        if len(fields) < 3 or KEY_TYPE_RE.fullmatch(fields[-2]) is None:
            raise SshMaterialError("SSH_KNOWN_HOSTS_INVALID")
        try:
            base64.b64decode(fields[-1], validate=True)
        except (binascii.Error, ValueError) as exc:
            raise SshMaterialError("SSH_KNOWN_HOSTS_INVALID") from exc
        hosts = fields[-3].split(",")
        if expected in hosts or (port == 22 and f"[{host}]:22" in hosts):
            found = True
    if not found:
        raise SshMaterialError("SSH_KNOWN_HOSTS_INVALID")
    return b"\n".join(lines) + b"\n"


def _write_exclusive(path: Path, payload: bytes) -> None:
    descriptor = os.open(
        path,
        os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0),
        0o600,
    )
    try:
        with os.fdopen(descriptor, "wb") as stream:
            descriptor = -1
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        details = path.lstat()
        if not stat.S_ISREG(details.st_mode) or stat.S_IMODE(details.st_mode) != 0o600:
            raise SshMaterialError("SSH_CONFIGURATION_INVALID")
    finally:
        if descriptor >= 0:
            os.close(descriptor)


def _run_keygen(argv: tuple[str, ...]) -> bytes:
    try:
        completed = subprocess.run(
            argv,
            check=False,
            stdin=subprocess.DEVNULL,
            capture_output=True,
            timeout=15,
            env={"PATH": "/usr/bin:/bin", "LANG": "C.UTF-8", "LC_ALL": "C.UTF-8"},
        )
    except (OSError, subprocess.SubprocessError) as exc:
        raise SshMaterialError("SSH_KEY_FORMAT_INVALID") from exc
    if (
        completed.returncode != 0
        or len(completed.stdout) > MAX_DIAGNOSTIC
        or len(completed.stderr) > MAX_DIAGNOSTIC
    ):
        raise SshMaterialError("SSH_KEY_FORMAT_INVALID")
    return completed.stdout


def _fingerprint(key: Path, keygen: Path) -> str:
    try:
        public = _run_keygen((os.fspath(keygen), "-y", "-f", os.fspath(key)))
        fields = public.decode("ascii", errors="strict").strip().split()
    except UnicodeDecodeError as exc:
        raise SshMaterialError("SSH_KEY_FORMAT_INVALID") from exc
    if len(fields) < 2 or fields[0] != "ssh-ed25519":
        raise SshMaterialError("SSH_KEY_FORMAT_INVALID")
    try:
        result = _run_keygen((os.fspath(keygen), "-lf", os.fspath(key), "-E", "sha256"))
        parts = result.decode("ascii", errors="strict").strip().split()
    except UnicodeDecodeError as exc:
        raise SshMaterialError("SSH_KEY_FORMAT_INVALID") from exc
    if len(parts) < 2 or FINGERPRINT_RE.fullmatch(parts[1]) is None:
        raise SshMaterialError("SSH_KEY_FORMAT_INVALID")
    return parts[1]


def cleanup_ssh_configuration(directory: Path) -> None:
    directory = Path(directory)
    if not directory.exists():
        return
    if directory.is_symlink() or not directory.is_dir():
        raise SshMaterialError("SSH_CONFIGURATION_INVALID")
    allowed = {"identity", "known_hosts", "config"}
    if not {entry.name for entry in directory.iterdir()}.issubset(allowed):
        raise SshMaterialError("SSH_CONFIGURATION_INVALID")
    identity = directory / "identity"
    if identity.exists():
        if identity.is_symlink() or not identity.is_file():
            raise SshMaterialError("SSH_CONFIGURATION_INVALID")
        shred = shutil.which("shred", path="/usr/bin:/bin")
        if shred is None:
            raise SshMaterialError("SSH_UNAVAILABLE")
        completed = subprocess.run(
            (_validate_binary(Path(shred), "shred"), "-u", os.fspath(identity)),
            check=False,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            env={"PATH": "/usr/bin:/bin", "LANG": "C.UTF-8", "LC_ALL": "C.UTF-8"},
        )
        if completed.returncode != 0 or identity.exists():
            raise SshMaterialError("SSH_UNAVAILABLE")
    for name in ("known_hosts", "config"):
        target = directory / name
        if target.exists():
            if target.is_symlink() or not target.is_file():
                raise SshMaterialError("SSH_CONFIGURATION_INVALID")
            target.unlink()
    directory.rmdir()


def materialize_ssh_configuration(
    *,
    directory: Path,
    host: str,
    port: int,
    private_key: bytes | str,
    known_hosts: bytes | str,
    expected_fingerprint: str,
    ssh_binary: Path | None = None,
    scp_binary: Path | None = None,
    keygen_binary: Path | None = None,
) -> SshConfiguration:
    _validate_host(host, port)
    if not isinstance(expected_fingerprint, str) or FINGERPRINT_RE.fullmatch(expected_fingerprint) is None:
        raise SshMaterialError("SSH_KEY_FINGERPRINT_MISMATCH")
    private_payload = normalize_private_key(private_key)
    hosts_payload = normalize_known_hosts(known_hosts, host=host, port=port)
    ssh = _validate_binary(ssh_binary, "ssh") if ssh_binary else resolve_openssh("ssh")
    scp = _validate_binary(scp_binary, "scp") if scp_binary else resolve_openssh("scp")
    keygen = _validate_binary(keygen_binary, "ssh-keygen") if keygen_binary else resolve_openssh("ssh-keygen")
    directory = Path(directory)
    try:
        directory.mkdir(mode=0o700, parents=False, exist_ok=False)
        if stat.S_IMODE(directory.lstat().st_mode) != 0o700:
            raise SshMaterialError("SSH_CONFIGURATION_INVALID")
        key = directory / "identity"
        hosts = directory / "known_hosts"
        config = directory / "config"
        _write_exclusive(key, private_payload)
        fingerprint = _fingerprint(key, keygen)
        if fingerprint != expected_fingerprint:
            raise SshMaterialError("SSH_KEY_FINGERPRINT_MISMATCH")
        _write_exclusive(hosts, hosts_payload)
        lines = [
            "Host production",
            f"  HostName {host}",
            f"  Port {port}",
            f"  User {REMOTE_USER}",
            "  BatchMode yes",
            "  IdentitiesOnly yes",
            "  IdentityAgent none",
            "  StrictHostKeyChecking yes",
            f"  UserKnownHostsFile {hosts}",
            f"  IdentityFile {key}",
            "  ConnectTimeout 15",
            "  ConnectionAttempts 1",
            "  ServerAliveInterval 15",
            "  ServerAliveCountMax 2",
            "  ForwardAgent no",
            "  ClearAllForwardings yes",
            "  PasswordAuthentication no",
            "  KbdInteractiveAuthentication no",
            "  LogLevel ERROR",
            "",
        ]
        _write_exclusive(config, "\n".join(lines).encode("ascii"))
        return SshConfiguration(directory, config, "production", ssh, scp, fingerprint)
    except Exception:
        if directory.exists():
            cleanup_ssh_configuration(directory)
        raise


def classify_ssh_failure(stderr: bytes | str, *, stage: str) -> str:
    raw = stderr.encode("utf-8", errors="ignore") if isinstance(stderr, str) else stderr
    lowered = raw[:MAX_DIAGNOSTIC].decode("utf-8", errors="ignore").lower()
    if stage == "known_hosts" or "host key verification failed" in lowered or "known_hosts" in lowered:
        return "SSH_KNOWN_HOSTS_INVALID"
    if "permission denied" in lowered or "no supported authentication methods" in lowered:
        return "SSH_AUTHENTICATION_FAILED"
    if any(marker in lowered for marker in ("connection timed out", "connection refused", "no route to host", "could not resolve hostname", "connection closed")):
        return "SSH_CONNECTION_FAILED"
    return "REMOTE_CAPABILITY_MISMATCH" if stage == "capabilities" else "SSH_CONNECTION_FAILED"
