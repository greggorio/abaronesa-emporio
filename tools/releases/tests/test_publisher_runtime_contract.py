from __future__ import annotations

import importlib.util
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
MODULE_PATH = ROOT / "tools/releases/validate_publisher_runtime.py"
SPEC = importlib.util.spec_from_file_location("validate_publisher_runtime", MODULE_PATH)
validator = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = validator
SPEC.loader.exec_module(validator)


class PublisherRuntimeContractTest(unittest.TestCase):
    def mutant(self) -> Path:
        target = Path(tempfile.mkdtemp(prefix="s15-runtime-"))
        self.addCleanup(shutil.rmtree, target, True)
        for directory in ("release_control", "docs/infrastructure", ".github", "ops"):
            source = ROOT / directory
            if source.exists():
                shutil.copytree(source, target / directory)
        return target

    def test_real_runtime_is_valid(self) -> None:
        validator.validate(ROOT)

    def test_legitimate_isolated_deployer_router_is_compatible(self) -> None:
        root = self.mutant()
        self.assertTrue(
            (root / "release_control/src/emporio_release_control/deployer_api.py").is_file()
        )
        validator.validate(root)

    def test_missing_lockfile_fails(self) -> None:
        root = self.mutant()
        (root / "release_control/uv.lock").unlink()
        with self.assertRaisesRegex(ValueError, "required-file"):
            validator.validate(root)

    def test_wrong_run_name_fails(self) -> None:
        root = self.mutant()
        path = root / ".github/workflows/publish-release.yml"
        path.write_text(path.read_text().replace("publish-release-", "wrong-"), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "workflow-run-name"):
            validator.validate(root)

    def test_deployer_router_fails(self) -> None:
        root = self.mutant()
        path = root / "release_control/src/emporio_release_control/api.py"
        path.write_text(path.read_text() + '\nX = "/api/deployment-control/v1/current"\n')
        with self.assertRaisesRegex(ValueError, "deployer-router"):
            validator.validate(root)

    def test_publisher_workflow_constant_mutant_fails(self) -> None:
        root = self.mutant()
        path = root / "release_control/src/emporio_release_control/constants.py"
        path.write_text(
            path.read_text(encoding="utf-8").replace(
                'PUBLISHER_WORKFLOW = "publish-release.yml"',
                'PUBLISHER_WORKFLOW = "deploy-production.yml"',
                1,
            ),
            encoding="utf-8",
        )
        with self.assertRaisesRegex(ValueError, "publisher-workflow"):
            validator.validate(root)

    def test_deployer_capability_in_publisher_mutant_fails(self) -> None:
        root = self.mutant()
        path = root / "release_control/src/emporio_release_control/api.py"
        path.write_text(
            path.read_text(encoding="utf-8").replace(
                '["release:read", "release:publish"]',
                '["release:read", "release:publish", "deployment:execute"]',
                1,
            ),
            encoding="utf-8",
        )
        with self.assertRaisesRegex(ValueError, "publisher-capabilities"):
            validator.validate(root)

    def test_forbidden_subprocess_fails(self) -> None:
        root = self.mutant()
        path = root / "release_control/src/emporio_release_control/api.py"
        path.write_text(path.read_text() + "\nimport subprocess\n")
        with self.assertRaisesRegex(ValueError, "forbidden-capability"):
            validator.validate(root)

    def test_openapi_extra_route_fails(self) -> None:
        root = self.mutant()
        path = root / "docs/infrastructure/deployment/release-control/api/publisher.openapi.yml"
        text = path.read_text()
        path.write_text(text.replace("\ncomponents:\n", "\n  /extra: {}\ncomponents:\n"))
        with self.assertRaisesRegex(ValueError, "openapi-routes"):
            validator.validate(root)

    def test_public_error_enum_mutant_fails(self) -> None:
        root = self.mutant()
        path = root / "release_control/src/emporio_release_control/schemas.py"
        path.write_text(
            path.read_text().replace('    "INTERNAL_ERROR",\n', ""),
            encoding="utf-8",
        )
        with self.assertRaisesRegex(ValueError, "public-error-enum"):
            validator.validate(root)

    def test_public_error_normalizer_mutant_fails(self) -> None:
        root = self.mutant()
        path = root / "release_control/src/emporio_release_control/api.py"
        path.write_text(
            path.read_text().replace(
                "failure = normalize_public_failure(failure)",
                "failure = failure",
            ),
            encoding="utf-8",
        )
        with self.assertRaisesRegex(ValueError, "public-error-normalizer"):
            validator.validate(root)

    def test_inherited_run_validation_mutant_fails(self) -> None:
        root = self.mutant()
        path = root / "release_control/src/emporio_release_control/sync.py"
        path.write_text(
            path.read_text().replace(
                'self._run(\n                inherited_run, "Publish Candidate"\n            )',
                "positive_id(inherited_run.get(\"id\"), \"CANDIDATE_ARTIFACT_INVALID\"), "
                "1, inherited_run.get(\"head_sha\"), None",
            ),
            encoding="utf-8",
        )
        with self.assertRaisesRegex(ValueError, "inherited-run-validation"):
            validator.validate(root)

    def test_dispatch_phase_mutant_fails(self) -> None:
        root = self.mutant()
        path = root / "release_control/src/emporio_release_control/service.py"
        path.write_text(
            path.read_text().replace("if exc.uncertain:", "if True:"),
            encoding="utf-8",
        )
        with self.assertRaisesRegex(ValueError, "dispatch-phase-boundary"):
            validator.validate(root)

    def test_predecessor_binding_mutant_fails(self) -> None:
        root = self.mutant()
        path = root / "release_control/src/emporio_release_control/sync.py"
        path.write_text(
            path.read_text().replace(
                'outcome["predecessorCandidateId"]\n'
                '            != candidate.manifest["predecessor"]["candidateId"]',
                "False",
            ),
            encoding="utf-8",
        )
        with self.assertRaisesRegex(ValueError, "candidate-predecessor-binding"):
            validator.validate(root)

    def test_release_asset_limit_mutant_fails(self) -> None:
        root = self.mutant()
        path = root / "release_control/src/emporio_release_control/sync.py"
        path.write_text(path.read_text().replace("2097152", "2097153"), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "release-asset-contract"):
            validator.validate(root)

    def test_release_asset_mime_mutant_fails(self) -> None:
        root = self.mutant()
        path = root / "release_control/src/emporio_release_control/sync.py"
        path.write_text(
            path.read_text().replace(
                '"release.json": (2097152, "application/json")',
                '"release.json": (2097152, "text/plain")',
            ),
            encoding="utf-8",
        )
        with self.assertRaisesRegex(ValueError, "release-asset-contract"):
            validator.validate(root)

    def test_publication_outcome_binding_mutant_fails(self) -> None:
        root = self.mutant()
        path = root / "release_control/src/emporio_release_control/reconciliation.py"
        path.write_text(
            path.read_text().replace(
                'or outcome["githubRelease"]["tagName"] != outcome["release"]',
                "or False",
            ),
            encoding="utf-8",
        )
        with self.assertRaisesRegex(ValueError, "publication-outcome-bindings"):
            validator.validate(root)

    def test_closed_decision_reintroduced_as_pending_fails(self) -> None:
        root = self.mutant()
        path = (
            root
            / "docs/infrastructure/deployment/release-control/"
            "CONTRATO_API_ESTADOS_SEGURANCA.md"
        )
        path.write_text(
            path.read_text() + "\n## Decisoes pendentes\n- tecnologia de persistencia\n",
            encoding="utf-8",
        )
        with self.assertRaisesRegex(ValueError, "publisher-decisions-pending"):
            validator.validate(root)


if __name__ == "__main__":
    unittest.main()
