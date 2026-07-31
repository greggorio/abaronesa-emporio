#!/usr/bin/env python3
"""Fail-closed validator for the offline S25 commercial rollback contract."""

from __future__ import annotations

import re
import sys
from pathlib import Path
from typing import Any

import yaml


ROOT = Path(__file__).resolve().parents[2]
DOC = Path("docs/infrastructure/deployment/release-control/ROLLBACK_COMERCIAL.md")
OPENAPI = Path(
    "docs/infrastructure/deployment/release-control/api/rollback.openapi.yml"
)
STATES = Path(
    "docs/infrastructure/deployment/release-control/contracts/rollback-state-machine.yml"
)
SECURITY = Path(
    "docs/infrastructure/deployment/release-control/contracts/rollback-security.yml"
)
README = Path("docs/infrastructure/deployment/release-control/README.md")
CONTRACT = Path(
    "docs/infrastructure/deployment/release-control/CONTRATO_API_ESTADOS_SEGURANCA.md"
)
RUNTIME = Path(
    "docs/infrastructure/deployment/release-control/RUNTIME_DEPLOYER.md"
)

REQUIRED = (DOC, OPENAPI, STATES, SECURITY, README, CONTRACT, RUNTIME)
API_PATHS = {
    "/api/deployment-control/v1/rollbacks",
    "/api/deployment-control/v1/rollbacks/{operationId}",
}
ROLLBACK_STATES = (
    "QUEUED",
    "PRECHECKING",
    "RESTORING",
    "SWITCHING",
    "VERIFYING",
    "SUCCEEDED",
    "ROLLING_BACK",
    "ROLLED_BACK",
    "FAILED",
    "UNCERTAIN",
)
TRANSITIONS = frozenset(
    {
        ("QUEUED", "PRECHECKING"),
        ("PRECHECKING", "RESTORING"),
        ("PRECHECKING", "SWITCHING"),
        ("PRECHECKING", "FAILED"),
        ("PRECHECKING", "UNCERTAIN"),
        ("RESTORING", "SWITCHING"),
        ("RESTORING", "FAILED"),
        ("RESTORING", "ROLLING_BACK"),
        ("RESTORING", "UNCERTAIN"),
        ("SWITCHING", "VERIFYING"),
        ("SWITCHING", "ROLLING_BACK"),
        ("SWITCHING", "UNCERTAIN"),
        ("VERIFYING", "SUCCEEDED"),
        ("VERIFYING", "ROLLING_BACK"),
        ("VERIFYING", "UNCERTAIN"),
        ("ROLLING_BACK", "ROLLED_BACK"),
        ("ROLLING_BACK", "UNCERTAIN"),
    }
)
TERMINAL_STATES = frozenset(("SUCCEEDED", "ROLLED_BACK", "FAILED", "UNCERTAIN"))
TERMINAL_STATES_ORDER = ("SUCCEEDED", "ROLLED_BACK", "FAILED", "UNCERTAIN")
RELEASE_PATTERN = r"^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$"
DIGEST_PATTERN = r"^sha256:[0-9a-f]{64}$"
IDEMPOTENCY_PATTERN = (
    r"^deployer-rollback-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-"
    r"[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
)


class ValidationError(ValueError):
    """Stable validation failure exposed by the local validator."""


def require(condition: bool, code: str) -> None:
    if not condition:
        raise ValidationError(code)


def read_text(root: Path, path: Path) -> str:
    return (root / path).read_text(encoding="utf-8")


def read_yaml(root: Path, path: Path) -> dict[str, Any]:
    value = yaml.safe_load(read_text(root, path))
    require(isinstance(value, dict), f"yaml-object:{path.name}")
    return value


def require_mapping(value: Any, code: str) -> dict[str, Any]:
    require(isinstance(value, dict), code)
    return value


def require_exact(value: Any, expected: Any, code: str) -> None:
    require(value == expected, code)


def validate_openapi(root: Path) -> None:
    value = read_yaml(root, OPENAPI)
    require_exact(value.get("openapi"), "3.1.0", "openapi-version")
    metadata = require_mapping(value.get("x-emporio-contract"), "openapi-metadata")
    require_exact(metadata.get("status"), "future-only", "openapi-status")
    require_exact(metadata.get("runtime-consumer"), "none", "openapi-runtime-consumer")
    require_exact(metadata.get("activation-slice"), "S26", "openapi-activation")
    require_exact(metadata.get("current-capability"), "absent", "openapi-capability")
    require_exact(
        metadata.get("current-runtime-route"),
        "reserved-and-unavailable",
        "openapi-route-status",
    )

    paths = require_mapping(value.get("paths"), "openapi-paths")
    require(set(paths) == API_PATHS, "openapi-path-surface")
    post = require_mapping(paths["/api/deployment-control/v1/rollbacks"].get("post"), "openapi-post")
    get = require_mapping(
        paths["/api/deployment-control/v1/rollbacks/{operationId}"].get("get"),
        "openapi-get",
    )
    for operation, code in ((post, "post"), (get, "get")):
        require_exact(operation.get("x-required-role"), "deployment:rollback", f"openapi-{code}-scope")
        require_exact(operation.get("x-availability"), "reserved-until-S26", f"openapi-{code}-availability")
        require_exact(operation.get("security"), [{"bearerAuth": []}], f"openapi-{code}-auth")

    post_parameters = require_mapping(post.get("parameters", [])[0], "openapi-idempotency-parameter")
    require_exact(post_parameters.get("$ref"), "#/components/parameters/IdempotencyKey", "openapi-idempotency-ref")
    get_parameters = require_mapping(get.get("parameters", [])[0], "openapi-operation-parameter")
    require_exact(get_parameters.get("$ref"), "#/components/parameters/OperationId", "openapi-operation-ref")

    components = require_mapping(value.get("components"), "openapi-components")
    parameters = require_mapping(components.get("parameters"), "openapi-parameters")
    idempotency = require_mapping(parameters.get("IdempotencyKey"), "openapi-idempotency")
    require_exact(idempotency.get("name"), "Idempotency-Key", "openapi-idempotency-name")
    require_exact(idempotency.get("in"), "header", "openapi-idempotency-location")
    require_exact(idempotency.get("required"), True, "openapi-idempotency-required")
    idempotency_schema = require_mapping(idempotency.get("schema"), "openapi-idempotency-schema")
    require_exact(idempotency_schema.get("pattern"), IDEMPOTENCY_PATTERN, "openapi-idempotency-pattern")
    require_exact(idempotency_schema.get("minLength"), 54, "openapi-idempotency-min")
    require_exact(idempotency_schema.get("maxLength"), 54, "openapi-idempotency-max")

    schemas = require_mapping(components.get("schemas"), "openapi-schemas")
    request = require_mapping(schemas.get("RollbackRequest"), "openapi-request")
    require_exact(request.get("type"), "object", "openapi-request-type")
    require_exact(request.get("additionalProperties"), False, "openapi-request-closed")
    require_exact(request.get("required"), ["release", "reason"], "openapi-request-fields")
    require(set(require_mapping(request.get("properties"), "openapi-request-properties")) == {"release", "reason"}, "openapi-request-properties")
    require_exact(
        require_mapping(request["properties"]["release"], "openapi-release-property").get("$ref"),
        "#/components/schemas/ReleaseId",
        "openapi-release-property",
    )
    reason = require_mapping(request["properties"]["reason"], "openapi-reason")
    require_exact(reason.get("type"), "string", "openapi-reason-type")
    require_exact(reason.get("minLength"), 10, "openapi-reason-min")
    require_exact(reason.get("maxLength"), 1000, "openapi-reason-max")

    operation = require_mapping(schemas.get("RollbackOperation"), "openapi-operation")
    require_exact(operation.get("type"), "object", "openapi-operation-type")
    require_exact(operation.get("additionalProperties"), False, "openapi-operation-closed")
    properties = require_mapping(operation.get("properties"), "openapi-operation-properties")
    required = set(operation.get("required", []))
    require({"operationId", "operationType", "state", "sourceRelease", "targetRelease", "databaseRestoreRequired", "createdAt", "updatedAt"} <= required, "openapi-operation-required")
    require_exact(properties.get("operationType", {}).get("const"), "rollback", "openapi-operation-kind")
    require_exact(properties.get("state", {}).get("enum"), list(ROLLBACK_STATES), "openapi-operation-states")
    require_exact(properties.get("sourceRelease", {}).get("$ref"), "#/components/schemas/ReleaseId", "openapi-source-release")
    require_exact(properties.get("targetRelease", {}).get("$ref"), "#/components/schemas/ReleaseId", "openapi-target-release")
    require_exact(properties.get("databaseRestoreRequired", {}).get("type"), "boolean", "openapi-restore-flag")

    release = require_mapping(schemas.get("ReleaseId"), "openapi-release-schema")
    require_exact(release.get("pattern"), RELEASE_PATTERN, "openapi-release-pattern")
    operation_id = require_mapping(schemas.get("OperationId"), "openapi-operation-id")
    require_exact(operation_id.get("pattern"), r"^rbk_[0-9a-f]{32}$", "openapi-operation-id-pattern")
    require_exact(operation_id.get("minLength"), 36, "openapi-operation-id-min")
    require_exact(operation_id.get("maxLength"), 36, "openapi-operation-id-max")

    problem = require_mapping(schemas.get("ProblemDetails"), "openapi-problem")
    require_exact(problem.get("additionalProperties"), False, "openapi-problem-closed")
    codes = require_mapping(problem.get("properties"), "openapi-problem-properties")["code"]
    require(set(codes.get("enum", [])) == {"BAD_REQUEST", "UNAUTHORIZED", "FORBIDDEN", "NOT_FOUND", "IDEMPOTENCY_CONFLICT", "PRODUCTION_OPERATION_ACTIVE", "RELEASE_NOT_ELIGIBLE", "UNPROCESSABLE", "RATE_LIMITED", "INTERNAL_ERROR"}, "openapi-problem-codes")


def validate_states(root: Path) -> None:
    value = read_yaml(root, STATES)
    require_exact(value.get("schema_version"), 1, "states-schema-version")
    require_exact(value.get("contract_status"), "future_only", "states-status")
    require_exact(value.get("runtime_consumer"), "none", "states-runtime-consumer")
    require_exact(value.get("activation_slice"), "S26", "states-activation")
    require_exact(value.get("operation_type"), "rollback", "states-operation-type")
    require_exact(value.get("initial_state"), "QUEUED", "states-initial")
    states = require_mapping(value.get("states"), "states-map")
    require(set(states) == set(ROLLBACK_STATES), "states-vocabulary")
    terminal = {name for name, config in states.items() if require_mapping(config, f"state:{name}").get("terminal") is True}
    require_exact(terminal, set(TERMINAL_STATES), "states-terminal")
    require(states["SUCCEEDED"].get("success") is True, "states-success")
    require(states["ROLLED_BACK"].get("failure") is True, "states-rolled-back")
    require(states["FAILED"].get("failure") is True, "states-failed")
    require(states["UNCERTAIN"].get("uncertain") is True, "states-uncertain")

    transitions = value.get("transitions")
    require(isinstance(transitions, list), "states-transitions")
    transition_pairs: set[tuple[str, str]] = set()
    for item in transitions:
        transition = require_mapping(item, "state-transition-shape")
        source = transition.get("from")
        target = transition.get("to")
        require(isinstance(source, str) and isinstance(target, str), "state-transition-endpoints")
        transition_pairs.add((source, target))
        require_exact(transition.get("actor"), "reconciler", "state-transition-actor")
        require(source not in TERMINAL_STATES, "state-terminal-outgoing")
    require_exact(transition_pairs, set(TRANSITIONS), "state-transition-vocabulary")
    require_exact(value.get("terminal_states"), list(TERMINAL_STATES_ORDER), "states-terminal-list")
    require_exact(value.get("main_flow"), ["QUEUED", "PRECHECKING", "RESTORING", "SWITCHING", "VERIFYING", "SUCCEEDED"], "states-main-flow")
    invariants = require_mapping(value.get("invariants"), "states-invariants")
    expected_invariants = {
        "only_reconciler_transitions": True,
        "client_cannot_set_state": True,
        "terminal_states_have_no_outgoing_transitions": True,
        "terminal_states_repeat_side_effects": False,
        "success_requires_reconciled_target": True,
        "success_requires_restore_evidence_when_required": True,
        "database_restore_required_stays_true_until_terminal_restore_evidence": True,
        "uncertainty_blocks_new_operation_until_human_reconciliation": True,
        "compensating_rollback_is_not_commercial_rollback_success": True,
    }
    for name, expected in expected_invariants.items():
        require_exact(invariants.get(name), expected, f"states-invariant:{name}")


def validate_security(root: Path) -> None:
    value = read_yaml(root, SECURITY)
    require_exact(value.get("schema_version"), 1, "security-schema-version")
    require_exact(value.get("contract_status"), "future_only", "security-status")
    require_exact(value.get("runtime_consumer"), "none", "security-runtime-consumer")
    require_exact(value.get("activation_slice"), "S26", "security-activation")
    current = require_mapping(value.get("current_runtime"), "security-current")
    require_exact(current.get("deployer_capabilities_exact"), ["deployment:read", "deployment:execute"], "security-current-capabilities")
    require_exact(current.get("rollback_capability"), "absent", "security-current-rollback-capability")
    require_exact(current.get("rollback_route"), "reserved_and_unavailable", "security-current-route")
    require_exact(current.get("ui"), "unchanged_and_unavailable", "security-current-ui")

    authorization = require_mapping(value.get("authorization"), "security-authorization")
    require_exact(authorization.get("future_scope"), "deployment:rollback", "security-future-scope")
    require_exact(authorization.get("route_method"), "POST", "security-route-method")
    require_exact(authorization.get("route_path"), "/api/deployment-control/v1/rollbacks", "security-route-path")
    for key in ("server_calculates_eligibility", "capability_enabled_by_this_contract"):
        require_exact(authorization.get(key), True if key == "server_calculates_eligibility" else False, f"security-authorization:{key}")
    for key in ("operator_selects_component", "operator_selects_digest", "operator_selects_tag", "operator_selects_image"):
        require_exact(authorization.get(key), False, f"security-operator-selection:{key}")

    eligibility = require_mapping(value.get("eligibility"), "security-eligibility")
    current_eligibility = require_mapping(eligibility.get("current_installation"), "security-current-eligibility")
    for key in ("reconciled_required", "snapshot_required", "source_commit_verified", "manifest_verified"):
        require_exact(current_eligibility.get(key), True, f"security-current-rule:{key}")
    target = require_mapping(eligibility.get("target_release"), "security-target")
    require_exact(target.get("kind"), "global-release", "security-target-kind")
    for key in ("published_required", "deployable_required", "immutable_required", "previous_required", "strictly_before_current", "immediate_predecessor_required", "same_chain_required"):
        require_exact(target.get(key), True, f"security-target-rule:{key}")
    for key in ("candidate_allowed", "non_deployable_allowed", "divergent_predecessor_allowed", "skipped_release_allowed"):
        require_exact(target.get(key), False, f"security-target-rule:{key}")

    migrations = require_mapping(value.get("migrations"), "security-migrations")
    require_exact(migrations.get("databases_in_order"), ["erp", "website"], "security-database-order")
    require_exact(migrations.get("compare_fields"), ["version", "path", "sha256"], "security-migration-fields")
    for key in ("applied_since_target_requires_restore", "reversible_delta_exception_requires_integral_explicit_proof", "absent_proof_requires_restore", "restore_required_is_sticky_until_terminal_evidence", "image_revert_without_restore_is_not_success"):
        require_exact(migrations.get(key), True, f"security-migration-rule:{key}")

    backup = require_mapping(value.get("backup"), "security-backup")
    require_exact(backup.get("canonical_fields"), ["backupId", "sourceRelease", "sourceStateSha256", "databases", "artifactSha256", "createdAt", "expiresAt"], "security-backup-fields")
    require_exact(backup.get("databases_exact"), ["erp", "website"], "security-backup-databases")
    require_exact(backup.get("retention_minimum_days"), 365, "security-backup-retention")
    require_exact(backup.get("silent_renewal"), False, "security-backup-renewal")
    require_exact(backup.get("required_before_restore"), True, "security-backup-before-restore")
    require(set(backup.get("forbidden_fields", [])) == {"path", "credential", "credentials", "dump", "dumpContent", "privateUrl", "url"}, "security-backup-forbidden-fields")

    data = require_mapping(value.get("data_and_sessions"), "security-data")
    uploads = require_mapping(data.get("uploads"), "security-uploads")
    require_exact(uploads.get("deleted_implicitly"), False, "security-uploads-delete")
    require_exact(uploads.get("restored_implicitly"), False, "security-uploads-restore")
    require_exact(uploads.get("restore_requires_explicit_evidence"), True, "security-uploads-evidence")
    require_exact(uploads.get("restore_requires_contracted_operation"), True, "security-uploads-operation")
    session = require_mapping(data.get("whatsapp_session"), "security-session")
    require_exact(session.get("restored_automatically"), False, "security-session-restore")
    require_exact(session.get("incompatibility_state"), "safe_and_manual_repair_required", "security-session-incompatibility")

    api = require_mapping(value.get("api"), "security-api")
    require_exact(api.get("request_exact_fields"), ["release", "reason"], "security-request-fields")
    require_exact(api.get("request_additional_properties"), False, "security-request-closed")
    require_exact(api.get("reason_min_length"), 10, "security-reason-min")
    require_exact(api.get("reason_max_length"), 1000, "security-reason-max")
    idempotency = require_mapping(api.get("idempotency"), "security-idempotency")
    require_exact(idempotency.get("header"), "Idempotency-Key", "security-idempotency-header")
    require_exact(idempotency.get("pattern"), "^deployer-rollback-<UUID v4>$", "security-idempotency-pattern")
    require_exact(idempotency.get("replay_identical_request"), "same_operation", "security-idempotency-replay")
    require_exact(idempotency.get("divergent_request_same_key"), "IDEMPOTENCY_CONFLICT", "security-idempotency-conflict")
    require_exact(idempotency.get("automatic_retry_after_network_or_invalid_response"), False, "security-idempotency-retry")
    require_exact(api.get("operation_type"), "rollback", "security-operation-type")
    lock = require_mapping(api.get("lock"), "security-lock")
    require_exact(lock.get("name"), "production_global", "security-lock-name")
    require_exact(lock.get("shared_with"), ["deployment"], "security-lock-sharing")
    require_exact(lock.get("max_active"), 1, "security-lock-max")
    require_exact(lock.get("conflict_code"), "PRODUCTION_OPERATION_ACTIVE", "security-lock-conflict")
    require_exact(lock.get("cancels_active_operation"), False, "security-lock-cancel")

    recovery = require_mapping(value.get("recovery"), "security-recovery")
    require_exact(recovery.get("states"), list(ROLLBACK_STATES), "security-recovery-states")
    require_exact(recovery.get("failed_before_switch"), "FAILED", "security-recovery-failed")
    require_exact(recovery.get("side_effect_failure_requires_compensation"), True, "security-recovery-compensation")
    require_exact(recovery.get("complete_compensation"), "ROLLED_BACK", "security-recovery-rolled-back")
    require_exact(recovery.get("uncertainty_sources"), ["database", "links", "volume", "operation", "journal"], "security-recovery-uncertainty-sources")
    require_exact(recovery.get("uncertainty_state"), "UNCERTAIN", "security-recovery-uncertainty-state")
    require_exact(recovery.get("uncertainty_blocks_new_operation"), True, "security-recovery-block")
    require_exact(recovery.get("terminal_states_repeat_side_effects"), False, "security-recovery-terminal")

    forbidden = require_mapping(value.get("forbidden_activation"), "security-forbidden-activation")
    for key in forbidden:
        require_exact(forbidden[key], False, f"security-forbidden:{key}")


def validate_documentation(root: Path) -> None:
    text = read_text(root, DOC).casefold()
    for phrase in (
        "contrato futuro",
        "não é consumido",
        "não executa rollback",
        "elegibilidade",
        "predecessor",
        "compensação forward",
        "databaseRestoreRequired",
        "365 dias",
        "uploads não",
        "sessão whatsapp",
        "idempotency-key",
        "uncertain",
        "rolled_back",
        "não é sinônimo",
        "não habilita",
    ):
        require(phrase.casefold() in text, f"documentation:{phrase}")
    readme = read_text(root, README)
    for reference in (
        "ROLLBACK_COMERCIAL.md",
        "api/rollback.openapi.yml",
        "contracts/rollback-state-machine.yml",
        "contracts/rollback-security.yml",
    ):
        require(reference in readme, f"readme-reference:{reference}")
    contract = read_text(root, CONTRACT).casefold()
    runtime = read_text(root, RUNTIME).casefold()
    require(
        "rollback comercial nao e anunciado" in contract
        or "rollback comercial não é anunciado" in contract,
        "active-contract-forward-only",
    )
    require(
        "deployment:rollback" in runtime
        and (
            "deployment:rollback nao aparece em" in runtime
            or "não anuncia `deployment:rollback`" in runtime
        ),
        "active-runtime-capability",
    )


def validate(root: Path = ROOT) -> None:
    require(all((root / path).is_file() for path in REQUIRED), "required-file")
    validate_openapi(root)
    validate_states(root)
    validate_security(root)
    validate_documentation(root)


def main() -> int:
    try:
        validate()
    except (OSError, UnicodeError, ValueError, yaml.YAMLError) as exc:
        code = str(exc) if isinstance(exc, ValidationError) else "validation-error"
        print(f"rollback-contract:invalid:{code}", file=sys.stderr)
        return 3
    print("rollback-contract:valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
