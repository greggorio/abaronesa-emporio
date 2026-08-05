#!/usr/bin/env python3
"""Static fail-closed validator for the release publication workflow."""
from __future__ import annotations
import json
from pathlib import Path
import jsonschema
import yaml

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/publish-release.yml"
EXPECTED = {
    "ci.yml",
    "publish-candidate.yml",
    "publish-release.yml",
    "deploy-production.yml",
    "rollback-production.yml",
    "verify-production-transport.yml",
    "verify-deployment-engine.yml",
    # Operational-only workflow for the release-control image.
    "publish-release-control.yml",
}
ACTIONS = {
    "actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd",
    "actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02",
    "actions/download-artifact@634f93cb2916e3fdff6788551b99b062d0335ce0",
}

class WorkflowError(ValueError):
    pass

def require(value: bool, code: str) -> None:
    if not value:
        raise WorkflowError(code)

def validate(path: Path = WORKFLOW) -> None:
    active = {p.name for p in (ROOT / ".github/workflows").iterdir()
              if p.is_file() and p.suffix in {".yml", ".yaml"}}
    require(active == EXPECTED, "active-workflows")
    required = [
        "ops/releases/release-publication-plan.schema.json",
        "ops/releases/release-publication-outcome.schema.json",
        "ops/releases/examples/release-publication-plan.example.json",
        "ops/releases/examples/release-publication-outcome.example.json",
        "tools/releases/release_publication.py",
    ]
    require(all((ROOT / item).is_file() for item in required), "required-artifact")
    text = path.read_text(encoding="utf-8")
    data = yaml.load(text, Loader=yaml.BaseLoader)
    require(isinstance(data, dict) and set(data) >= {"name", "on", "permissions", "concurrency", "jobs"}, "shape")
    require(data["name"] == "Publish Release", "name")
    trigger = data["on"]
    require(isinstance(trigger, dict) and set(trigger) == {"workflow_dispatch"}, "trigger")
    inputs = trigger["workflow_dispatch"].get("inputs", {})
    require(list(inputs) == ["operation_id", "candidate_id", "version_bump", "description", "changelog"], "inputs")
    require(inputs["version_bump"].get("type") == "choice", "bump-type")
    require(inputs["version_bump"].get("options") == ["PATCH", "MINOR", "MAJOR"], "bump-options")
    require(all(item.get("required") == "true" for item in inputs.values()), "inputs-required")
    require(data["permissions"] == {"contents": "read", "actions": "read"}, "permissions-top")
    require(data["concurrency"] == {"group": "emporio-release-publication", "cancel-in-progress": "false"}, "concurrency")
    jobs = data["jobs"]
    require(list(jobs) == ["trust", "prepare", "publish", "outcome"], "jobs")
    needs = {"prepare": "trust", "publish": "prepare", "outcome": ["prepare", "publish"]}
    timeouts = {"trust": "10", "prepare": "20", "publish": "15", "outcome": "10"}
    for name, job in jobs.items():
        require(job.get("runs-on") == "ubuntu-24.04", "runner")
        require(job.get("timeout-minutes") == timeouts[name], "timeout")
        if name in needs:
            require(job.get("needs") == needs[name], "graph")
        permissions = job.get("permissions", {"contents": "read", "actions": "read"})
        wanted = {"contents": "write", "actions": "read"} if name == "publish" else {"contents": "read", "actions": "read"}
        require(permissions == wanted, "job-permissions")
        for step in job.get("steps", []):
            if "uses" in step:
                require(step["uses"] in ACTIONS, "action-pin")
                if step["uses"].startswith("actions/checkout@"):
                    require(step.get("with", {}).get("persist-credentials") == "false", "checkout-credentials")
                    require(step.get("with", {}).get("fetch-depth") == "0", "checkout-depth")
            run = step.get("run", "")
            require("${{ inputs." not in run and "${{ github.event.inputs." not in run, "untrusted-interpolation")
    require(jobs["publish"].get("if") == "needs.prepare.outputs.mode == 'publish'", "publish-gate")
    outcome_if = jobs["outcome"].get("if", "")
    require("needs.prepare.result == 'success'" in outcome_if and "needs.publish.result == 'skipped'" in outcome_if, "outcome-gate")
    lowered = text.lower()
    forbidden = ("workflow_run:", "push:", "pull_request:", "schedule:", "packages:", "id-token:",
                 "attestations:", "deployments:", "environment:", "ssh", "docker ", "compose ", "production")
    require(all(item not in lowered for item in forbidden), "forbidden-capability")
    for schema, example in (
        ("release-publication-plan.schema.json", "release-publication-plan.example.json"),
        ("release-publication-outcome.schema.json", "release-publication-outcome.example.json"),
    ):
        s = json.loads((ROOT / "ops/releases" / schema).read_text())
        e = json.loads((ROOT / "ops/releases/examples" / example).read_text())
        jsonschema.Draft202012Validator(s, format_checker=jsonschema.FormatChecker()).validate(e)

def main() -> int:
    try:
        validate()
    except Exception as exc:
        code = str(exc) if isinstance(exc, WorkflowError) else "validation-error"
        print(f"release-workflow:invalid:{code}")
        return 3
    print("release-workflow:valid")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
