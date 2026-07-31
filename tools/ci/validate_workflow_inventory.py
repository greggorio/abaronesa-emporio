"""Fail-closed offline gate for the S29 workflow inventory."""

from __future__ import annotations

import ast
import re
import sys
from pathlib import Path
from typing import Any

import yaml

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW_DIR = Path(".github/workflows")
EXPECTED_WORKFLOWS = frozenset(
    {
        "ci.yml",
        "publish-candidate.yml",
        "publish-release.yml",
        "deploy-production.yml",
        "rollback-production.yml",
    }
)
CHECKOUT_SHA = "de0fac2e4500dabe0009e67214ff5f5447ce83dd"
ACTION_PIN = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+@[0-9a-f]{40}$")


def _workflow_on(workflow: dict[str, Any]) -> Any:
    return workflow.get("on", workflow.get(True))


def _load_workflow(path: Path) -> tuple[dict[str, Any] | None, str | None]:
    try:
        value = yaml.load(path.read_text(encoding="utf-8"), Loader=yaml.BaseLoader)
    except (OSError, UnicodeError, yaml.YAMLError) as exc:
        return None, f"yaml:{path.name}:{type(exc).__name__}"
    if not isinstance(value, dict):
        return None, f"shape:{path.name}"
    return value, None


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


def _release_expected(source: str) -> set[str] | None:
    try:
        tree = ast.parse(source)
    except SyntaxError:
        return None
    for node in tree.body:
        if isinstance(node, ast.Assign) and any(
            isinstance(target, ast.Name) and target.id == "EXPECTED"
            for target in node.targets
        ):
            try:
                value = ast.literal_eval(node.value)
            except (ValueError, TypeError):
                return None
            if isinstance(value, (set, frozenset, list, tuple)) and all(
                isinstance(item, str) for item in value
            ):
                return set(value)
    return None


def _validate_readme(root: Path, errors: list[str]) -> None:
    path = root / ".github/workflows/README.md"
    try:
        text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeError):
        errors.append("readme-unreadable")
        return
    required = (
        "Existem exatamente cinco workflows ativos",
        "`ci.yml`",
        "`publish-candidate.yml`",
        "`publish-release.yml`",
        "`deploy-production.yml`",
        "`rollback-production.yml`",
        "`rollback-production.yml` é exclusivamente manual",
        "ainda não foram executados no GitHub",
        "não foi executado remotamente",
    )
    errors.extend(f"readme:{marker}" for marker in required if marker not in text)
    if "Existem exatamente quatro workflows" in text:
        errors.append("readme-four-workflows")


def _validate_rollback(workflow: dict[str, Any], source: str, errors: list[str]) -> None:
    if workflow.get("name") != "Rollback Production":
        errors.append("rollback-name")
    trigger = _workflow_on(workflow)
    if not isinstance(trigger, dict) or set(trigger) != {"workflow_dispatch"}:
        errors.append("rollback-manual-trigger")
    else:
        dispatch = trigger["workflow_dispatch"]
        inputs = dispatch.get("inputs", {}) if isinstance(dispatch, dict) else {}
        if set(inputs) != {"operation_id", "release"}:
            errors.append("rollback-inputs")
        for name in ("operation_id", "release"):
            config = inputs.get(name, {})
            if not isinstance(config, dict) or str(config.get("required")).lower() != "true":
                errors.append(f"rollback-required:{name}")

    if workflow.get("permissions") != {"contents": "read"}:
        errors.append("rollback-read-only-permissions")
    if workflow.get("concurrency") != {
        "group": "emporio-production",
        "cancel-in-progress": "false",
    }:
        errors.append("rollback-concurrency")
    jobs = workflow.get("jobs")
    if not isinstance(jobs, dict) or set(jobs) != {"protocol"}:
        errors.append("rollback-jobs")
    uses = _uses(workflow)
    if uses != [f"actions/checkout@{CHECKOUT_SHA}"]:
        errors.append("rollback-checkout-only")

    lowered = source.lower()
    for marker in (
        "push:",
        "pull_request:",
        "workflow_run:",
        "schedule:",
        "docker",
        "ssh",
        "curl",
        "scp",
        "rsync",
        "secrets.",
        "github_token",
        "contents: write",
        "actions: write",
        "packages: write",
    ):
        if marker in lowered:
            errors.append(f"rollback-forbidden:{marker}")


def validate(root: Path = ROOT) -> list[str]:
    errors: list[str] = []
    directory = root / WORKFLOW_DIR
    try:
        active = {
            path.name
            for path in directory.iterdir()
            if path.is_file() and path.suffix in {".yml", ".yaml"}
        }
    except OSError:
        return ["workflow-directory"]
    if active != EXPECTED_WORKFLOWS:
        errors.append(f"workflow-set:{sorted(active)}")

    loaded: dict[str, dict[str, Any]] = {}
    sources: dict[str, str] = {}
    for name in sorted(EXPECTED_WORKFLOWS):
        path = directory / name
        workflow, error = _load_workflow(path)
        if error:
            errors.append(error)
            continue
        assert workflow is not None
        loaded[name] = workflow
        sources[name] = path.read_text(encoding="utf-8")
        for uses in _uses(workflow):
            if ACTION_PIN.fullmatch(uses) is None:
                errors.append(f"action-pin:{name}:{uses}")

    rollback = loaded.get("rollback-production.yml")
    if rollback is not None:
        _validate_rollback(rollback, sources["rollback-production.yml"], errors)

    release_validator = root / "tools/releases/validate_release_workflow.py"
    try:
        expected = _release_expected(release_validator.read_text(encoding="utf-8"))
    except (OSError, UnicodeError):
        expected = None
    if expected != set(EXPECTED_WORKFLOWS):
        errors.append("release-validator-expected")

    _validate_readme(root, errors)
    return sorted(set(errors))


def main() -> int:
    errors = validate()
    if errors:
        for error in errors:
            print(f"workflow-inventory:invalid:{error}", file=sys.stderr)
        return 3
    print("workflow-inventory:valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
