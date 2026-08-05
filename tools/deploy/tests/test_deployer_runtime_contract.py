from __future__ import annotations

import importlib.util
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
MODULE_PATH = ROOT / "tools/deploy/validate_deployer_runtime.py"
SPEC = importlib.util.spec_from_file_location("validate_deployer_runtime", MODULE_PATH)
validator = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = validator
SPEC.loader.exec_module(validator)


class DeployerRuntimeContractTest(unittest.TestCase):
    def mutant(self) -> Path:
        target = Path(tempfile.mkdtemp(prefix="s22-runtime-"))
        self.addCleanup(shutil.rmtree, target, True)
        for directory in ("release_control", "docs/infrastructure"):
            shutil.copytree(ROOT / directory, target / directory)
        return target

    def replace(self, root: Path, relative: str, old: str, new: str) -> None:
        path = root / relative
        text = path.read_text(encoding="utf-8")
        self.assertIn(old, text)
        path.write_text(text.replace(old, new, 1), encoding="utf-8")

    def assert_mutant(self, root: Path, code: str) -> None:
        with self.assertRaisesRegex(ValueError, code):
            validator.validate(root)

    def test_real_runtime_is_valid(self) -> None:
        validator.validate(ROOT)

    def test_missing_required_file_fails(self) -> None:
        root = self.mutant()
        (
            root / "release_control/src/emporio_release_control/deployer_service.py"
        ).unlink()
        self.assert_mutant(root, "required-file")

    def test_mixed_router_fails(self) -> None:
        root = self.mutant()
        path = root / "release_control/src/emporio_release_control/deployer_api.py"
        path.write_text(
            path.read_text()
            + '\nPUBLISHER_ROUTE = "/api/release-publisher/v1/releases"\n'
        )
        self.assert_mutant(root, "mixed-router")

    def test_changed_workflow_identity_fails(self) -> None:
        root = self.mutant()
        self.replace(
            root,
            "release_control/src/emporio_release_control/constants.py",
            'DEPLOYER_WORKFLOW = "deploy-production.yml"',
            'DEPLOYER_WORKFLOW = "other.yml"',
        )
        self.assert_mutant(root, "identity")

    def test_changed_repository_identity_fails(self) -> None:
        root = self.mutant()
        self.replace(
            root,
            "release_control/src/emporio_release_control/constants.py",
            'REPOSITORY = "greggorio/abaronesa-emporio"',
            'REPOSITORY = "attacker/repository"',
        )
        self.assert_mutant(root, "identity")

    def test_rollback_capability_fails(self) -> None:
        root = self.mutant()
        self.replace(
            root,
            "release_control/src/emporio_release_control/deployer_api.py",
            '                "deployment:rollback",\n',
            '                "deployment:removed",\n',
        )
        self.assert_mutant(root, "rollback-capability")

    def test_removed_idempotency_fails(self) -> None:
        root = self.mutant()
        path = root / "release_control/src/emporio_release_control/deployer_service.py"
        path.write_text(path.read_text().replace("idempotency", "replay_key"))
        self.assert_mutant(root, "idempotency")

    def test_removed_active_slot_fails(self) -> None:
        root = self.mutant()
        path = root / "release_control/src/emporio_release_control/deployer_service.py"
        path.write_text(path.read_text().replace("active_slot", "slot_removed"))
        self.assert_mutant(root, "production-slot")

    def test_release_jump_guard_removed_fails(self) -> None:
        root = self.mutant()
        path = root / "release_control/src/emporio_release_control/deployer_service.py"
        path.write_text(
            path.read_text().replace("previousRelease", "predecessor_removed")
        )
        self.assert_mutant(root, "release-eligibility")

    def test_intermediate_state_added_fails(self) -> None:
        root = self.mutant()
        self.replace(
            root,
            "release_control/src/emporio_release_control/constants.py",
            '        "PRECHECKING",\n',
            '        "PULLING",\n',
        )
        self.assert_mutant(root, "runtime-states")

    def test_artifact_binding_removed_fails(self) -> None:
        root = self.mutant()
        self.replace(
            root,
            "release_control/src/emporio_release_control/deployment_artifacts.py",
            '"controlSha": control_sha',
            '"controlShaRemoved": control_sha',
        )
        self.assert_mutant(root, "artifact-binding")

    def test_current_evidence_guard_removed_fails(self) -> None:
        root = self.mutant()
        path = root / "release_control/src/emporio_release_control/deployer_service.py"
        path.write_text(
            path.read_text().replace(
                "CURRENT_INSTALLATION_UNRECONCILED",
                "CURRENT_INSTALLATION_ASSUMED",
            )
        )
        self.assert_mutant(root, "current-evidence")

    def test_extra_runtime_route_fails(self) -> None:
        root = self.mutant()
        path = root / "release_control/src/emporio_release_control/deployer_api.py"
        path.write_text(
            path.read_text()
            + '\n@app.get("/api/deployment-control/v1/admin")\ndef extra():\n    return {}\n',
            encoding="utf-8",
        )
        self.assert_mutant(root, "runtime-routes")

    def test_forbidden_dependency_fails(self) -> None:
        root = self.mutant()
        path = root / "release_control/pyproject.toml"
        text = path.read_text(encoding="utf-8")
        path.write_text(
            text.replace("dependencies = [", 'dependencies = [\n  "paramiko",', 1)
        )
        self.assert_mutant(root, "forbidden-dependency")

    def test_indeterminate_reconciliation_guard_removed_fails(self) -> None:
        root = self.mutant()
        path = (
            root
            / "release_control/src/emporio_release_control/deployer_reconciliation.py"
        )
        path.write_text(
            path.read_text().replace("mark_uncertain", "terminalize_uncertain")
        )
        self.assert_mutant(root, "reconciliation-contract")

    def test_uncertain_listing_guard_removed_fails(self) -> None:
        root = self.mutant()
        self.replace(
            root,
            "release_control/src/emporio_release_control/deployer_service.py",
            "current, consistent = self._current_evidence(session)",
            "current = self._current_or_clean(session)\n            consistent = True",
        )
        self.assert_mutant(root, "uncertain-release-listing")

    def test_restore_conflict_guard_removed_fails(self) -> None:
        root = self.mutant()
        self.replace(
            root,
            "release_control/src/emporio_release_control/deployer_service.py",
            'RuntimeFailure("DEPLOYMENT_OUTCOME_RESTORE_CONFLICT")',
            'RuntimeFailure("DEPLOYMENT_OUTCOME_INVALID")',
        )
        self.assert_mutant(root, "restore-conflict")

    def test_integrity_race_active_id_guard_removed_fails(self) -> None:
        root = self.mutant()
        self.replace(
            root,
            "release_control/src/emporio_release_control/deployer_service.py",
            "raise ActiveOperationFailure(active)",
            'raise RuntimeFailure("PRODUCTION_OPERATION_ACTIVE", 409, "Conflict")',
        )
        self.assert_mutant(root, "integrity-race")

    def test_workflow_binding_state_constraint_weakened_fails(self) -> None:
        root = self.mutant()
        self.replace(
            root,
            "release_control/migrations/versions/0002_deployer_runtime.py",
            "dispatch_state IN ('NOT_SENT','SENT','UNCERTAIN')",
            "dispatch_state IN ('NOT_SENT','SENT','UNCERTAIN','CONFIRMED')",
        )
        self.assert_mutant(root, "workflow-binding-state")

    def test_deployer_problem_enum_divergence_fails(self) -> None:
        root = self.mutant()
        self.replace(
            root,
            "release_control/src/emporio_release_control/deployer_schemas.py",
            '    "SERVICE_UNAVAILABLE",\n',
            "",
        )
        self.assert_mutant(root, "deployer-problem-codes")

    def test_remote_uncertainty_classification_removed_fails(self) -> None:
        root = self.mutant()
        path = (
            root
            / "release_control/src/emporio_release_control/deployer_reconciliation.py"
        )
        path.write_text(
            path.read_text(encoding="utf-8").replace(
                "lineage_failures", "ignored_lineage_failures"
            ),
            encoding="utf-8",
        )
        self.assert_mutant(root, "uncertain-remote-evidence")

    def test_lock_release_guard_removed_fails(self) -> None:
        root = self.mutant()
        self.replace(
            root,
            "release_control/src/emporio_release_control/deployer_reconciliation.py",
            "release_deployer_advisory_lock(session)",
            "session.close()",
        )
        self.assert_mutant(root, "resilient-reconciliation-cycle")

    def test_prospective_empty_current_recovery_removed_fails(self) -> None:
        root = self.mutant()
        self.replace(
            root,
            "release_control/src/emporio_release_control/deployer_service.py",
            "self._recover_empty_current_locked(\n                        session, operation, outcome_digest, trace_id\n                    )",
            "False",
        )
        self.assert_mutant(root, "prospective-empty-current-recovery")

    def test_historical_empty_current_artifact_validation_removed_fails(self) -> None:
        root = self.mutant()
        self.replace(
            root,
            "release_control/src/emporio_release_control/deployer_reconciliation.py",
            "validate_deployment_outcome(\n                raw_outcome,",
            "accept_unvalidated_outcome(\n                raw_outcome,",
        )
        self.assert_mutant(root, "historical-empty-current-recovery")


if __name__ == "__main__":
    unittest.main()
