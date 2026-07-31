#!/usr/bin/env python3
"""Fail-closed structural validator for the S22 deployer runtime."""

from __future__ import annotations

import ast
import re
import sys
from pathlib import Path
from typing import Any

import tomllib
import yaml

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = Path("release_control/src/emporio_release_control")
OPENAPI = Path(
    "docs/infrastructure/deployment/release-control/api/deployer.openapi.yml"
)
STATES = Path(
    "docs/infrastructure/deployment/release-control/contracts/state-machines.yml"
)

REQUIRED = {
    "release_control/migrations/versions/0002_deployer_runtime.py",
    "release_control/src/emporio_release_control/deployer_api.py",
    "release_control/src/emporio_release_control/deployer_schemas.py",
    "release_control/src/emporio_release_control/deployer_service.py",
    "release_control/src/emporio_release_control/deployer_reconciliation.py",
    "release_control/src/emporio_release_control/deployment_artifacts.py",
    "release_control/tests/test_deployer_api.py",
    "release_control/tests/test_deployer_persistence.py",
    "release_control/tests/test_deployer_reconciliation.py",
    "release_control/tests/test_deployer_remote_contract.py",
    "release_control/tests/test_mode_isolation.py",
    "docs/infrastructure/deployment/release-control/RUNTIME_DEPLOYER.md",
}
ROUTES = {
    ("GET", "/health/live"),
    ("GET", "/health/ready"),
    ("GET", "/api/release-control/v1/capabilities"),
    ("GET", "/api/deployment-control/v1/current"),
    ("GET", "/api/deployment-control/v1/releases"),
    ("GET", "/api/deployment-control/v1/releases/{release_id}/plan"),
    ("POST", "/api/deployment-control/v1/deployments"),
    ("GET", "/api/deployment-control/v1/deployments/{deployment_id}"),
    ("POST", "/api/deployment-control/v1/rollbacks"),
    ("GET", "/api/deployment-control/v1/rollbacks/{operation_id}"),
}
IDENTITIES = {
    "PUBLISHER_MODE": "publisher",
    "DEPLOYER_MODE": "deployer",
    "REPOSITORY": "greggorio/abaronesa-emporio",
    "OWNER": "greggorio",
    "REPO": "abaronesa-emporio",
    "REF": "main",
    "PUBLISHER_WORKFLOW": "publish-release.yml",
    "DEPLOYER_WORKFLOW": "deploy-production.yml",
    "GITHUB_API": "https://api.github.com",
}
FORBIDDEN_IMPORT_ROOTS = {"subprocess", "docker", "git", "paramiko", "fabric"}
FORBIDDEN_DEPENDENCIES = {"docker", "gitpython", "paramiko", "fabric", "sh"}
DEPLOYMENT_STATES = {
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
}


class ValidationError(ValueError):
    pass


def require(condition: bool, code: str) -> None:
    if not condition:
        raise ValidationError(code)


def _literal_assignments(path: Path) -> dict[str, Any]:
    tree = ast.parse(path.read_text(encoding="utf-8"))
    result: dict[str, Any] = {}
    for node in tree.body:
        if isinstance(node, ast.Assign) and len(node.targets) == 1:
            target = node.targets[0]
            if isinstance(target, ast.Name):
                try:
                    result[target.id] = ast.literal_eval(node.value)
                except (ValueError, TypeError):
                    if (
                        isinstance(node.value, ast.Call)
                        and isinstance(node.value.func, ast.Name)
                        and node.value.func.id == "frozenset"
                        and len(node.value.args) == 1
                    ):
                        try:
                            result[target.id] = frozenset(
                                ast.literal_eval(node.value.args[0])
                            )
                        except (ValueError, TypeError):
                            pass
    return result


def _runtime_routes(path: Path) -> set[tuple[str, str]]:
    tree = ast.parse(path.read_text(encoding="utf-8"))
    routes: set[tuple[str, str]] = set()
    for node in ast.walk(tree):
        if not isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            continue
        for decorator in node.decorator_list:
            if (
                isinstance(decorator, ast.Call)
                and isinstance(decorator.func, ast.Attribute)
                and decorator.func.attr in {"get", "post"}
                and decorator.args
                and isinstance(decorator.args[0], ast.Constant)
                and isinstance(decorator.args[0].value, str)
            ):
                routes.add((decorator.func.attr.upper(), decorator.args[0].value))
    return routes


def _forbidden_imports(paths: list[Path]) -> bool:
    for path in paths:
        tree = ast.parse(path.read_text(encoding="utf-8"))
        for node in ast.walk(tree):
            names: list[str] = []
            if isinstance(node, ast.Import):
                names = [item.name for item in node.names]
            elif isinstance(node, ast.ImportFrom) and node.module:
                names = [node.module]
            if any(name.split(".", 1)[0] in FORBIDDEN_IMPORT_ROOTS for name in names):
                return True
    return False


def _dependencies(path: Path) -> set[str]:
    value = tomllib.loads(path.read_text(encoding="utf-8"))
    raw = value.get("project", {}).get("dependencies", [])
    names: set[str] = set()
    for item in raw:
        if isinstance(item, str):
            name = re.split(r"[<>=!~ ;\[]", item, maxsplit=1)[0]
            names.add(name.lower().replace("_", "-"))
    return names


def _class_method(tree: ast.Module, class_name: str, method_name: str) -> ast.FunctionDef:
    for node in tree.body:
        if isinstance(node, ast.ClassDef) and node.name == class_name:
            for item in node.body:
                if isinstance(item, ast.FunctionDef) and item.name == method_name:
                    return item
    raise ValidationError(f"runtime-method:{class_name}.{method_name}")


def _node_vocabulary(node: ast.AST) -> set[str]:
    values: set[str] = set()
    for item in ast.walk(node):
        if isinstance(item, ast.Name):
            values.add(item.id)
        elif isinstance(item, ast.Attribute):
            values.add(item.attr)
        elif isinstance(item, ast.Constant) and isinstance(item.value, str):
            values.add(item.value)
    return values


def _literal_members(tree: ast.Module, name: str) -> tuple[str, ...]:
    for node in tree.body:
        value: ast.expr | None = None
        if (
            isinstance(node, ast.Assign)
            and len(node.targets) == 1
            and isinstance(node.targets[0], ast.Name)
            and node.targets[0].id == name
        ):
            value = node.value
        elif (
            isinstance(node, ast.AnnAssign)
            and isinstance(node.target, ast.Name)
            and node.target.id == name
        ):
            value = node.annotation
        if (
            isinstance(value, ast.Subscript)
            and isinstance(value.value, ast.Name)
            and value.value.id == "Literal"
        ):
            values = (
                value.slice.elts
                if isinstance(value.slice, ast.Tuple)
                else [value.slice]
            )
            require(
                all(
                    isinstance(value, ast.Constant)
                    and isinstance(value.value, str)
                    for value in values
                ),
                "deployer-problem-codes",
            )
            return tuple(value.value for value in values)  # type: ignore[misc]
    raise ValidationError("deployer-problem-codes")


def _test_functions(path: Path) -> set[str]:
    return {
        node.name
        for node in ast.parse(path.read_text(encoding="utf-8")).body
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))
        and node.name.startswith("test_")
    }


def validate(root: Path = ROOT) -> None:
    require(all((root / path).is_file() for path in REQUIRED), "required-file")
    package = root / PACKAGE
    python_files = sorted(package.glob("*.py"))
    require(not _forbidden_imports(python_files), "forbidden-capability")
    lowered = "\n".join(
        path.read_text(encoding="utf-8") for path in python_files
    ).lower()
    require(
        all(
            marker not in lowered
            for marker in ("docker.sock", "git clone", "gh api", "ssh ")
        ),
        "forbidden-capability",
    )
    require(
        not (
            _dependencies(root / "release_control/pyproject.toml")
            & FORBIDDEN_DEPENDENCIES
        ),
        "forbidden-dependency",
    )

    constants = _literal_assignments(package / "constants.py")
    require(
        all(constants.get(key) == value for key, value in IDENTITIES.items()),
        "identity",
    )
    require(
        "MODE" not in constants and "WORKFLOW" not in constants, "ambiguous-identity"
    )
    require(
        set(constants.get("DEPLOYMENT_STATES", ())) == DEPLOYMENT_STATES,
        "runtime-states",
    )

    api = package / "deployer_api.py"
    require(_runtime_routes(api) == ROUTES, "runtime-routes")
    api_text = api.read_text(encoding="utf-8")
    require(
        '"deployment:read"' in api_text
        and '"deployment:execute"' in api_text
        and '"deployment:rollback"'
        in api_text.partition("def capabilities")[2].partition("@app")[0],
        "rollback-capability",
    )
    require("/api/release-publisher/" not in api_text, "mixed-router")

    main = (package / "main.py").read_text(encoding="utf-8")
    require("create_app" in main and "create_deployer_app" in main, "mode-bootstrap")
    require("PUBLISHER_MODE" in main and "DEPLOYER_MODE" in main, "mode-bootstrap")

    service_path = package / "deployer_service.py"
    service = service_path.read_text(encoding="utf-8")
    for marker, code in (
        ("create_deployment", "deployment-service"),
        ("active_slot", "production-slot"),
        ("idempotency", "idempotency"),
        ("eligible", "release-eligibility"),
        ("previousRelease", "release-eligibility"),
        ("rollback.rejected", "rollback-unavailable"),
        ("RELEASE_NOT_ELIGIBLE", "rollback-unavailable"),
        ("CURRENT_INSTALLATION_UNRECONCILED", "current-evidence"),
        ("reconciled", "current-evidence"),
    ):
        require(marker in service, code)
    service_tree = ast.parse(service)
    evidence_words = _node_vocabulary(
        _class_method(service_tree, "DeployerService", "_current_evidence")
    )
    require(
        {
            "release", "source_commit", "installed_at", "last_operation_id",
            "ReleaseSnapshot", "_release_domain_green",
        }
        <= evidence_words,
        "current-evidence-causal",
    )
    listing_words = _node_vocabulary(
        _class_method(service_tree, "DeployerService", "list_releases")
    )
    require(
        "_current_evidence" in listing_words
        and "_current_or_clean" not in listing_words
        and "consistent" in listing_words,
        "uncertain-release-listing",
    )
    outcome_words = _node_vocabulary(
        _class_method(service_tree, "DeployerService", "apply_outcome")
    )
    require(
        "DEPLOYMENT_OUTCOME_RESTORE_CONFLICT" in outcome_words
        and {"CONFIRMED", "SUCCEEDED", "restore_required"} <= outcome_words,
        "restore-conflict",
    )
    race_words = _node_vocabulary(
        _class_method(service_tree, "DeployerService", "_resolve_integrity_race")
    )
    require(
        {"active_slot", "ActiveOperationFailure", "INTERNAL_ERROR"} <= race_words,
        "integrity-race",
    )

    migration = (
        root / "release_control/migrations/versions/0002_deployer_runtime.py"
    ).read_text(encoding="utf-8")
    for marker in (
        'down_revision: str | None = "0001_publisher_runtime"',
        '"rc_deployment_operation"',
        '"rc_deployment_idempotency_key"',
        '"rc_current_installation"',
        '"uq_rc_deployment_active_slot"',
        'postgresql_where=sa.text("active_slot = 1")',
    ):
        require(marker in migration, "migration-contract")
    migration_tree = ast.parse(migration)
    check_constraints = {
        item.args[0].value
        for item in ast.walk(migration_tree)
        if isinstance(item, ast.Call)
        and isinstance(item.func, ast.Attribute)
        and item.func.attr == "CheckConstraint"
        and item.args
        and isinstance(item.args[0], ast.Constant)
        and isinstance(item.args[0].value, str)
    }
    require(
        any(
            "dispatch_state = 'CONFIRMED'" in value
            and "dispatch_state IN ('NOT_SENT','SENT','UNCERTAIN')" in value
            and value.count("workflow_run_id IS NOT NULL") == 1
            and value.count("workflow_run_id IS NULL") == 1
            for value in check_constraints
        ),
        "workflow-binding-state",
    )

    reconciliation = (package / "deployer_reconciliation.py").read_text(
        encoding="utf-8"
    )
    for marker in (
        "DEPLOYMENT_TERMINAL_STATES",
        "WORKFLOW_RUNS_PATH",
        "display_title",
        "workflow_attempt",
        "control_sha",
        "validate_deployment_artifact",
        "validate_deployment_outcome",
        "validate_run_conclusion",
        "mark_uncertain",
        "apply_outcome",
    ):
        require(marker in reconciliation, "reconciliation-contract")
    reconciliation_tree = ast.parse(reconciliation)
    operation_words = _node_vocabulary(
        _class_method(reconciliation_tree, "DeployerReconciler", "_operation")
    )
    require(
        {
            "lineage_failures", "mark_uncertain", "RECONCILE_FAILED",
            "apply_outcome",
        }
        <= operation_words,
        "uncertain-remote-evidence",
    )
    require(
        "WORKFLOW_ATTEMPT_REGRESSION" in _node_vocabulary(reconciliation_tree),
        "uncertain-remote-evidence",
    )
    cycle_node = _class_method(reconciliation_tree, "DeployerReconciler", "cycle")
    cycle_words = _node_vocabulary(cycle_node)
    require(
        {
            "cleanup_expired_idempotency", "_operation", "_set_domain",
            "release_deployer_advisory_lock", "RECONCILE_FAILED",
        }
        <= cycle_words
        and any(isinstance(node, ast.Try) and node.finalbody for node in ast.walk(cycle_node)),
        "resilient-reconciliation-cycle",
    )
    require(
        not (DEPLOYMENT_STATES ^ set(constants.get("DEPLOYMENT_STATES", ()))),
        "invented-intermediate-state",
    )

    artifact = (package / "deployment_artifacts.py").read_text(encoding="utf-8")
    for marker in (
        "MAX_OUTCOME_ZIP_BYTES = 16 * 1024 * 1024",
        "MAX_OUTCOME_BYTES = 64 * 1024",
        "archive_download_url",
        'workflow_run.get("id") != run_id',
        'workflow_run.get("head_sha") != head_sha',
        '"operationId": operation_id',
        '"targetRelease": target_release',
        '"workflowRunId": run_id',
        '"workflowRunAttempt": attempt',
        '"controlSha": control_sha',
    ):
        require(marker in artifact, "artifact-binding")

    github = (package / "github.py").read_text(encoding="utf-8")
    require("def dispatch_deployment" in github, "deployment-dispatch")
    require(
        "DEPLOYER_WORKFLOW" in github and '"ref": REF' in github, "deployment-dispatch"
    )
    require("uncertain=True" in github, "dispatch-uncertainty")

    spec = yaml.safe_load((root / OPENAPI).read_text(encoding="utf-8"))
    require(
        isinstance(spec, dict) and spec.get("info", {}).get("version") == "1.1.0",
        "openapi-version",
    )
    openapi_routes = {
        (
            method.upper(),
            path.replace("{releaseId}", "{release_id}").replace(
                "{deploymentId}", "{deployment_id}"
            ).replace(
                "{operationId}", "{operation_id}"
            ),
        )
        for path, item in spec.get("paths", {}).items()
        for method in item
        if method in {"get", "post"}
    }
    require(openapi_routes == ROUTES, "openapi-routes")
    openapi_codes = tuple(
        spec["components"]["schemas"]["ProblemDetails"]["properties"]["code"][
            "enum"
        ]
    )
    schema_tree = ast.parse((package / "deployer_schemas.py").read_text(encoding="utf-8"))
    require(
        _literal_members(schema_tree, "DeployerProblemCode") == openapi_codes,
        "deployer-problem-codes",
    )

    states = yaml.safe_load((root / STATES).read_text(encoding="utf-8"))
    require(
        isinstance(states, dict) and states.get("schema_version") == 2, "state-contract"
    )
    transitions = (
        states.get("machines", {}).get("deployment", {}).get("transitions", [])
    )
    for target in ("SUCCEEDED", "ROLLED_BACK"):
        require(
            any(
                item.get("from") == "QUEUED"
                and item.get("to") == target
                and item.get("actor") == "reconciler"
                and item.get("requires_remote_evidence") is True
                for item in transitions
            ),
            "state-contract",
        )

    doc = (
        root / "docs/infrastructure/deployment/release-control/RUNTIME_DEPLOYER.md"
    ).read_text(encoding="utf-8")
    for marker in (
        "CURRENT_INSTALLATION_UNRECONCILED",
        "RELEASE_NOT_ELIGIBLE",
        "deploy-production.yml",
        "forward-only",
        "INDETERMINATE",
    ):
        require(marker in doc, "runtime-documentation")

    api_tests = _test_functions(root / "release_control/tests/test_deployer_api.py")
    persistence_tests = _test_functions(
        root / "release_control/tests/test_deployer_persistence.py"
    )
    reconciliation_tests = _test_functions(
        root / "release_control/tests/test_deployer_reconciliation.py"
    )
    require(
        {
            "test_inconsistent_current_is_fail_closed_without_mutating_evidence",
            "test_confirmed_success_requiring_restore_is_rejected_before_any_write",
            "test_integrity_race_reports_real_active_operation",
            "test_unrelated_integrity_failure_is_sanitized_internal_error",
        }
        <= api_tests,
        "causal-runtime-tests",
    )
    require(
        {
            "test_confirmed_dispatch_with_integral_workflow_binding_passes",
            "test_confirmed_dispatch_without_workflow_binding_fails",
            "test_integral_workflow_binding_with_sent_dispatch_fails",
            "test_partial_workflow_binding_fails_in_every_dispatch_state",
            "test_deployer_problem_code_literal_equals_openapi_enum_exactly",
        }
        <= persistence_tests,
        "causal-persistence-tests",
    )
    require(
        {
            "test_cycle_classifies_entire_lineage_and_marks_invalid_run_uncertain",
            "test_cycle_marks_every_binding_divergence_uncertain",
            "test_cycle_marks_apply_outcome_failure_uncertain",
            "test_list_runs_failure_marks_domain_only",
            "test_cleanup_failure_does_not_skip_query_and_domain_update",
            "test_operation_query_failure_still_sets_domain_red",
            "test_individual_failure_does_not_skip_remaining_operations",
            "test_set_domain_failure_and_lock_release_failure_do_not_escape",
            "test_lock_not_acquired_returns_false_without_domain_change",
        }
        <= reconciliation_tests,
        "causal-reconciliation-tests",
    )


def main() -> int:
    try:
        validate()
    except (
        OSError,
        SyntaxError,
        ValueError,
        tomllib.TOMLDecodeError,
        yaml.YAMLError,
    ) as exc:
        code = str(exc) if isinstance(exc, ValidationError) else "validation-error"
        print(f"deployer-runtime:invalid:{code}", file=sys.stderr)
        return 2
    print("deployer-runtime:valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
