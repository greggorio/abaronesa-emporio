#!/usr/bin/env python3
"""Fail-closed structural validation for the S15 publisher runtime."""

from __future__ import annotations

import ast
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
RUNTIME = ROOT / "release_control"
WORKFLOW = ROOT / ".github/workflows/publish-release.yml"
OPENAPI = ROOT / "docs/infrastructure/deployment/release-control/api/publisher.openapi.yml"

REQUIRED = {
    "release_control/pyproject.toml",
    "release_control/uv.lock",
    "release_control/.env.example",
    "release_control/alembic.ini",
    "release_control/migrations/env.py",
    "release_control/migrations/versions/0001_publisher_runtime.py",
    "release_control/src/emporio_release_control/api.py",
    "release_control/src/emporio_release_control/errors.py",
    "release_control/src/emporio_release_control/github.py",
    "release_control/src/emporio_release_control/persistence.py",
    "release_control/src/emporio_release_control/reconciliation.py",
    "release_control/src/emporio_release_control/sync.py",
    "release_control/src/emporio_release_control/schemas.py",
    "release_control/tests/test_api.py",
    "release_control/tests/test_persistence_service.py",
    "docs/infrastructure/deployment/release-control/RUNTIME_PUBLISHER.md",
    "docs/infrastructure/deployment/release-control/CONTRATO_API_ESTADOS_SEGURANCA.md",
}
ROUTES = {
    "/health/live",
    "/health/ready",
    "/api/release-control/v1/capabilities",
    "/api/release-publisher/v1/candidates",
    "/api/release-publisher/v1/releases",
    "/api/release-publisher/v1/operations/{operationId}",
}
FORBIDDEN = (
    "subprocess",
    "os.system",
    "git checkout",
    "git clone",
    "gh api",
    "docker.sock",
    "paramiko",
    "ssh ",
)
PUBLIC_CODES = {
    "BAD_REQUEST", "UNAUTHORIZED", "FORBIDDEN", "NOT_FOUND",
    "IDEMPOTENCY_CONFLICT", "VERSION_RESERVATION_CONFLICT", "UNPROCESSABLE",
    "RATE_LIMITED", "INTERNAL_ERROR", "SERVICE_UNAVAILABLE",
}
PUBLIC_FAILURE_PAIRS = {
    (400, "BAD_REQUEST"), (401, "UNAUTHORIZED"), (403, "FORBIDDEN"),
    (404, "NOT_FOUND"), (409, "IDEMPOTENCY_CONFLICT"),
    (409, "VERSION_RESERVATION_CONFLICT"), (422, "UNPROCESSABLE"),
    (429, "RATE_LIMITED"),
}
RELEASE_ASSETS = {
    "release.json": (2 * 1024 * 1024, "application/json"),
    "release.json.sha256": (128, "text/plain"),
    "metadata.json": (16 * 1024, "application/json"),
}


class ValidationError(ValueError):
    pass


def require(condition: bool, code: str) -> None:
    if not condition:
        raise ValidationError(code)


def assignment(tree: ast.Module, name: str) -> ast.expr:
    for node in tree.body:
        if isinstance(node, (ast.Assign, ast.AnnAssign)):
            targets = node.targets if isinstance(node, ast.Assign) else [node.target]
            if any(isinstance(target, ast.Name) and target.id == name for target in targets):
                return node.value
    raise ValidationError(f"assignment:{name}")


def literal_members(node: ast.expr) -> set[str]:
    if not isinstance(node, ast.Subscript):
        raise ValidationError("public-error-enum")
    values = node.slice.elts if isinstance(node.slice, ast.Tuple) else [node.slice]
    if any(not isinstance(value, ast.Constant) or not isinstance(value.value, str) for value in values):
        raise ValidationError("public-error-enum")
    return {value.value for value in values if isinstance(value, ast.Constant)}


def validate(root: Path = ROOT) -> None:
    require(all((root / item).is_file() for item in REQUIRED), "required-file")
    workflow = (root / WORKFLOW.relative_to(ROOT)).read_text(encoding="utf-8")
    require(
        "run-name: publish-release-${{ inputs.operation_id }}" in workflow,
        "workflow-run-name",
    )
    runtime_files = sorted((root / "release_control/src").rglob("*.py"))
    runtime_text = "\n".join(item.read_text(encoding="utf-8") for item in runtime_files)
    lowered = runtime_text.lower()
    require(all(item not in lowered for item in FORBIDDEN), "forbidden-capability")
    require("github_pat" not in lowered and "personal_access_token" not in lowered, "static-token")
    constants = (
        root / "release_control/src/emporio_release_control/constants.py"
    ).read_text(encoding="utf-8")
    require('REPOSITORY = "greggorio/abaronesa-emporio"' in constants, "repository")
    require('REF = "main"' in constants, "ref")
    require(
        'PUBLISHER_WORKFLOW = "publish-release.yml"' in constants,
        "publisher-workflow",
    )
    require("GitHub App" in (root / "release_control/README.md").read_text(), "github-app")

    spec = yaml.safe_load((root / OPENAPI.relative_to(ROOT)).read_text(encoding="utf-8"))
    require(isinstance(spec, dict) and set(spec.get("paths", {})) == ROUTES, "openapi-routes")
    api = (root / "release_control/src/emporio_release_control/api.py").read_text()
    implemented = set(re.findall(r'"/(health|api)/[^"]+', api))
    _ = implemented
    for route in ROUTES:
        source_route = route.replace("{operationId}", "{operation_id}")
        require(f'"{source_route}"' in api, f"runtime-route:{route}")
    require("/api/deployment-control/" not in api, "deployer-router")
    capability_source = api.partition("def capabilities")[2].partition("@app.")[0]
    require(
        '"capabilities": ["release:read", "release:publish"]'
        in capability_source
        and "deployment:" not in capability_source,
        "publisher-capabilities",
    )

    package = root / "release_control/src/emporio_release_control"
    schemas = (package / "schemas.py").read_text(encoding="utf-8")
    schema_tree = ast.parse(schemas)
    require(
        literal_members(assignment(schema_tree, "PublicProblemCode")) == PUBLIC_CODES,
        "public-error-enum",
    )
    problem = next(
        (
            node
            for node in schema_tree.body
            if isinstance(node, ast.ClassDef) and node.name == "ProblemDetails"
        ),
        None,
    )
    require(
        isinstance(problem, ast.ClassDef)
        and any(
            isinstance(node, ast.AnnAssign)
            and isinstance(node.target, ast.Name)
            and node.target.id == "code"
            and isinstance(node.annotation, ast.Name)
            and node.annotation.id == "PublicProblemCode"
            for node in problem.body
        ),
        "public-error-enum",
    )
    errors = (package / "errors.py").read_text(encoding="utf-8")
    error_tree = ast.parse(errors)
    raw_pairs = ast.literal_eval(assignment(error_tree, "PUBLIC_FAILURES"))
    require(set(raw_pairs) == PUBLIC_FAILURE_PAIRS, "public-error-normalizer")
    require(
        'return RuntimeFailure("INTERNAL_ERROR", 500, "Internal server error")'
        in errors
        and "failure = normalize_public_failure(failure)" in api,
        "public-error-normalizer",
    )
    require(
        "@app.exception_handler(Exception)" in api
        and 'request.method == "POST"' in api
        and 'RuntimeFailure("BAD_REQUEST", 400' in api
        and 'RuntimeFailure("UNPROCESSABLE", 422' in api,
        "public-error-handler",
    )
    github = (package / "github.py").read_text(encoding="utf-8")
    service = (package / "service.py").read_text(encoding="utf-8")
    require(
        "headers = self._headers()" in github
        and "raise PreDispatchFailure() from exc" in github
        and "raise RemoteTransportFailure(uncertain=True) from exc" in github
        and "except PreDispatchFailure:" in service
        and "if exc.uncertain:" in service
        and 'self.fail(operation_id, "WORKFLOW_DISPATCH_NOT_SENT"' in service,
        "dispatch-phase-boundary",
    )

    sync = (package / "sync.py").read_text(encoding="utf-8")
    sync_tree = ast.parse(sync)
    require(
        ast.literal_eval(assignment(sync_tree, "RELEASE_ASSETS")) == RELEASE_ASSETS,
        "release-asset-contract",
    )
    require(
        'self._run(\n                inherited_run, "Publish Candidate"\n            )' in sync,
        "inherited-run-validation",
    )
    require(
        'outcome["predecessorCandidateId"]\n            != candidate.manifest["predecessor"]["candidateId"]'
        in sync,
        "candidate-predecessor-binding",
    )
    require(
        "isinstance(size, bool)" in sync
        and "not 1 <= size <= expected[0]" in sync
        and 'asset.get("content_type") != expected[1]' in sync
        and "asset_id in asset_ids" in sync
        and sync.index("for asset in assets:") < sync.index("for name in RELEASE_ASSETS:")
        < sync.index("self.github.get_bytes(", sync.index("for name in RELEASE_ASSETS:")),
        "release-asset-validation",
    )
    reconciliation = (package / "reconciliation.py").read_text(encoding="utf-8")
    require(
        'outcome["workflow"]["url"]' in reconciliation
        and f'"https://github.com/{{REPOSITORY}}/actions/runs/{{run_id}}"' in reconciliation
        and 'outcome["githubRelease"]["tagName"] != outcome["release"]' in reconciliation
        and 'outcome["githubRelease"]["url"]' in reconciliation
        and '"https://github.com/{REPOSITORY}/releases/tag/"' in reconciliation,
        "publication-outcome-bindings",
    )

    human = (
        root
        / "docs/infrastructure/deployment/release-control/CONTRATO_API_ESTADOS_SEGURANCA.md"
    ).read_text(encoding="utf-8")
    runtime_doc = (
        root / "docs/infrastructure/deployment/release-control/RUNTIME_PUBLISHER.md"
    ).read_text(encoding="utf-8")
    require(
        all(
            marker in human
            for marker in (
                "Python 3.13", "FastAPI", "PostgreSQL 16", "SQLAlchemy 2",
                "Alembic", "Psycopg 3", "RS256", "GitHub App", "365 dias",
                "16 KiB", "120 GET/min", "5 POST/min", "allowlist HTTPS",
            )
        ),
        "publisher-decisions",
    )
    pending = human.partition("## Decisoes pendentes")[2]
    require(
        all(
            marker not in pending
            for marker in (
                "framework", "tecnologia de persistencia", "GitHub App ou token",
                "issuer", "audience", "algoritmo", "retencao", "CORS",
            )
        ),
        "publisher-decisions-pending",
    )
    require(
        all(
            marker in runtime_doc
            for marker in (
                "INTERNAL_ERROR", "WORKFLOW_DISPATCH_NOT_SENT",
                "WORKFLOW_DISPATCH_REJECTED", "UNCERTAIN", "candidate-manifest",
                "workflow.url", "githubRelease.tagName", "2 MiB", "128 B", "16 KiB",
            )
        ),
        "runtime-correction-contract",
    )
    tests = "\n".join(
        item.read_text(encoding="utf-8")
        for item in sorted((root / "release_control/tests").glob("test_*.py"))
    )
    for marker in (
        "test_concurrent_same_request_returns_one_operation",
        "test_advisory_lock_is_exclusive",
        "test_jwt_rejects",
        "test_post_validation_idempotency",
        "test_readiness",
    ):
        require(marker in tests, f"causal-test:{marker}")


def main() -> int:
    try:
        validate()
    except (OSError, ValueError, yaml.YAMLError) as exc:
        code = str(exc) if isinstance(exc, ValidationError) else "validation-error"
        print(f"publisher-runtime:invalid:{code}", file=sys.stderr)
        return 2
    print("publisher-runtime:valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
