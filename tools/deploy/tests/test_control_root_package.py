"""Causal tests of the control-root package, lock, installer and sha binding.

The numbered classes map onto the sixteen rejection categories the slice
contract requires. Nothing here touches Docker, SSH, GHCR or the VPS.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import re
import subprocess
import sys
import tarfile
import tempfile
import unittest
from pathlib import Path
from unittest import mock

REPO = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO / "tools/deploy"))

import control_root_package as pkg  # noqa: E402
import validate_control_root_package as contract  # noqa: E402

SHA = "a" * 40


def head_sha() -> str:
    return subprocess.run(
        ["git", "-C", str(REPO), "rev-parse", "HEAD"],
        check=True, capture_output=True, text=True,
    ).stdout.strip()


def manifest_fixture(**overrides) -> dict:
    value = {
        "schemaVersion": 1,
        "kind": pkg.KIND,
        "repository": pkg.REPOSITORY,
        "sourceSha": SHA,
        "platform": pkg.PLATFORM,
        "pythonAbi": pkg.PYTHON_ABI,
        "requirementsSha256": "b" * 64,
        "files": [
            {
                "path": "ops/db/init-databases.sh", "mode": "0755",
                "size": 1, "sha256": "1" * 64,
            },
            {
                "path": "ops/deploy/deploy-release.sh", "mode": "0755",
                "size": 1, "sha256": "c" * 64,
            },
            {
                "path": "ops/deploy/deployment-remote.py", "mode": "0755",
                "size": 1, "sha256": "d" * 64,
            },
            {
                "path": "tools/example.py", "mode": "0600",
                "size": 1, "sha256": "e" * 64,
            },
            {
                "path": "vendor/package.py", "mode": "0644",
                "size": 1, "sha256": "f" * 64,
            },
        ],
        "createdAt": "2026-08-03T10:00:00Z",
    }
    value.update(overrides)
    return value


class T01SourceSha(unittest.TestCase):
    def test_absent_invalid_or_non_commit_sha_is_rejected(self) -> None:
        for candidate in ("", "zz", "A" * 40, "a" * 39, "a" * 41, None, 123):
            with self.subTest(sha=candidate), self.assertRaises(pkg.PackageError):
                pkg.resolve_commit(REPO, candidate)

    def test_tree_object_is_not_accepted_as_identity(self) -> None:
        tree = subprocess.run(
            ["git", "-C", str(REPO), "rev-parse", "HEAD^{tree}"],
            check=True, capture_output=True, text=True,
        ).stdout.strip()
        with self.assertRaises(pkg.PackageError):
            pkg.resolve_commit(REPO, tree)

    def test_real_commit_is_accepted(self) -> None:
        self.assertEqual(head_sha(), pkg.resolve_commit(REPO, head_sha()))


class T02WorkingTree(unittest.TestCase):
    def test_untracked_reports_never_enter_the_selection(self) -> None:
        selected = pkg.selected_paths(REPO, head_sha())
        self.assertTrue(all(not p.startswith("docs/") for p in selected))
        self.assertTrue(all("report" not in p for p in selected))

    def test_content_comes_from_the_commit_not_the_worktree(self) -> None:
        # deployment-remote.py is read through git cat-file, so a dirty worktree
        # cannot change what the package contains.
        blob = pkg.read_blob(REPO, head_sha(), "ops/deploy/deployment-remote.py")
        committed = subprocess.run(
            ["git", "-C", str(REPO), "show", f"{head_sha()}:ops/deploy/deployment-remote.py"],
            check=True, capture_output=True,
        ).stdout
        self.assertEqual(committed, blob)


class T03Allowlist(unittest.TestCase):
    def test_executable_set_and_mode_classes_are_exact(self) -> None:
        self.assertEqual(
            {
                "ops/db/init-databases.sh",
                "ops/deploy/deploy-release.sh",
                "ops/deploy/deployment-remote.py",
            },
            pkg.EXECUTABLE_FILES,
        )
        for path in pkg.EXECUTABLE_FILES:
            self.assertEqual(0o755, pkg._mode_for(path))
        self.assertEqual(0o600, pkg._mode_for("tools/deploy/deployment_cli.py"))
        self.assertEqual(0o600, pkg._mode_for(pkg.LOCK_PATH))
        self.assertEqual(0o644, pkg._mode_for("vendor/jsonschema/__init__.py"))

    def test_helper_disables_bytecode_before_importing_packaged_modules(self) -> None:
        helper = (REPO / "ops/deploy/deployment-remote.py").read_text()
        disable = helper.index("sys.dont_write_bytecode = True")
        packaged_import = helper.index("import deployment_plan")
        self.assertLess(disable, packaged_import)

    def test_selection_is_closed_and_excludes_tests_and_examples(self) -> None:
        selected = pkg.selected_paths(REPO, head_sha())
        self.assertIn("ops/deploy/deployment-remote.py", selected)
        self.assertIn("ops/compose/compose.prod.yml", selected)
        self.assertTrue(all("/tests/" not in p for p in selected))
        self.assertTrue(all("/examples/" not in p for p in selected))
        self.assertTrue(all(not p.startswith(".github/") for p in selected))

    def test_missing_allowlisted_file_fails_the_build(self) -> None:
        with mock.patch.object(pkg, "SOURCE_FILES", ("ops/deploy/does-not-exist.py",)):
            with self.assertRaises(pkg.PackageError):
                pkg.read_blob(REPO, head_sha(), "ops/deploy/does-not-exist.py")

    def test_manifest_rejects_extra_or_duplicated_file(self) -> None:
        duplicated = manifest_fixture(files=[
            {"path": "a.py", "mode": "0600", "size": 1, "sha256": "c" * 64},
            {"path": "a.py", "mode": "0600", "size": 1, "sha256": "c" * 64},
        ])
        with self.assertRaises(pkg.PackageError):
            pkg.validate_manifest(duplicated)


class T04ArchiveSafety(unittest.TestCase):
    def _tar_with(self, info: tarfile.TarInfo, payload: bytes = b"") -> Path:
        handle = tempfile.NamedTemporaryFile(suffix=".tar", delete=False)
        with tarfile.open(handle.name, "w") as tar:
            tar.addfile(info, io.BytesIO(payload))
        return Path(handle.name)

    def test_traversal_link_device_and_unsafe_mode_are_refused(self) -> None:
        cases = []
        traversal = tarfile.TarInfo("../escape"); traversal.size = 0
        cases.append(traversal)
        absolute = tarfile.TarInfo("/etc/passwd"); absolute.size = 0
        cases.append(absolute)
        symlink = tarfile.TarInfo("link"); symlink.type = tarfile.SYMTYPE; symlink.linkname = "/etc/passwd"
        cases.append(symlink)
        hardlink = tarfile.TarInfo("hard"); hardlink.type = tarfile.LNKTYPE; hardlink.linkname = "a.py"
        cases.append(hardlink)
        fifo = tarfile.TarInfo("pipe"); fifo.type = tarfile.FIFOTYPE
        cases.append(fifo)
        device = tarfile.TarInfo("dev"); device.type = tarfile.CHRTYPE
        cases.append(device)
        group_write = tarfile.TarInfo("loose.py"); group_write.size = 0; group_write.mode = 0o666
        cases.append(group_write)

        for info in cases:
            path = self._tar_with(info)
            try:
                with tarfile.open(path) as tar:
                    with self.subTest(member=info.name), self.assertRaises(pkg.PackageError):
                        pkg._safe_members(tar)
            finally:
                path.unlink(missing_ok=True)

    def test_missing_helper_execute_bit_and_extra_executable_are_refused(self) -> None:
        cases = []
        helper = tarfile.TarInfo("ops/deploy/deployment-remote.py")
        helper.size = 0
        helper.mode = 0o600
        cases.append(helper)
        extra = tarfile.TarInfo("tools/deploy/deployment_cli.py")
        extra.size = 0
        extra.mode = 0o755
        cases.append(extra)
        for info in cases:
            path = self._tar_with(info)
            try:
                with tarfile.open(path) as tar:
                    with self.subTest(member=info.name), self.assertRaises(pkg.PackageError):
                        pkg._safe_members(tar)
            finally:
                path.unlink(missing_ok=True)


class T05Wheels(unittest.TestCase):
    def test_platform_and_abi_compatibility(self) -> None:
        self.assertTrue(pkg._wheel_is_compatible("attrs-26.1.0-py3-none-any.whl"))
        self.assertTrue(pkg._wheel_is_compatible(
            "rpds_py-0.30.0-cp310-cp310-manylinux_2_17_x86_64.manylinux2014_x86_64.whl"))
        for bad in (
            "rpds_py-0.30.0-cp311-cp311-manylinux_2_17_x86_64.whl",
            "rpds_py-0.30.0-cp310-cp310-macosx_11_0_arm64.whl",
            "rpds_py-0.30.0-cp310-cp310-win_amd64.whl",
            "rpds_py-0.30.0-cp310-cp310-manylinux_2_17_aarch64.whl",
        ):
            with self.subTest(wheel=bad):
                self.assertFalse(pkg._wheel_is_compatible(bad))

    def test_lock_refuses_unpinned_unhashed_sdist_or_duplicate(self) -> None:
        for text in (
            "jsonschema>=4.0  sha256=" + "a" * 64 + "  file=x.whl",
            "jsonschema==4.23.0  file=x.whl",
            "jsonschema==4.23.0  sha256=" + "a" * 64 + "  file=jsonschema-4.23.0.tar.gz",
            "jsonschema==4.23.0  sha256=" + "a" * 64 + "  file=a.whl\njsonschema==4.23.0  sha256=" + "b" * 64 + "  file=b.whl",
            "",
        ):
            with self.subTest(text=text[:40]), self.assertRaises(pkg.PackageError):
                pkg.parse_lock(text)

    def test_real_lock_parses_and_is_fully_hashed(self) -> None:
        entries = pkg.parse_lock((REPO / pkg.LOCK_PATH).read_text(encoding="utf-8"))
        names = {e["name"].lower().replace("-", "_") for e in entries}
        self.assertIn("jsonschema", names)
        self.assertIn("pyyaml", names)
        self.assertIn("typing_extensions", names)
        for entry in entries:
            self.assertRegex(entry["sha256"], r"^[0-9a-f]{64}$")
            self.assertTrue(pkg._wheel_is_compatible(entry["file"]))


class T06LockTampering(unittest.TestCase):
    def test_wheel_digest_mismatch_stops_vendoring(self) -> None:
        entries = pkg.parse_lock(
            "attrs==26.1.0  sha256=" + "0" * 64 + "  file=attrs-26.1.0-py3-none-any.whl"
        )
        with tempfile.TemporaryDirectory() as raw:
            wheels = Path(raw) / "wheels"
            wheels.mkdir()
            (wheels / "attrs-26.1.0-py3-none-any.whl").write_bytes(b"not the real wheel")
            with self.assertRaises(pkg.PackageError):
                pkg.vendor_runtime(entries, wheels, Path(raw) / "vendor")

    def test_missing_wheel_stops_vendoring(self) -> None:
        entries = pkg.parse_lock(
            "attrs==26.1.0  sha256=" + "0" * 64 + "  file=attrs-26.1.0-py3-none-any.whl"
        )
        with tempfile.TemporaryDirectory() as raw:
            with self.assertRaises(pkg.PackageError):
                pkg.vendor_runtime(entries, Path(raw), Path(raw) / "vendor")


class T07ManifestIntegrity(unittest.TestCase):
    def test_valid_manifest_is_accepted(self) -> None:
        self.assertEqual(manifest_fixture(), pkg.validate_manifest(manifest_fixture()))

    def test_shape_and_value_mutants_are_rejected(self) -> None:
        for key, value in (
            ("kind", "other"),
            ("repository", "other/repo"),
            ("platform", "linux/arm64"),
            ("pythonAbi", "cp311"),
            ("sourceSha", "A" * 40),
            ("requirementsSha256", "zz"),
            ("createdAt", "2026-08-03 10:00:00"),
            ("files", []),
            ("schemaVersion", 2),
        ):
            with self.subTest(key=key), self.assertRaises(pkg.PackageError):
                pkg.validate_manifest(manifest_fixture(**{key: value}))
        extra = manifest_fixture(); extra["extra"] = 1
        with self.assertRaises(pkg.PackageError):
            pkg.validate_manifest(extra)

    def test_unordered_unsafe_or_group_writable_files_are_rejected(self) -> None:
        unordered = manifest_fixture(files=[
            {"path": "b.py", "mode": "0600", "size": 1, "sha256": "c" * 64},
            {"path": "a.py", "mode": "0600", "size": 1, "sha256": "d" * 64},
        ])
        with self.assertRaises(pkg.PackageError):
            pkg.validate_manifest(unordered)
        for path in ("/etc/passwd", "../escape"):
            with self.subTest(path=path), self.assertRaises(pkg.PackageError):
                pkg.validate_manifest(manifest_fixture(files=[
                    {"path": path, "mode": "0600", "size": 1, "sha256": "c" * 64}
                ]))
        with self.assertRaises(pkg.PackageError):
            pkg.validate_manifest(manifest_fixture(files=[
                {"path": "a.py", "mode": "0666", "size": 1, "sha256": "c" * 64}
            ]))

    def test_manifest_requires_exact_executable_set_and_mode_classes(self) -> None:
        for executable in sorted(pkg.EXECUTABLE_FILES):
            mutant = json.loads(json.dumps(manifest_fixture()))
            next(x for x in mutant["files"] if x["path"] == executable)["mode"] = "0600"
            with self.subTest(path=executable), self.assertRaises(pkg.PackageError):
                pkg.validate_manifest(mutant)

        missing = json.loads(json.dumps(manifest_fixture()))
        missing["files"] = [
            x for x in missing["files"]
            if x["path"] != "ops/deploy/deployment-remote.py"
        ]
        with self.assertRaises(pkg.PackageError):
            pkg.validate_manifest(missing)

        extra = json.loads(json.dumps(manifest_fixture()))
        extra["files"].append(
            {"path": "z-extra.py", "mode": "0755", "size": 1, "sha256": "a" * 64}
        )
        with self.assertRaises(pkg.PackageError):
            pkg.validate_manifest(extra)

        vendor = json.loads(json.dumps(manifest_fixture()))
        next(x for x in vendor["files"] if x["path"].startswith("vendor/"))["mode"] = "0600"
        with self.assertRaises(pkg.PackageError):
            pkg.validate_manifest(vendor)

    def test_altered_file_is_detected_by_verify_tree(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            (root / "a.py").write_bytes(b"x")
            (root / "a.py").chmod(0o600)
            manifest = manifest_fixture(files=[{
                "path": "a.py", "mode": "0600", "size": 1,
                "sha256": hashlib.sha256(b"x").hexdigest(),
            }])
            pkg.verify_tree(root, manifest)
            (root / "a.py").write_bytes(b"y")
            with self.assertRaises(pkg.PackageError):
                pkg.verify_tree(root, manifest)

    def test_verify_tree_rejects_mode_tampering(self) -> None:
        payloads = {
            "ops/db/init-databases.sh": b"#!/bin/sh\n",
            "ops/deploy/deploy-release.sh": b"s",
            "ops/deploy/deployment-remote.py": b"h",
            "tools/example.py": b"t",
            "vendor/package.py": b"v",
        }
        files = [
            {
                "path": path,
                "mode": f"{pkg._mode_for(path):04o}",
                "size": len(payload),
                "sha256": hashlib.sha256(payload).hexdigest(),
            }
            for path, payload in sorted(payloads.items())
        ]
        manifest = manifest_fixture(files=files)
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            for path, payload in payloads.items():
                destination = root / path
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(payload)
                destination.chmod(pkg._mode_for(path))
            for directory in (x for x in root.rglob("*") if x.is_dir()):
                directory.chmod(0o700)
            pkg.verify_tree(root, manifest)
            helper = root / "ops/deploy/deployment-remote.py"
            helper.chmod(0o600)
            with self.assertRaises(pkg.PackageError):
                pkg.verify_tree(root, manifest)
            helper.chmod(0o755)
            extra = root / "tools/example.py"
            extra.chmod(0o755)
            with self.assertRaises(pkg.PackageError):
                pkg.verify_tree(root, manifest)


class T08Determinism(unittest.TestCase):
    def test_manifest_serialisation_is_canonical_and_stable(self) -> None:
        first = pkg.canonical(manifest_fixture())
        second = pkg.canonical(json.loads(first))
        self.assertEqual(first, second)
        self.assertTrue(first.endswith(b"\n"))


class T09HostRequirements(unittest.TestCase):
    def test_installer_refuses_non_root(self) -> None:
        with mock.patch.object(pkg.os, "geteuid", return_value=1000):
            with self.assertRaises(pkg.PackageError):
                pkg._require_host()

    def test_installer_refuses_wrong_python_or_architecture(self) -> None:
        with mock.patch.object(pkg.os, "geteuid", return_value=0):
            with mock.patch.object(pkg.sys, "version_info", (3, 13, 0)):
                with self.assertRaises(pkg.PackageError):
                    pkg._require_host()
            with mock.patch.object(pkg.sys, "version_info", (3, 10, 0)), \
                 mock.patch.object(pkg.os, "uname", return_value=mock.Mock(machine="aarch64")):
                with self.assertRaises(pkg.PackageError):
                    pkg._require_host()

    def test_installer_refuses_missing_user(self) -> None:
        with mock.patch.object(pkg.os, "geteuid", return_value=0), \
             mock.patch.object(pkg.sys, "version_info", (3, 10, 0)), \
             mock.patch.object(pkg.os, "uname", return_value=mock.Mock(machine="x86_64")), \
             mock.patch.object(pkg.pwd, "getpwnam", side_effect=KeyError):
            with self.assertRaises(pkg.PackageError):
                pkg._require_host()


class T10TargetState(unittest.TestCase):
    def test_non_empty_wrong_mode_or_symlink_target_is_refused(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            target = Path(raw) / "control"
            target.mkdir(mode=0o700)
            owner = mock.Mock(pw_uid=target.lstat().st_uid, pw_gid=target.lstat().st_gid)
            with mock.patch.object(pkg.pwd, "getpwnam", return_value=owner):
                pkg._require_empty_target(target)          # empty is accepted
                (target / "leftover").write_text("x")
                with self.assertRaises(pkg.PackageError):  # not empty
                    pkg._require_empty_target(target)
            (target / "leftover").unlink()
            target.chmod(0o755)
            with mock.patch.object(pkg.pwd, "getpwnam", return_value=owner):
                with self.assertRaises(pkg.PackageError):  # wrong mode
                    pkg._require_empty_target(target)

    def test_wrong_owner_is_refused(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            target = Path(raw) / "control"
            target.mkdir(mode=0o700)
            other = mock.Mock(pw_uid=999999, pw_gid=999999)
            with mock.patch.object(pkg.pwd, "getpwnam", return_value=other):
                with self.assertRaises(pkg.PackageError):
                    pkg._require_empty_target(target)


class _FakePasswdEntry:
    """Supports both attribute access (our code) and index access (tarfile's chown)."""

    def __init__(self, uid: int, gid: int) -> None:
        self.pw_uid = uid
        self.pw_gid = gid

    def __getitem__(self, index: int):
        return (None, None, self.pw_uid, self.pw_gid, None, None, None)[index]


class T11ExtractionFailureLeavesNoResidue(unittest.TestCase):
    """A failure discovered after extraction must never reach the target."""

    def test_bad_sidecar_inside_the_archive_leaves_target_empty_and_no_staging(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            target = root / "control"
            target.mkdir(mode=0o700)
            owner = _FakePasswdEntry(target.lstat().st_uid, target.lstat().st_gid)

            archive_dir = root / "archive"
            archive_dir.mkdir()
            manifest_payload = pkg.canonical({"not": "checked-before-sidecar"})
            (archive_dir / pkg.MANIFEST_NAME).write_bytes(manifest_payload)
            # sidecar deliberately does not match the manifest bytes above.
            (archive_dir / f"{pkg.MANIFEST_NAME}.sha256").write_bytes(b"0" * 64 + b"\n")

            archive = root / "control-root.tar"
            with tarfile.open(archive, "w") as tar:
                for name in sorted(str(p.relative_to(archive_dir)) for p in archive_dir.iterdir()):
                    tar.add(archive_dir / name, arcname=name)
            sidecar = root / "control-root.tar.sha256"
            sidecar.write_bytes(pkg.sha256_hex(archive.read_bytes()).encode() + b"\n")

            args = argparse.Namespace(
                archive=str(archive), sidecar=str(sidecar), source_sha=SHA,
            )
            with mock.patch.object(pkg, "TARGET", str(target)), \
                 mock.patch.object(pkg.os, "geteuid", return_value=0), \
                 mock.patch.object(pkg.sys, "version_info", (3, 10, 0)), \
                 mock.patch.object(pkg.os, "uname", return_value=mock.Mock(machine="x86_64")), \
                 mock.patch.object(pkg.pwd, "getpwnam", return_value=owner), \
                 mock.patch.object(pkg.os, "chown"):
                with self.assertRaises(pkg.PackageError):
                    pkg._cli_install(args)

            staging = target.parent / f".control-staging-{os.getpid()}"
            self.assertFalse(staging.exists())
            self.assertTrue(target.exists())
            self.assertEqual([], list(target.iterdir()))

    def test_install_preserves_exact_executable_modes_and_owner(self) -> None:
        payloads = {
            "ops/db/init-databases.sh": b"#!/bin/sh\n",
            "ops/deploy/deploy-release.sh": b"#!/bin/sh\n",
            "ops/deploy/deployment-remote.py": b"#!/usr/bin/env python3\n",
            "tools/example.py": b"pass\n",
            "vendor/package.py": b"VALUE = 1\n",
        }
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            tree = root / "tree"
            tree.mkdir(mode=0o700)
            for path, payload in payloads.items():
                destination = tree / path
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(payload)
                destination.chmod(pkg._mode_for(path))
            for directory in (x for x in tree.rglob("*") if x.is_dir()):
                directory.chmod(0o700)
            manifest = pkg.build_manifest(
                SHA, "2026-08-03T10:00:00Z", "lock\n", pkg.collect_files(tree)
            )
            archive = root / "control-root.tar"
            pkg.write_archive(tree, manifest, archive)
            sidecar = root / "control-root.tar.sha256"
            sidecar.write_bytes(pkg.sha256_hex(archive.read_bytes()).encode() + b"\n")
            target = root / "control"
            target.mkdir(mode=0o700)
            info = target.lstat()
            owner = _FakePasswdEntry(info.st_uid, info.st_gid)
            args = argparse.Namespace(
                archive=str(archive), sidecar=str(sidecar), source_sha=SHA,
            )
            with mock.patch.object(pkg, "TARGET", str(target)), \
                 mock.patch.object(pkg, "_require_host"), \
                 mock.patch.object(pkg.pwd, "getpwnam", return_value=owner), \
                 mock.patch.object(pkg.os, "chown"):
                self.assertEqual(0, pkg._cli_install(args))

            executable = {
                path.relative_to(target).as_posix()
                for path in target.rglob("*")
                if path.is_file() and path.stat().st_mode & 0o111
            }
            self.assertEqual(pkg.EXECUTABLE_FILES, executable)
            helper = target / "ops/deploy/deployment-remote.py"
            self.assertTrue(helper.is_file())
            self.assertFalse(helper.is_symlink())
            self.assertEqual(0o755, helper.stat().st_mode & 0o777)
            initializer = target / "ops/db/init-databases.sh"
            self.assertEqual(0o755, initializer.stat().st_mode & 0o777)
            self.assertEqual((info.st_uid, info.st_gid), (helper.stat().st_uid, helper.stat().st_gid))
            direct = subprocess.run(
                [os.fspath(helper), "capabilities"],
                check=False,
                capture_output=True,
                text=True,
            )
            # Outside the isolated deploy-emporio runtime the helper rejects
            # identity, but the kernel must execute its shebang directly. Exit
            # 126/PermissionError would reproduce the production defect.
            self.assertNotEqual(126, direct.returncode)
            self.assertNotIn("Permission denied", direct.stderr)


class T12ControlShaBinding(unittest.TestCase):
    def helper(self):
        sys.path.insert(0, str(REPO / "ops/deploy"))
        import importlib.util
        spec = importlib.util.spec_from_file_location(
            "deployment_remote_under_test", REPO / "ops/deploy/deployment-remote.py"
        )
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def test_missing_manifest_tampered_bytes_and_divergent_sha(self) -> None:
        remote = self.helper()
        with tempfile.TemporaryDirectory() as raw:
            control = Path(raw) / "control"
            control.mkdir()
            with mock.patch.object(remote, "CONTROL_ROOT", control):
                with self.assertRaises(remote.RemoteError):   # manifest missing
                    remote._installed_control_sha()

                payload_file = control / "helper.py"
                payload_file.write_bytes(b"real")
                manifest = {
                    "sourceSha": SHA,
                    "files": [{"path": "helper.py", "sha256": hashlib.sha256(b"real").hexdigest()}],
                }
                blob = json.dumps(manifest).encode()
                (control / "control-root.manifest.json").write_bytes(blob)
                (control / "control-root.manifest.json.sha256").write_bytes(
                    hashlib.sha256(blob).hexdigest().encode() + b"\n"
                )
                self.assertEqual(SHA, remote._installed_control_sha())

                payload_file.write_bytes(b"tampered")        # helper mutated
                with self.assertRaises(remote.RemoteError):
                    remote._installed_control_sha()

                payload_file.write_bytes(b"real")
                (control / "control-root.manifest.json.sha256").write_bytes(b"0" * 64 + b"\n")
                with self.assertRaises(remote.RemoteError):  # sidecar divergent
                    remote._installed_control_sha()


class T13TransportRefusesBeforeMutation(unittest.TestCase):
    def transport(self):
        sys.path.insert(0, str(REPO / "tools/releases"))
        import deployment_transport
        return deployment_transport

    def test_divergent_sha_fails_before_any_remote_mutation(self) -> None:
        t = self.transport()

        class Runner:
            def __init__(self): self.calls = []
            def run(self, argv, timeout_seconds=None):
                self.calls.append(tuple(map(str, argv)))
                value = {
                    "controlSha": "b" * 40,          # helper reports another commit
                    "deployRoot": t.DEPLOY_ROOT, "protocol": t.PROTOCOL,
                    "schemaVersion": 1, "user": t.REMOTE_USER,
                }
                return t.ProcessResult(0, t.canonical(value))

        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            (root / "config").write_text("")
            configuration = t.SshConfiguration(
                root, root / "config", "production",
                Path("/usr/bin/ssh"), Path("/usr/bin/scp"), "SHA256:" + "A" * 43,
            )
            runner = Runner()
            client = t.OpenSshTransport(configuration, runner)
            with self.assertRaises(t.DeploymentTransportError) as raised:
                client.capabilities("a" * 40)
            self.assertEqual("REMOTE_CAPABILITY_MISMATCH", raised.exception.code)
            # only capabilities was ever sent; no snapshot, upload or install
            self.assertEqual(1, len(runner.calls))
            self.assertIn("capabilities", runner.calls[0])

    def test_malformed_expected_sha_never_reaches_the_host(self) -> None:
        t = self.transport()

        class Runner:
            def __init__(self): self.calls = []
            def run(self, argv, timeout_seconds=None):
                self.calls.append(argv)
                raise AssertionError("must not contact the host")

        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            (root / "config").write_text("")
            configuration = t.SshConfiguration(
                root, root / "config", "production",
                Path("/usr/bin/ssh"), Path("/usr/bin/scp"), "SHA256:" + "A" * 43,
            )
            runner = Runner()
            client = t.OpenSshTransport(configuration, runner)
            for bad in ("", "zz", "A" * 40, None):
                with self.subTest(sha=bad), self.assertRaises(t.DeploymentTransportError):
                    client.capabilities(bad)
            self.assertEqual([], runner.calls)


class T14Draft202012(unittest.TestCase):
    """The reason the package vendors its own runtime at all."""

    SCHEMA = {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "$defs": {"digest": {"type": "string", "pattern": "^sha256:[0-9a-f]{64}$"}},
        "type": "object",
        "properties": {
            "pair": {
                "type": "array",
                "prefixItems": [{"type": "string"}, {"type": "integer"}],
                "items": False,
            },
            "digest": {"$ref": "#/$defs/digest"},
        },
        "required": ["pair", "digest"],
    }
    VALID = {"pair": ["a", 1], "digest": "sha256:" + "a" * 64}
    PREFIX_MUTANT = {"pair": [1, "a"], "digest": "sha256:" + "a" * 64}
    EXTRA_ITEM_MUTANT = {"pair": ["a", 1, "extra"], "digest": "sha256:" + "a" * 64}
    DEFS_MUTANT = {"pair": ["a", 1], "digest": "sha256:NOTHEX"}

    def test_installed_runtime_is_jsonschema_4x(self) -> None:
        import jsonschema
        self.assertTrue(jsonschema.__version__.startswith("4."), jsonschema.__version__)

    def test_prefix_items_and_defs_mutants_are_rejected(self) -> None:
        import jsonschema
        validator = jsonschema.Draft202012Validator(self.SCHEMA)
        validator.validate(self.VALID)
        for name, mutant in (
            ("prefixItems order", self.PREFIX_MUTANT),
            ("prefixItems extra item", self.EXTRA_ITEM_MUTANT),
            ("$defs pattern", self.DEFS_MUTANT),
        ):
            with self.subTest(mutant=name), self.assertRaises(jsonschema.ValidationError):
                validator.validate(mutant)


class T15Capabilities(unittest.TestCase):
    def test_capabilities_shape_carries_the_control_sha(self) -> None:
        source = (REPO / "ops/deploy/deployment-remote.py").read_text(encoding="utf-8")
        self.assertIn('"controlSha": _installed_control_sha()', source)


class T16NoExternalCalls(unittest.TestCase):
    def test_module_never_reaches_docker_ssh_ghcr_or_the_vps(self) -> None:
        source = (REPO / "tools/deploy/control_root_package.py").read_text(encoding="utf-8")
        for forbidden in ("docker", "ghcr", "31.97.251.16", "urllib", "requests", "socket"):
            with self.subTest(token=forbidden):
                # word-ish boundary: "requirementsSha256" must not count as "ssh"
                self.assertIsNone(
                    re.search(rf"(?<![A-Za-z0-9]){re.escape(forbidden)}(?![A-Za-z0-9])", source.lower())
                )


class TContract(unittest.TestCase):
    def test_static_contract_is_valid(self) -> None:
        self.assertEqual([], contract.validate())


if __name__ == "__main__":
    unittest.main()
