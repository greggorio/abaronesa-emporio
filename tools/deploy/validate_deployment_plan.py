#!/usr/bin/env python3
"""Fail-closed structural validator for the S18 offline deployment planner."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

import jsonschema
import yaml


ROOT = Path(__file__).resolve().parents[2]
DEPLOY_ROOT = ROOT / "ops/deploy"
PLANNER = ROOT / "tools/deploy/deployment_plan.py"
INSTALLED_SCHEMA = DEPLOY_ROOT / "schemas/installed-state.schema.json"
PLAN_SCHEMA = DEPLOY_ROOT / "schemas/deployment-plan.schema.json"
INSTALLED_EXAMPLE = DEPLOY_ROOT / "examples/installed-state.example.json"
PLAN_EXAMPLE = DEPLOY_ROOT / "examples/deployment-plan.example.json"
CATALOG = ROOT / "ops/releases/components.yml"
COMPOSE = ROOT / "ops/compose/compose.prod.yml"
OPENAPI = ROOT / "docs/infrastructure/deployment/release-control/api/deployer.openapi.yml"
DOCUMENTATION = (
    ROOT / "docs/infrastructure/deployment/release-control/PLANO_IMPLANTACAO.md"
)

COMPONENTS = (
    "backend",
    "website_back",
    "frontend",
    "website_front",
    "whatsapp_service",
    "gateway",
)
DATABASES = ("erp", "website")
MAPPINGS = {
    "backend": (
        "backend",
        "BACKEND_IMAGE",
        "ghcr.io/greggorio/abaronesa-emporio-backend",
    ),
    "website_back": (
        "website_back",
        "WEBSITE_BACK_IMAGE",
        "ghcr.io/greggorio/abaronesa-emporio-website-backend",
    ),
    "frontend": (
        "frontend",
        "FRONTEND_IMAGE",
        "ghcr.io/greggorio/abaronesa-emporio-frontend",
    ),
    "website_front": (
        "website_front",
        "WEBSITE_FRONT_IMAGE",
        "ghcr.io/greggorio/abaronesa-emporio-website-frontend",
    ),
    "whatsapp_service": (
        "whatsapp_service",
        "WHATSAPP_IMAGE",
        "ghcr.io/greggorio/abaronesa-emporio-whatsapp-service",
    ),
    "gateway": (
        "gateway",
        "GATEWAY_IMAGE",
        "ghcr.io/greggorio/abaronesa-emporio-gateway",
    ),
}
EXECUTION_ORDER = (
    "VALIDATE",
    "PULL",
    "BACKUP",
    "MIGRATE",
    "UPDATE",
    "VERIFY",
    "COMMIT_STATE",
)
REQUIRED_BUNDLE_FILES = {
    "manifest.json",
    "compose.prod.yml",
    "release.env",
    "deployment-plan.json",
    "installed-state.next.json",
    "bundle.sha256",
}


class ValidationError(ValueError):
    """Stable validator error."""


def require(condition: bool, code: str) -> None:
    if not condition:
        raise ValidationError(code)


def read_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(value, dict), f"json-object:{path.name}")
    return value


def require_closed_objects(value: Any, location: str = "$") -> None:
    if isinstance(value, dict):
        if value.get("type") == "object":
            require(
                value.get("additionalProperties") is False,
                f"schema-open-object:{location}",
            )
        for key, child in value.items():
            require_closed_objects(child, f"{location}.{key}")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            require_closed_objects(child, f"{location}[{index}]")


def validate_schema(schema: dict[str, Any], example: dict[str, Any], name: str) -> None:
    require(
        schema.get("$schema") == "https://json-schema.org/draft/2020-12/schema",
        f"{name}-schema-draft",
    )
    require_closed_objects(schema)
    validator = jsonschema.Draft202012Validator(
        schema, format_checker=jsonschema.FormatChecker()
    )
    require(not list(validator.iter_errors(example)), f"{name}-example-schema")


def validate_installed_example(value: dict[str, Any]) -> None:
    require(value.get("schemaVersion") == 1, "installed-schema-version")
    require(value.get("kind") == "installed-state", "installed-kind")
    require(value.get("environment") == "production", "installed-environment")
    require(value.get("reconciled") is True, "installed-confirmed")
    require(
        [item.get("id") for item in value.get("components", [])] == list(COMPONENTS),
        "installed-component-order",
    )
    require(
        [item.get("id") for item in value.get("databases", [])] == list(DATABASES),
        "installed-database-order",
    )
    owners = {
        item.get("id"): item.get("ownerComponent")
        for item in value.get("databases", [])
    }
    require(
        owners == {"erp": "backend", "website": "website_back"},
        "installed-database-owner",
    )
    for item in value["components"]:
        expected_repository = MAPPINGS[item["id"]][2]
        require(
            item.get("immutableRef", "").startswith(expected_repository + "@sha256:"),
            f"installed-repository:{item['id']}",
        )


def validate_plan_example(value: dict[str, Any]) -> None:
    require(value.get("schemaVersion") == 1, "plan-schema-version")
    require(value.get("kind") == "deployment-plan", "plan-kind")
    require(value.get("environment") == "production", "plan-environment")
    components = value.get("components", [])
    require(
        [item.get("component") for item in components] == list(COMPONENTS),
        "plan-component-order",
    )
    for item in components:
        component = item["component"]
        service, variable, repository = MAPPINGS[component]
        require(item.get("service") == service, f"plan-service:{component}")
        require(
            item.get("imageVariable") == variable,
            f"plan-image-variable:{component}",
        )
        require(
            item.get("targetImmutableRef", "").startswith(repository + "@sha256:"),
            f"plan-repository:{component}",
        )
    updates = [
        item["service"] for item in components if item.get("action") == "UPDATE"
    ]
    require(value.get("servicesToPull") == updates, "plan-services-to-pull")
    require(value.get("servicesToUpdate") == updates, "plan-services-to-update")
    require(
        [item.get("id") for item in value.get("databases", [])] == list(DATABASES),
        "plan-database-order",
    )
    changed = any(item.get("changed") is True for item in value["databases"])
    require(value.get("migrationRequired") is changed, "plan-migration-required")
    require(value.get("backupRequired") is changed, "plan-backup-required")
    require(
        value.get("executionOrder") == list(EXECUTION_ORDER),
        "plan-execution-order",
    )


def validate_catalog_and_compose(root: Path) -> None:
    catalog = yaml.safe_load((root / CATALOG.relative_to(ROOT)).read_text())
    require(isinstance(catalog, dict), "catalog-shape")
    require(catalog.get("canonical_order") == list(COMPONENTS), "catalog-order")
    catalog_components = catalog.get("components")
    require(isinstance(catalog_components, dict), "catalog-components")
    for component, (_service, _variable, repository) in MAPPINGS.items():
        value = catalog_components.get(component)
        require(isinstance(value, dict), f"catalog-component:{component}")
        require(
            value.get("image_repository") == repository,
            f"catalog-repository:{component}",
        )

    compose = yaml.safe_load((root / COMPOSE.relative_to(ROOT)).read_text())
    require(isinstance(compose, dict), "compose-shape")
    services = compose.get("services")
    require(isinstance(services, dict), "compose-services")
    for component, (service, variable, _repository) in MAPPINGS.items():
        service_value = services.get(service)
        require(isinstance(service_value, dict), f"compose-service:{component}")
        require(
            service_value.get("image") == f"${{{variable}:?{variable} is required}}",
            f"compose-image-variable:{component}",
        )


def validate_openapi(root: Path) -> None:
    value = yaml.safe_load((root / OPENAPI.relative_to(ROOT)).read_text())
    try:
        source = value["components"]["schemas"]["DeploymentPlan"]["properties"][
            "sourceRelease"
        ]
    except (KeyError, TypeError) as exc:
        raise ValidationError("openapi-source-release") from exc
    require(
        source
        == {
            "oneOf": [
                {"$ref": "#/components/schemas/ReleaseId"},
                {"type": "null"},
            ]
        },
        "openapi-source-release",
    )


def validate_planner_source(root: Path) -> None:
    source = (root / PLANNER.relative_to(ROOT)).read_text(encoding="utf-8")
    lowered = source.lower()
    for forbidden in (
        "import subprocess",
        "from subprocess",
        "os.system",
        "docker compose",
        "podman",
        "psql",
        "pg_dump",
        "curl ",
        "gh api",
        "paramiko",
    ):
        require(forbidden not in lowered, "planner-external-command")
    for clock in ("datetime.now(", "datetime.utcnow(", "time.time("):
        require(clock not in source, "planner-system-clock")
    for token in (
        "generate",
        "validate",
        "--target",
        "--current",
        "--current-manifest",
        "--compose",
        "--planned-at",
        "--output",
        "--bundle",
    ):
        require(token in source, f"planner-cli:{token}")
    for code in (
        "NON_FORWARD_MIGRATION",
        "RELEASE_CHAIN_MISMATCH",
        "CURRENT_STATE_MISMATCH",
        "UNSAFE_PATH",
        "BUNDLE_CONFLICT",
        "INVALID_CONTRACT",
    ):
        require(code in source, f"planner-error-code:{code}")
    for component, (service, variable, repository) in MAPPINGS.items():
        require(component in source, f"planner-component:{component}")
        require(service in source, f"planner-service:{component}")
        require(variable in source, f"planner-variable:{component}")
        require(repository in source, f"planner-repository:{component}")
    for filename in REQUIRED_BUNDLE_FILES:
        require(filename in source, f"planner-bundle-file:{filename}")


def validate_documentation(root: Path) -> None:
    text = (root / DOCUMENTATION.relative_to(ROOT)).read_text(encoding="utf-8")
    lowered = text.casefold()
    for phrase in (
        "keep",
        "update",
        "forward-only",
        "installed-state.next.json",
        "intenção",
        "backup",
        "primeira implantação",
        "não implanta",
        "deployment_plan.py generate",
        "deployment_plan.py validate",
        "validate_deployment_plan.py",
    ):
        require(phrase.casefold() in lowered, f"documentation:{phrase}")


def validate(root: Path = ROOT) -> None:
    required = (
        PLANNER,
        INSTALLED_SCHEMA,
        PLAN_SCHEMA,
        INSTALLED_EXAMPLE,
        PLAN_EXAMPLE,
        CATALOG,
        COMPOSE,
        OPENAPI,
        DOCUMENTATION,
    )
    require(
        all((root / path.relative_to(ROOT)).is_file() for path in required),
        "required-file",
    )
    installed_schema = read_json(root / INSTALLED_SCHEMA.relative_to(ROOT))
    plan_schema = read_json(root / PLAN_SCHEMA.relative_to(ROOT))
    installed_example = read_json(root / INSTALLED_EXAMPLE.relative_to(ROOT))
    plan_example = read_json(root / PLAN_EXAMPLE.relative_to(ROOT))
    validate_schema(installed_schema, installed_example, "installed")
    validate_schema(plan_schema, plan_example, "plan")
    validate_installed_example(installed_example)
    validate_plan_example(plan_example)
    validate_catalog_and_compose(root)
    validate_openapi(root)
    validate_planner_source(root)
    validate_documentation(root)


def main() -> int:
    try:
        validate()
    except (
        OSError,
        UnicodeError,
        ValueError,
        json.JSONDecodeError,
        yaml.YAMLError,
        jsonschema.SchemaError,
    ) as exc:
        code = str(exc) if isinstance(exc, ValidationError) else "validation-error"
        print(f"deployment-plan-contract:invalid:{code}", file=sys.stderr)
        return 3
    print("deployment-plan-contract:valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
