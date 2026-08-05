#!/usr/bin/env python3
"""Deterministic control-root package: build, validate and install.

The control root is what actually runs on the VPS. Three properties make it
trustworthy and each one is enforced here rather than assumed:

Identity. The package is built from a Git commit object, never from the working
tree, so an uncommitted edit can never reach production.

Isolation. Ubuntu 22.04 ships jsonschema 3.2.0, which silently accepts Draft
2020-12 `prefixItems` violations. The package therefore vendors its own pinned,
hash-verified runtime and never touches the global interpreter's packages.

Binding. The installed manifest records every byte, so the helper can prove that
what is installed is exactly the commit the workflow believes it is talking to.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import pwd
import re
import shutil
import stat
import subprocess
import sys
import tarfile
import tempfile
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

REPOSITORY = "greggorio/abaronesa-emporio"
KIND = "emporio-control-root"
PLATFORM = "linux/amd64"
PYTHON_ABI = "cp310"
TARGET = "/opt/sistemas/emporio/shared/control"
REMOTE_USER = "deploy-emporio"
SCHEMA_VERSION = 1

MANIFEST_NAME = "control-root.manifest.json"
VENDOR_DIR = "vendor"
LOCK_PATH = "ops/deploy/control-root/requirements.lock"

SHA_RE = re.compile(r"^[0-9a-f]{40}$")
DIGEST_RE = re.compile(r"^[0-9a-f]{64}$")
LOCK_LINE_RE = re.compile(
    r"^(?P<name>[A-Za-z0-9._-]+)==(?P<version>[A-Za-z0-9._-]+)\s+"
    r"sha256=(?P<sha256>[0-9a-f]{64})\s+file=(?P<file>[A-Za-z0-9._+-]+\.whl)$"
)

# Every path that may exist in the package, derived from the real import closure
# of the helper, the CLI, the executor and the adapter. Anything else is refused.
SOURCE_FILES: tuple[str, ...] = (
    "ops/deploy/deployment-remote.py",
    "ops/deploy/deploy-release.sh",
    "ops/compose/compose.prod.yml",
    "ops/releases/components.yml",
    "tools/deploy/deployment_cli.py",
    "tools/deploy/deployment_executor.py",
    "tools/deploy/deployment_plan.py",
    "tools/deploy/production_adapter.py",
    "tools/deploy/ssh_material.py",
    "tools/candidates/artifact_io.py",
    "tools/candidates/lineage.py",
    "tools/releases/candidate_manifest.py",
    "tools/releases/catalog.py",
    "tools/releases/global_release.py",
)
SCHEMA_GLOB_DIRS: tuple[str, ...] = (
    "ops/deploy/schemas",
    "ops/releases",
)
EXECUTABLE_FILES = frozenset(
    {
        "ops/deploy/deploy-release.sh",
        "ops/deploy/deployment-remote.py",
    }
)

MAX_FILE_BYTES = 8 * 1024 * 1024
EPOCH = 0


class PackageError(Exception):
    """Stable, sanitized failure of the control-root package contract."""


def canonical(value: Any) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":")).encode() + b"\n"


def sha256_hex(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


# --------------------------------------------------------------------------- #
# Git object access
# --------------------------------------------------------------------------- #


def _git(root: Path, arguments: list[str], binary: bool = False) -> Any:
    completed = subprocess.run(
        ["git", "-C", str(root), *arguments],
        check=False,
        capture_output=True,
        timeout=120,
    )
    if completed.returncode != 0:
        raise PackageError(f"git {arguments[0]} failed")
    return completed.stdout if binary else completed.stdout.decode()


def resolve_commit(root: Path, source_sha: str) -> str:
    """Accept only a full 40-hex SHA that is a real commit in this repository."""
    if not isinstance(source_sha, str) or SHA_RE.fullmatch(source_sha) is None:
        raise PackageError("source sha must be 40 lowercase hex characters")
    kind = _git(root, ["cat-file", "-t", source_sha]).strip()
    if kind != "commit":
        raise PackageError("source sha is not a commit object")
    return source_sha


def commit_timestamp(root: Path, source_sha: str) -> str:
    """Derive createdAt from the commit, never from the build clock."""
    raw = _git(root, ["show", "-s", "--format=%cI", source_sha]).strip()
    moment = datetime.fromisoformat(raw).astimezone(timezone.utc)
    return moment.strftime("%Y-%m-%dT%H:%M:%SZ")


def read_blob(root: Path, source_sha: str, path: str) -> bytes:
    return _git(root, ["cat-file", "blob", f"{source_sha}:{path}"], binary=True)


def list_tree(root: Path, source_sha: str, prefix: str) -> list[str]:
    output = _git(root, ["ls-tree", "-r", "--name-only", source_sha, prefix])
    return sorted(line for line in output.splitlines() if line)


def selected_paths(root: Path, source_sha: str) -> list[str]:
    """The closed content set, resolved from the commit object itself."""
    paths = set(SOURCE_FILES)
    for directory in SCHEMA_GLOB_DIRS:
        for entry in list_tree(root, source_sha, directory):
            # Schemas and the catalogue only. Examples are documentation and the
            # contract forbids them inside the package.
            if "/examples/" in entry or "/tests/" in entry:
                continue
            if entry.endswith((".json", ".yml", ".yaml")):
                paths.add(entry)
    for path in sorted(paths):
        if path.startswith("/") or ".." in Path(path).parts:
            raise PackageError("selected path escapes the repository")
    return sorted(paths)


# --------------------------------------------------------------------------- #
# Lock and vendored runtime
# --------------------------------------------------------------------------- #


def parse_lock(text: str) -> list[dict[str, str]]:
    """Parse the closed lock format, refusing anything unpinned or unhashed."""
    entries: list[dict[str, str]] = []
    seen: set[str] = set()
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        match = LOCK_LINE_RE.fullmatch(stripped)
        if match is None:
            raise PackageError("lock line is not exactly name==version sha256= file=")
        entry = match.groupdict()
        if not entry["file"].endswith(".whl"):
            raise PackageError("lock accepts wheels only, never sdist")
        lowered = entry["name"].lower().replace("-", "_")
        if lowered in seen:
            raise PackageError("lock has a duplicated distribution")
        seen.add(lowered)
        entries.append(entry)
    if not entries:
        raise PackageError("lock is empty")
    return entries


def _wheel_is_compatible(filename: str) -> bool:
    """Pure-python or manylinux x86_64 built for cp310. Nothing else runs there."""
    stem = filename[: -len(".whl")]
    parts = stem.split("-")
    if len(parts) < 5:
        return False
    python_tag, abi_tag, platform_tag = parts[-3], parts[-2], parts[-1]
    if python_tag.startswith("py3") and abi_tag == "none" and platform_tag == "any":
        return True
    if python_tag != PYTHON_ABI or abi_tag != PYTHON_ABI:
        return False
    return "manylinux" in platform_tag and "x86_64" in platform_tag


def vendor_runtime(entries: list[dict[str, str]], wheels: Path, destination: Path) -> None:
    """Unpack the verified wheels so installing needs neither pip nor network."""
    destination.mkdir(parents=True, exist_ok=True)
    for entry in entries:
        wheel = wheels / entry["file"]
        if not wheel.is_file():
            raise PackageError(f"wheel is missing from the wheel directory: {entry['name']}")
        payload = wheel.read_bytes()
        if sha256_hex(payload) != entry["sha256"]:
            raise PackageError(f"wheel digest does not match the lock: {entry['name']}")
        if not _wheel_is_compatible(entry["file"]):
            raise PackageError(f"wheel is not cp310 linux x86_64 compatible: {entry['name']}")
        with zipfile.ZipFile(wheel) as archive:
            for member in archive.namelist():
                target = (destination / member).resolve()
                if not str(target).startswith(str(destination.resolve())):
                    raise PackageError("wheel member escapes the vendor directory")
            archive.extractall(destination)


# --------------------------------------------------------------------------- #
# Manifest and archive
# --------------------------------------------------------------------------- #


def _mode_for(path: str) -> int:
    if path in EXECUTABLE_FILES:
        return 0o755
    if path.startswith(f"{VENDOR_DIR}/"):
        return 0o644
    return 0o600


def build_tree(root: Path, source_sha: str, lock_text: str, wheels: Path, staging: Path) -> None:
    """Materialise the exact package tree from the commit and the vendored lock."""
    for path in selected_paths(root, source_sha):
        payload = read_blob(root, source_sha, path)
        if len(payload) > MAX_FILE_BYTES:
            raise PackageError(f"file exceeds the size limit: {path}")
        destination = staging / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(payload)
        destination.chmod(_mode_for(path))
    lock_destination = staging / LOCK_PATH
    lock_destination.parent.mkdir(parents=True, exist_ok=True)
    lock_destination.write_text(lock_text, encoding="utf-8")
    lock_destination.chmod(0o600)
    vendor_runtime(parse_lock(lock_text), wheels, staging / VENDOR_DIR)


def collect_files(staging: Path) -> list[dict[str, Any]]:
    # Order by the canonical posix path, which is what the manifest records;
    # sorting Path objects compares parts and yields a different sequence.
    files: list[dict[str, Any]] = []
    for relative in sorted(p.relative_to(staging).as_posix() for p in staging.rglob("*")):
        path = staging / relative
        info = path.lstat()
        if stat.S_ISDIR(info.st_mode):
            continue
        if not stat.S_ISREG(info.st_mode):
            raise PackageError(f"package accepts regular files only: {relative}")
        payload = path.read_bytes()
        files.append(
            {
                "path": relative,
                "mode": f"{stat.S_IMODE(info.st_mode):04o}",
                "size": len(payload),
                "sha256": sha256_hex(payload),
            }
        )
    return files


def build_manifest(source_sha: str, created_at: str, lock_text: str, files: list[dict[str, Any]]) -> dict[str, Any]:
    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "kind": KIND,
        "repository": REPOSITORY,
        "sourceSha": source_sha,
        "platform": PLATFORM,
        "pythonAbi": PYTHON_ABI,
        "requirementsSha256": sha256_hex(lock_text.encode()),
        "files": files,
        "createdAt": created_at,
    }
    return validate_manifest(manifest)


def validate_manifest(manifest: Any) -> dict[str, Any]:
    """Closed shape: exact keys, exact values, deterministic file ordering."""
    expected = {
        "schemaVersion", "kind", "repository", "sourceSha", "platform",
        "pythonAbi", "requirementsSha256", "files", "createdAt",
    }
    if not isinstance(manifest, dict) or set(manifest) != expected:
        raise PackageError("manifest keys are not exactly the contracted set")
    if manifest["schemaVersion"] != SCHEMA_VERSION or manifest["kind"] != KIND:
        raise PackageError("manifest identity is invalid")
    if manifest["repository"] != REPOSITORY or manifest["platform"] != PLATFORM:
        raise PackageError("manifest target is invalid")
    if manifest["pythonAbi"] != PYTHON_ABI:
        raise PackageError("manifest python abi is invalid")
    if SHA_RE.fullmatch(str(manifest["sourceSha"])) is None:
        raise PackageError("manifest sourceSha is invalid")
    if DIGEST_RE.fullmatch(str(manifest["requirementsSha256"])) is None:
        raise PackageError("manifest requirementsSha256 is invalid")
    if re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z", str(manifest["createdAt"])) is None:
        raise PackageError("manifest createdAt is not a normalized UTC timestamp")
    files = manifest["files"]
    if not isinstance(files, list) or not files:
        raise PackageError("manifest files are missing")
    seen: set[str] = set()
    executable: set[str] = set()
    for entry in files:
        if not isinstance(entry, dict) or set(entry) != {"path", "mode", "size", "sha256"}:
            raise PackageError("manifest file entry is malformed")
        path = entry["path"]
        if not isinstance(path, str) or path.startswith("/") or ".." in path.split("/"):
            raise PackageError("manifest file path is unsafe")
        if path in seen:
            raise PackageError("manifest has a duplicated file")
        seen.add(path)
        if DIGEST_RE.fullmatch(str(entry["sha256"])) is None:
            raise PackageError("manifest file digest is invalid")
        if not isinstance(entry["size"], int) or entry["size"] < 0:
            raise PackageError("manifest file size is invalid")
        if re.fullmatch(r"0[0-7]{3}", str(entry["mode"])) is None:
            raise PackageError("manifest file mode is invalid")
        mode = int(str(entry["mode"]), 8)
        if mode & 0o022:
            raise PackageError("manifest file mode allows group or other write")
        if mode != _mode_for(path):
            raise PackageError(f"manifest file mode is not contracted: {path}")
        if mode & 0o111:
            executable.add(path)
    if [entry["path"] for entry in files] != sorted(entry["path"] for entry in files):
        raise PackageError("manifest files are not deterministically ordered")
    if executable != EXECUTABLE_FILES or not EXECUTABLE_FILES.issubset(seen):
        raise PackageError("manifest executable file set is not exactly contracted")
    return manifest


def write_archive(staging: Path, manifest: dict[str, Any], archive_path: Path) -> None:
    """Deterministic tar: sorted, fixed mtime, uid/gid 0, no device or link."""
    payload = canonical(manifest)
    (staging / MANIFEST_NAME).write_bytes(payload)
    (staging / MANIFEST_NAME).chmod(0o600)
    (staging / f"{MANIFEST_NAME}.sha256").write_bytes(sha256_hex(payload).encode() + b"\n")
    (staging / f"{MANIFEST_NAME}.sha256").chmod(0o600)

    names = sorted(p.relative_to(staging).as_posix() for p in staging.rglob("*"))
    with tarfile.open(archive_path, "w", format=tarfile.PAX_FORMAT) as tar:
        for name in names:
            source = staging / name
            info = tar.gettarinfo(str(source), arcname=name)
            info.uid = info.gid = 0
            info.uname = info.gname = ""
            info.mtime = EPOCH
            if info.isdir():
                info.mode = 0o700
                tar.addfile(info)
            elif info.isreg():
                with source.open("rb") as handle:
                    tar.addfile(info, handle)
            else:
                raise PackageError(f"archive accepts regular files and directories only: {name}")


def _cli_build(args: argparse.Namespace) -> int:
    root = Path(args.repository).resolve()
    source_sha = resolve_commit(root, args.source_sha)
    lock_text = Path(args.lock).read_text(encoding="utf-8")
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="control-root-build-") as raw:
        staging = Path(raw) / "tree"
        staging.mkdir()
        build_tree(root, source_sha, lock_text, Path(args.wheels), staging)
        manifest = build_manifest(
            source_sha, commit_timestamp(root, source_sha), lock_text, collect_files(staging)
        )
        archive = output / "control-root.tar"
        write_archive(staging, manifest, archive)
    digest = sha256_hex(archive.read_bytes())
    (output / "control-root.tar.sha256").write_bytes(digest.encode() + b"\n")
    print(f"control-root-package:built:{digest}")
    return 0


# --------------------------------------------------------------------------- #
# Installer
# --------------------------------------------------------------------------- #


def _safe_members(tar: tarfile.TarFile) -> list[tarfile.TarInfo]:
    """Refuse traversal, links, devices and FIFOs before anything is written."""
    members: list[tarfile.TarInfo] = []
    for member in tar.getmembers():
        name = member.name
        if name.startswith("/") or ".." in Path(name).parts:
            raise PackageError("archive member escapes the target")
        if member.issym() or member.islnk():
            raise PackageError("archive must not contain links")
        if member.ischr() or member.isblk() or member.isfifo() or member.isdev():
            raise PackageError("archive must not contain devices or fifos")
        if not (member.isreg() or member.isdir()):
            raise PackageError("archive accepts regular files and directories only")
        if member.mode & 0o022:
            raise PackageError("archive member mode allows group or other write")
        expected_mode = (
            0o700
            if member.isdir()
            else 0o600
            if name in {MANIFEST_NAME, f"{MANIFEST_NAME}.sha256"}
            else _mode_for(name)
        )
        if member.mode != expected_mode:
            raise PackageError(f"archive member mode is not contracted: {name}")
        members.append(member)
    return members


def _require_host() -> None:
    if os.geteuid() != 0:
        raise PackageError("installer must run as root")
    if sys.version_info[:2] != (3, 10):
        raise PackageError("installer requires CPython 3.10")
    if os.uname().machine != "x86_64":
        raise PackageError("installer requires linux x86_64")
    try:
        pwd.getpwnam(REMOTE_USER)
    except KeyError as exc:
        raise PackageError(f"user {REMOTE_USER} does not exist") from exc


def _require_empty_target(target: Path) -> None:
    info = target.lstat()
    if not stat.S_ISDIR(info.st_mode) or stat.S_ISLNK(info.st_mode):
        raise PackageError("control root must be a real directory")
    if stat.S_IMODE(info.st_mode) != 0o700:
        raise PackageError("control root must be mode 0700")
    owner = pwd.getpwnam(REMOTE_USER)
    if info.st_uid != owner.pw_uid or info.st_gid != owner.pw_gid:
        raise PackageError("control root must belong to the dedicated user")
    if any(target.iterdir()):
        raise PackageError("control root is not empty; this version refuses upgrades")


def _fsync_dir(path: Path) -> None:
    handle = os.open(str(path), os.O_RDONLY)
    try:
        os.fsync(handle)
    finally:
        os.close(handle)


def _cli_install(args: argparse.Namespace) -> int:
    target = Path(TARGET)
    archive = Path(args.archive)
    sidecar = Path(args.sidecar)
    _require_host()
    _require_empty_target(target)

    payload = archive.read_bytes()
    digest = sha256_hex(payload)
    if sidecar.read_bytes() != digest.encode() + b"\n":
        raise PackageError("archive sidecar does not match the archive")

    staging = target.parent / f".control-staging-{os.getpid()}"
    if staging.exists():
        raise PackageError("staging directory already exists")
    owner = pwd.getpwnam(REMOTE_USER)
    try:
        staging.mkdir(mode=0o700)
        with tarfile.open(archive, "r") as tar:
            members = _safe_members(tar)
            tar.extractall(staging, members=members)

        manifest_payload = (staging / MANIFEST_NAME).read_bytes()
        if (staging / f"{MANIFEST_NAME}.sha256").read_bytes() != sha256_hex(manifest_payload).encode() + b"\n":
            raise PackageError("manifest sidecar does not match the manifest")
        manifest = validate_manifest(json.loads(manifest_payload))
        if manifest["sourceSha"] != args.source_sha:
            raise PackageError("archive does not belong to the expected source sha")
        verify_tree(staging, manifest)

        for path in sorted(staging.rglob("*")):
            os.chown(path, owner.pw_uid, owner.pw_gid)
            path.chmod(0o700 if path.is_dir() else stat.S_IMODE(path.lstat().st_mode))
            if path.is_file():
                handle = os.open(str(path), os.O_RDONLY)
                try:
                    os.fsync(handle)
                finally:
                    os.close(handle)
        os.chown(staging, owner.pw_uid, owner.pw_gid)

        for entry in sorted(staging.iterdir()):
            shutil.move(str(entry), str(target / entry.name))
        _fsync_dir(target)
        verify_tree(target, manifest, expected_owner=(owner.pw_uid, owner.pw_gid))
    except BaseException:
        shutil.rmtree(staging, ignore_errors=True)
        for entry in list(target.iterdir()) if target.exists() else []:
            shutil.rmtree(entry, ignore_errors=True) if entry.is_dir() else entry.unlink(missing_ok=True)
        raise
    finally:
        shutil.rmtree(staging, ignore_errors=True)

    print(f"control-root-package:installed:{manifest['sourceSha']}")
    return 0


def verify_tree(
    root: Path,
    manifest: dict[str, Any],
    *,
    expected_owner: tuple[int, int] | None = None,
) -> None:
    """Re-read every byte and refuse extra, missing or altered files."""
    expected = {entry["path"]: entry for entry in manifest["files"]}
    # The manifest describes the payload, so it cannot describe itself; its own
    # integrity is proven by the external sidecar instead.
    selfish = {MANIFEST_NAME, f"{MANIFEST_NAME}.sha256"}
    present = {
        path.relative_to(root).as_posix()
        for path in root.rglob("*")
        if path.is_file() and not path.is_symlink()
    } - selfish
    if present != set(expected):
        raise PackageError("installed tree does not match the manifest file set")
    for path in root.rglob("*"):
        info = path.lstat()
        if stat.S_ISLNK(info.st_mode):
            raise PackageError("installed tree must not contain symlinks")
        if stat.S_ISDIR(info.st_mode) and stat.S_IMODE(info.st_mode) != 0o700:
            raise PackageError("installed directory mode is not 0700")
        if expected_owner is not None and (info.st_uid, info.st_gid) != expected_owner:
            raise PackageError("installed tree owner is not the dedicated user")
    for relative, entry in expected.items():
        path = root / relative
        info = path.lstat()
        if stat.S_ISLNK(info.st_mode) or not stat.S_ISREG(info.st_mode):
            raise PackageError(f"installed path is not a regular file: {relative}")
        if stat.S_IMODE(info.st_mode) != int(str(entry["mode"]), 8):
            raise PackageError(f"installed file mode does not match the manifest: {relative}")
        payload = path.read_bytes()
        if len(payload) != entry["size"] or sha256_hex(payload) != entry["sha256"]:
            raise PackageError(f"installed file does not match the manifest: {relative}")
    for name in selfish:
        path = root / name
        if not path.exists():
            continue
        info = path.lstat()
        if (
            stat.S_ISLNK(info.st_mode)
            or not stat.S_ISREG(info.st_mode)
            or stat.S_IMODE(info.st_mode) != 0o600
        ):
            raise PackageError(f"installed manifest material is invalid: {name}")


def _cli_verify(args: argparse.Namespace) -> int:
    root = Path(args.root)
    manifest_payload = (root / MANIFEST_NAME).read_bytes()
    if (root / f"{MANIFEST_NAME}.sha256").read_bytes() != sha256_hex(manifest_payload).encode() + b"\n":
        raise PackageError("manifest sidecar does not match the manifest")
    manifest = validate_manifest(json.loads(manifest_payload))
    if canonical(manifest) != manifest_payload:
        raise PackageError("manifest is not canonical")
    if args.source_sha and manifest["sourceSha"] != args.source_sha:
        raise PackageError("installed control root does not match the expected source sha")
    owner = pwd.getpwnam(REMOTE_USER)
    verify_tree(root, manifest, expected_owner=(owner.pw_uid, owner.pw_gid))
    print(f"control-root-package:verified:{manifest['sourceSha']}")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)

    build = sub.add_parser("build", help="build the deterministic package from a commit")
    build.add_argument("--repository", default=".")
    build.add_argument("--source-sha", required=True)
    build.add_argument("--lock", default=LOCK_PATH)
    build.add_argument("--wheels", required=True)
    build.add_argument("--output", required=True)
    build.set_defaults(handler=_cli_build)

    install = sub.add_parser("install", help="install into the empty control root")
    install.add_argument("--archive", required=True)
    install.add_argument("--sidecar", required=True)
    install.add_argument("--source-sha", required=True)
    install.set_defaults(handler=_cli_install)

    verify = sub.add_parser("verify", help="verify an installed control root")
    verify.add_argument("--root", default=TARGET)
    verify.add_argument("--source-sha", default="")
    verify.set_defaults(handler=_cli_verify)

    args = parser.parse_args(argv)
    try:
        return int(args.handler(args))
    except (PackageError, OSError, ValueError, json.JSONDecodeError, tarfile.TarError) as exc:
        message = exc.args[0] if isinstance(exc, PackageError) else type(exc).__name__
        print(f"control-root-package:invalid:{message}", file=sys.stderr)
        return 3


if __name__ == "__main__":
    raise SystemExit(main())
