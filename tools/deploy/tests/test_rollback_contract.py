from __future__ import annotations

import copy
import importlib.util
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[3]
MODULE_PATH = ROOT / "tools/deploy/validate_rollback_contract.py"
SPEC = importlib.util.spec_from_file_location("validate_rollback_contract", MODULE_PATH)
validator = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = validator
SPEC.loader.exec_module(validator)


class RollbackContractTest(unittest.TestCase):
    def mutant(self) -> Path:
        target = Path(tempfile.mkdtemp(prefix="s25-rollback-contract-", dir="/tmp"))
        self.addCleanup(shutil.rmtree, target, True)
        for relative in (
            "docs/infrastructure/deployment/release-control/ROLLBACK_COMERCIAL.md",
            "docs/infrastructure/deployment/release-control/api/rollback.openapi.yml",
            "docs/infrastructure/deployment/release-control/contracts/rollback-state-machine.yml",
            "docs/infrastructure/deployment/release-control/contracts/rollback-security.yml",
            "docs/infrastructure/deployment/release-control/README.md",
            "docs/infrastructure/deployment/release-control/CONTRATO_API_ESTADOS_SEGURANCA.md",
            "docs/infrastructure/deployment/release-control/RUNTIME_DEPLOYER.md",
        ):
            destination = target / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / relative, destination)
        return target

    def assert_invalid(self, root: Path, code: str) -> None:
        with self.assertRaisesRegex(ValueError, code):
            validator.validate(root)

    def load(self, root: Path, relative: Path) -> tuple[Path, dict[str, object]]:
        path = root / relative
        value = yaml.safe_load(path.read_text(encoding="utf-8"))
        self.assertIsInstance(value, dict)
        return path, value

    def test_real_contract_is_valid(self) -> None:
        validator.validate(ROOT)

    def test_missing_artifact_fails(self) -> None:
        root = self.mutant()
        (root / validator.SECURITY).unlink()
        self.assert_invalid(root, "required-file")

    def test_request_extra_field_mutant_fails(self) -> None:
        root = self.mutant()
        path, value = self.load(root, validator.OPENAPI)
        value["components"]["schemas"]["RollbackRequest"]["required"].append("component")
        path.write_text(yaml.safe_dump(value, sort_keys=False), encoding="utf-8")
        self.assert_invalid(root, "openapi-request-fields")

    def test_reason_minimum_mutant_fails(self) -> None:
        root = self.mutant()
        path, value = self.load(root, validator.OPENAPI)
        value["components"]["schemas"]["RollbackRequest"]["properties"]["reason"]["minLength"] = 9
        path.write_text(yaml.safe_dump(value, sort_keys=False), encoding="utf-8")
        self.assert_invalid(root, "openapi-reason-min")

    def test_idempotency_uuid_version_mutant_fails(self) -> None:
        root = self.mutant()
        path, value = self.load(root, validator.OPENAPI)
        value["components"]["parameters"]["IdempotencyKey"]["schema"]["pattern"] = value["components"]["parameters"]["IdempotencyKey"]["schema"]["pattern"].replace("-4[0-9a-f]", "-[0-9a-f]")
        path.write_text(yaml.safe_dump(value, sort_keys=False), encoding="utf-8")
        self.assert_invalid(root, "openapi-idempotency-pattern")

    def test_operation_type_mutant_fails(self) -> None:
        root = self.mutant()
        path, value = self.load(root, validator.OPENAPI)
        value["components"]["schemas"]["RollbackOperation"]["properties"]["operationType"]["const"] = "deployment"
        path.write_text(yaml.safe_dump(value, sort_keys=False), encoding="utf-8")
        self.assert_invalid(root, "openapi-operation-kind")

    def test_state_uncertain_removed_mutant_fails(self) -> None:
        root = self.mutant()
        path, value = self.load(root, validator.STATES)
        del value["states"]["UNCERTAIN"]
        path.write_text(yaml.safe_dump(value, sort_keys=False), encoding="utf-8")
        self.assert_invalid(root, "states-vocabulary")

    def test_state_impossible_transition_mutant_fails(self) -> None:
        root = self.mutant()
        path, value = self.load(root, validator.STATES)
        value["transitions"].append({"from": "SUCCEEDED", "to": "FAILED", "actor": "reconciler"})
        path.write_text(yaml.safe_dump(value, sort_keys=False), encoding="utf-8")
        self.assert_invalid(root, "state-terminal-outgoing")

    def test_state_client_actor_mutant_fails(self) -> None:
        root = self.mutant()
        path, value = self.load(root, validator.STATES)
        value["transitions"][0]["actor"] = "client"
        path.write_text(yaml.safe_dump(value, sort_keys=False), encoding="utf-8")
        self.assert_invalid(root, "state-transition-actor")

    def test_state_transition_removed_mutant_fails(self) -> None:
        root = self.mutant()
        path, value = self.load(root, validator.STATES)
        value["transitions"] = [item for item in value["transitions"] if item["from"] != "VERIFYING" or item["to"] != "SUCCEEDED"]
        path.write_text(yaml.safe_dump(value, sort_keys=False), encoding="utf-8")
        self.assert_invalid(root, "state-transition-vocabulary")

    def test_security_target_skip_mutant_fails(self) -> None:
        root = self.mutant()
        path, value = self.load(root, validator.SECURITY)
        value["eligibility"]["target_release"]["skipped_release_allowed"] = True
        path.write_text(yaml.safe_dump(value, sort_keys=False), encoding="utf-8")
        self.assert_invalid(root, "security-target-rule:skipped_release_allowed")

    def test_security_current_capability_mutant_fails(self) -> None:
        root = self.mutant()
        path, value = self.load(root, validator.SECURITY)
        value["current_runtime"]["deployer_capabilities_exact"].append("deployment:rollback")
        path.write_text(yaml.safe_dump(value, sort_keys=False), encoding="utf-8")
        self.assert_invalid(root, "security-current-capabilities")

    def test_security_backup_path_mutant_fails(self) -> None:
        root = self.mutant()
        path, value = self.load(root, validator.SECURITY)
        value["backup"]["forbidden_fields"].remove("path")
        path.write_text(yaml.safe_dump(value, sort_keys=False), encoding="utf-8")
        self.assert_invalid(root, "security-backup-forbidden-fields")

    def test_security_backup_retention_mutant_fails(self) -> None:
        root = self.mutant()
        path, value = self.load(root, validator.SECURITY)
        value["backup"]["retention_minimum_days"] = 364
        path.write_text(yaml.safe_dump(value, sort_keys=False), encoding="utf-8")
        self.assert_invalid(root, "security-backup-retention")

    def test_security_upload_restore_mutant_fails(self) -> None:
        root = self.mutant()
        path, value = self.load(root, validator.SECURITY)
        value["data_and_sessions"]["uploads"]["restored_implicitly"] = True
        path.write_text(yaml.safe_dump(value, sort_keys=False), encoding="utf-8")
        self.assert_invalid(root, "security-uploads-restore")

    def test_security_reason_bounds_mutant_fails(self) -> None:
        root = self.mutant()
        path, value = self.load(root, validator.SECURITY)
        value["api"]["reason_max_length"] = 1001
        path.write_text(yaml.safe_dump(value, sort_keys=False), encoding="utf-8")
        self.assert_invalid(root, "security-reason-max")

    def test_security_automatic_retry_mutant_fails(self) -> None:
        root = self.mutant()
        path, value = self.load(root, validator.SECURITY)
        value["api"]["idempotency"]["automatic_retry_after_network_or_invalid_response"] = True
        path.write_text(yaml.safe_dump(value, sort_keys=False), encoding="utf-8")
        self.assert_invalid(root, "security-idempotency-retry")

    def test_documentation_compensation_mutant_fails(self) -> None:
        root = self.mutant()
        path = root / validator.DOC
        text = path.read_text(encoding="utf-8").replace("Compensação forward", "Rollback automático")
        path.write_text(text, encoding="utf-8")
        self.assert_invalid(root, "documentation:compensação forward")

    def test_documentation_activation_mutant_fails(self) -> None:
        root = self.mutant()
        path = root / validator.DOC
        text = path.read_text(encoding="utf-8").replace("não habilita a rota futura", "habilita a rota futura")
        path.write_text(text, encoding="utf-8")
        self.assert_invalid(root, "documentation:não habilita")

    def test_readme_reference_mutant_fails(self) -> None:
        root = self.mutant()
        path = root / validator.README
        text = path.read_text(encoding="utf-8").replace("ROLLBACK_COMERCIAL.md", "ROLLBACK_REMOVIDO.md")
        path.write_text(text, encoding="utf-8")
        self.assert_invalid(root, "readme-reference:ROLLBACK_COMERCIAL.md")

    def test_current_forward_only_marker_mutant_fails(self) -> None:
        root = self.mutant()
        path = root / validator.RUNTIME
        text = path.read_text(encoding="utf-8")
        text = text.replace("deployment:rollback nao aparece em", "deployment:rollback aparece em")
        text = text.replace("não anuncia `deployment:rollback`", "anuncia `deployment:rollback`")
        path.write_text(text, encoding="utf-8")
        self.assert_invalid(root, "active-runtime-capability")

    def test_validator_does_not_accept_copy_with_active_capability(self) -> None:
        root = self.mutant()
        path, value = self.load(root, validator.SECURITY)
        value["authorization"]["capability_enabled_by_this_contract"] = True
        path.write_text(yaml.safe_dump(value, sort_keys=False), encoding="utf-8")
        self.assert_invalid(root, "security-authorization:capability_enabled_by_this_contract")


if __name__ == "__main__":
    unittest.main()
