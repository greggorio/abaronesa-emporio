from __future__ import annotations

import copy
import importlib.util
import json
import sys
import unittest
from pathlib import Path

import jsonschema
import yaml


ROOT = Path(__file__).resolve().parents[3]
MODULE_PATH = ROOT / "tools/deploy/validate_deploy_workflow.py"
SPEC = importlib.util.spec_from_file_location("validate_deploy_workflow", MODULE_PATH)
validator = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = validator
SPEC.loader.exec_module(validator)

WORKFLOW_PATH = ROOT / ".github/workflows/deploy-production.yml"


class DeployWorkflowContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.workflow_source = WORKFLOW_PATH.read_text(encoding="utf-8")
        self.workflow = yaml.safe_load(self.workflow_source)
        self.contracts: dict[str, tuple[dict, dict, bytes]] = {}
        for name, (schema_path, example_path) in validator.CONTRACTS.items():
            schema = json.loads((ROOT / schema_path).read_text(encoding="utf-8"))
            example_file = ROOT / example_path
            example = json.loads(example_file.read_text(encoding="utf-8"))
            self.contracts[name] = (schema, example, example_file.read_bytes())

    def assert_workflow_invalid(
        self,
        workflow: dict,
        code: str,
        source: str | None = None,
    ) -> None:
        with self.assertRaisesRegex(ValueError, code):
            validator.validate_workflow(
                workflow,
                self.workflow_source if source is None else source,
            )

    @staticmethod
    def workflow_on(workflow: dict) -> dict:
        return workflow.get("on", workflow.get(True))

    def test_real_versioned_contract_is_valid(self) -> None:
        validator.validate(ROOT)

    def test_three_schemas_are_draft_closed_and_examples_canonical(self) -> None:
        for name, (schema, example, raw) in self.contracts.items():
            with self.subTest(contract=name):
                validator.validate_contract(name, schema, example, raw)

    def test_request_rejects_extra_invalid_identity_and_noncanonical_semver(self) -> None:
        schema, example, _ = self.contracts["request"]
        mutations = (
            lambda value: value.update(extra=True),
            lambda value: value.update(operationId="short"),
            lambda value: value.update(targetRelease="v01.2.3"),
            lambda value: value.update(controlSha="A" * 40),
            lambda value: value.update(workflowRunId=0),
            lambda value: value.update(plannedAt="2026-07-31T18:00:00.1Z"),
        )
        for index, mutation in enumerate(mutations):
            with self.subTest(index=index):
                candidate = copy.deepcopy(example)
                mutation(candidate)
                self.assertTrue(
                    list(jsonschema.Draft202012Validator(schema).iter_errors(candidate))
                )

    def test_snapshot_first_install_requires_three_nulls(self) -> None:
        schema, example, _ = self.contracts["snapshot"]
        first = copy.deepcopy(example)
        first.update(
            mode="FIRST_INSTALL",
            currentRelease=None,
            installedStateSha256=None,
            currentManifestSha256=None,
        )
        self.assertFalse(
            list(jsonschema.Draft202012Validator(schema).iter_errors(first))
        )
        for field in (
            "currentRelease",
            "installedStateSha256",
            "currentManifestSha256",
        ):
            with self.subTest(field=field):
                candidate = copy.deepcopy(first)
                candidate[field] = example[field]
                self.assertTrue(
                    list(jsonschema.Draft202012Validator(schema).iter_errors(candidate))
                )

    def test_snapshot_update_requires_release_and_both_digests(self) -> None:
        schema, example, _ = self.contracts["snapshot"]
        for field in (
            "currentRelease",
            "installedStateSha256",
            "currentManifestSha256",
        ):
            with self.subTest(field=field):
                candidate = copy.deepcopy(example)
                candidate[field] = None
                self.assertTrue(
                    list(jsonschema.Draft202012Validator(schema).iter_errors(candidate))
                )

    def test_indeterminate_outcome_nulls_remote_state_and_requires_error(self) -> None:
        schema, example, _ = self.contracts["outcome"]
        candidate = copy.deepcopy(example)
        candidate.update(
            transportStatus="INDETERMINATE",
            deploymentState=None,
            databaseRestoreRequired=None,
            errorCode="REMOTE_RESULT_UNAVAILABLE",
        )
        self.assertFalse(
            list(jsonschema.Draft202012Validator(schema).iter_errors(candidate))
        )
        for field, value in (
            ("deploymentState", "FAILED"),
            ("databaseRestoreRequired", False),
            ("errorCode", None),
        ):
            with self.subTest(field=field):
                mutant = copy.deepcopy(candidate)
                mutant[field] = value
                self.assertTrue(
                    list(jsonschema.Draft202012Validator(schema).iter_errors(mutant))
                )

    def test_confirmed_success_requires_null_error_and_boolean_restore(self) -> None:
        schema, example, _ = self.contracts["outcome"]
        for field, value in (
            ("errorCode", "REMOTE_RESULT_INVALID"),
            ("databaseRestoreRequired", None),
            ("deploymentState", None),
        ):
            with self.subTest(field=field):
                candidate = copy.deepcopy(example)
                candidate[field] = value
                self.assertTrue(
                    list(jsonschema.Draft202012Validator(schema).iter_errors(candidate))
                )

    def test_confirmed_success_preserves_cleanup_failure_and_restore_state(self) -> None:
        schema, example, _ = self.contracts["outcome"]
        candidate = copy.deepcopy(example)
        candidate.update(
            transportStatus="CONFIRMED",
            deploymentState="SUCCEEDED",
            databaseRestoreRequired=True,
            errorCode="REMOTE_CLEANUP_FAILED",
        )
        self.assertFalse(
            list(jsonschema.Draft202012Validator(schema).iter_errors(candidate))
        )

        ordinary = copy.deepcopy(candidate)
        ordinary["errorCode"] = None
        self.assertFalse(
            list(jsonschema.Draft202012Validator(schema).iter_errors(ordinary))
        )

    def test_workflow_has_only_dispatch_and_two_authority_inputs(self) -> None:
        trigger_mutant = copy.deepcopy(self.workflow)
        self.workflow_on(trigger_mutant)["push"] = {"branches": ["main"]}
        self.assert_workflow_invalid(trigger_mutant, "workflow-trigger")

        for forbidden in validator.FORBIDDEN_INPUTS:
            with self.subTest(input=forbidden):
                input_mutant = copy.deepcopy(self.workflow)
                self.workflow_on(input_mutant)["workflow_dispatch"]["inputs"][
                    forbidden
                ] = {"required": True, "type": "string"}
                self.assert_workflow_invalid(input_mutant, "workflow-inputs")

    def test_jobs_and_dependencies_are_exact(self) -> None:
        extra = copy.deepcopy(self.workflow)
        extra["jobs"]["extra"] = copy.deepcopy(extra["jobs"]["trust"])
        self.assert_workflow_invalid(extra, "workflow-jobs")

        dependency = copy.deepcopy(self.workflow)
        dependency["jobs"]["deploy"]["needs"] = "trust"
        self.assert_workflow_invalid(dependency, "workflow-needs:deploy")

    def test_only_deploy_uses_production_environment(self) -> None:
        missing = copy.deepcopy(self.workflow)
        del missing["jobs"]["deploy"]["environment"]
        self.assert_workflow_invalid(missing, "workflow-environment:deploy")

        leaked = copy.deepcopy(self.workflow)
        leaked["jobs"]["prepare"]["environment"] = "production"
        self.assert_workflow_invalid(leaked, "workflow-environment:prepare")

    def test_global_concurrency_permissions_and_timeouts_are_frozen(self) -> None:
        mutations = (
            ("workflow-concurrency", lambda value: value["concurrency"].update({"cancel-in-progress": True})),
            ("workflow-permissions", lambda value: value["permissions"].update({"packages": "write"})),
            ("workflow-timeout:deploy", lambda value: value["jobs"]["deploy"].update({"timeout-minutes": 46})),
        )
        for code, mutate in mutations:
            with self.subTest(code=code):
                candidate = copy.deepcopy(self.workflow)
                mutate(candidate)
                self.assert_workflow_invalid(candidate, code)

    def test_actions_are_official_and_pinned_to_exact_shas(self) -> None:
        for replacement, code in (
            ("actions/checkout@main", "workflow-action-pin:actions/checkout"),
            ("vendor/ssh-action@" + "a" * 40, "workflow-third-party:vendor/ssh-action"),
        ):
            with self.subTest(replacement=replacement):
                candidate = copy.deepcopy(self.workflow)
                candidate["jobs"]["trust"]["steps"][0]["uses"] = replacement
                self.assert_workflow_invalid(candidate, code)

    def test_production_secrets_are_scoped_to_deploy_env(self) -> None:
        deployment_step = next(
            step
            for step in self.workflow["jobs"]["deploy"]["steps"]
            if step.get("id") == "deployment"
        )
        self.assertEqual(
            "${{ vars.PRODUCTION_SSH_PUBLIC_KEY_SHA256 }}",
            deployment_step["env"]["PRODUCTION_SSH_PUBLIC_KEY_SHA256"],
        )
        candidate = copy.deepcopy(self.workflow)
        candidate["jobs"]["prepare"]["steps"][2].setdefault("env", {})[
            "KEY"
        ] = "${{ secrets.PRODUCTION_SSH_PRIVATE_KEY }}"
        self.assert_workflow_invalid(candidate, "workflow-secret-scope:prepare")

        run_interpolation = copy.deepcopy(self.workflow)
        run_interpolation["jobs"]["deploy"]["steps"][2]["run"] += (
            " ${{ secrets.PRODUCTION_SSH_PRIVATE_KEY }}"
        )
        self.assert_workflow_invalid(run_interpolation, "workflow-secret-run")

    def test_trust_environment_contains_every_frozen_binding(self) -> None:
        candidate = copy.deepcopy(self.workflow)
        trust_step = next(
            step for step in candidate["jobs"]["trust"]["steps"] if "run" in step
        )
        del trust_step["env"]["TRUSTED_ACTOR_ID"]
        self.assert_workflow_invalid(candidate, "workflow-trust-env")

    def test_result_and_outcome_upload_precede_final_failure(self) -> None:
        for job, artifact in (
            ("deploy", "deployment-result"),
            ("outcome", "deployment-workflow-outcome"),
        ):
            with self.subTest(job=job):
                candidate = copy.deepcopy(self.workflow)
                steps = candidate["jobs"][job]["steps"]
                exit_step = next(step for step in steps if step.get("run") == "exit 1")
                steps.remove(exit_step)
                steps.insert(0, exit_step)
                self.assert_workflow_invalid(
                    candidate, f"workflow-upload-order:{job}"
                )

    def test_non_successful_outcome_reconciliation_reaches_final_exit_gate(self) -> None:
        outcome_steps = self.workflow["jobs"]["outcome"]["steps"]
        reconciliation = next(
            step for step in outcome_steps if step.get("id") == "reconciliation"
        )
        self.assertTrue(reconciliation["continue-on-error"])
        gate = next(step for step in outcome_steps if step.get("run") == "exit 1")
        self.assertEqual(
            gate["if"], "steps.reconciliation.outcome != 'success'"
        )

        mutant = copy.deepcopy(self.workflow)
        mutant_gate = next(
            step
            for step in mutant["jobs"]["outcome"]["steps"]
            if step.get("run") == "exit 1"
        )
        mutant_gate["if"] = "false"
        self.assert_workflow_invalid(mutant, "workflow-outcome-final-gate")

    def test_forbidden_mutants_fail_closed(self) -> None:
        for mutant in (
            "sudo command",
            "docker build .",
            "docker compose down",
            "docker system prune",
            "image:latest",
            "StrictHostKeyChecking=no",
            "sshpass",
            "ForwardAgent yes",
            "shell=True",
            "bash -c command",
        ):
            with self.subTest(mutant=mutant):
                self.assert_workflow_invalid(
                    self.workflow,
                    "workflow-forbidden",
                    self.workflow_source + "\n# " + mutant + "\n",
                )


if __name__ == "__main__":
    unittest.main()
