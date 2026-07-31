from __future__ import annotations

import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "release_control/src"))

from emporio_release_control.constants import ROLLBACK_STATES, ROLLBACK_TRANSITIONS  # noqa: E402
from emporio_release_control.deployer_schemas import RollbackRequest  # noqa: E402
from emporio_release_control.deployer_service import DeployerService  # noqa: E402


class RollbackRuntimeContractTest(unittest.TestCase):
    def test_validator_passes(self) -> None:
        result = subprocess.run(
            [sys.executable, "tools/deploy/validate_rollback_runtime.py"],
            cwd=ROOT,
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(result.stdout.strip(), "rollback-runtime:valid")

    def test_request_is_closed_and_canonical(self) -> None:
        request = RollbackRequest.model_validate(
            {"release": "v1.2.3", "reason": "operator approved recovery"}
        )
        self.assertEqual(
            request.canonical_dict(),
            {"release": "v1.2.3", "reason": "operator approved recovery"},
        )
        with self.assertRaises(ValueError):
            RollbackRequest.model_validate(
                {"release": "v1.2.3", "reason": "long enough reason", "component": "erp"}
            )

    def test_migration_without_integral_proof_requires_restore(self) -> None:
        target = {"databases": [{"id": "erp", "migrations": []}, {"id": "website", "migrations": []}]}
        current = {
            "databases": [
                {"id": "erp", "migrations": [{"version": "1", "path": "erp/V1.sql"}]},
                {"id": "website", "migrations": [{"version": "1", "path": "website/V1.sql"}]},
            ]
        }
        self.assertEqual(DeployerService._rollback_migration_delta(target, current), (True, True))

    def test_reversible_migration_can_avoid_restore(self) -> None:
        target = {"databases": [{"id": "erp", "migrations": []}, {"id": "website", "migrations": []}]}
        current = {
            "databases": [
                {"id": "erp", "migrations": [{"version": "1", "path": "erp/V1.sql", "reversible": True, "rollbackProof": "erp-proof"}]},
                {"id": "website", "migrations": [{"version": "1", "path": "website/V1.sql", "reversible": True, "rollbackProof": "website-proof"}]},
            ]
        }
        self.assertEqual(DeployerService._rollback_migration_delta(target, current), (True, False))

    def test_divergent_migration_chain_fails_closed(self) -> None:
        target = {"databases": [{"id": "erp", "migrations": [{"version": "2"}]}, {"id": "website", "migrations": []}]}
        current = {"databases": [{"id": "erp", "migrations": [{"version": "1"}]}, {"id": "website", "migrations": []}]}
        self.assertEqual(DeployerService._rollback_migration_delta(target, current), (False, True))

    def test_protocol_command_is_closed_and_bound(self) -> None:
        sys.path.insert(0, str(ROOT / "ops/deploy"))
        from rollback_protocol import command, validate

        value = command("rbk_" + "a" * 32, "SWITCHING", "v1.2.3", "sha256:" + "b" * 64)
        self.assertEqual(validate(value), value)
        mutated = dict(value)
        mutated["schemaVersion"] = 2
        with self.assertRaises(ValueError):
            validate(mutated)

    def test_protocol_rejects_path_and_command_fields(self) -> None:
        sys.path.insert(0, str(ROOT / "ops/deploy"))
        from rollback_protocol import validate

        with self.assertRaises(ValueError):
            validate({"protocol": "emporio-commercial-rollback-transport", "schemaVersion": 1, "path": "/tmp"})

    def test_state_contract_has_no_terminal_outgoing_edges(self) -> None:
        terminal = {"SUCCEEDED", "ROLLED_BACK", "FAILED", "UNCERTAIN"}
        self.assertEqual(set(ROLLBACK_STATES), {
            "QUEUED", "PRECHECKING", "RESTORING", "SWITCHING", "VERIFYING",
            "SUCCEEDED", "ROLLING_BACK", "ROLLED_BACK", "FAILED", "UNCERTAIN",
        })
        self.assertTrue(all(source not in terminal for source, _ in ROLLBACK_TRANSITIONS))

    def test_backup_evidence_is_closed_and_retained(self) -> None:
        from datetime import UTC, datetime, timedelta
        from emporio_release_control.persistence import RollbackBackup

        now = datetime(2026, 7, 31, tzinfo=UTC)
        backup = RollbackBackup(
            backup_id="backup-1",
            source_release="v1.2.4",
            source_state_sha256="sha256:" + "a" * 64,
            databases=["erp", "website"],
            artifact_sha256="sha256:" + "b" * 64,
            created_at=now,
            expires_at=now + timedelta(days=365),
            verified=True,
            evidence_json={
                "backupId": "backup-1",
                "sourceRelease": "v1.2.4",
                "sourceStateSha256": "sha256:" + "a" * 64,
                "artifactSha256": "sha256:" + "b" * 64,
                "databases": ["erp", "website"],
            },
        )
        self.assertTrue(DeployerService._backup_is_canonical(backup))
        backup.evidence_json["path"] = "/forbidden"
        self.assertFalse(DeployerService._backup_is_canonical(backup))


if __name__ == "__main__":
    unittest.main()
