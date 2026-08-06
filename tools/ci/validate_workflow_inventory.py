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
        # Non-mutating production SSH capability probe. It shares the
        # production concurrency group but cannot deploy or roll back.
        "verify-production-transport.yml",
        # Real Docker rehearsal of the production transaction without VPS or
        # production credentials.
        "verify-deployment-engine.yml",
        # Operational-only: publishes the release-control image, never a
        # commercial component and never part of the global release BOM.
        "publish-release-control.yml",
    }
)
CHECKOUT_SHA = "de0fac2e4500dabe0009e67214ff5f5447ce83dd"
UPLOAD_SHA = "ea165f8d65b6e75b540449e92b4886f43607fa02"
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
        "Existem exatamente oito workflows ativos",
        "`ci.yml`",
        "`publish-candidate.yml`",
        "`publish-release.yml`",
        "`deploy-production.yml`",
        "`rollback-production.yml`",
        "`publish-release-control.yml`",
        "`verify-production-transport.yml`",
        "`verify-deployment-engine.yml`",
        "`rollback-production.yml` é exclusivamente manual",
        "executa exclusivamente `capabilities`",
        "não realiza deploy, rollback ou mutação comercial",
    )
    errors.extend(f"readme:{marker}" for marker in required if marker not in text)
    for stale in (
        "Existem exatamente quatro workflows",
        "Existem exatamente cinco workflows",
        "Existem exatamente seis workflows",
        "Existem exatamente sete workflows",
    ):
        if stale in text:
            errors.append("readme-stale-workflow-count")


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
    if not isinstance(jobs, dict) or set(jobs) != {"rollback"}:
        errors.append("rollback-jobs")
    # O rollback deixou de ser contrato e passou a executar de fato: ele alcanca
    # a VPS por SSH e consome os segredos de transporte. O que continua proibido
    # e o que sempre foi a razao do guarda — nenhum gatilho automatico, nenhuma
    # permissao de escrita, e nada alem de checkout e upload do resultado.
    uses = _uses(workflow)
    if uses != [f"actions/checkout@{CHECKOUT_SHA}", f"actions/upload-artifact@{UPLOAD_SHA}"]:
        errors.append("rollback-actions")

    lowered = source.lower()
    for marker in (
        "push:",
        "pull_request:",
        "workflow_run:",
        "schedule:",
        "docker",
        "curl",
        "scp",
        "rsync",
        "github_token",
        "contents: write",
        "actions: write",
        "packages: write",
    ):
        if marker in lowered:
            errors.append(f"rollback-forbidden:{marker}")
    # Segredos permitidos, mas so os do transporte de producao: qualquer outro
    # indica que o rollback ganhou um alcance que ninguem revisou.
    allowed_secrets = {"PRODUCTION_SSH_PRIVATE_KEY", "PRODUCTION_SSH_KNOWN_HOSTS"}
    used_secrets = set(re.findall(r"secrets\.([A-Z0-9_]+)", source))
    for name in sorted(used_secrets - allowed_secrets):
        errors.append(f"rollback-secret-forbidden:{name}")


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
