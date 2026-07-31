#!/usr/bin/env python3
"""Fail-closed validator for the S19 transactional deployment core."""

from __future__ import annotations

import ast
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import jsonschema
import yaml


ROOT = Path(__file__).resolve().parents[2]
SCHEMA = ROOT / "ops/deploy/schemas/deployment-journal.schema.json"
EXAMPLE = ROOT / "ops/deploy/examples/deployment-journal.example.json"
EXECUTOR = ROOT / "tools/deploy/deployment_executor.py"
STATE_MACHINES = (
    ROOT
    / "docs/infrastructure/deployment/release-control/contracts/state-machines.yml"
)
DOCUMENTATION = (
    ROOT
    / "docs/infrastructure/deployment/release-control/TRANSACAO_IMPLANTACAO.md"
)
PLAN_DOCUMENTATION = (
    ROOT / "docs/infrastructure/deployment/release-control/PLANO_IMPLANTACAO.md"
)
README = ROOT / "docs/infrastructure/deployment/release-control/README.md"

STATES = (
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
)
TERMINAL_STATES = frozenset(("SUCCEEDED", "ROLLED_BACK", "FAILED"))
STEPS = (
    "PULL",
    "BACKUP",
    "MIGRATE",
    "UPDATE",
    "VERIFY",
    "COMMIT_STATE",
    "ROLLBACK",
)
TRANSITIONS = frozenset(
    (
        ("QUEUED", "PULLING"),
        ("PULLING", "BACKING_UP"),
        ("BACKING_UP", "MIGRATING"),
        ("MIGRATING", "UPDATING"),
        ("UPDATING", "VERIFYING"),
        ("VERIFYING", "SUCCEEDED"),
        ("QUEUED", "FAILED"),
        ("PULLING", "FAILED"),
        ("BACKING_UP", "FAILED"),
        ("MIGRATING", "ROLLING_BACK"),
        ("UPDATING", "ROLLING_BACK"),
        ("VERIFYING", "ROLLING_BACK"),
        ("ROLLING_BACK", "ROLLED_BACK"),
        ("ROLLING_BACK", "FAILED"),
    )
)
S22_DIRECT_TRANSITIONS = frozenset(
    (
        ("QUEUED", "SUCCEEDED"),
        ("QUEUED", "ROLLED_BACK"),
    )
)
MACHINE_TRANSITIONS = TRANSITIONS | S22_DIRECT_TRANSITIONS
STEP_STATES = {
    "PULL": "PULLING",
    "BACKUP": "BACKING_UP",
    "MIGRATE": "MIGRATING",
    "UPDATE": "UPDATING",
    "VERIFY": "VERIFYING",
    "COMMIT_STATE": "VERIFYING",
    "ROLLBACK": "ROLLING_BACK",
}
PUBLIC_SURFACE = frozenset(
    (
        "DeploymentExecutionError",
        "ProbeResult",
        "ActionContext",
        "DeploymentAdapter",
        "DeploymentClock",
        "execute_deployment",
    )
)
TIME_RE = re.compile(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z")


class ValidationError(ValueError):
    """Stable validation failure."""


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


def _timestamp(value: Any, code: str) -> datetime:
    require(isinstance(value, str) and TIME_RE.fullmatch(value) is not None, code)
    try:
        return datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(
            tzinfo=timezone.utc
        )
    except ValueError as exc:
        raise ValidationError(code) from exc


def validate_schema_and_example(
    schema: dict[str, Any], example: dict[str, Any]
) -> None:
    require(
        schema.get("$schema") == "https://json-schema.org/draft/2020-12/schema",
        "journal-schema-draft",
    )
    require_closed_objects(schema)
    try:
        jsonschema.Draft202012Validator.check_schema(schema)
    except jsonschema.SchemaError as exc:
        raise ValidationError("journal-schema-invalid") from exc
    errors = list(
        jsonschema.Draft202012Validator(
            schema, format_checker=jsonschema.FormatChecker()
        ).iter_errors(example)
    )
    require(not errors, "journal-example-schema")


def validate_journal(value: dict[str, Any]) -> None:
    """Validate cross-field invariants not expressible conveniently in JSON Schema."""
    transitions = value.get("transitions")
    steps = value.get("steps")
    require(isinstance(transitions, list) and transitions, "journal-transitions")
    require(
        isinstance(steps, list)
        and [step.get("name") for step in steps if isinstance(step, dict)]
        == list(STEPS),
        "journal-step-order",
    )
    require(value.get("sequence") == len(transitions), "journal-sequence")
    require(
        [item.get("sequence") for item in transitions]
        == list(range(1, len(transitions) + 1)),
        "journal-transition-sequence",
    )
    first = transitions[0]
    require(
        first.get("from") is None and first.get("to") == "QUEUED",
        "journal-initial-transition",
    )
    previous_to = "QUEUED"
    chronological: list[datetime] = [
        _timestamp(value.get("createdAt"), "journal-created-at")
    ]
    chronological.append(_timestamp(first.get("at"), "journal-transition-time"))
    for transition in transitions[1:]:
        source = transition.get("from")
        target = transition.get("to")
        require(source == previous_to, "journal-transition-chain")
        require((source, target) in TRANSITIONS, "journal-transition-machine")
        previous_to = target
        chronological.append(
            _timestamp(transition.get("at"), "journal-transition-time")
        )
    require(value.get("state") == previous_to, "journal-state-transition")
    require(
        all(left <= right for left, right in zip(chronological, chronological[1:])),
        "journal-transition-time-order",
    )

    updated = _timestamp(value.get("updatedAt"), "journal-updated-at")
    transition_entries = {
        item["to"]: _timestamp(item["at"], "journal-transition-time")
        for item in transitions
    }
    transition_exits = {
        item["from"]: _timestamp(item["at"], "journal-transition-time")
        for item in transitions
        if item["from"] is not None
    }
    mutation_times = list(chronological)
    for step in steps:
        status = step.get("status")
        attempts = step.get("attempts")
        started = step.get("startedAt")
        finished = step.get("finishedAt")
        evidence = step.get("evidence")
        if status == "PENDING":
            require(
                attempts == 0
                and started is None
                and finished is None
                and evidence is None
                and step.get("errorCode") is None,
                f"journal-step-pending:{step['name']}",
            )
        elif status == "RUNNING":
            require(
                isinstance(attempts, int)
                and attempts >= 1
                and started is not None
                and finished is None
                and evidence is None,
                f"journal-step-running:{step['name']}",
            )
        elif status in {"SUCCEEDED", "SKIPPED", "FAILED"}:
            require(
                isinstance(attempts, int)
                and attempts >= 0
                and started is not None
                and finished is not None,
                f"journal-step-finished:{step['name']}",
            )
            require(
                (status == "SUCCEEDED") is (evidence is not None),
                f"journal-step-evidence:{step['name']}",
            )
            if status == "FAILED":
                require(step.get("errorCode") is not None, f"journal-step-error:{step['name']}")
        else:
            raise ValidationError(f"journal-step-status:{step.get('name')}")
        if started is not None:
            started_time = _timestamp(started, "journal-step-time")
            mutation_times.append(started_time)
        if finished is not None:
            finished_time = _timestamp(finished, "journal-step-time")
            mutation_times.append(finished_time)
            if started is not None:
                require(
                    _timestamp(started, "journal-step-time") <= finished_time,
                    f"journal-step-time-order:{step['name']}",
                )
        if evidence is not None:
            observed = _timestamp(
                evidence.get("observedAt"), "journal-evidence-time"
            )
            mutation_times.append(observed)
            require(
                started is not None
                and finished is not None
                and _timestamp(started, "journal-step-time")
                <= observed
                <= _timestamp(finished, "journal-step-time"),
                f"journal-evidence-order:{step['name']}",
            )
        if status != "PENDING":
            state = STEP_STATES[step["name"]]
            require(state in transition_entries, f"journal-step-window:{step['name']}")
            lower = transition_entries[state]
            upper = transition_exits.get(state, updated)
            require(lower <= upper, f"journal-step-window:{step['name']}")
            require(
                started is not None
                and lower <= _timestamp(started, "journal-step-time") <= upper,
                f"journal-step-window:{step['name']}",
            )
            if finished is not None:
                require(
                    lower <= _timestamp(finished, "journal-step-time") <= upper,
                    f"journal-step-window:{step['name']}",
                )
            if evidence is not None:
                require(
                    lower
                    <= _timestamp(evidence["observedAt"], "journal-evidence-time")
                    <= upper,
                    f"journal-step-window:{step['name']}",
                )

    require(updated >= max(mutation_times), "journal-updated-at-order")
    verify = next(step for step in steps if step["name"] == "VERIFY")
    commit = next(step for step in steps if step["name"] == "COMMIT_STATE")
    if commit.get("status") != "PENDING":
        require(
            verify.get("finishedAt") is not None
            and commit.get("startedAt") is not None
            and _timestamp(verify["finishedAt"], "journal-step-time")
            <= _timestamp(commit["startedAt"], "journal-step-time"),
            "journal-commit-after-verify",
        )
    terminal = value.get("state") in TERMINAL_STATES
    require(
        (value.get("finishedAt") is not None) is terminal,
        "journal-finished-at",
    )
    if terminal:
        finished_at = _timestamp(value["finishedAt"], "journal-finished-at")
        last_transition_at = _timestamp(
            transitions[-1]["at"], "journal-transition-time"
        )
        require(
            finished_at == updated == last_transition_at,
            "journal-finished-at-order",
        )

    by_name = {step["name"]: step for step in steps}
    state = value.get("state")
    if state == "SUCCEEDED":
        require(
            by_name["COMMIT_STATE"]["status"] == "SUCCEEDED"
            and value.get("errorCode") is None
            and value.get("rollbackErrorCode") is None
            and isinstance(value.get("confirmedStateSha256"), str),
            "journal-succeeded-invariants",
        )
        expected_evidence = "state:" + value["confirmedStateSha256"].removeprefix(
            "sha256:"
        )
        require(
            by_name["COMMIT_STATE"]["evidence"].get("evidenceId")
            == expected_evidence,
            "journal-confirmed-state-evidence",
        )
    elif state == "ROLLED_BACK":
        require(
            by_name["ROLLBACK"]["status"] == "SUCCEEDED"
            and value.get("errorCode") is not None,
            "journal-rolled-back-invariants",
        )
    elif state == "FAILED":
        require(
            value.get("errorCode") is not None
            or value.get("rollbackErrorCode") is not None,
            "journal-failed-invariants",
        )
    require(
        (value.get("sourceRelease") is None)
        is (value.get("sourceStateSha256") is None),
        "journal-source-state",
    )


def validate_state_machine(root: Path) -> None:
    value = yaml.safe_load(
        (root / STATE_MACHINES.relative_to(ROOT)).read_text(encoding="utf-8")
    )
    try:
        deployment = value["machines"]["deployment"]
        states = deployment["states"]
        transitions = deployment["transitions"]
    except (KeyError, TypeError) as exc:
        raise ValidationError("state-machine-shape") from exc
    require(
        tuple(state for state in states if state != "AVAILABLE") == STATES,
        "state-machine-states",
    )
    machine_items = [
        item for item in transitions if item.get("from") != "AVAILABLE"
    ]
    machine_edges = {
        (item.get("from"), item.get("to"))
        for item in machine_items
    }
    require(
        machine_edges == MACHINE_TRANSITIONS
        and len(machine_items) == len(MACHINE_TRANSITIONS),
        "state-machine-transitions",
    )
    direct_items = {
        (item.get("from"), item.get("to")): item
        for item in machine_items
        if (item.get("from"), item.get("to")) in S22_DIRECT_TRANSITIONS
    }
    for source, target in S22_DIRECT_TRANSITIONS:
        require(
            direct_items.get((source, target))
            == {
                "from": source,
                "to": target,
                "actor": "reconciler",
                "requires_remote_evidence": True,
            },
            "state-machine-direct-transition-metadata",
        )
    available_edges = {
        (item.get("from"), item.get("to"))
        for item in transitions
        if item.get("from") == "AVAILABLE"
    }
    require(
        available_edges == {("AVAILABLE", "QUEUED")},
        "state-machine-eligibility-boundary",
    )


def validate_executor_source(root: Path) -> None:
    source = (root / EXECUTOR.relative_to(ROOT)).read_text(encoding="utf-8")
    try:
        tree = ast.parse(source)
    except SyntaxError as exc:
        raise ValidationError("executor-syntax") from exc
    definitions = {
        node.name
        for node in tree.body
        if isinstance(node, (ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef))
    }
    require(PUBLIC_SURFACE <= definitions, "executor-public-surface")
    lowered = source.casefold()
    for forbidden in (
        "import subprocess",
        "from subprocess",
        "os.system",
        "os.popen",
        "docker compose",
        "docker sdk",
        "podman",
        "psql",
        "pg_dump",
        "requests.",
        "urllib.request",
        "socket.",
        "paramiko",
    ):
        require(forbidden not in lowered, "executor-operational-adapter")
    for clock in ("datetime.now(", "datetime.utcnow(", "time.time("):
        require(clock not in source, "executor-system-clock")
    for token in (
        "fcntl.flock",
        "fcntl.LOCK_EX",
        "fcntl.LOCK_NB",
        "O_NOFOLLOW",
        "validate_bundle",
        "os.replace",
        "os.fsync",
        "0o600",
        "JOURNAL_IO_FAILED",
        "PRODUCTION_OPERATION_ACTIVE",
        "OPERATION_CONFLICT",
    ):
        require(token in source, f"executor-required:{token}")


def validate_documentation(root: Path) -> None:
    transaction = (
        root / DOCUMENTATION.relative_to(ROOT)
    ).read_text(encoding="utf-8").casefold()
    for phrase in (
        "bundle",
        "journal",
        "adapter",
        "lock global",
        "probe",
        "queued",
        "commit_state",
        "databaseRestoreRequired",
        "restore",
        "s20",
        "não implanta",
    ):
        require(phrase.casefold() in transaction, f"documentation:{phrase}")
    plan = (
        root / PLAN_DOCUMENTATION.relative_to(ROOT)
    ).read_text(encoding="utf-8").casefold()
    for phrase in ("transação", "journal", "s19"):
        require(phrase.casefold() in plan, f"plan-documentation:{phrase}")
    readme = (root / README.relative_to(ROOT)).read_text(encoding="utf-8")
    require(
        "./TRANSACAO_IMPLANTACAO.md" in readme,
        "readme-documentation:transaction-link",
    )


def validate(root: Path = ROOT) -> None:
    required = (
        SCHEMA,
        EXAMPLE,
        EXECUTOR,
        STATE_MACHINES,
        DOCUMENTATION,
        PLAN_DOCUMENTATION,
        README,
    )
    require(
        all((root / path.relative_to(ROOT)).is_file() for path in required),
        "required-file",
    )
    schema = read_json(root / SCHEMA.relative_to(ROOT))
    example = read_json(root / EXAMPLE.relative_to(ROOT))
    validate_schema_and_example(schema, example)
    validate_journal(example)
    validate_state_machine(root)
    validate_executor_source(root)
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
        print(f"deployment-executor-contract:invalid:{code}", file=sys.stderr)
        return 3
    print("deployment-executor-contract:valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
