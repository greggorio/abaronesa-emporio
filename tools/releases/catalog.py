#!/usr/bin/env python3
"""Validate and resolve the Emporio commercial component catalog."""

from __future__ import annotations

import argparse
import copy
import json
import posixpath
import sys
from pathlib import Path
from typing import Any, Iterable

import jsonschema
import yaml


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CATALOG = ROOT / "ops/releases/components.yml"
DEFAULT_SCHEMA = ROOT / "ops/releases/components.schema.json"
CANONICAL = [
    "backend",
    "website_back",
    "frontend",
    "website_front",
    "whatsapp_service",
    "gateway",
]
EXPECTED_DEPENDENCIES = {
    "backend": ["whatsapp_service"],
    "website_back": ["backend"],
    "frontend": ["backend"],
    "website_front": ["website_back"],
    "whatsapp_service": [],
    "gateway": [
        "backend",
        "website_back",
        "frontend",
        "website_front",
        "whatsapp_service",
    ],
}
EXPECTED_CLOSURES = {
    "backend": ["backend", "website_back", "frontend", "website_front", "gateway"],
    "website_back": ["website_back", "website_front", "gateway"],
    "frontend": ["frontend", "gateway"],
    "website_front": ["website_front", "gateway"],
    "whatsapp_service": CANONICAL,
    "gateway": ["gateway"],
}
IMAGE_PREFIX = "ghcr.io/greggorio/abaronesa-emporio-"
EXPECTED_GLOBAL_PATHS = [
    ".github/workflows/**",
    "ops/releases/**",
    "ops/compose/**",
    "ops/deploy/**",
    "deploy/**",
]
EXPECTED_DOCUMENTATION_PATHS = ["docs/**", "README.md"]
EXPECTED_SOURCE_PATHS = {
    "backend": ["backend/**"],
    "website_back": ["website_back/**"],
    "frontend": ["frontend/**"],
    "website_front": ["website_front/**"],
    "whatsapp_service": ["whatsapp_service/**"],
    "gateway": ["ops/gateway/**"],
}


class CatalogError(ValueError):
    """Contract validation or input error."""


def load_yaml(path: Path = DEFAULT_CATALOG) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as stream:
        value = yaml.safe_load(stream)
    if not isinstance(value, dict):
        raise CatalogError("catalog root must be an object")
    return value


def load_schema(path: Path = DEFAULT_SCHEMA) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as stream:
        return json.load(stream)


def _ordered(values: Iterable[str], order: list[str]) -> list[str]:
    selected = set(values)
    return [component for component in order if component in selected]


def validation_closure(catalog: dict[str, Any], direct: Iterable[str]) -> list[str]:
    order = catalog["canonical_order"]
    components = catalog["components"]
    consumers: dict[str, set[str]] = {component: set() for component in order}
    for consumer, contract in components.items():
        for provider in contract["dependencies"]:
            consumers[provider].add(consumer)

    selected = set(direct)
    queue = list(selected)
    while queue:
        provider = queue.pop()
        for consumer in consumers[provider]:
            if consumer not in selected:
                selected.add(consumer)
                queue.append(consumer)
    return _ordered(selected, order)


def _detect_cycle(catalog: dict[str, Any]) -> bool:
    components = catalog["components"]
    state: dict[str, int] = {}

    def visit(component: str) -> bool:
        if state.get(component) == 1:
            return True
        if state.get(component) == 2:
            return False
        state[component] = 1
        for dependency in components[component]["dependencies"]:
            if dependency not in components:
                continue
            if visit(dependency):
                return True
        state[component] = 2
        return False

    return any(visit(component) for component in components if not state.get(component))


def semantic_errors(catalog: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    order = catalog.get("canonical_order", [])
    components = catalog.get("components", {})

    if order != CANONICAL:
        errors.append("canonical_order must match the approved six-component order")
    if set(components) != set(CANONICAL):
        errors.append("commercial BOM must contain exactly the six approved components")
        return errors
    if catalog.get("excluded_operational_components") != ["release_control"]:
        errors.append("release_control must be the sole excluded operational component")
    if catalog.get("unknown_path_policy") != "fail_closed_all":
        errors.append("unknown path policy must be fail_closed_all")
    if catalog.get("global_paths") != EXPECTED_GLOBAL_PATHS:
        errors.append("global_paths diverge from the approved policy")
    if catalog.get("documentation_paths") != EXPECTED_DOCUMENTATION_PATHS:
        errors.append("documentation_paths diverge from the approved policy")

    images: list[str] = []
    source_owners: dict[str, str] = {}
    for component_id in order:
        component = components[component_id]
        if component.get("id") != component_id:
            errors.append(f"{component_id}: id does not match map key")

        image = component.get("image_repository", "")
        images.append(image)
        if not image.startswith(IMAGE_PREFIX):
            errors.append(f"{component_id}: image namespace is not approved")
        if "@" in image or ":" in image:
            errors.append(f"{component_id}: image repository must not contain tag or digest")

        for dependency in component.get("dependencies", []):
            if dependency not in components:
                errors.append(f"{component_id}: unknown dependency {dependency}")
        if component.get("dependencies") != EXPECTED_DEPENDENCIES[component_id]:
            errors.append(f"{component_id}: direct dependency graph diverges")

        gates = component.get("readiness_gates", [])
        readiness = component.get("readiness")
        health = component.get("health_check", {})
        if readiness == "ready" and gates:
            errors.append(f"{component_id}: ready component still has gates")
        if readiness == "blocked" and not gates:
            errors.append(f"{component_id}: blocked component must have gates")
        if readiness == "ready":
            if component.get("build", {}).get("status") != "confirmed" or not component.get(
                "build", {}
            ).get("command"):
                errors.append(f"{component_id}: ready component needs confirmed build")
            if component.get("test", {}).get("status") != "confirmed" or not component.get(
                "test", {}
            ).get("command"):
                errors.append(f"{component_id}: ready component needs confirmed test")
            if health.get("status") != "confirmed" or not health.get("path"):
                errors.append(
                    f"{component_id}: ready component needs confirmed health check"
                )
            if any(item.get("status") != "confirmed" for item in component.get("persistence", [])):
                errors.append(
                    f"{component_id}: ready component needs confirmed persistence"
                )

        for contract_name in ("build", "test"):
            contract = component.get(contract_name, {})
            if contract.get("status") == "pending" and contract.get("command") is not None:
                errors.append(f"{component_id}: pending {contract_name} must have null command")
            if contract.get("status") == "confirmed" and not contract.get("command"):
                errors.append(f"{component_id}: confirmed {contract_name} needs command")

        if health.get("status") == "pending" and health.get("path") is not None:
            errors.append(f"{component_id}: pending health check must have null path")
        if health.get("status") in {"confirmed", "inferred"} and not health.get("path"):
            errors.append(f"{component_id}: known health check needs path")

        migrations = component.get("migrations", {})
        if migrations.get("type") == "none" and migrations.get("path") is not None:
            errors.append(f"{component_id}: component without migrations must have null path")
        if migrations.get("type") == "flyway" and not migrations.get("path"):
            errors.append(f"{component_id}: Flyway component needs migration path")

        for source_path in component.get("source_paths", []):
            owner = source_owners.get(source_path)
            if owner and owner != component_id:
                errors.append(f"source path collision: {source_path}")
            source_owners[source_path] = component_id
        if component.get("source_paths") != EXPECTED_SOURCE_PATHS[component_id]:
            errors.append(f"{component_id}: source_paths diverge from approved policy")

    if len(images) != len(set(images)):
        errors.append("image repositories must be unique")
    if _detect_cycle(catalog):
        errors.append("commercial dependency graph contains a cycle")
    if "release_control" in components or "release_control" in order:
        errors.append("release_control cannot enter the commercial BOM")

    if not errors:
        for component_id, expected in EXPECTED_CLOSURES.items():
            actual = validation_closure(catalog, [component_id])
            if actual != expected:
                errors.append(
                    f"{component_id}: closure {actual!r} diverges from {expected!r}"
                )
    return errors


def validate_catalog(
    catalog: dict[str, Any],
    schema: dict[str, Any] | None = None,
) -> list[str]:
    schema = schema or load_schema()
    errors: list[str] = []
    validator = jsonschema.Draft202012Validator(schema)
    for error in sorted(validator.iter_errors(catalog), key=lambda item: list(item.path)):
        location = ".".join(str(part) for part in error.path) or "<root>"
        errors.append(f"schema:{location}:{error.message}")
    if not errors:
        errors.extend(f"semantic:{message}" for message in semantic_errors(catalog))
    return errors


def readiness_gates(catalog: dict[str, Any]) -> list[str]:
    gates: list[str] = []
    for component_id in catalog["canonical_order"]:
        for gate in catalog["components"][component_id]["readiness_gates"]:
            if gate["status"] == "pending":
                gates.append(f"{component_id}:{gate['code']}")
    return gates


def normalize_changed_path(raw_path: str) -> str:
    if raw_path is None or not raw_path.strip():
        raise CatalogError("changed path must not be empty")
    candidate = raw_path.strip().replace("\\", "/")
    if candidate.startswith("/") or (len(candidate) >= 2 and candidate[1] == ":"):
        raise CatalogError(f"absolute changed path is not allowed: {raw_path}")
    parts = candidate.split("/")
    if ".." in parts:
        raise CatalogError(f"parent traversal is not allowed: {raw_path}")
    normalized = posixpath.normpath(candidate)
    if normalized in {"", "."} or normalized.startswith("../"):
        raise CatalogError(f"invalid changed path: {raw_path}")
    return normalized


def _matches(path: str, pattern: str) -> bool:
    if pattern.endswith("/**"):
        return path.startswith(pattern[:-2])
    return path == pattern


def resolve(
    catalog: dict[str, Any],
    changed_paths: Iterable[str] | None = None,
    first_release: bool = False,
) -> dict[str, Any]:
    order = catalog["canonical_order"]
    if first_release:
        return {
            "classification": "first_release",
            "changedPaths": [],
            "directComponents": order,
            "buildComponents": order,
            "validationComponents": order,
            "inheritedComponents": [],
            "warnings": ["FIRST_RELEASE_REQUIRES_COMPLETE_BOM"],
        }

    raw_paths = list(changed_paths or [])
    if not raw_paths:
        raise CatalogError("at least one --changed path or --first-release is required")
    paths = sorted(set(normalize_changed_path(path) for path in raw_paths))

    direct: set[str] = set()
    unknown: list[str] = []
    has_global = False
    has_documentation = False

    for path in paths:
        matched_components = [
            component_id
            for component_id in order
            if any(
                _matches(path, pattern)
                for pattern in catalog["components"][component_id]["source_paths"]
            )
        ]
        if matched_components:
            direct.update(matched_components)
        elif any(_matches(path, pattern) for pattern in catalog["global_paths"]):
            has_global = True
        elif any(_matches(path, pattern) for pattern in catalog["documentation_paths"]):
            has_documentation = True
        else:
            unknown.append(path)

    if unknown:
        return {
            "classification": "unknown",
            "changedPaths": paths,
            "directComponents": _ordered(direct, order),
            "buildComponents": order,
            "validationComponents": order,
            "inheritedComponents": [],
            "warnings": [f"FAIL_CLOSED_UNKNOWN_PATH:{path}" for path in unknown],
        }

    build = _ordered(direct, order)
    if has_global:
        validation = order
        classification = "global" if not direct else "mixed_global"
    elif direct:
        validation = validation_closure(catalog, direct)
        classification = "components" if not has_documentation else "mixed"
    else:
        validation = []
        classification = "documentation"
    inherited = [component for component in order if component not in build]

    return {
        "classification": classification,
        "changedPaths": paths,
        "directComponents": build,
        "buildComponents": build,
        "validationComponents": validation,
        "inheritedComponents": inherited,
        "warnings": [],
    }


def _parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG)
    parser.add_argument("--schema", type=Path, default=DEFAULT_SCHEMA)
    subparsers = parser.add_subparsers(dest="command", required=True)

    validate_parser = subparsers.add_parser("validate")
    validate_parser.add_argument("--require-release-ready", action="store_true")

    resolve_parser = subparsers.add_parser("resolve")
    resolve_parser.add_argument("--changed", action="append", default=[])
    resolve_parser.add_argument("--first-release", action="store_true")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(argv)
    try:
        catalog = load_yaml(args.catalog)
        schema = load_schema(args.schema)
        errors = validate_catalog(catalog, schema)
        if errors:
            for error in errors:
                print(error, file=sys.stderr)
            return 2

        if args.command == "validate":
            if args.require_release_ready:
                gates = readiness_gates(catalog)
                if gates:
                    for gate in gates:
                        print(gate, file=sys.stderr)
                    return 3
            print("catalog:valid")
            return 0

        result = resolve(catalog, args.changed, args.first_release)
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
        return 0
    except (CatalogError, OSError, yaml.YAMLError, json.JSONDecodeError) as error:
        print(f"catalog:error:{error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
