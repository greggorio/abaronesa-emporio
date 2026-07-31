from __future__ import annotations

import importlib.util
import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[3]
MODULE_PATH = ROOT / "tools/deploy/validate_deployment_plan.py"
SPEC = importlib.util.spec_from_file_location("validate_deployment_plan", MODULE_PATH)
validator = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = validator
SPEC.loader.exec_module(validator)


class DeploymentPlanContractTest(unittest.TestCase):
    def mutant(self) -> Path:
        target = Path(tempfile.mkdtemp(prefix="s18-contract-"))
        self.addCleanup(shutil.rmtree, target, True)
        for directory in ("tools/deploy", "ops/deploy"):
            shutil.copytree(ROOT / directory, target / directory)
        for path in (
            "ops/releases/components.yml",
            "ops/compose/compose.prod.yml",
            "docs/infrastructure/deployment/release-control/api/deployer.openapi.yml",
            "docs/infrastructure/deployment/release-control/PLANO_IMPLANTACAO.md",
        ):
            destination = target / path
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / path, destination)
        return target

    def assert_invalid(self, root: Path, code: str) -> None:
        with self.assertRaisesRegex(ValueError, code):
            validator.validate(root)

    def test_real_versioned_contract_is_valid(self) -> None:
        validator.validate(ROOT)

    def test_missing_versioned_file_fails(self) -> None:
        root = self.mutant()
        (root / "ops/deploy/schemas/deployment-plan.schema.json").unlink()
        self.assert_invalid(root, "required-file")

    def test_schema_open_object_mutant_fails(self) -> None:
        root = self.mutant()
        path = root / "ops/deploy/schemas/deployment-plan.schema.json"
        value = json.loads(path.read_text())
        value["additionalProperties"] = True
        path.write_text(json.dumps(value), encoding="utf-8")
        self.assert_invalid(root, "schema-open-object")

    def test_installed_component_order_mutant_fails(self) -> None:
        root = self.mutant()
        path = root / "ops/deploy/examples/installed-state.example.json"
        value = json.loads(path.read_text())
        value["components"][0], value["components"][1] = (
            value["components"][1],
            value["components"][0],
        )
        path.write_text(json.dumps(value), encoding="utf-8")
        self.assert_invalid(root, "installed-example-schema")

    def test_plan_service_mapping_mutant_fails(self) -> None:
        root = self.mutant()
        path = root / "ops/deploy/examples/deployment-plan.example.json"
        value = json.loads(path.read_text())
        value["components"][0]["service"] = "wrong_backend"
        path.write_text(json.dumps(value), encoding="utf-8")
        self.assert_invalid(root, "plan-example-schema")

    def test_compose_image_variable_mutant_fails(self) -> None:
        root = self.mutant()
        path = root / "ops/compose/compose.prod.yml"
        text = path.read_text()
        path.write_text(
            text.replace(
                "${BACKEND_IMAGE:?BACKEND_IMAGE is required}",
                "${WRONG_IMAGE:?WRONG_IMAGE is required}",
            )
        )
        self.assert_invalid(root, "compose-image-variable:backend")

    def test_openapi_source_release_without_null_fails(self) -> None:
        root = self.mutant()
        path = (
            root
            / "docs/infrastructure/deployment/release-control/api/deployer.openapi.yml"
        )
        value = yaml.safe_load(path.read_text())
        value["components"]["schemas"]["DeploymentPlan"]["properties"][
            "sourceRelease"
        ] = {"$ref": "#/components/schemas/ReleaseId"}
        path.write_text(yaml.safe_dump(value, sort_keys=False))
        self.assert_invalid(root, "openapi-source-release")

    def test_openapi_source_release_without_ref_fails(self) -> None:
        root = self.mutant()
        path = (
            root
            / "docs/infrastructure/deployment/release-control/api/deployer.openapi.yml"
        )
        value = yaml.safe_load(path.read_text())
        value["components"]["schemas"]["DeploymentPlan"]["properties"][
            "sourceRelease"
        ] = {"oneOf": [{"type": "string"}, {"type": "null"}]}
        path.write_text(yaml.safe_dump(value, sort_keys=False))
        self.assert_invalid(root, "openapi-source-release")

    def test_planner_cli_surface_mutant_fails(self) -> None:
        root = self.mutant()
        path = root / "tools/deploy/deployment_plan.py"
        path.write_text(path.read_text().replace("--planned-at", "--clock"))
        self.assert_invalid(root, "planner-cli:--planned-at")

    def test_planner_external_command_mutant_fails(self) -> None:
        root = self.mutant()
        path = root / "tools/deploy/deployment_plan.py"
        path.write_text(path.read_text() + "\nimport subprocess\n")
        self.assert_invalid(root, "planner-external-command")

    def test_planner_system_clock_mutant_fails(self) -> None:
        root = self.mutant()
        path = root / "tools/deploy/deployment_plan.py"
        path.write_text(path.read_text() + "\nCLOCK = datetime.now()\n")
        self.assert_invalid(root, "planner-system-clock")

    def test_planner_mapping_mutant_fails(self) -> None:
        root = self.mutant()
        path = root / "tools/deploy/deployment_plan.py"
        path.write_text(path.read_text().replace("BACKEND_IMAGE", "WRONG_IMAGE"))
        self.assert_invalid(root, "planner-variable:backend")

    def test_documentation_forward_only_mutant_fails(self) -> None:
        root = self.mutant()
        path = (
            root
            / "docs/infrastructure/deployment/release-control/PLANO_IMPLANTACAO.md"
        )
        path.write_text(path.read_text().replace("forward-only", "retroativa"))
        self.assert_invalid(root, "documentation:forward-only")


if __name__ == "__main__":
    unittest.main()
