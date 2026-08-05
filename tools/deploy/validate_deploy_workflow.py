#!/usr/bin/env python3
"""Fail-closed validator for the S21 deployment workflow contract."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

import jsonschema
import yaml


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = Path(".github/workflows/deploy-production.yml")
CONTRACTS = {
    "request": (
        Path("ops/deploy/schemas/deployment-request.schema.json"),
        Path("ops/deploy/examples/deployment-request.example.json"),
    ),
    "snapshot": (
        Path("ops/deploy/schemas/production-snapshot.schema.json"),
        Path("ops/deploy/examples/production-snapshot.example.json"),
    ),
    "outcome": (
        Path("ops/deploy/schemas/deployment-workflow-outcome.schema.json"),
        Path("ops/deploy/examples/deployment-workflow-outcome.example.json"),
    ),
}

PINS = {
    "actions/checkout": "de0fac2e4500dabe0009e67214ff5f5447ce83dd",
    "actions/download-artifact": "634f93cb2916e3fdff6788551b99b062d0335ce0",
    "actions/upload-artifact": "ea165f8d65b6e75b540449e92b4886f43607fa02",
}
JOBS = ("trust", "prepare", "deploy", "outcome")
TRUST_ENV = {
    "TRUSTED_REPOSITORY",
    "TRUSTED_OWNER",
    "TRUSTED_EVENT",
    "TRUSTED_REF",
    "TRUSTED_SHA",
    "TRUSTED_RUN_ID",
    "TRUSTED_RUN_ATTEMPT",
    "TRUSTED_ACTOR_ID",
    "TRUSTED_OPERATION_ID",
    "TRUSTED_RELEASE",
    "DEPLOYER_ACTOR_IDS",
}
FORBIDDEN_INPUTS = {
    "component",
    "components",
    "digest",
    "host",
    "port",
    "path",
    "command",
    "url",
    "image",
    "action",
}


class ValidationError(ValueError):
    """Stable local contract failure."""


def require(condition: bool, code: str) -> None:
    if not condition:
        raise ValidationError(code)


def canonical(value: Any) -> bytes:
    return (
        json.dumps(
            value,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
        ).encode("utf-8")
        + b"\n"
    )


def read_json(path: Path) -> dict[str, Any]:
    try:
        raw = path.read_bytes()
        value = json.loads(raw)
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ValidationError(f"json-invalid:{path.name}") from exc
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


def validate_contract(
    name: str,
    schema: dict[str, Any],
    example: dict[str, Any],
    example_bytes: bytes,
) -> None:
    require(
        schema.get("$schema") == "https://json-schema.org/draft/2020-12/schema",
        f"schema-draft:{name}",
    )
    require(schema.get("additionalProperties") is False, f"schema-closed:{name}")
    require_closed_objects(schema)
    try:
        jsonschema.Draft202012Validator.check_schema(schema)
    except jsonschema.SchemaError as exc:
        raise ValidationError(f"schema-invalid:{name}") from exc
    errors = list(
        jsonschema.Draft202012Validator(
            schema,
            format_checker=jsonschema.FormatChecker(),
        ).iter_errors(example)
    )
    require(not errors, f"example-invalid:{name}")
    require(example_bytes == canonical(example), f"example-noncanonical:{name}")


def _workflow_on(workflow: dict[str, Any]) -> Any:
    return workflow.get("on", workflow.get(True))


def _needs(job: dict[str, Any]) -> list[str]:
    value = job.get("needs", [])
    if isinstance(value, str):
        return [value]
    require(isinstance(value, list), "workflow-needs-shape")
    return value


def _steps(job: dict[str, Any]) -> list[dict[str, Any]]:
    value = job.get("steps")
    require(isinstance(value, list) and value, "workflow-steps")
    require(all(isinstance(step, dict) for step in value), "workflow-step-shape")
    return value


def _artifact_step(
    steps: list[dict[str, Any]], action: str, artifact: str
) -> tuple[int, dict[str, Any]]:
    expected = f"{action}@{PINS[action]}"
    matches = [
        (index, step)
        for index, step in enumerate(steps)
        if step.get("uses") == expected
        and isinstance(step.get("with"), dict)
        and step["with"].get("name") == artifact
    ]
    require(len(matches) == 1, f"workflow-artifact:{artifact}")
    return matches[0]


def validate_workflow(workflow: dict[str, Any], source: str) -> None:
    require(workflow.get("name") == "Deploy Production", "workflow-name")
    require(
        workflow.get("run-name") == "deploy-production-${{ inputs.operation_id }}",
        "workflow-run-name",
    )
    trigger = _workflow_on(workflow)
    require(
        isinstance(trigger, dict) and set(trigger) == {"workflow_dispatch"},
        "workflow-trigger",
    )
    dispatch = trigger["workflow_dispatch"]
    require(isinstance(dispatch, dict), "workflow-dispatch")
    inputs = dispatch.get("inputs")
    require(
        isinstance(inputs, dict) and set(inputs) == {"operation_id", "release"},
        "workflow-inputs",
    )
    require(not (set(inputs) & FORBIDDEN_INPUTS), "workflow-authority-input")
    for name in ("operation_id", "release"):
        require(
            inputs[name].get("required") is True
            and inputs[name].get("type") == "string",
            f"workflow-input:{name}",
        )

    require(
        workflow.get("permissions") == {"contents": "read", "actions": "read"},
        "workflow-permissions",
    )
    require(
        workflow.get("concurrency")
        == {"group": "emporio-production", "cancel-in-progress": False},
        "workflow-concurrency",
    )
    jobs = workflow.get("jobs")
    require(isinstance(jobs, dict) and tuple(jobs) == JOBS, "workflow-jobs")
    expected_needs = {
        "trust": [],
        "prepare": ["trust"],
        "deploy": ["prepare"],
        "outcome": ["trust", "prepare", "deploy"],
    }
    expected_timeouts = {"trust": 10, "prepare": 15, "deploy": 120, "outcome": 10}
    for name in JOBS:
        job = jobs[name]
        require(_needs(job) == expected_needs[name], f"workflow-needs:{name}")
        require(job.get("runs-on") == "ubuntu-24.04", f"workflow-runner:{name}")
        require(
            job.get("timeout-minutes") == expected_timeouts[name],
            f"workflow-timeout:{name}",
        )
        require(
            (job.get("environment") == "production") == (name == "deploy"),
            f"workflow-environment:{name}",
        )
        require("permissions" not in job, f"workflow-job-permissions:{name}")
    require(jobs["outcome"].get("if") == "always()", "workflow-outcome-always")

    for job_name, job in jobs.items():
        for step in _steps(job):
            uses = step.get("uses")
            if uses is not None:
                require(isinstance(uses, str) and "@" in uses, "workflow-action-shape")
                action, pin = uses.rsplit("@", 1)
                require(action in PINS, f"workflow-third-party:{action}")
                require(pin == PINS[action], f"workflow-action-pin:{action}")
            if job_name != "deploy":
                require(
                    "${{ secrets." not in json.dumps(step),
                    f"workflow-secret-scope:{job_name}",
                )
            run = step.get("run")
            if isinstance(run, str):
                require("${{ secrets." not in run, "workflow-secret-run")

    trust_steps = _steps(jobs["trust"])
    trust_run = next(step for step in trust_steps if "run" in step)
    require(set(trust_run.get("env", {})) == TRUST_ENV, "workflow-trust-env")
    for name in JOBS:
        checkout = [
            step
            for step in _steps(jobs[name])
            if step.get("uses") == f"actions/checkout@{PINS['actions/checkout']}"
        ]
        require(len(checkout) == 1, f"workflow-checkout:{name}")
        with_values = checkout[0].get("with", {})
        require(with_values.get("ref") == "${{ github.sha }}", f"workflow-ref:{name}")
        require(
            with_values.get("persist-credentials") is False,
            f"workflow-credentials:{name}",
        )

    artifact_rules = (
        ("trust", "deployment-trust", 1),
        ("prepare", "deployment-handoff", 1),
        ("deploy", "deployment-result", 90),
        ("outcome", "deployment-workflow-outcome", 90),
    )
    for job_name, artifact, retention in artifact_rules:
        _, step = _artifact_step(
            _steps(jobs[job_name]), "actions/upload-artifact", artifact
        )
        require(
            step["with"].get("retention-days") == retention,
            f"workflow-retention:{artifact}",
        )

    for job_name, artifact in (
        ("prepare", "deployment-trust"),
        ("deploy", "deployment-handoff"),
        ("outcome", "deployment-handoff"),
        ("outcome", "deployment-result"),
    ):
        _artifact_step(
            _steps(jobs[job_name]), "actions/download-artifact", artifact
        )

    for job_name, artifact in (
        ("deploy", "deployment-result"),
        ("outcome", "deployment-workflow-outcome"),
    ):
        steps = _steps(jobs[job_name])
        upload_index, _ = _artifact_step(
            steps, "actions/upload-artifact", artifact
        )
        exit_indices = [
            index for index, step in enumerate(steps) if step.get("run") == "exit 1"
        ]
        require(exit_indices and upload_index < min(exit_indices), f"workflow-upload-order:{job_name}")

    outcome_steps = _steps(jobs["outcome"])
    reconciliation = [
        step for step in outcome_steps if step.get("id") == "reconciliation"
    ]
    require(
        len(reconciliation) == 1
        and reconciliation[0].get("continue-on-error") is True,
        "workflow-outcome-reconciliation",
    )
    final_outcome_gate = [
        step
        for step in outcome_steps
        if step.get("run") == "exit 1"
        and step.get("if") == "steps.reconciliation.outcome != 'success'"
    ]
    require(len(final_outcome_gate) == 1, "workflow-outcome-final-gate")

    lowered = source.casefold()
    for forbidden in (
        "pull_request",
        "workflow_run",
        "repository_dispatch",
        "workflow_call",
        "schedule:",
        "packages: write",
        "strictHostKeyChecking=no".casefold(),
        "userknownhostsfile=/dev/null",
        "forwardagent yes",
        "sshpass",
        "rsync",
        "sudo",
        "docker build",
        "docker compose down",
        "prune",
        ":latest",
        "shell=true",
        "bash -c",
        "eval ",
    ):
        require(forbidden not in lowered, f"workflow-forbidden:{forbidden}")

    for command in (
        "deployment_transport.py trust",
        "deployment_transport.py prepare",
        "deployment_transport.py deploy",
        "deployment_transport.py outcome",
    ):
        require(command in source, f"workflow-command:{command}")


def validate(root: Path = ROOT) -> None:
    required = [WORKFLOW]
    for schema_path, example_path in CONTRACTS.values():
        required.extend((schema_path, example_path))
    for relative in required:
        require((root / relative).is_file(), f"required-file:{relative}")

    for name, (schema_path, example_path) in CONTRACTS.items():
        schema = read_json(root / schema_path)
        example_file = root / example_path
        example = read_json(example_file)
        validate_contract(name, schema, example, example_file.read_bytes())

    workflow_path = root / WORKFLOW
    source = workflow_path.read_text(encoding="utf-8")
    try:
        workflow = yaml.safe_load(source)
    except yaml.YAMLError as exc:
        raise ValidationError("workflow-yaml") from exc
    require(isinstance(workflow, dict), "workflow-object")
    validate_workflow(workflow, source)


def main() -> int:
    try:
        validate(ROOT)
    except (OSError, ValidationError, json.JSONDecodeError) as exc:
        code = str(exc) if isinstance(exc, ValidationError) else "validation-io"
        print(f"deploy-workflow-contract: failed:{code}", file=sys.stderr)
        return 1
    print("deploy-workflow-contract: ok")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
