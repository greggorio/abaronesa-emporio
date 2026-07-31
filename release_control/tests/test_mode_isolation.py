from __future__ import annotations

import ast
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "src/emporio_release_control/main.py"


def runtime_function() -> ast.FunctionDef:
    tree = ast.parse(MAIN.read_text(encoding="utf-8"))
    function = next(
        (
            node
            for node in tree.body
            if isinstance(node, ast.FunctionDef) and node.name == "create_runtime"
        ),
        None,
    )
    assert function is not None
    return function


def mode_branch(name: str) -> list[ast.stmt]:
    function = runtime_function()
    for current in (node for node in ast.walk(function) if isinstance(node, ast.If)):
        test = current.test
        if (
            isinstance(test, ast.Compare)
            and len(test.comparators) == 1
            and isinstance(test.comparators[0], ast.Name)
            and test.comparators[0].id == name
        ):
            return current.body
    raise AssertionError(f"mode branch missing: {name}")


def imported_modules(nodes: list[ast.stmt]) -> set[str]:
    return {
        node.module or ""
        for statement in nodes
        for node in ast.walk(statement)
        if isinstance(node, ast.ImportFrom)
    }


def called_names(nodes: list[ast.stmt]) -> set[str]:
    return {
        node.func.id
        for statement in nodes
        for node in ast.walk(statement)
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Name)
    }


def test_publisher_branch_instantiates_only_publisher_graph() -> None:
    branch = mode_branch("PUBLISHER_MODE")
    modules = imported_modules(branch)
    calls = called_names(branch)
    assert {"api", "reconciliation", "service"} <= {value.removeprefix(".") for value in modules}
    assert "deployer_api" not in " ".join(modules)
    assert "create_app" in calls
    assert "create_deployer_app" not in calls
    assert "PublisherService" in calls
    assert "DeployerService" not in calls


def test_deployer_branch_instantiates_only_deployer_graph() -> None:
    branch = mode_branch("DEPLOYER_MODE")
    modules = imported_modules(branch)
    calls = called_names(branch)
    assert {"deployer_api", "deployer_reconciliation", "deployer_service"} <= {
        value.removeprefix(".") for value in modules
    }
    assert "api" not in modules and "service" not in modules
    assert "create_deployer_app" in calls
    assert "create_app" not in calls
    assert "DeployerService" in calls
    assert "PublisherService" not in calls


def test_mode_specific_imports_are_lazy() -> None:
    function = runtime_function()
    top_level_imports = {
        node.module or ""
        for node in ast.parse(MAIN.read_text(encoding="utf-8")).body
        if isinstance(node, ast.ImportFrom)
    }
    assert (
        not {
            "api",
            "service",
            "reconciliation",
            "deployer_api",
            "deployer_service",
            "deployer_reconciliation",
        }
        & top_level_imports
    )
    assert sum(isinstance(node, ast.If) for node in function.body) >= 1


def test_unknown_mode_fails_without_fallback() -> None:
    function = runtime_function()
    messages: list[object] = []
    for node in ast.walk(function):
        if not isinstance(node, ast.Raise) or not isinstance(node.exc, ast.Call):
            continue
        call = node.exc
        if (
            isinstance(call.func, ast.Name)
            and call.func.id == "RuntimeError"
            and call.args
            and isinstance(call.args[0], ast.Constant)
        ):
            messages.append(call.args[0].value)
    assert "unsupported release-control mode" in messages


def test_shutdown_closes_only_constructed_graph_resources() -> None:
    function = runtime_function()
    shutdown = next(
        (
            node
            for node in ast.walk(function)
            if isinstance(node, ast.FunctionDef) and node.name == "shutdown"
        ),
        None,
    )
    assert shutdown is not None
    source = ast.unparse(shutdown)
    for marker in ("loop.stop()", "jwks_http.close()", "github.client.close()", "engine.dispose()"):
        assert marker in source
