#!/usr/bin/env python3
"""Static fail-closed contract for the isolated deployment-engine rehearsal."""

from __future__ import annotations

import ast
import sys
from pathlib import Path
from typing import Any

import yaml

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = Path(".github/workflows/verify-deployment-engine.yml")
RUNTIME = Path("tools/deploy/deployment_engine_rehearsal.py")
PINS = {
    "actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd",
    "actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02",
    "actions/download-artifact@634f93cb2916e3fdff6788551b99b062d0335ce0",
}


def _on(value: dict[str, Any]) -> Any:
    return value.get("on", value.get(True))


def _assignment(source: str, name: str) -> Any:
    try:
        tree = ast.parse(source)
    except SyntaxError:
        return None
    for node in tree.body:
        if isinstance(node, ast.Assign) and any(isinstance(target, ast.Name) and target.id == name for target in node.targets):
            try:
                return ast.literal_eval(node.value)
            except (ValueError, TypeError):
                return None
    return None


def validate(root: Path = ROOT) -> list[str]:
    errors: list[str] = []
    try:
        source = (root / WORKFLOW).read_text(encoding="utf-8")
        runtime = (root / RUNTIME).read_text(encoding="utf-8")
        workflow = yaml.load(source, Loader=yaml.BaseLoader)
    except (OSError, UnicodeError, yaml.YAMLError):
        return ["source"]
    if not isinstance(workflow, dict):
        return ["shape"]
    if workflow.get("name") != "Verify Deployment Engine": errors.append("name")
    if workflow.get("run-name") != "verify-deployment-engine-${{ github.sha }}-${{ github.run_id }}-${{ github.run_attempt }}": errors.append("run-name")
    trigger = _on(workflow)
    if not isinstance(trigger, dict) or set(trigger) != {"workflow_dispatch"}: errors.append("trigger")
    elif isinstance(trigger["workflow_dispatch"], dict) and trigger["workflow_dispatch"].get("inputs"): errors.append("inputs")
    if workflow.get("permissions") != {"contents": "read"}: errors.append("permissions")
    if workflow.get("concurrency") != {"group": "emporio-deployment-engine-rehearsal", "cancel-in-progress": "false"}: errors.append("concurrency")
    jobs = workflow.get("jobs")
    if not isinstance(jobs, dict) or list(jobs) != ["trust", "rehearse", "outcome"]:
        errors.append("jobs")
        jobs = jobs if isinstance(jobs, dict) else {}
    expected = {"trust": (None, "10"), "rehearse": ("trust", "90"), "outcome": (["trust", "rehearse"], "10")}
    for name, (needs, timeout) in expected.items():
        job = jobs.get(name)
        if not isinstance(job, dict): continue
        if (needs is not None and job.get("needs") != needs) or (needs is None and "needs" in job): errors.append(f"needs:{name}")
        if job.get("runs-on") != "ubuntu-24.04" or job.get("timeout-minutes") != timeout: errors.append(f"runner:{name}")
        if "environment" in job: errors.append(f"environment:{name}")
        if name == "rehearse" and job.get("permissions") != {"contents": "read", "actions": "read", "packages": "read"}: errors.append("rehearse-permissions")
        for step in job.get("steps", []):
            if not isinstance(step, dict): errors.append(f"step:{name}"); continue
            if "uses" in step and step["uses"] not in PINS: errors.append(f"action:{name}")
            if isinstance(step.get("uses"), str) and step["uses"].startswith("actions/checkout@") and step.get("with") != {"ref": "${{ github.sha }}", "persist-credentials": "false"}: errors.append(f"checkout:{name}")
    rehearsal_steps = [
        step for step in jobs.get("rehearse", {}).get("steps", [])
        if isinstance(step, dict) and step.get("id") == "rehearsal"
    ]
    if len(rehearsal_steps) != 1 or rehearsal_steps[0].get("continue-on-error") != "true":
        errors.append("rehearsal-diagnostic-preservation")
    named_rehearsal_steps = {
        step.get("name"): (index, step)
        for index, step in enumerate(jobs.get("rehearse", {}).get("steps", []))
        if isinstance(step, dict) and isinstance(step.get("name"), str)
    }
    authenticate = named_rehearsal_steps.get("Authenticate the ephemeral runner to GHCR")
    normalize = named_rehearsal_steps.get("Normalize the ephemeral Docker config permissions")
    execute = named_rehearsal_steps.get("Execute the real isolated deployment transaction")
    expected_normalization = (
        'chmod 0700 "$HOME/.docker"\n'
        'chmod 0600 "$HOME/.docker/config.json"\n'
        'test "$(stat -c \'%a\' "$HOME/.docker")" = 700\n'
        'test "$(stat -c \'%a\' "$HOME/.docker/config.json")" = 600\n'
    )
    if (
        authenticate is None
        or normalize is None
        or execute is None
        or not authenticate[0] < normalize[0] < execute[0]
        or normalize[1].get("run") != expected_normalization
    ):
        errors.append("docker-config-normalization")
    if jobs.get("outcome", {}).get("if") != "always()": errors.append("outcome-always")
    for marker in (
        "gh release download v0.1.1", "/releases/365219520", "/git/ref/tags/v0.1.1",
        "docker login ghcr.io", "docker logout ghcr.io",
        'chmod 0700 "$HOME/.docker"', 'chmod 0600 "$HOME/.docker/config.json"',
        'stat -c \'%a\' "$HOME/.docker"', 'stat -c \'%a\' "$HOME/.docker/config.json"',
        "deployment_engine_rehearsal.py rehearse", "deployment-engine-rehearsal-outcome",
        "continue-on-error: true", "if: always()", "DEPLOYER_ACTOR_IDS",
    ):
        if marker not in source: errors.append(f"marker:{marker}")
    lowered = source.lower()
    for forbidden in (
        "environment: production", "secrets.production_ssh", "production_ssh_host",
        "deploy-production.yml", "rollback-production.yml", "workflow_call:",
        "pull_request:", "push:", "schedule:", "docker system prune", ":latest",
    ):
        if forbidden in lowered: errors.append(f"forbidden:{forbidden}")
    constants = {
        "REPOSITORY": "greggorio/abaronesa-emporio",
        "WORKFLOW": ".github/workflows/verify-deployment-engine.yml",
        "RELEASE": "v0.1.1",
        "PREVIOUS_RELEASE": "v0.1.0",
        "RELEASE_ID": 365219520,
        "PROJECT": "abaronesa-emporio",
        "POSTGRES_IMAGE": "postgres:16.10-alpine3.22@sha256:029660641a0cfc575b14f336ba448fb8a75fd595d42e1fa316b9fb4378742297",
        "EXPECTED_STEPS": (
            "PULL", "BACKUP", "MIGRATE", "UPDATE", "VERIFY", "COMMIT_STATE", "ROLLBACK",
        ),
    }
    for name, value in constants.items():
        if _assignment(runtime, name) != value: errors.append(f"runtime:{name}")
    for marker in (
        "deployment_plan.generate_bundle(", "deployment_cli.py", "EMPORIO_DEPLOY_ROOT",
        "_prepare_root(bound[\"runId\"])", "_validate_ephemeral_root",
        'failed_stage = "BUNDLE_GENERATION"',
        'failed_stage = "DEPLOYMENT_CLI"',
        'failed_stage = "TRANSACTION_EVIDENCE"',
        "current_path=None", "current_manifest_path=None",
        '"BACKUP"', '"MIGRATE"', '"UPDATE"', '"VERIFY"', '"COMMIT_STATE"', '"ROLLBACK"',
        "postgres:16.10-alpine3.22@sha256:029660641a0cfc575b14f336ba448fb8a75fd595d42e1fa316b9fb4378742297",
        'journal.get("databaseRestoreRequired") is True', '"transactionStatus"',
        '"cleanupStatus"', '"causeCode"', '"postgresManifestResolved"',
        "backup-manifest.json", "journalUnchanged", "containersUnchanged",
        '"down", "-v", "--remove-orphans"', '"image", "rm"',
        "_remove_ephemeral_root(root, run_id=run_id)",
        '"failedStage"', '"PREPARE_ROOT_FAILED"',
        '"BUNDLE_GENERATION_FAILED"', '"DEPLOYMENT_CLI_FAILED"',
        '"TRANSACTION_EVIDENCE_FAILED"', '"CLEANUP_INCOMPLETE"',
    ):
        if marker not in runtime: errors.append(f"runtime-marker:{marker}")
    for forbidden in ("PRODUCTION_SSH", "environment: production", "deploy-production.yml", "rollback-production.yml", "shell=True", "docker system prune", "RUNNER_TEMP"):
        if forbidden in runtime: errors.append(f"runtime-forbidden:{forbidden}")
    return sorted(set(errors))


def main() -> int:
    errors = validate()
    if errors:
        for error in errors: print(f"deployment-engine-workflow:invalid:{error}", file=sys.stderr)
        return 3
    print("deployment-engine-workflow:valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
