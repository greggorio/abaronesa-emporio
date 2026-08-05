from __future__ import annotations

import os
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from tools.deploy import deployment_engine_rehearsal as rehearsal
from tools.deploy.validate_deployment_engine_workflow import validate

ROOT = Path(__file__).resolve().parents[3]
SHA = "a" * 40


def environment() -> dict[str, str]:
    return {
        "TRUSTED_REPOSITORY": rehearsal.REPOSITORY,
        "TRUSTED_WORKFLOW_REF": f"{rehearsal.REPOSITORY}/{rehearsal.WORKFLOW}@{rehearsal.REF}",
        "TRUSTED_EVENT": "workflow_dispatch",
        "TRUSTED_REF": rehearsal.REF,
        "TRUSTED_SHA": SHA,
        "TRUSTED_RUN_ID": "123",
        "TRUSTED_RUN_ATTEMPT": "1",
        "TRUSTED_ACTOR": "emporio-deployer[bot]",
        "TRUSTED_ACTOR_ID": "313092947",
        "TRUSTED_TRIGGERING_ACTOR": "emporio-deployer[bot]",
        "TRUSTED_SENDER_ID": "313092947",
        "DEPLOYER_ACTOR_IDS": "313092947",
        "TRUST_RESULT": "success",
        "REHEARSAL_RESULT": "success",
    }


class DeploymentEngineWorkflowTest(unittest.TestCase):
    def package_copy(self) -> tuple[tempfile.TemporaryDirectory[str], Path]:
        temporary = tempfile.TemporaryDirectory()
        root = Path(temporary.name)
        for relative in (
            ".github/workflows/verify-deployment-engine.yml",
            "tools/deploy/deployment_engine_rehearsal.py",
        ):
            target = root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / relative, target)
        return temporary, root

    def test_baseline_is_valid(self) -> None:
        self.assertEqual([], validate(ROOT))

    def test_workflow_and_runtime_mutants_fail_closed(self) -> None:
        mutants = (
            ("workflow", "workflow_dispatch:\n", "workflow_dispatch:\n    inputs:\n      command:\n"),
            ("workflow", "packages: read", "packages: write"),
            ("workflow", "gh release download v0.1.1", "gh release download v0.1.2"),
            ("workflow", "id: rehearsal\n        continue-on-error: true", "id: rehearsal\n        continue-on-error: false"),
            ("runtime", '"down", "-v", "--remove-orphans"', '"down"'),
            ("runtime", '"image", "rm"', '"system", "prune"'),
            ("runtime", "deployment_plan.generate_bundle(", "fake_bundle("),
            ("runtime", "deployment_cli.py", "fake_cli.py"),
            ("runtime", "shutil.rmtree(root)", "pass # leaked root"),
        )
        for target_name, old, new in mutants:
            with self.subTest(old=old):
                temporary, root = self.package_copy()
                self.addCleanup(temporary.cleanup)
                relative = ".github/workflows/verify-deployment-engine.yml" if target_name == "workflow" else "tools/deploy/deployment_engine_rehearsal.py"
                path = root / relative
                source = path.read_text(encoding="utf-8")
                self.assertIn(old, source)
                path.write_text(source.replace(old, new, 1), encoding="utf-8")
                self.assertTrue(validate(root), old)

    def test_canonical_artifacts_bind_success_and_cleanup(self) -> None:
        with tempfile.TemporaryDirectory() as raw, mock.patch.dict(os.environ, environment(), clear=True):
            self.assertTrue(Path(raw).is_dir())
            value = {
                "schemaVersion": 1,
                "kind": "deployment-engine-rehearsal",
                **rehearsal.binding(),
                "release": rehearsal.RELEASE,
                "releaseId": rehearsal.RELEASE_ID,
                "previousRelease": rehearsal.PREVIOUS_RELEASE,
                "operationId": rehearsal.OPERATION,
                "status": "SUCCESS",
                "errorCode": None,
                "journalSha256": "sha256:" + "a" * 64,
                "installedStateSha256": "sha256:" + "b" * 64,
                "steps": [{"name": name, "status": "SUCCEEDED"} for name in rehearsal.EXPECTED_STEPS],
                "backup": [{"id": "erp", "size": 1, "sha256": "sha256:" + "c" * 64}, {"id": "website", "size": 1, "sha256": "sha256:" + "d" * 64}],
                "services": [{"id": str(index), "immutableRef": "sha256:" + str(index) * 64} for index in range(1, 8)],
                "current": rehearsal.RELEASE,
                "previous": None,
                "replay": {"journalUnchanged": True, "backupUnchanged": True, "containersUnchanged": True},
                "cleanup": {"containers": 0, "volumes": 0, "networks": 0, "images": 0},
            }
            rehearsal._validate_rehearsal(value, success=True)
            value["cleanup"]["volumes"] = 1
            with self.assertRaises(rehearsal.RehearsalError):
                rehearsal._validate_rehearsal(value, success=True)


if __name__ == "__main__":
    unittest.main()
