from __future__ import annotations

import copy
import importlib.util
import sys
import unittest
from pathlib import Path
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[3]
MODULE_PATH = ROOT / "tools/releases/release_control_contract.py"
SPEC = importlib.util.spec_from_file_location("release_control_contract", MODULE_PATH)
contract = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = contract
SPEC.loader.exec_module(contract)


class ReleaseControlContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.real = contract.load_contracts()

    def values(self):
        return copy.deepcopy(self.real)

    def errors(self, values, check_files=False):
        return contract.validate_contracts(*values, check_files=check_files)

    def assertInvalid(self, values):
        self.assertTrue(self.errors(values))

    def test_01_real_contract_is_valid(self):
        self.assertEqual([], self.errors(self.values(), check_files=True))

    def test_02_missing_publisher_path_fails(self):
        values = self.values()
        del values[0]["paths"]["/api/release-publisher/v1/candidates"]
        self.assertInvalid(values)

    def test_03_extra_publisher_path_fails(self):
        values = self.values()
        values[0]["paths"]["/api/release-publisher/v1/extra"] = {}
        self.assertInvalid(values)

    def test_04_missing_deployer_path_fails(self):
        values = self.values()
        del values[1]["paths"]["/api/deployment-control/v1/current"]
        self.assertInvalid(values)

    def test_05_extra_deployer_path_fails(self):
        values = self.values()
        values[1]["paths"]["/api/deployment-control/v1/extra"] = {}
        self.assertInvalid(values)

    def test_06_publisher_route_in_deployer_fails(self):
        values = self.values()
        values[1]["paths"]["/api/release-publisher/v1/releases"] = copy.deepcopy(
            values[0]["paths"]["/api/release-publisher/v1/releases"]
        )
        self.assertInvalid(values)

    def test_operation_polling_parameter_drift_fails(self):
        values = self.values()
        values[0]["paths"][
            "/api/release-publisher/v1/operations/{operationId}"
        ]["get"]["parameters"] = []
        self.assertInvalid(values)

    def test_operation_polling_response_drift_fails(self):
        values = self.values()
        values[0]["paths"][
            "/api/release-publisher/v1/operations/{operationId}"
        ]["get"]["responses"]["200"]["content"]["application/json"]["schema"] = {
            "$ref": "#/components/schemas/GlobalReleaseDetail"
        }
        self.assertInvalid(values)

    def test_07_deployer_route_in_publisher_fails(self):
        values = self.values()
        values[0]["paths"]["/api/deployment-control/v1/current"] = copy.deepcopy(
            values[1]["paths"]["/api/deployment-control/v1/current"]
        )
        self.assertInvalid(values)

    def test_08_missing_bearer_auth_fails(self):
        values = self.values()
        del values[0]["components"]["securitySchemes"]["bearerAuth"]
        self.assertInvalid(values)

    def test_09_sensitive_health_field_fails(self):
        values = self.values()
        values[0]["components"]["schemas"]["HealthResponse"]["properties"]["token"] = {
            "type": "string"
        }
        self.assertInvalid(values)

    def test_10_post_without_idempotency_key_fails(self):
        values = self.values()
        values[0]["paths"]["/api/release-publisher/v1/releases"]["post"]["parameters"] = []
        self.assertInvalid(values)

    def test_11_open_request_fails(self):
        values = self.values()
        values[0]["components"]["schemas"]["PublishReleaseRequest"][
            "additionalProperties"
        ] = True
        self.assertInvalid(values)

    def add_request_field(self, schema_name, field):
        values = self.values()
        schema = values[0]["components"]["schemas"][schema_name]
        schema["properties"][field] = {"type": "string"}
        schema["required"].append(field)
        return values

    def test_12_digest_request_field_fails(self):
        self.assertInvalid(self.add_request_field("PublishReleaseRequest", "digest"))

    def test_13_image_request_field_fails(self):
        self.assertInvalid(self.add_request_field("PublishReleaseRequest", "image"))

    def test_14_component_request_field_fails(self):
        self.assertInvalid(self.add_request_field("PublishReleaseRequest", "component"))

    def test_15_command_request_field_fails(self):
        self.assertInvalid(self.add_request_field("PublishReleaseRequest", "command"))

    def test_16_version_bump_outside_enum_fails(self):
        values = self.values()
        values[0]["components"]["schemas"]["PublishReleaseRequest"]["properties"][
            "versionBump"
        ]["enum"].append("CUSTOM")
        self.assertInvalid(values)

    def test_17_release_id_pattern_drift_fails(self):
        values = self.values()
        values[1]["components"]["schemas"]["ReleaseId"]["pattern"] = ".*"
        self.assertInvalid(values)

    def test_18_missing_state_fails(self):
        values = self.values()
        del values[2]["machines"]["publication"]["states"]["PUBLISHING"]
        self.assertInvalid(values)

    def test_19_transition_to_unknown_state_fails(self):
        values = self.values()
        values[2]["machines"]["publication"]["transitions"].append(
            {"from": "REQUESTED", "to": "UNKNOWN", "actor": "reconciler"}
        )
        self.assertInvalid(values)

    def test_20_terminal_with_outgoing_transition_fails(self):
        values = self.values()
        values[2]["machines"]["publication"]["transitions"].append(
            {"from": "PUBLISHED", "to": "FAILED", "actor": "reconciler"}
        )
        self.assertInvalid(values)

    def test_21_publication_main_flow_break_fails(self):
        values = self.values()
        values[2]["machines"]["publication"]["transitions"] = [
            item
            for item in values[2]["machines"]["publication"]["transitions"]
            if not (item["from"] == "VALIDATING" and item["to"] == "PUBLISHING")
        ]
        self.assertInvalid(values)

    def test_22_deployment_main_flow_break_fails(self):
        values = self.values()
        values[2]["machines"]["deployment"]["transitions"] = [
            item
            for item in values[2]["machines"]["deployment"]["transitions"]
            if not (item["from"] == "UPDATING" and item["to"] == "VERIFYING")
        ]
        self.assertInvalid(values)

    def test_23_wrong_role_fails(self):
        values = self.values()
        values[3]["authorization"]["routes"][0]["role"] = "deployment:execute"
        self.assertInvalid(values)

    def test_24_shared_mode_credential_fails(self):
        values = self.values()
        values[3]["outbound_credentials"]["deployer"] = copy.deepcopy(
            values[3]["outbound_credentials"]["publisher"]
        )
        self.assertInvalid(values)

    def test_25_docker_socket_allowed_fails(self):
        values = self.values()
        values[3]["prohibitions"]["docker_socket_access"] = True
        self.assertInvalid(values)

    def test_26_local_git_allowed_fails(self):
        values = self.values()
        values[3]["prohibitions"]["local_git_access"] = True
        self.assertInvalid(values)

    def test_27_direct_ssh_allowed_fails(self):
        values = self.values()
        values[3]["prohibitions"]["direct_ssh"] = True
        self.assertInvalid(values)

    def test_28_component_selection_allowed_fails(self):
        values = self.values()
        values[3]["prohibitions"]["component_selection"] = True
        self.assertInvalid(values)

    def test_29_wrong_capability_mode_fails(self):
        values = self.values()
        values[0]["components"]["schemas"]["CapabilityResponse"]["properties"]["mode"][
            "enum"
        ] = ["deployer"]
        self.assertInvalid(values)

    def test_30_rollback_without_reason_fails(self):
        values = self.values()
        schema = values[1]["components"]["schemas"]["RollbackRequest"]
        schema["required"].remove("reason")
        self.assertInvalid(values)

    def test_31_deployment_extra_field_fails(self):
        values = self.values()
        schema = values[1]["components"]["schemas"]["DeploymentRequest"]
        schema["properties"]["tag"] = {"type": "string"}
        schema["required"].append("tag")
        self.assertInvalid(values)

    def test_32_idempotency_conflict_must_be_documented(self):
        values = self.values()
        del values[1]["paths"]["/api/deployment-control/v1/deployments"]["post"][
            "responses"
        ]["409"]
        self.assertInvalid(values)

    def test_33_deployment_and_rollback_share_lock(self):
        values = self.values()
        values[2]["concurrency"]["production"]["shared_by"] = ["deployment"]
        self.assertInvalid(values)

    def test_34_workflow_run_binding_is_required_after_correlation(self):
        values = self.values()
        values[2]["reconciliation"]["workflow_run_binding"][
            "required_after_correlation"
        ] = False
        self.assertInvalid(values)

    def test_35_human_document_is_required(self):
        values = self.values()
        missing = ROOT / "does-not-exist.md"
        with patch.object(contract, "HUMAN_DOC", missing):
            self.assertTrue(self.errors(values, check_files=True))

    def test_36_cross_mode_transition_actor_fails(self):
        values = self.values()
        values[2]["machines"]["publication"]["transitions"][0]["actor"] = "deployer"
        self.assertInvalid(values)

    def test_37_success_without_remote_evidence_fails(self):
        values = self.values()
        values[2]["reconciliation"]["success_requires_remote_evidence"] = False
        self.assertInvalid(values)

    def test_38_client_selectable_mode_fails(self):
        values = self.values()
        values[2]["bootstrap"]["client_selectable"] = True
        self.assertInvalid(values)

    def test_39_operation_state_enum_drift_fails(self):
        values = self.values()
        values[1]["components"]["schemas"]["DeploymentOperation"]["properties"]["state"][
            "enum"
        ].remove("VERIFYING")
        self.assertInvalid(values)

    def test_40_success_transition_requires_remote_evidence(self):
        values = self.values()
        transition = next(
            item
            for item in values[2]["machines"]["publication"]["transitions"]
            if item["to"] == "PUBLISHED"
        )
        transition["requires_remote_evidence"] = False
        self.assertInvalid(values)

    def test_41_workflow_run_id_is_optional_before_discovery(self):
        values = self.values()
        values[2]["reconciliation"]["operation_record_required"].append("workflowRunId")
        self.assertInvalid(values)

    def test_42_referenced_idempotency_key_must_be_required(self):
        values = self.values()
        values[0]["components"]["parameters"]["IdempotencyKey"]["required"] = False
        self.assertInvalid(values)

    def test_43_referenced_idempotency_key_pattern_cannot_drift(self):
        values = self.values()
        values[1]["components"]["parameters"]["IdempotencyKey"]["schema"][
            "pattern"
        ] = ".*"
        self.assertInvalid(values)

    def test_44_referenced_idempotency_key_limit_cannot_drift(self):
        values = self.values()
        values[1]["components"]["parameters"]["IdempotencyKey"]["schema"][
            "maxLength"
        ] = 4096
        self.assertInvalid(values)

    def test_45_state_metadata_is_required(self):
        values = self.values()
        del values[2]["machines"]["publication"]["states"]["REQUESTED"]["active"]
        self.assertInvalid(values)

    def test_46_terminal_state_cannot_be_active(self):
        values = self.values()
        values[2]["machines"]["deployment"]["states"]["SUCCEEDED"]["active"] = True
        self.assertInvalid(values)

    def test_47_success_state_cannot_be_failure(self):
        values = self.values()
        values[2]["machines"]["publication"]["states"]["PUBLISHED"]["failure"] = True
        self.assertInvalid(values)

    def test_48_available_is_not_active(self):
        values = self.values()
        values[2]["machines"]["deployment"]["states"]["AVAILABLE"]["active"] = True
        self.assertInvalid(values)

    def test_49_rolled_back_semantics_cannot_drift(self):
        values = self.values()
        values[2]["machines"]["deployment"]["states"]["ROLLED_BACK"]["failure"] = False
        self.assertInvalid(values)

    def test_50_authentication_issuer_is_required(self):
        values = self.values()
        values[3]["authentication"]["issuer_required"] = False
        self.assertInvalid(values)

    def test_51_authentication_audience_is_required(self):
        values = self.values()
        del values[3]["authentication"]["audience_required"]
        self.assertInvalid(values)

    def test_52_authentication_algorithm_allowlist_is_required(self):
        values = self.values()
        values[3]["authentication"]["algorithm_allowlist_required"] = False
        self.assertInvalid(values)

    def test_53_transport_cors_must_use_configured_allowlist(self):
        values = self.values()
        values[3]["transport"]["cors"] = "any"
        self.assertInvalid(values)

    def test_54_unexpected_content_type_must_be_rejected(self):
        values = self.values()
        values[3]["transport"]["unexpected_content_type"] = "allow"
        self.assertInvalid(values)

    def test_55_mutations_must_be_audited(self):
        values = self.values()
        values[3]["transport"]["mutation_audit"] = "disabled"
        self.assertInvalid(values)

    def test_56_deployment_conflict_exposes_authorized_operation_reference(self):
        values = self.values()
        schema = values[1]["components"]["schemas"]["DeploymentConflictProblem"]
        del schema["properties"]["activeOperationId"]
        self.assertInvalid(values)

    def test_57_normal_operation_does_not_expose_active_operation_reference(self):
        values = self.values()
        values[1]["components"]["schemas"]["DeploymentOperation"]["properties"][
            "activeOperationId"
        ] = {"type": "string"}
        self.assertInvalid(values)

    def test_58_publisher_release_detail_is_flat_and_closed(self):
        values = self.values()
        values[0]["components"]["schemas"]["GlobalReleaseDetail"] = {
            "allOf": [
                {"$ref": "#/components/schemas/GlobalReleaseSummary"},
                {"type": "object", "additionalProperties": False},
            ]
        }
        self.assertInvalid(values)

    def test_59_deployment_plan_source_release_requires_null(self):
        values = self.values()
        source_release = values[1]["components"]["schemas"]["DeploymentPlan"][
            "properties"
        ]["sourceRelease"]
        source_release["oneOf"] = [
            item for item in source_release["oneOf"] if item.get("type") != "null"
        ]
        self.assertInvalid(values)

    def test_60_deployment_plan_source_release_requires_release_id(self):
        values = self.values()
        source_release = values[1]["components"]["schemas"]["DeploymentPlan"][
            "properties"
        ]["sourceRelease"]
        source_release["oneOf"] = [
            item for item in source_release["oneOf"] if "$ref" not in item
        ]
        self.assertInvalid(values)

    def test_61_deployment_plan_source_release_rejects_other_types(self):
        values = self.values()
        values[1]["components"]["schemas"]["DeploymentPlan"]["properties"][
            "sourceRelease"
        ]["oneOf"].append({"type": "integer"})
        self.assertInvalid(values)

    def test_62_deployer_api_version_is_1_1(self):
        values = self.values()
        values[1]["info"]["version"] = "1.0.0"
        self.assertInvalid(values)

    def test_63_rollback_capability_is_not_advertised(self):
        values = self.values()
        values[1]["components"]["schemas"]["CapabilityResponse"]["examples"][0][
            "capabilities"
        ].append("deployment:rollback")
        self.assertInvalid(values)

    def test_64_current_uncertainty_response_is_required(self):
        values = self.values()
        del values[1]["paths"]["/api/deployment-control/v1/current"]["get"][
            "responses"
        ]["409"]
        self.assertInvalid(values)

    def test_65_current_uncertainty_code_is_required(self):
        values = self.values()
        values[1]["components"]["schemas"]["ProblemDetails"]["properties"]["code"][
            "enum"
        ].remove("CURRENT_INSTALLATION_UNRECONCILED")
        self.assertInvalid(values)

    def test_66_rollback_must_not_advertise_202(self):
        values = self.values()
        values[1]["paths"]["/api/deployment-control/v1/rollbacks"]["post"][
            "responses"
        ]["202"] = {"description": "incorrect"}
        self.assertInvalid(values)

    def test_67_rollback_409_is_release_not_eligible(self):
        values = self.values()
        values[1]["components"]["schemas"]["RollbackUnavailableProblem"][
            "properties"
        ]["code"]["const"] = "PRODUCTION_OPERATION_ACTIVE"
        self.assertInvalid(values)

    def test_68_state_machine_schema_version_is_2(self):
        values = self.values()
        values[2]["schema_version"] = 1
        self.assertInvalid(values)

    def test_69_queued_success_requires_remote_evidence(self):
        values = self.values()
        transition = next(
            item
            for item in values[2]["machines"]["deployment"]["transitions"]
            if item["from"] == "QUEUED" and item["to"] == "SUCCEEDED"
        )
        transition["requires_remote_evidence"] = False
        self.assertInvalid(values)

    def test_70_queued_rolled_back_transition_is_required(self):
        values = self.values()
        values[2]["machines"]["deployment"]["transitions"] = [
            item
            for item in values[2]["machines"]["deployment"]["transitions"]
            if not (item["from"] == "QUEUED" and item["to"] == "ROLLED_BACK")
        ]
        self.assertInvalid(values)

    def test_71_workflow_run_id_is_immutable_after_correlation(self):
        values = self.values()
        values[2]["reconciliation"]["workflow_run_binding"][
            "immutable_run_id_after_correlation"
        ] = False
        self.assertInvalid(values)

    def test_72_attempt_may_not_regress(self):
        values = self.values()
        values[2]["reconciliation"]["workflow_run_binding"][
            "attempt_may_regress"
        ] = True
        self.assertInvalid(values)

    def test_73_deployer_operation_id_is_exact(self):
        values = self.values()
        values[1]["components"]["schemas"]["OperationId"]["pattern"] = ".*"
        self.assertInvalid(values)


if __name__ == "__main__":
    unittest.main()
