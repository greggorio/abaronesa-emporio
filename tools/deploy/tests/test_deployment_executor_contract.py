from __future__ import annotations

import copy
import importlib.util
import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

import jsonschema
import yaml


ROOT = Path(__file__).resolve().parents[3]
MODULE_PATH = ROOT / "tools/deploy/validate_deployment_executor.py"
SPEC = importlib.util.spec_from_file_location(
    "validate_deployment_executor", MODULE_PATH
)
validator = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = validator
SPEC.loader.exec_module(validator)

SCHEMA_PATH = ROOT / "ops/deploy/schemas/deployment-journal.schema.json"
EXAMPLE_PATH = ROOT / "ops/deploy/examples/deployment-journal.example.json"


class DeploymentExecutorContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
        self.example = json.loads(EXAMPLE_PATH.read_text(encoding="utf-8"))

    def mutant(self) -> Path:
        target = Path(tempfile.mkdtemp(prefix="s19-contract-", dir="/tmp"))
        self.addCleanup(shutil.rmtree, target, True)
        for path in (
            "ops/deploy/schemas/deployment-journal.schema.json",
            "ops/deploy/examples/deployment-journal.example.json",
            "tools/deploy/deployment_executor.py",
            "docs/infrastructure/deployment/release-control/contracts/state-machines.yml",
            "docs/infrastructure/deployment/release-control/TRANSACAO_IMPLANTACAO.md",
            "docs/infrastructure/deployment/release-control/PLANO_IMPLANTACAO.md",
            "docs/infrastructure/deployment/release-control/README.md",
        ):
            destination = target / path
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / path, destination)
        return target

    def assert_invalid(self, root: Path, code: str) -> None:
        with self.assertRaisesRegex(ValueError, code):
            validator.validate(root)

    def test_schema_is_draft_2020_12_closed_and_example_is_valid(self) -> None:
        validator.validate_schema_and_example(self.schema, self.example)
        validator.validate_journal(self.example)

    def test_real_versioned_contract_is_valid(self) -> None:
        validator.validate(ROOT)

    def test_missing_required_file_fails(self) -> None:
        root = self.mutant()
        (root / "ops/deploy/examples/deployment-journal.example.json").unlink()
        self.assert_invalid(root, "required-file")

    def test_schema_open_object_mutant_fails(self) -> None:
        schema = copy.deepcopy(self.schema)
        schema["$defs"]["evidence"]["additionalProperties"] = True
        with self.assertRaisesRegex(ValueError, "schema-open-object"):
            validator.validate_schema_and_example(schema, self.example)

    def test_journal_and_evidence_extra_fields_fail_schema(self) -> None:
        for location in ("journal", "evidence"):
            with self.subTest(location=location):
                value = copy.deepcopy(self.example)
                if location == "journal":
                    value["unexpected"] = True
                else:
                    value["steps"][0]["evidence"]["unexpected"] = True
                errors = list(
                    jsonschema.Draft202012Validator(self.schema).iter_errors(value)
                )
                self.assertTrue(errors)

    def test_step_order_mutant_fails(self) -> None:
        value = copy.deepcopy(self.example)
        value["steps"][0], value["steps"][1] = value["steps"][1], value["steps"][0]
        with self.assertRaisesRegex(ValueError, "journal-step-order"):
            validator.validate_journal(value)

    def test_sequence_and_transition_mutants_fail(self) -> None:
        value = copy.deepcopy(self.example)
        value["sequence"] += 1
        with self.assertRaisesRegex(ValueError, "journal-sequence"):
            validator.validate_journal(value)
        value = copy.deepcopy(self.example)
        value["transitions"][3]["to"] = "VERIFYING"
        with self.assertRaisesRegex(ValueError, "journal-transition-machine"):
            validator.validate_journal(value)

    def test_timestamp_regression_mutant_fails(self) -> None:
        value = copy.deepcopy(self.example)
        value["transitions"][2]["at"] = "2026-07-29T16:00:00Z"
        with self.assertRaisesRegex(ValueError, "journal-transition-time-order"):
            validator.validate_journal(value)

    def test_step_finished_before_started_mutant_fails(self) -> None:
        value = copy.deepcopy(self.example)
        value["steps"][0]["finishedAt"] = "2026-07-29T16:01:00Z"
        value["steps"][0]["evidence"]["observedAt"] = "2026-07-29T16:01:00Z"
        with self.assertRaisesRegex(ValueError, "journal-step-time-order"):
            validator.validate_journal(value)

    def test_evidence_before_started_mutant_fails(self) -> None:
        value = copy.deepcopy(self.example)
        value["steps"][0]["evidence"]["observedAt"] = "2026-07-29T16:01:00Z"
        with self.assertRaisesRegex(ValueError, "journal-evidence-order"):
            validator.validate_journal(value)

    def test_evidence_after_finished_mutant_fails(self) -> None:
        value = copy.deepcopy(self.example)
        value["steps"][0]["evidence"]["observedAt"] = "2026-07-29T16:04:00Z"
        with self.assertRaisesRegex(ValueError, "journal-evidence-order"):
            validator.validate_journal(value)

    def test_step_timestamps_outside_state_window_mutants_fail(self) -> None:
        before_entry = copy.deepcopy(self.example)
        before_entry["steps"][0]["startedAt"] = "2026-07-29T16:01:00Z"
        with self.assertRaisesRegex(ValueError, "journal-step-window:PULL"):
            validator.validate_journal(before_entry)

        after_exit = copy.deepcopy(self.example)
        after_exit["steps"][0]["finishedAt"] = "2026-07-29T16:05:00Z"
        after_exit["steps"][0]["evidence"]["observedAt"] = "2026-07-29T16:05:00Z"
        with self.assertRaisesRegex(ValueError, "journal-step-window:PULL"):
            validator.validate_journal(after_exit)

    def test_commit_state_cannot_start_before_verify_finishes(self) -> None:
        value = copy.deepcopy(self.example)
        value["steps"][5]["startedAt"] = "2026-07-29T16:10:00Z"
        with self.assertRaisesRegex(ValueError, "journal-commit-after-verify"):
            validator.validate_journal(value)

    def test_terminal_timestamp_must_equal_updated_and_last_transition(self) -> None:
        for field in ("finishedAt", "updatedAt"):
            with self.subTest(field=field):
                value = copy.deepcopy(self.example)
                value[field] = "2026-07-29T16:14:00Z"
                with self.assertRaisesRegex(ValueError, "journal-finished-at-order"):
                    validator.validate_journal(value)

    def test_equal_second_boundaries_remain_valid(self) -> None:
        value = copy.deepcopy(self.example)
        value["steps"][0]["startedAt"] = "2026-07-29T16:02:00Z"
        value["steps"][0]["finishedAt"] = "2026-07-29T16:02:00Z"
        value["steps"][0]["evidence"]["observedAt"] = "2026-07-29T16:02:00Z"
        validator.validate_journal(value)

    def test_pending_and_evidence_invariants_fail(self) -> None:
        value = copy.deepcopy(self.example)
        value["steps"][-1]["attempts"] = 1
        with self.assertRaisesRegex(ValueError, "journal-step-pending"):
            validator.validate_journal(value)
        value = copy.deepcopy(self.example)
        value["steps"][0]["status"] = "SKIPPED"
        with self.assertRaisesRegex(ValueError, "journal-step-evidence"):
            validator.validate_journal(value)

    def test_succeeded_requires_commit_and_confirmed_hash(self) -> None:
        value = copy.deepcopy(self.example)
        value["confirmedStateSha256"] = None
        with self.assertRaisesRegex(ValueError, "journal-succeeded-invariants"):
            validator.validate_journal(value)
        value = copy.deepcopy(self.example)
        value["steps"][5]["evidence"]["evidenceId"] = "state:" + "c" * 64
        with self.assertRaisesRegex(ValueError, "journal-confirmed-state-evidence"):
            validator.validate_journal(value)

    def test_source_release_and_source_hash_are_coherent(self) -> None:
        value = copy.deepcopy(self.example)
        value["sourceStateSha256"] = "sha256:" + "c" * 64
        with self.assertRaisesRegex(ValueError, "journal-source-state"):
            validator.validate_journal(value)

    def _machine_mutant(self) -> tuple[Path, Path, dict[str, object]]:
        root = self.mutant()
        path = (
            root
            / "docs/infrastructure/deployment/release-control/contracts/state-machines.yml"
        )
        value = yaml.safe_load(path.read_text(encoding="utf-8"))
        return root, path, value

    def test_s19_machine_transition_removed_mutant_fails(self) -> None:
        root, path, value = self._machine_mutant()
        transitions = value["machines"]["deployment"]["transitions"]
        transitions[:] = [
            item
            for item in transitions
            if not (item["from"] == "VERIFYING" and item["to"] == "SUCCEEDED")
        ]
        path.write_text(yaml.safe_dump(value, sort_keys=False), encoding="utf-8")
        self.assert_invalid(root, "state-machine-transitions")

    def test_s22_direct_success_transition_removed_mutant_fails(self) -> None:
        root, path, value = self._machine_mutant()
        transitions = value["machines"]["deployment"]["transitions"]
        transitions[:] = [
            item
            for item in transitions
            if not (item["from"] == "QUEUED" and item["to"] == "SUCCEEDED")
        ]
        path.write_text(yaml.safe_dump(value, sort_keys=False), encoding="utf-8")
        self.assert_invalid(root, "state-machine-transitions")

    def test_s22_direct_rolled_back_transition_removed_mutant_fails(self) -> None:
        root, path, value = self._machine_mutant()
        transitions = value["machines"]["deployment"]["transitions"]
        transitions[:] = [
            item
            for item in transitions
            if not (item["from"] == "QUEUED" and item["to"] == "ROLLED_BACK")
        ]
        path.write_text(yaml.safe_dump(value, sort_keys=False), encoding="utf-8")
        self.assert_invalid(root, "state-machine-transitions")

    def test_arbitrary_machine_transition_added_mutant_fails(self) -> None:
        root, path, value = self._machine_mutant()
        value["machines"]["deployment"]["transitions"].append(
            {"from": "QUEUED", "to": "VERIFYING", "actor": "reconciler"}
        )
        path.write_text(yaml.safe_dump(value, sort_keys=False), encoding="utf-8")
        self.assert_invalid(root, "state-machine-transitions")

    def test_s22_direct_transition_metadata_mutants_fail(self) -> None:
        for field, replacement in (
            ("actor", "internal_request"),
            ("requires_remote_evidence", False),
        ):
            with self.subTest(field=field):
                root, path, value = self._machine_mutant()
                transition = next(
                    item
                    for item in value["machines"]["deployment"]["transitions"]
                    if item["from"] == "QUEUED" and item["to"] == "SUCCEEDED"
                )
                transition[field] = replacement
                path.write_text(
                    yaml.safe_dump(value, sort_keys=False), encoding="utf-8"
                )
                self.assert_invalid(
                    root, "state-machine-direct-transition-metadata"
                )

    def test_s19_journal_rejects_direct_s22_transition(self) -> None:
        value = copy.deepcopy(self.example)
        value["transitions"][1]["to"] = "SUCCEEDED"
        with self.assertRaisesRegex(ValueError, "journal-transition-machine"):
            validator.validate_journal(value)

    def test_available_boundary_mutant_fails(self) -> None:
        root, path, value = self._machine_mutant()
        value["machines"]["deployment"]["transitions"][0]["to"] = "PULLING"
        path.write_text(yaml.safe_dump(value, sort_keys=False), encoding="utf-8")
        self.assert_invalid(root, "state-machine-eligibility-boundary")

    def test_public_surface_mutant_fails(self) -> None:
        root = self.mutant()
        path = root / "tools/deploy/deployment_executor.py"
        path.write_text(
            path.read_text(encoding="utf-8").replace(
                "class ProbeResult", "class RemovedProbeResult", 1
            ),
            encoding="utf-8",
        )
        self.assert_invalid(root, "executor-public-surface")

    def test_external_adapter_and_system_clock_mutants_fail(self) -> None:
        for name, source, code in (
            ("subprocess", "\nimport subprocess\n", "executor-operational-adapter"),
            ("clock", "\nCLOCK = datetime.now()\n", "executor-system-clock"),
        ):
            with self.subTest(name=name):
                root = self.mutant()
                path = root / "tools/deploy/deployment_executor.py"
                path.write_text(path.read_text(encoding="utf-8") + source, encoding="utf-8")
                self.assert_invalid(root, code)

    def test_lock_and_nofollow_mutants_fail(self) -> None:
        for token in ("fcntl.LOCK_NB", "O_NOFOLLOW"):
            with self.subTest(token=token):
                root = self.mutant()
                path = root / "tools/deploy/deployment_executor.py"
                path.write_text(
                    path.read_text(encoding="utf-8").replace(token, "REMOVED_TOKEN"),
                    encoding="utf-8",
                )
                self.assert_invalid(root, f"executor-required:{token}")

    def test_planner_binding_and_atomicity_mutants_fail(self) -> None:
        for token in ("validate_bundle", "os.replace", "os.fsync"):
            with self.subTest(token=token):
                root = self.mutant()
                path = root / "tools/deploy/deployment_executor.py"
                path.write_text(
                    path.read_text(encoding="utf-8").replace(token, "removed_gate"),
                    encoding="utf-8",
                )
                self.assert_invalid(root, f"executor-required:{token}")

    def test_documentation_mutant_fails(self) -> None:
        root = self.mutant()
        path = (
            root
            / "docs/infrastructure/deployment/release-control/TRANSACAO_IMPLANTACAO.md"
        )
        path.write_text(
            path.read_text(encoding="utf-8").replace(
                "databaseRestoreRequired", "restauração implícita"
            ),
            encoding="utf-8",
        )
        self.assert_invalid(root, "documentation:databaseRestoreRequired")


if __name__ == "__main__":
    unittest.main()
