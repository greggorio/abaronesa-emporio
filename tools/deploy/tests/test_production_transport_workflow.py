from __future__ import annotations

import shutil
import tempfile
import unittest
from pathlib import Path

from tools.deploy.validate_production_transport_workflow import validate

ROOT = Path(__file__).resolve().parents[3]


class ProductionTransportWorkflowTest(unittest.TestCase):
    def package_copy(self) -> tuple[tempfile.TemporaryDirectory[str], Path]:
        temporary = tempfile.TemporaryDirectory()
        root = Path(temporary.name)
        for relative in (
            ".github/workflows/verify-production-transport.yml",
            "tools/deploy/production_transport_probe.py",
        ):
            destination = root / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / relative, destination)
        return temporary, root

    def test_baseline_is_valid(self) -> None:
        self.assertEqual(validate(ROOT), [])

    def test_workflow_mutants_are_rejected(self) -> None:
        mutants = (
            ("workflow", "workflow_dispatch:\n", "workflow_dispatch:\n    inputs:\n      command:\n"),
            ("workflow", "contents: read", "packages: write"),
            ("workflow", "environment: production", "environment: staging"),
            ("workflow", "group: emporio-production", "group: other"),
            ("runtime", "StrictHostKeyChecking", "WeakHostKeyChecking"),
            ("workflow", "if: always()", "if: success()"),
            ("workflow", "name: production-transport-probe", "name: other-probe"),
            (
                "runtime",
                'REMOTE_COMMAND = (REMOTE_HELPER, "capabilities")',
                'REMOTE_COMMAND = (REMOTE_HELPER, "snapshot")',
            ),
            ("runtime", "IdentitiesOnly yes", "IdentitiesOnly no"),
            ("runtime", "IdentityAgent none", "IdentityAgent SSH_AUTH_SOCK"),
            ("runtime", "shred", "unlink-only"),
        )
        for target, old, new in mutants:
            with self.subTest(old=old):
                temporary, root = self.package_copy()
                self.addCleanup(temporary.cleanup)
                relative = (
                    ".github/workflows/verify-production-transport.yml"
                    if target == "workflow"
                    else "tools/deploy/production_transport_probe.py"
                )
                path = root / relative
                source = path.read_text(encoding="utf-8")
                self.assertIn(old, source)
                path.write_text(source.replace(old, new, 1), encoding="utf-8")
                self.assertTrue(validate(root), f"accepted mutant: {old}")


if __name__ == "__main__":
    unittest.main()
