"""Causal tests for the S29 workflow inventory gate."""

from __future__ import annotations

import shutil
import tempfile
import unittest
from pathlib import Path

from tools.ci.validate_workflow_inventory import EXPECTED_WORKFLOWS, validate


ROOT = Path(__file__).resolve().parents[3]


class WorkflowInventoryContractTest(unittest.TestCase):
    def package_copy(self) -> tuple[tempfile.TemporaryDirectory[str], Path]:
        temporary = tempfile.TemporaryDirectory()
        root = Path(temporary.name)
        for relative in (
            ".github/workflows/README.md",
            ".gitignore",
            "tools/releases/validate_release_workflow.py",
        ):
            destination = root / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / relative, destination)
        workflow_dir = root / ".github/workflows"
        for name in EXPECTED_WORKFLOWS:
            shutil.copy2(ROOT / ".github/workflows" / name, workflow_dir / name)
        return temporary, root

    def assert_mutant_rejected(self, relative: str, mutate) -> None:
        temporary, root = self.package_copy()
        self.addCleanup(temporary.cleanup)
        path = root / relative
        path.write_text(mutate(path.read_text(encoding="utf-8")), encoding="utf-8")
        self.assertTrue(validate(root), msg=f"mutant accepted: {relative}")

    def test_baseline_is_valid(self) -> None:
        self.assertEqual(validate(ROOT), [])

    def test_mutant_01_extra_workflow_is_rejected(self) -> None:
        temporary, root = self.package_copy()
        self.addCleanup(temporary.cleanup)
        (root / ".github/workflows/extra.yml").write_text("name: extra\n", encoding="utf-8")
        self.assertTrue(validate(root))

    def test_mutant_02_action_tag_is_rejected(self) -> None:
        self.assert_mutant_rejected(
            ".github/workflows/ci.yml",
            lambda text: text.replace(
                "actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd",
                "actions/checkout@v6",
                1,
            ),
        )

    def test_mutant_03_rollback_push_trigger_is_rejected(self) -> None:
        self.assert_mutant_rejected(
            ".github/workflows/rollback-production.yml",
            lambda text: text.replace("on:\n  workflow_dispatch:", "on:\n  push:\n  workflow_dispatch:", 1),
        )

    def test_mutant_04_rollback_write_permission_is_rejected(self) -> None:
        self.assert_mutant_rejected(
            ".github/workflows/rollback-production.yml",
            lambda text: text.replace("contents: read", "contents: write", 1),
        )

    def test_mutant_05_stale_workflow_count_readme_is_rejected(self) -> None:
        for stale in ("quatro", "cinco"):
            with self.subTest(stale=stale):
                self.assert_mutant_rejected(
                    ".github/workflows/README.md",
                    lambda text, stale=stale: text.replace(
                        "Existem exatamente seis workflows",
                        f"Existem exatamente {stale} workflows",
                        1,
                    ),
                )

    def test_mutant_05b_readme_missing_the_release_control_workflow_is_rejected(self) -> None:
        self.assert_mutant_rejected(
            ".github/workflows/README.md",
            lambda text: text.replace("`publish-release-control.yml`", "`other.yml`"),
        )

    def test_mutant_06_release_expected_set_is_rejected(self) -> None:
        self.assert_mutant_rejected(
            "tools/releases/validate_release_workflow.py",
            lambda text: text.replace('    "rollback-production.yml",\n', "", 1),
        )

    def test_mutant_07_rollback_external_effect_is_rejected(self) -> None:
        self.assert_mutant_rejected(
            ".github/workflows/rollback-production.yml",
            lambda text: text + "\n          docker run forbidden\n",
        )

    def test_mutant_08_remote_execution_claim_is_rejected(self) -> None:
        self.assert_mutant_rejected(
            ".github/workflows/README.md",
            lambda text: text.replace(
                "ainda não foram executados no GitHub",
                "foram executados no GitHub",
                1,
            ),
        )


if __name__ == "__main__":
    unittest.main()
