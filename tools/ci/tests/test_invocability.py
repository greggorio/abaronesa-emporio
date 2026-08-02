from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]


def load():
    path = ROOT / "tools/ci/invocability.py"
    spec = importlib.util.spec_from_file_location("ci_invocability", path)
    module = importlib.util.module_from_spec(spec)
    assert spec and spec.loader
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


invocability = load()


class InvocabilityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.ci = invocability.CI.read_text()
        cls.publish = invocability.PUBLISH.read_text()

    def test_01_all_26_commands_stop_at_the_cli_boundary(self):
        commands, inventory_errors = invocability.inventory(self.ci, self.publish)
        self.assertEqual([], inventory_errors)
        self.assertEqual(26, len(commands))
        self.assertEqual([], invocability.validate(self.ci, self.publish))
        self.assertEqual(
            {"tools/ci/migrations_contract.py", "tools/compose/validate_compose.py", "tools/gateway/validate_gateway.py"},
            {command.script for command in commands if invocability._argument_free(ROOT / command.script, command.argv)},
        )

    def test_02_six_prescribed_mutants_are_all_reported_together(self):
        ci = self.ci
        ci_mutations = (
            ("python3 tools/releases/release_control_contract.py validate", "python3 tools/releases/release_control_contract.py"),
            ("python3 tools/security/bootstrap_contract.py validate", "python3 tools/security/bootstrap_contract.py"),
            ("python3 tools/docker/java_images_contract.py validate", "python3 tools/docker/java_images_contract.py"),
            ("python3 tools/releases/catalog.py validate --require-release-ready", "python3 tools/releases/catalog.py validate --require-release-ready --unknown-s30a"),
            ("python3 tools/candidates/candidate_plan.py generate", "python3 tools/candidates/candidate_plan.py nonexistent"),
        )
        for old, new in ci_mutations:
            self.assertIn(old, ci)
            ci = ci.replace(old, new, 1)
        publish = self.publish
        required = " --event workflow-run.json"
        self.assertIn(required, publish)
        publish = publish.replace(required, "", 1)
        errors = invocability.validate(ci, publish)
        self.assertEqual(6, len(errors), "\n".join(errors))
        for marker in (
            "release_control_contract.py",
            "bootstrap_contract.py",
            "java_images_contract.py",
            "--unknown-s30a",
            "nonexistent",
            "trust.py",
        ):
            with self.subTest(marker=marker):
                self.assertTrue(any(marker in error for error in errors), "\n".join(errors))

    def test_03_unknown_expression_is_replaced_with_fixed_syntax(self):
        argv = invocability.synthesize(
            'python3 tools/candidates/outcome.py --status "${{ steps.mode }}" '
            '--sha "${{ github.sha }}" --run "${{ github.run_id }}"'
        )
        self.assertEqual("synthetic", argv[3])
        self.assertEqual("1" * 40, argv[5])
        self.assertEqual("1", argv[7])


if __name__ == "__main__":
    unittest.main()
