#!/usr/bin/env python3
"""Semantic, fail-closed validator for the sole canonical CI workflow."""

from __future__ import annotations

import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/ci.yml"
SHA_USE = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+@[0-9a-f]{40}$")
REQUIRED_JOBS = {"plan", "contracts", "backend", "website_back", "frontend", "website_front", "whatsapp", "images"}
REQUIRED_COMMANDS = (
    "python3 tools/releases/catalog.py validate --require-release-ready",
    "python3 -m unittest discover -s tools/releases/tests -v",
    "python3 tools/releases/release_control_contract.py validate",
    "python3 tools/security/bootstrap_contract.py validate",
    "python3 -m unittest discover -s tools/security/tests -v",
    "python3 tools/docker/java_images_contract.py validate",
    "python3 -m unittest discover -s tools/docker/tests -v",
    "python3 tools/compose/validate_compose.py",
    "python3 -m unittest discover -s tools/compose/tests -v",
    "python3 tools/gateway/validate_gateway.py",
    "python3 tools/gateway/validate_host_nginx.py",
    "python3 -m unittest discover -s tools/gateway/tests -v",
    "python3 tools/ci/migrations_contract.py",
    "python3 tools/deploy/validate_production_transport_workflow.py",
    "python3 tools/ci/secret_scan.py --tracked",
    "mvn -B verify",
    "npm run lint",
    "npm run test",
    "npm run build",
    "node --check index.js",
    "node --check app.js",
    "python3 tools/candidates/candidate_plan.py generate",
    "python3 tools/candidates/candidate_plan.py validate",
    "python3 tools/ci/invocability.py",
)
SUBCOMMAND_CONTRACTS = (
    "python3 tools/releases/release_control_contract.py",
    "python3 tools/security/bootstrap_contract.py",
    "python3 tools/docker/java_images_contract.py",
)
POSTGRES_IMAGE = "postgres:16.10-alpine3.22@sha256:029660641a0cfc575b14f336ba448fb8a75fd595d42e1fa316b9fb4378742297"
POSTGRES_IMMUTABLE = re.compile(r"^(?:docker\.io/library/)?postgres:[^\s@]+@sha256:[0-9a-f]{64}$")
POSTGRES_ENV = {"POSTGRES_DB": "testdb", "POSTGRES_USER": "test", "POSTGRES_PASSWORD": "test"}
POSTGRES_PORTS = ["5432:5432"]
POSTGRES_OPTIONS = (
    '--health-cmd "pg_isready -U test -d testdb"',
    "--health-interval",
    "--health-timeout",
    "--health-retries",
)
FORBIDDEN = re.compile(r"(?i)(workflow_dispatch|pull_request_target|repository_dispatch|schedule:|packages:\s*write|contents:\s*write|id-token:\s*write|docker/login-action|docker\s+(?:login|push)|scp\b|ssh\b|rsync\b|workflow_run|continue-on-error:\s*true|self-hosted)")


def load_workflow(text: str):
    return yaml.load(text, Loader=yaml.BaseLoader)


def validate(text: str | None = None) -> list[str]:
    text = text if text is not None else WORKFLOW.read_text(encoding="utf-8")
    errors: list[str] = []
    try:
        data = load_workflow(text)
    except yaml.YAMLError as error:
        return [f"yaml:{error}"]
    if not isinstance(data, dict):
        return ["workflow root"]
    trigger = data.get("on", {})
    if set(trigger) != {"pull_request", "push"}:
        errors.append("trigger set")
    for name in ("pull_request", "push"):
        if trigger.get(name, {}).get("branches") != ["main"]:
            errors.append(f"{name} branches")
    if data.get("permissions") != {"contents": "read"}:
        errors.append("global permissions")
    concurrency = data.get("concurrency", {})
    if "github.workflow" not in concurrency.get("group", "") or "github.ref" not in concurrency.get("group", "") or concurrency.get("cancel-in-progress") != "true":
        errors.append("concurrency")
    jobs = data.get("jobs", {})
    if set(jobs) != REQUIRED_JOBS:
        errors.append("job set")
    for name, job in jobs.items():
        if job.get("runs-on") != "ubuntu-24.04":
            errors.append(f"{name}:runner")
        if "timeout-minutes" not in job:
            errors.append(f"{name}:timeout")
        if "permissions" in job and job["permissions"] != {"contents": "read"}:
            errors.append(f"{name}:permissions")
        for step in job.get("steps", []):
            use = step.get("uses")
            if use and not SHA_USE.fullmatch(use):
                errors.append(f"{name}:mutable action:{use}")
    if FORBIDDEN.search(text):
        errors.append("forbidden capability")
    for command in REQUIRED_COMMANDS:
        if command not in text:
            errors.append(f"missing command:{command}")
    # These CLIs require a subcommand. A substring test would accept both
    # spellings, so reject every bare command by whole-line comparison.
    stripped_lines = {line.strip() for line in text.splitlines()}
    for command in SUBCOMMAND_CONTRACTS:
        if command in stripped_lines:
            errors.append(f"contract without subcommand:{command}")
    checkout_steps = [
        step
        for job in jobs.values()
        for step in job.get("steps", [])
        if str(step.get("uses", "")).startswith("actions/checkout@")
    ]
    if not checkout_steps or any(step.get("with", {}).get("fetch-depth") != "0" for step in checkout_steps):
        errors.append("checkout must be full")
    images = jobs.get("images", {})
    required_needs = {"plan", "contracts", "backend", "website_back", "frontend", "website_front", "whatsapp"}
    if set(images.get("needs", [])) != required_needs:
        errors.append("image dependencies")
    matrix = images.get("strategy", {}).get("matrix", {}).get("include", [])
    expected_matrix = [
        {"component": "backend", "context": "backend", "dockerfile": "backend/Dockerfile"},
        {"component": "website_back", "context": "website_back", "dockerfile": "website_back/Dockerfile"},
        {"component": "frontend", "context": "frontend", "dockerfile": "frontend/Dockerfile"},
        {"component": "website_front", "context": "website_front", "dockerfile": "website_front/Dockerfile"},
        {"component": "whatsapp_service", "context": "whatsapp_service", "dockerfile": "whatsapp_service/Dockerfile"},
        {"component": "gateway", "context": "ops/gateway", "dockerfile": "ops/gateway/Dockerfile"},
    ]
    if matrix != expected_matrix:
        errors.append("image matrix")
    if "platforms: linux/amd64" not in text or "push: false" not in text or "load: true" not in text:
        errors.append("image build policy")
    if "aquasecurity/trivy-action@" not in text or "severity: HIGH,CRITICAL" not in text or "exit-code: '1'" not in text:
        errors.append("vulnerability scan")
    if text.count("name: candidate-plan") != 1 or "retention-days: 7" not in text:
        errors.append("candidate plan artifact")
    errors.extend(validate_backend_database(jobs.get("backend", {})))
    return errors


def validate_backend_database(backend: dict) -> list[str]:
    """The backend integration suite requires PostgreSQL reachable on localhost:5432."""
    errors: list[str] = []
    services = backend.get("services", {})
    if set(services) != {"postgres"}:
        return ["backend:postgres service"]
    service = services["postgres"]
    image = str(service.get("image", ""))
    if image != POSTGRES_IMAGE:
        errors.append("backend:postgres image")
    if not POSTGRES_IMMUTABLE.fullmatch(image):
        errors.append("backend:postgres image must be immutable digest")
    if service.get("env") != POSTGRES_ENV:
        errors.append("backend:postgres env")
    if service.get("ports") != POSTGRES_PORTS:
        errors.append("backend:postgres ports")
    options = str(service.get("options", ""))
    if not all(fragment in options for fragment in POSTGRES_OPTIONS):
        errors.append("backend:postgres healthcheck")
    return errors


def main() -> int:
    errors = validate()
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print("ci:valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
