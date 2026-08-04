#!/usr/bin/env python3
"""Fail-closed validator for release-control API, state and security contracts."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Any

import yaml


ROOT = Path(__file__).resolve().parents[2]
CONTRACT_ROOT = ROOT / "docs/infrastructure/deployment/release-control"
PUBLISHER_FILE = CONTRACT_ROOT / "api/publisher.openapi.yml"
DEPLOYER_FILE = CONTRACT_ROOT / "api/deployer.openapi.yml"
STATE_FILE = CONTRACT_ROOT / "contracts/state-machines.yml"
SECURITY_FILE = CONTRACT_ROOT / "contracts/security-matrix.yml"
HUMAN_DOC = CONTRACT_ROOT / "CONTRATO_API_ESTADOS_SEGURANCA.md"
README = CONTRACT_ROOT / "README.md"

COMMON_PATHS = {
    "/health/live",
    "/health/ready",
    "/api/release-control/v1/capabilities",
}
PUBLISHER_PATHS = COMMON_PATHS | {
    "/api/release-publisher/v1/candidates",
    "/api/release-publisher/v1/releases",
    "/api/release-publisher/v1/operations/{operationId}",
}
DEPLOYER_PATHS = COMMON_PATHS | {
    "/api/deployment-control/v1/current",
    "/api/deployment-control/v1/releases",
    "/api/deployment-control/v1/releases/{releaseId}/plan",
    "/api/deployment-control/v1/deployments",
    "/api/deployment-control/v1/deployments/{deploymentId}",
    "/api/deployment-control/v1/rollbacks",
    "/api/deployment-control/v1/rollbacks/{operationId}",
}
POSTS = {
    ("publisher", "/api/release-publisher/v1/releases"),
    ("deployer", "/api/deployment-control/v1/deployments"),
    ("deployer", "/api/deployment-control/v1/rollbacks"),
}
REQUEST_SCHEMAS = {
    ("publisher", "/api/release-publisher/v1/releases"): (
        "PublishReleaseRequest",
        {"candidateId", "versionBump", "description", "changelog"},
    ),
    ("deployer", "/api/deployment-control/v1/deployments"): (
        "DeploymentRequest",
        {"release"},
    ),
    ("deployer", "/api/deployment-control/v1/rollbacks"): (
        "RollbackRequest",
        {"release", "reason"},
    ),
}
EXPECTED_STATES = {
    "candidate_eligibility": {"NOT_ELIGIBLE", "READY"},
    "publication": {"REQUESTED", "VALIDATING", "PUBLISHING", "PUBLISHED", "FAILED"},
    "deployment": {
        "AVAILABLE",
        "QUEUED",
        "PULLING",
        "BACKING_UP",
        "MIGRATING",
        "UPDATING",
        "VERIFYING",
        "SUCCEEDED",
        "ROLLING_BACK",
        "ROLLED_BACK",
        "FAILED",
    },
}
EXPECTED_MAIN_FLOWS = {
    "candidate_eligibility": ["NOT_ELIGIBLE", "READY"],
    "publication": ["REQUESTED", "VALIDATING", "PUBLISHING", "PUBLISHED"],
    "deployment": [
        "QUEUED",
        "PULLING",
        "BACKING_UP",
        "MIGRATING",
        "UPDATING",
        "VERIFYING",
        "SUCCEEDED",
    ],
}
DEPLOYER_CAPABILITIES = [
    "deployment:read",
    "deployment:execute",
    "deployment:rollback",
]
WORKFLOW_RUN_BINDING = {
    "optional_before_discovery": True,
    "required_after_correlation": True,
    "immutable_run_id_after_correlation": True,
    "immutable_control_sha_after_correlation": True,
    "same_run_rerun_may_increase_attempt": True,
    "attempt_may_regress": False,
}
FORBIDDEN_FIELDS = {
    "mode",
    "releaseControlMode",
    "image",
    "images",
    "digest",
    "digests",
    "tag",
    "tags",
    "component",
    "components",
    "command",
    "commands",
    "path",
    "paths",
    "url",
    "workflow",
    "repository",
    "owner",
    "environment",
    "env",
}
EXPECTED_ROLES = {
    ("publisher", "GET", "/api/release-control/v1/capabilities"): "release:read",
    ("publisher", "GET", "/api/release-publisher/v1/candidates"): "release:read",
    ("publisher", "GET", "/api/release-publisher/v1/releases"): "release:read",
    ("publisher", "POST", "/api/release-publisher/v1/releases"): "release:publish",
    ("publisher", "GET", "/api/release-publisher/v1/operations/{operationId}"): "release:read",
    ("deployer", "GET", "/api/release-control/v1/capabilities"): "deployment:read",
    ("deployer", "GET", "/api/deployment-control/v1/current"): "deployment:read",
    ("deployer", "GET", "/api/deployment-control/v1/releases"): "deployment:read",
    ("deployer", "GET", "/api/deployment-control/v1/releases/{releaseId}/plan"): "deployment:read",
    ("deployer", "POST", "/api/deployment-control/v1/deployments"): "deployment:execute",
    ("deployer", "GET", "/api/deployment-control/v1/deployments/{deploymentId}"): "deployment:read",
    ("deployer", "POST", "/api/deployment-control/v1/rollbacks"): "deployment:rollback",
    ("deployer", "GET", "/api/deployment-control/v1/rollbacks/{operationId}"): "deployment:read",
}
RELEASE_PATTERN = r"^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$"
IDEMPOTENCY_KEY_PATTERN = r"^[A-Za-z0-9][A-Za-z0-9._:-]+$"
IDEMPOTENCY_KEY_SCHEMA = {
    "type": "string",
    "minLength": 16,
    "maxLength": 128,
    "pattern": IDEMPOTENCY_KEY_PATTERN,
}
STATE_FLAGS = {"terminal", "active", "success", "failure", "ui_visible"}
EXPECTED_STATE_METADATA = {
    "candidate_eligibility": {
        "NOT_ELIGIBLE": (False, False, False, True, True),
        "READY": (False, False, True, False, True),
    },
    "publication": {
        "REQUESTED": (False, True, False, False, True),
        "VALIDATING": (False, True, False, False, True),
        "PUBLISHING": (False, True, False, False, True),
        "PUBLISHED": (True, False, True, False, True),
        "FAILED": (True, False, False, True, True),
    },
    "deployment": {
        "AVAILABLE": (False, False, False, False, True),
        "QUEUED": (False, True, False, False, True),
        "PULLING": (False, True, False, False, True),
        "BACKING_UP": (False, True, False, False, True),
        "MIGRATING": (False, True, False, False, True),
        "UPDATING": (False, True, False, False, True),
        "VERIFYING": (False, True, False, False, True),
        "SUCCEEDED": (True, False, True, False, True),
        "ROLLING_BACK": (False, True, False, False, True),
        "ROLLED_BACK": (True, False, False, True, True),
        "FAILED": (True, False, False, True, True),
    },
}
EXPECTED_AUTHENTICATION = {
    "scheme": "bearer_jwt",
    "api_routes_authenticated": True,
    "health_routes_public": True,
    "issuer_required": True,
    "audience_required": True,
    "algorithm_allowlist_required": True,
    "rotation_required": True,
    "mode_from_claim": False,
}
EXPECTED_TRANSPORT = {
    "cors": "configured_allowlist",
    "mutation_rate_limit": "required",
    "polling_rate_limit": "required",
    "payload_limit": "required",
    "accepted_content_types": ["application/json"],
    "unexpected_content_type": "reject",
    "mutation_audit": "required",
    "sanitized_errors": True,
}


def load_yaml(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as stream:
        value = yaml.safe_load(stream)
    if not isinstance(value, dict):
        raise ValueError(f"{path}: root must be an object")
    return value


def load_contracts() -> tuple[dict[str, Any], ...]:
    return (
        load_yaml(PUBLISHER_FILE),
        load_yaml(DEPLOYER_FILE),
        load_yaml(STATE_FILE),
        load_yaml(SECURITY_FILE),
    )


def _operation(spec: dict[str, Any], path: str, method: str) -> dict[str, Any]:
    return spec.get("paths", {}).get(path, {}).get(method.lower(), {})


def _ref_name(ref: str) -> str:
    prefix = "#/components/schemas/"
    return ref[len(prefix) :] if isinstance(ref, str) and ref.startswith(prefix) else ""


def _request_schema(spec: dict[str, Any], path: str) -> dict[str, Any]:
    operation = _operation(spec, path, "post")
    schema = (
        operation.get("requestBody", {})
        .get("content", {})
        .get("application/json", {})
        .get("schema", {})
    )
    name = _ref_name(schema.get("$ref", ""))
    return spec.get("components", {}).get("schemas", {}).get(name, {})


def _has_required_idempotency(
    spec: dict[str, Any], operation: dict[str, Any]
) -> bool:
    for parameter in operation.get("parameters", []):
        expected_schema = IDEMPOTENCY_KEY_SCHEMA
        if parameter.get("$ref") == "#/components/parameters/IdempotencyKey":
            parameter = (
                spec.get("components", {})
                .get("parameters", {})
                .get("IdempotencyKey", {})
            )
        elif parameter.get("$ref") == "#/components/parameters/RollbackIdempotencyKey":
            parameter = (
                spec.get("components", {})
                .get("parameters", {})
                .get("RollbackIdempotencyKey", {})
            )
            expected_schema = parameter.get("schema", {})
        if (
            parameter.get("name") == "Idempotency-Key"
            and parameter.get("in") == "header"
            and parameter.get("required") is True
            and parameter.get("schema") == expected_schema
        ):
            return True
    return False


def _health_exposes_sensitive(spec: dict[str, Any]) -> bool:
    schema = spec.get("components", {}).get("schemas", {}).get("HealthResponse", {})
    properties = {str(item).lower() for item in schema.get("properties", {})}
    return bool(properties & {"token", "secret", "credential", "config", "mode", "url"})


def validate_contracts(
    publisher: dict[str, Any],
    deployer: dict[str, Any],
    states: dict[str, Any],
    security: dict[str, Any],
    *,
    check_files: bool = True,
) -> list[str]:
    errors: list[str] = []
    specs = {"publisher": publisher, "deployer": deployer}
    expected_paths = {"publisher": PUBLISHER_PATHS, "deployer": DEPLOYER_PATHS}

    for mode, spec in specs.items():
        if spec.get("openapi") != "3.1.0":
            errors.append(f"{mode}: OpenAPI must be 3.1.0")
        actual = set(spec.get("paths", {}))
        if actual != expected_paths[mode]:
            errors.append(f"{mode}: path set diverges")
        opposite_prefix = (
            "/api/deployment-control/" if mode == "publisher" else "/api/release-publisher/"
        )
        if any(path.startswith(opposite_prefix) for path in actual):
            errors.append(f"{mode}: contains opposite-mode route")

        bearer = (
            spec.get("components", {})
            .get("securitySchemes", {})
            .get("bearerAuth", {})
        )
        if bearer.get("type") != "http" or bearer.get("scheme") != "bearer":
            errors.append(f"{mode}: bearer auth missing")

        for path, path_item in spec.get("paths", {}).items():
            for method, operation in path_item.items():
                if method.lower() not in {"get", "post"}:
                    continue
                if path.startswith("/api/") and operation.get("security") != [
                    {"bearerAuth": []}
                ]:
                    errors.append(f"{mode}:{method.upper()}:{path}: API auth missing")
                if path.startswith("/health/") and operation.get("security") != []:
                    errors.append(f"{mode}:{method.upper()}:{path}: health must be public")
        if _health_exposes_sensitive(spec):
            errors.append(f"{mode}: health response exposes sensitive field")

        expected_version = "1.1.0" if mode == "deployer" else "1.0.0"
        if spec.get("info", {}).get("version") != expected_version:
            errors.append(f"{mode}: API version diverges")

        capability_mode = (
            spec.get("components", {})
            .get("schemas", {})
            .get("CapabilityResponse", {})
            .get("properties", {})
            .get("mode", {})
            .get("enum")
        )
        if capability_mode != [mode]:
            errors.append(f"{mode}: capability mode is incorrect")

        release_pattern = (
            spec.get("components", {})
            .get("schemas", {})
            .get("ReleaseId", {})
            .get("pattern")
        )
        if release_pattern != RELEASE_PATTERN:
            errors.append(f"{mode}: release ID pattern diverges")

    for mode, path in POSTS:
        spec = specs[mode]
        operation = _operation(spec, path, "post")
        if not _has_required_idempotency(spec, operation):
            errors.append(f"{mode}:POST:{path}: Idempotency-Key contract diverges")
        if "409" not in operation.get("responses", {}):
            errors.append(f"{mode}:POST:{path}: idempotency conflict not documented")
        schema_name, expected_properties = REQUEST_SCHEMAS[(mode, path)]
        schema = spec.get("components", {}).get("schemas", {}).get(schema_name, {})
        if schema.get("additionalProperties") is not False:
            errors.append(f"{schema_name}: request must reject additional properties")
        properties = set(schema.get("properties", {}))
        if properties != expected_properties:
            errors.append(f"{schema_name}: request property set diverges")
        if properties & FORBIDDEN_FIELDS:
            errors.append(f"{schema_name}: forbidden operational override")
        if set(schema.get("required", [])) != expected_properties:
            errors.append(f"{schema_name}: required property set diverges")

    version_enum = (
        publisher.get("components", {})
        .get("schemas", {})
        .get("PublishReleaseRequest", {})
        .get("properties", {})
        .get("versionBump", {})
        .get("enum")
    )
    if version_enum != ["MAJOR", "MINOR", "PATCH"]:
        errors.append("publisher: versionBump enum diverges")

    polling = _operation(
        publisher, "/api/release-publisher/v1/operations/{operationId}", "get"
    )
    polling_parameters = polling.get("parameters", [])
    polling_responses = polling.get("responses", {})
    if (
        polling.get("operationId") != "getPublicationStatus"
        or polling.get("x-required-role") != "release:read"
        or polling_parameters != [{"$ref": "#/components/parameters/OperationId"}]
        or set(polling_responses) != {"200", "400", "401", "403", "404", "429", "500"}
        or polling_responses.get("200", {})
        .get("content", {})
        .get("application/json", {})
        .get("schema", {})
        .get("$ref")
        != "#/components/schemas/PublicationOperation"
    ):
        errors.append("publisher: operation polling contract diverges")
    operation_parameter = (
        publisher.get("components", {}).get("parameters", {}).get("OperationId", {})
    )
    if operation_parameter != {
        "name": "operationId",
        "in": "path",
        "required": True,
        "schema": {"$ref": "#/components/schemas/OperationId"},
    }:
        errors.append("publisher: OperationId path parameter diverges")

    release_detail = (
        publisher.get("components", {}).get("schemas", {}).get("GlobalReleaseDetail", {})
    )
    expected_detail_properties = {
        "release",
        "sourceCommit",
        "state",
        "publishedAt",
        "manifestSchemaVersion",
        "componentDigests",
    }
    if (
        release_detail.get("type") != "object"
        or release_detail.get("additionalProperties") is not False
        or set(release_detail.get("properties", {})) != expected_detail_properties
        or set(release_detail.get("required", [])) != expected_detail_properties
        or "allOf" in release_detail
    ):
        errors.append("publisher: GlobalReleaseDetail must be flat and closed")

    deployer_schemas = deployer.get("components", {}).get("schemas", {})
    if deployer_schemas.get("OperationId") != {
        "type": "string",
        "minLength": 36,
        "maxLength": 36,
        "pattern": "^(dep|rbk)_[0-9a-f]{32}$",
    }:
        errors.append("deployer: operation ID contract diverges")
    deployer_capability = deployer_schemas.get("CapabilityResponse", {})
    if deployer_capability.get("examples") != [
        {
            "mode": "deployer",
            "apiVersion": "v1",
            "capabilities": DEPLOYER_CAPABILITIES,
        }
    ]:
        errors.append("deployer: rollback capability contract diverges")
    capability_items = (
        deployer_capability.get("properties", {}).get("capabilities", {})
    )
    if capability_items.get("minItems") != 3 or capability_items.get("maxItems") != 3:
        errors.append("deployer: capability cardinality diverges")

    current_responses = _operation(
        deployer, "/api/deployment-control/v1/current", "get"
    ).get("responses", {})
    if current_responses.get("409") != {
        "$ref": "#/components/responses/CurrentInstallationUnreconciled"
    }:
        errors.append("deployer: current uncertainty response diverges")
    public_codes = (
        deployer_schemas.get("ProblemDetails", {})
        .get("properties", {})
        .get("code", {})
        .get("enum", [])
    )
    if "CURRENT_INSTALLATION_UNRECONCILED" not in public_codes:
        errors.append("deployer: current uncertainty code missing")
    current_problem = deployer_schemas.get(
        "CurrentInstallationUnreconciledProblem", {}
    )
    if (
        current_problem.get("additionalProperties") is not False
        or current_problem.get("properties", {}).get("status", {}).get("const") != 409
        or current_problem.get("properties", {}).get("code", {}).get("const")
        != "CURRENT_INSTALLATION_UNRECONCILED"
        or deployer.get("components", {})
        .get("responses", {})
        .get("CurrentInstallationUnreconciled", {})
        .get("content", {})
        .get("application/problem+json", {})
        .get("schema", {})
        .get("$ref")
        != "#/components/schemas/CurrentInstallationUnreconciledProblem"
    ):
        errors.append("deployer: current uncertainty problem diverges")
    source_release = (
        deployer_schemas.get("DeploymentPlan", {})
        .get("properties", {})
        .get("sourceRelease")
    )
    if source_release != {
        "oneOf": [
            {"$ref": "#/components/schemas/ReleaseId"},
            {"type": "null"},
        ]
    }:
        errors.append("deployer: DeploymentPlan.sourceRelease diverges")
    deployment_operation = deployer_schemas.get("DeploymentOperation", {})
    if "activeOperationId" in deployment_operation.get("properties", {}):
        errors.append("deployer: normal operation exposes activeOperationId")
    conflict_schema = deployer_schemas.get("DeploymentConflictProblem", {})
    expected_conflict_properties = {
        "type",
        "title",
        "status",
        "code",
        "detail",
        "traceId",
        "activeOperationId",
    }
    if (
        conflict_schema.get("type") != "object"
        or conflict_schema.get("additionalProperties") is not False
        or set(conflict_schema.get("properties", {})) != expected_conflict_properties
        or set(conflict_schema.get("required", []))
        != {"type", "title", "status", "code", "traceId"}
        or conflict_schema.get("properties", {}).get("status", {}).get("const") != 409
        or conflict_schema.get("properties", {}).get("code", {}).get("enum")
        != ["IDEMPOTENCY_CONFLICT", "PRODUCTION_OPERATION_ACTIVE"]
        or conflict_schema.get("properties", {})
        .get("activeOperationId", {})
        .get("$ref")
        != "#/components/schemas/OperationId"
    ):
        errors.append("deployer: 409 conflict schema diverges")
    for path in ("/api/deployment-control/v1/deployments",):
        conflict_ref = (
            _operation(deployer, path, "post")
            .get("responses", {})
            .get("409", {})
            .get("content", {})
            .get("application/problem+json", {})
            .get("schema", {})
            .get("$ref")
        )
        if conflict_ref != "#/components/schemas/DeploymentConflictProblem":
            errors.append(f"deployer:POST:{path}: 409 conflict schema diverges")
    rollback_responses = _operation(
        deployer, "/api/deployment-control/v1/rollbacks", "post"
    ).get("responses", {})
    rollback_success = rollback_responses.get("202", {})
    if (
        rollback_success.get("content", {})
        .get("application/json", {})
        .get("schema", {})
        .get("$ref")
        != "#/components/schemas/RollbackOperation"
    ):
        errors.append("deployer: rollback acceptance response diverges")
    rollback_ref = (
        rollback_responses.get("409", {})
        .get("content", {})
        .get("application/problem+json", {})
        .get("schema", {})
        .get("$ref")
    )
    rollback_problem = deployer_schemas.get("RollbackUnavailableProblem", {})
    if (
        rollback_ref != "#/components/schemas/RollbackUnavailableProblem"
        or rollback_problem.get("additionalProperties") is not False
        or rollback_problem.get("properties", {}).get("status", {}).get("const") != 409
        or rollback_problem.get("properties", {}).get("code", {}).get("const")
        != "RELEASE_NOT_ELIGIBLE"
    ):
        errors.append("deployer: rollback unavailable response diverges")

    candidate_enum = (
        publisher.get("components", {})
        .get("schemas", {})
        .get("CandidateSummary", {})
        .get("properties", {})
        .get("eligibility", {})
        .get("enum")
    )
    if set(candidate_enum or []) != EXPECTED_STATES["candidate_eligibility"]:
        errors.append("publisher: candidate state enum diverges")
    publication_enum = (
        publisher.get("components", {})
        .get("schemas", {})
        .get("PublicationOperation", {})
        .get("properties", {})
        .get("state", {})
        .get("enum")
    )
    if set(publication_enum or []) != EXPECTED_STATES["publication"]:
        errors.append("publisher: publication state enum diverges")
    deployment_enum = (
        deployer.get("components", {})
        .get("schemas", {})
        .get("DeploymentOperation", {})
        .get("properties", {})
        .get("state", {})
        .get("enum")
    )
    if set(deployment_enum or []) != EXPECTED_STATES["deployment"] - {"AVAILABLE"}:
        errors.append("deployer: operation state enum diverges")

    machines = states.get("machines", {})
    if states.get("schema_version") != 2:
        errors.append("state machines schema version must be 2")
    if set(machines) != set(EXPECTED_STATES):
        errors.append("state machines set diverges")
    for machine_name, expected_states in EXPECTED_STATES.items():
        machine = machines.get(machine_name, {})
        state_defs = machine.get("states", {})
        if set(state_defs) != expected_states:
            errors.append(f"{machine_name}: state set diverges")
            continue
        for state_name, contract in state_defs.items():
            expected_values = EXPECTED_STATE_METADATA[machine_name][state_name]
            if set(contract) != STATE_FLAGS:
                errors.append(f"{machine_name}:{state_name}: state metadata set diverges")
                continue
            if any(type(contract.get(flag)) is not bool for flag in STATE_FLAGS):
                errors.append(f"{machine_name}:{state_name}: state metadata must be boolean")
                continue
            actual_values = tuple(
                contract[flag]
                for flag in ("terminal", "active", "success", "failure", "ui_visible")
            )
            if actual_values != expected_values:
                errors.append(f"{machine_name}:{state_name}: state metadata diverges")
            if contract["terminal"] and contract["active"]:
                errors.append(f"{machine_name}:{state_name}: terminal state cannot be active")
            if contract["success"] and contract["failure"]:
                errors.append(f"{machine_name}:{state_name}: success state cannot be failure")
        transitions = machine.get("transitions", [])
        edges = {(item.get("from"), item.get("to")) for item in transitions}
        for item in transitions:
            if item.get("from") not in state_defs or item.get("to") not in state_defs:
                errors.append(f"{machine_name}: transition references unknown state")
            if item.get("actor") not in {"reconciler", "internal_request"}:
                errors.append(f"{machine_name}: invalid transition actor")
        terminal_states = {
            name for name, contract in state_defs.items() if contract.get("terminal") is True
        }
        if any(source in terminal_states for source, _ in edges):
            errors.append(f"{machine_name}: terminal state has outgoing transition")
        main_flow = machine.get("main_flow")
        if main_flow != EXPECTED_MAIN_FLOWS[machine_name]:
            errors.append(f"{machine_name}: main flow diverges")
        elif any((left, right) not in edges for left, right in zip(main_flow, main_flow[1:])):
            errors.append(f"{machine_name}: main flow is not reachable")
        for item in transitions:
            if item.get("to") in {"PUBLISHED", "SUCCEEDED", "ROLLED_BACK"} and item.get(
                "requires_remote_evidence"
            ) is not True:
                errors.append(
                    f"{machine_name}: success transition lacks remote evidence"
                )

    bootstrap = states.get("bootstrap", {})
    if (
        bootstrap.get("variable") != "RELEASE_CONTROL_MODE"
        or bootstrap.get("allowed_values") != ["publisher", "deployer"]
        or bootstrap.get("client_selectable") is not False
        or bootstrap.get("immutable_at_runtime") is not True
    ):
        errors.append("bootstrap mode contract diverges")

    idempotency = states.get("idempotency", {})
    expected_idempotency = {
        "publisher:POST:/api/release-publisher/v1/releases",
        "deployer:POST:/api/deployment-control/v1/deployments",
        "deployer:POST:/api/deployment-control/v1/rollbacks",
    }
    if set(idempotency.get("required_operations", [])) != expected_idempotency:
        errors.append("idempotency operation set diverges")
    if idempotency.get("conflict_code") != "IDEMPOTENCY_CONFLICT":
        errors.append("idempotency conflict code missing")
    if idempotency.get("retry_starts_second_workflow") is not False:
        errors.append("idempotency replay may start duplicate workflow")

    production = states.get("concurrency", {}).get("production", {})
    if (
        production.get("lock") != "production_global"
        or set(production.get("shared_by", [])) != {"deployment", "rollback"}
        or production.get("max_active") != 1
        or production.get("new_request_cancels_active") is not False
    ):
        errors.append("deployment and rollback must share one production lock")

    reconciliation = states.get("reconciliation", {})
    if "nonterminal_requires_workflow_run_id" in reconciliation:
        errors.append("obsolete workflowRunId requirement remains")
    if reconciliation.get("workflow_run_binding") != WORKFLOW_RUN_BINDING:
        errors.append("workflow run binding semantics diverge")
    if "workflowRunId" in reconciliation.get("operation_record_required", []):
        errors.append("workflowRunId must be optional before discovery")
    optional_record = set(reconciliation.get("operation_record_optional", []))
    if not {"workflowRunId", "workflowRunUrl", "workflowAttempt", "controlSha"} <= optional_record:
        errors.append("operation record lacks optional workflow binding")
    if reconciliation.get("success_requires_remote_evidence") is not True:
        errors.append("success must require remote evidence")
    if reconciliation.get("terminal_state_regresses") is not False:
        errors.append("terminal operation may regress")
    if reconciliation.get("github_token_persisted") is not False:
        errors.append("GitHub token persistence is forbidden")
    deployment_transitions = machines.get("deployment", {}).get("transitions", [])
    transition_map = {
        (item.get("from"), item.get("to")): item for item in deployment_transitions
    }
    for target in ("SUCCEEDED", "ROLLED_BACK"):
        transition = transition_map.get(("QUEUED", target), {})
        if (
            transition.get("actor") != "reconciler"
            or transition.get("requires_remote_evidence") is not True
        ):
            errors.append(f"deployment: direct terminal transition to {target} diverges")
    queued_failed = transition_map.get(("QUEUED", "FAILED"), {})
    if queued_failed.get("actor") != "reconciler":
        errors.append("deployment: QUEUED to FAILED transition missing")

    matrix_entries = security.get("authorization", {}).get("routes", [])
    if security.get("authentication") != EXPECTED_AUTHENTICATION:
        errors.append("security authentication contract diverges")
    matrix = {
        (entry.get("mode"), entry.get("method"), entry.get("path")): entry
        for entry in matrix_entries
    }
    if set(matrix) != set(EXPECTED_ROLES):
        errors.append("security route matrix diverges")
    for key, role in EXPECTED_ROLES.items():
        entry = matrix.get(key, {})
        if entry.get("role") != role:
            errors.append(f"security role diverges: {key}")
        if entry.get("outbound_credential") != key[0]:
            errors.append(f"outbound credential diverges: {key}")
        operation = _operation(specs[key[0]], key[2], key[1])
        if operation.get("x-required-role") != role:
            errors.append(f"OpenAPI role diverges: {key}")

    credentials = security.get("outbound_credentials", {})
    for profile in ("publisher", "deployer", "vps", "build"):
        if profile not in credentials or credentials[profile].get("shared_with") != []:
            errors.append(f"{profile}: outbound credential must be isolated")
    if credentials.get("publisher") == credentials.get("deployer"):
        errors.append("publisher and deployer credentials are shared")

    if set(security.get("forbidden_request_fields", [])) != FORBIDDEN_FIELDS:
        errors.append("forbidden request fields diverge")
    prohibitions = security.get("prohibitions", {})
    required_false = {
        "client_selects_mode",
        "component_selection",
        "local_git_access",
        "docker_socket_access",
        "direct_ssh",
        "arbitrary_workflow",
        "arbitrary_repository",
        "arbitrary_url",
        "secrets_in_logs",
    }
    for key in required_false:
        if prohibitions.get(key) is not False:
            errors.append(f"prohibition must fail closed: {key}")
    if security.get("transport") != EXPECTED_TRANSPORT:
        errors.append("security transport contract diverges")

    if check_files:
        for path in (HUMAN_DOC, README, PUBLISHER_FILE, DEPLOYER_FILE, STATE_FILE, SECURITY_FILE):
            if not path.is_file():
                errors.append(f"referenced artifact missing: {path.relative_to(ROOT)}")
        human = HUMAN_DOC.read_text(encoding="utf-8") if HUMAN_DOC.is_file() else ""
        readme = README.read_text(encoding="utf-8") if README.is_file() else ""
        for marker in (
            "CURRENT_INSTALLATION_UNRECONCILED",
            "rollback comercial é anunciado",
            "workflowRunId",
            "outcome terminal canonico",
        ):
            if marker not in human:
                errors.append(f"human contract marker missing: {marker}")
        if "RUNTIME_DEPLOYER.md" not in readme:
            errors.append("release-control README lacks deployer runtime")
    return errors


def _parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["validate"])
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    _parse_args(argv)
    try:
        errors = validate_contracts(*load_contracts(), check_files=True)
    except (OSError, ValueError, yaml.YAMLError) as error:
        print(f"contract:error:{error}", file=sys.stderr)
        return 2
    if errors:
        for error in errors:
            print(f"contract:invalid:{error}", file=sys.stderr)
        return 2
    print("release-control-contract:valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
