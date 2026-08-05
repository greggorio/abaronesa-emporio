#!/usr/bin/env python3
"""Static fail-closed validator for the non-mutating production SSH probe."""

from __future__ import annotations

import ast
import re
import sys
from pathlib import Path
from typing import Any

import yaml

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = Path(".github/workflows/verify-production-transport.yml")
RUNTIME = Path("tools/deploy/production_transport_probe.py")
CHECKOUT = "actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd"
UPLOAD = "actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02"
DOWNLOAD = "actions/download-artifact@634f93cb2916e3fdff6788551b99b062d0335ce0"
ACTION_PIN = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+@[0-9a-f]{40}$")


def _workflow_on(value: dict[str, Any]) -> Any:
    return value.get("on", value.get(True))


def _uses(value: Any) -> list[str]:
    found: list[str] = []
    if isinstance(value, dict):
        for key, child in value.items():
            if key == "uses" and isinstance(child, str):
                found.append(child)
            found.extend(_uses(child))
    elif isinstance(value, list):
        for child in value:
            found.extend(_uses(child))
    return found


def _assignment(source: str, name: str) -> Any:
    try:
        tree = ast.parse(source)
    except SyntaxError:
        return None
    for node in tree.body:
        if isinstance(node, ast.Assign) and any(
            isinstance(target, ast.Name) and target.id == name for target in node.targets
        ):
            try:
                return ast.literal_eval(node.value)
            except (ValueError, TypeError):
                return None
    return None


def validate(root: Path = ROOT) -> list[str]:
    errors: list[str] = []
    workflow_path = root / WORKFLOW
    runtime_path = root / RUNTIME
    try:
        source = workflow_path.read_text(encoding="utf-8")
        runtime = runtime_path.read_text(encoding="utf-8")
        workflow = yaml.load(source, Loader=yaml.BaseLoader)
    except (OSError, UnicodeError, yaml.YAMLError):
        return ["source"]
    if not isinstance(workflow, dict):
        return ["shape"]
    if workflow.get("name") != "Verify Production Transport":
        errors.append("name")
    if workflow.get("run-name") != (
        "verify-production-transport-${{ github.sha }}-${{ github.run_id }}-"
        "${{ github.run_attempt }}"
    ):
        errors.append("run-name")
    trigger = _workflow_on(workflow)
    if not isinstance(trigger, dict) or set(trigger) != {"workflow_dispatch"}:
        errors.append("trigger")
    else:
        dispatch = trigger["workflow_dispatch"]
        if isinstance(dispatch, dict) and dispatch.get("inputs"):
            errors.append("inputs")
    if "${{ inputs." in source or "${{ github.event.inputs." in source:
        errors.append("untrusted-input")
    if workflow.get("permissions") != {"contents": "read"}:
        errors.append("permissions")
    if workflow.get("concurrency") != {
        "group": "emporio-production",
        "cancel-in-progress": "false",
    }:
        errors.append("concurrency")

    jobs = workflow.get("jobs")
    if not isinstance(jobs, dict) or list(jobs) != ["trust", "probe", "outcome"]:
        errors.append("jobs")
        jobs = jobs if isinstance(jobs, dict) else {}
    expected_needs: dict[str, Any] = {"probe": "trust", "outcome": ["trust", "probe"]}
    for name in ("trust", "probe", "outcome"):
        job = jobs.get(name)
        if not isinstance(job, dict):
            continue
        if job.get("runs-on") != "ubuntu-24.04" or job.get("timeout-minutes") != "10":
            errors.append(f"{name}:runner-timeout")
        if name in expected_needs and job.get("needs") != expected_needs[name]:
            errors.append(f"{name}:needs")
        if (name == "probe") != (job.get("environment") == "production"):
            errors.append(f"{name}:environment")
        if name != "probe" and "environment" in job:
            errors.append(f"{name}:environment")
        for step in job.get("steps", []):
            if not isinstance(step, dict):
                errors.append(f"{name}:step")
                continue
            action = step.get("uses")
            if action is not None and (
                not isinstance(action, str)
                or ACTION_PIN.fullmatch(action) is None
                or action not in {CHECKOUT, UPLOAD, DOWNLOAD}
            ):
                errors.append(f"{name}:action")
            if action == CHECKOUT and step.get("with") != {
                "ref": "${{ github.sha }}",
                "persist-credentials": "false",
            }:
                errors.append(f"{name}:checkout")

    trust_job = jobs.get("trust", {})
    probe_job = jobs.get("probe", {})
    outcome_job = jobs.get("outcome", {})
    trust_runs = [step.get("run") for step in trust_job.get("steps", []) if "run" in step]
    probe_runs = [step.get("run") for step in probe_job.get("steps", []) if "run" in step]
    outcome_runs = [step.get("run") for step in outcome_job.get("steps", []) if "run" in step]
    if trust_runs != [
        "python3 tools/deploy/production_transport_probe.py trust --output trust"
    ]:
        errors.append("trust-command")
    if probe_runs != [
        "python3 tools/deploy/production_transport_probe.py probe --trust trust --output probe",
        "python3 tools/deploy/production_transport_probe.py cleanup",
    ]:
        errors.append("probe-commands")
    cleanup_steps = [
        step
        for step in probe_job.get("steps", [])
        if isinstance(step, dict) and step.get("id") == "cleanup"
    ]
    if len(cleanup_steps) != 1 or cleanup_steps[0].get("if") != "always()":
        errors.append("probe-cleanup-condition")
    if outcome_runs != [
        "python3 tools/deploy/production_transport_probe.py outcome --trust trust --probe probe --output outcome"
    ]:
        errors.append("outcome-command")
    if outcome_job.get("if") != "always()":
        errors.append("outcome-condition")

    if source.count("environment: production") != 1:
        errors.append("environment-count")
    for artifact_name, expected_count in (
        ("name: production-transport-trust", 3),
        ("name: production-transport-probe", 3),
        ("name: production-transport-probe-outcome", 1),
    ):
        if source.count(artifact_name) != expected_count:
            errors.append(f"artifact-name:{artifact_name}")
    if source.count("${{ secrets.PRODUCTION_SSH_PRIVATE_KEY }}") != 1:
        errors.append("private-key-secret")
    if source.count("${{ secrets.PRODUCTION_SSH_KNOWN_HOSTS }}") != 1:
        errors.append("known-hosts-secret")
    for marker in (
        "TRUSTED_TRIGGERING_ACTOR: ${{ github.triggering_actor }}",
        "TRUSTED_SENDER_ID: ${{ github.event.sender.id }}",
        "DEPLOYER_ACTOR_IDS: ${{ vars.DEPLOYER_ACTOR_IDS }}",
        "PRODUCTION_SSH_HOST: ${{ vars.PRODUCTION_SSH_HOST }}",
        "PRODUCTION_SSH_PORT: ${{ vars.PRODUCTION_SSH_PORT }}",
        "if: always()",
        "name: production-transport-probe",
        "name: production-transport-probe-outcome",
        "persist-credentials: false",
    ):
        if marker not in source:
            errors.append(f"workflow-marker:{marker}")
    lowered = source.lower()
    for marker in (
        "packages: write",
        "contents: write",
        "actions: write",
        "id-token: write",
        "docker ",
        "scp ",
        "rsync ",
        "curl ",
        "snapshot",
        "migration",
        "rollback",
    ):
        if marker in lowered:
            errors.append(f"workflow-forbidden:{marker}")

    expected_constants = {
        "REPOSITORY": "greggorio/abaronesa-emporio",
        "WORKFLOW": ".github/workflows/verify-production-transport.yml",
        "REMOTE_USER": "deploy-emporio",
        "REMOTE_HELPER": (
            "/opt/sistemas/emporio/shared/control/ops/deploy/deployment-remote.py"
        ),
        "PROTOCOL": "emporio-deployment-transport",
        "DEPLOY_ROOT": "/opt/sistemas/emporio",
    }
    for name, expected in expected_constants.items():
        if _assignment(runtime, name) != expected:
            errors.append(f"runtime-constant:{name}")
    runtime_markers = (
        "StrictHostKeyChecking yes",
        "UserKnownHostsFile",
        "IdentitiesOnly yes",
        "IdentityAgent none",
        "BatchMode yes",
        "PasswordAuthentication no",
        "KbdInteractiveAuthentication no",
        "stderr=subprocess.DEVNULL",
        "shred",
    )
    for marker in runtime_markers:
        if marker not in runtime:
            errors.append(f"runtime-marker:{marker}")
    if "REMOTE_COMMAND = (REMOTE_HELPER, \"capabilities\")" not in runtime:
        errors.append("runtime-constant:REMOTE_COMMAND")
    if runtime.count("*REMOTE_COMMAND") != 1:
        errors.append("runtime-command-use")
    if "PRODUCTION_SSH_PRIVATE_KEY" not in runtime or "SSH_AUTH_SOCK" in runtime:
        errors.append("runtime-credential-policy")
    for forbidden in (
        '"snapshot"',
        '"upload"',
        '"install"',
        '"execute"',
        "docker",
        "scp",
        "rsync",
        "curl",
    ):
        if forbidden in runtime.lower():
            errors.append(f"runtime-forbidden:{forbidden}")
    return sorted(set(errors))


def main() -> int:
    errors = validate()
    if errors:
        for error in errors:
            print(f"production-transport-workflow:invalid:{error}", file=sys.stderr)
        return 3
    print("production-transport-workflow:valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
