from __future__ import annotations

import os
import shutil
import tempfile
import unittest
from pathlib import Path

from tools.deploy import deployment_executor as executor


class TrustedRootTest(unittest.TestCase):
    """The production layout installs the control root at
    <deploy_root>/shared/control, so the journal directory at
    <deploy_root>/shared/deploy/journals is a *sibling* of the tree this module
    infers from its own location. The inferred workspace therefore refused it and
    the commercial deploy could never create a journal, while the rehearsal
    passed only because its ephemeral root sits inside the checkout. A trusted
    root supplied by a caller that already validated it closes that gap without
    relaxing anything else."""

    def setUp(self) -> None:
        # Neither under the checkout nor under /tmp: both are allowed outright,
        # so either would hide the very condition under test.
        base = "/var/tmp"
        if not Path(base).is_dir():  # pragma: no cover - present on Linux
            self.skipTest("no directory outside the allowed trees")
        self.deploy_root = Path(tempfile.mkdtemp(prefix="s46-trusted-", dir=base))
        self.addCleanup(shutil.rmtree, self.deploy_root, True)
        self.deploy_root.chmod(0o700)
        self.journals = self.deploy_root / "shared/deploy/journals"
        self.journals.mkdir(mode=0o700, parents=True)
        for parent in (self.deploy_root / "shared", self.journals.parent):
            parent.chmod(0o700)

    def test_production_layout_is_refused_without_a_trusted_root(self) -> None:
        with self.assertRaises(executor.DeploymentExecutionError) as caught:
            executor._safe_directory(self.journals)
        self.assertEqual("UNSAFE_PATH", caught.exception.code)

    def test_production_layout_is_accepted_under_its_trusted_root(self) -> None:
        self.assertEqual(
            self.journals, executor._safe_directory(self.journals, self.deploy_root)
        )
        journal, state = executor._validate_paths(
            self.journals,
            self.journals.parent / "installed-state.json",
            self.deploy_root,
        )
        self.assertEqual(self.journals, journal)
        self.assertEqual(self.journals.parent / "installed-state.json", state)

    def test_a_trusted_root_relaxes_nothing_else(self) -> None:
        with self.subTest("the trusted root itself is never a journal directory"):
            with self.assertRaises(executor.DeploymentExecutionError):
                executor._safe_directory(self.deploy_root, self.deploy_root)

        with self.subTest("mode must still be exactly 0700"):
            loose = self.deploy_root / "shared/deploy/loose"
            loose.mkdir(mode=0o700)
            loose.chmod(0o750)
            with self.assertRaises(executor.DeploymentExecutionError):
                executor._safe_directory(loose, self.deploy_root)

        with self.subTest("symlinked components are still refused"):
            link = self.deploy_root / "shared/deploy/linked"
            link.symlink_to(self.journals, target_is_directory=True)
            with self.assertRaises(executor.DeploymentExecutionError):
                executor._safe_directory(link, self.deploy_root)

        with self.subTest("an unrelated trusted root does not admit the path"):
            other = Path(tempfile.mkdtemp(prefix="s46-other-", dir="/var/tmp"))
            self.addCleanup(shutil.rmtree, other, True)
            with self.assertRaises(executor.DeploymentExecutionError):
                executor._safe_directory(self.journals, other)

        with self.subTest("root and home roots stay refused"):
            for forbidden in (Path("/"), Path.home(), Path("/tmp")):
                with self.assertRaises(executor.DeploymentExecutionError):
                    executor._safe_directory(forbidden, Path("/"))

    def test_absent_trusted_root_preserves_the_previous_contract(self) -> None:
        inside = Path(tempfile.mkdtemp(prefix="s46-inside-", dir=executor.ROOT))
        self.addCleanup(shutil.rmtree, inside, True)
        inside.chmod(0o700)
        self.assertEqual(inside.resolve(), executor._safe_directory(inside))
        self.assertEqual(os.fspath(inside.resolve()), os.fspath(inside.resolve()))


if __name__ == "__main__":
    unittest.main()
