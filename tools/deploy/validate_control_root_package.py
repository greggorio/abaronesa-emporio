#!/usr/bin/env python3
"""Static contract of the control-root package, lock and control-sha binding.

These checks run without building anything. They exist so a weakening of the
package boundary — an unpinned dependency, a lost hash, a helper that stops
proving which commit it is — fails in CI rather than on the production host.
"""

from __future__ import annotations

import ast
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BUILDER = ROOT / "tools/deploy/control_root_package.py"
LOCK = ROOT / "ops/deploy/control-root/requirements.lock"
HELPER = ROOT / "ops/deploy/deployment-remote.py"
TRANSPORT = ROOT / "tools/deploy/deployment_transport.py"
CLI = ROOT / "tools/deploy/deployment_cli.py"

LOCK_LINE_RE = re.compile(
    r"^[A-Za-z0-9._-]+==[A-Za-z0-9._-]+\s+sha256=[0-9a-f]{64}\s+file=[A-Za-z0-9._+-]+\.whl$"
)
FORBIDDEN_DISTRIBUTIONS = ("fastapi", "sqlalchemy", "alembic", "uvicorn", "psycopg", "httpx")


def _validate_lock(errors: list[str]) -> None:
    try:
        text = LOCK.read_text(encoding="utf-8")
    except OSError:
        errors.append("lock-unreadable")
        return
    entries = [
        line.strip()
        for line in text.splitlines()
        if line.strip() and not line.strip().startswith("#")
    ]
    if not entries:
        errors.append("lock-empty")
        return
    names: list[str] = []
    for line in entries:
        if LOCK_LINE_RE.fullmatch(line) is None:
            errors.append("lock-line-unpinned-or-unhashed")
            continue
        name = line.split("==", 1)[0].lower().replace("-", "_")
        names.append(name)
        if ".tar.gz" in line or ".zip" in line:
            errors.append("lock-accepts-sdist")
    if len(set(names)) != len(names):
        errors.append("lock-duplicated-distribution")
    if "jsonschema" not in names:
        errors.append("lock-missing-jsonschema")
    else:
        version = next(l.split("==")[1].split()[0] for l in entries if l.lower().startswith("jsonschema=="))
        if not version.startswith("4."):
            errors.append("lock-jsonschema-not-4x")
    if "pyyaml" not in names:
        errors.append("lock-missing-pyyaml")
    # referencing needs typing_extensions below Python 3.13; the target is 3.10.
    if "referencing" in names and "typing_extensions" not in names:
        errors.append("lock-missing-typing-extensions")
    for forbidden in FORBIDDEN_DISTRIBUTIONS:
        if forbidden in names:
            errors.append(f"lock-forbidden-distribution:{forbidden}")


def _constants(module: Path) -> dict[str, object]:
    tree = ast.parse(module.read_text(encoding="utf-8"))
    found: dict[str, object] = {}
    for node in tree.body:
        # Annotated assignments are AnnAssign, not Assign; both are constants here.
        if isinstance(node, ast.Assign) and len(node.targets) == 1:
            target, value = node.targets[0], node.value
        elif isinstance(node, ast.AnnAssign) and node.value is not None:
            target, value = node.target, node.value
        else:
            continue
        if isinstance(target, ast.Name):
            try:
                found[target.id] = ast.literal_eval(value)
            except ValueError:
                if (
                    isinstance(value, ast.Call)
                    and isinstance(value.func, ast.Name)
                    and value.func.id == "frozenset"
                    and len(value.args) == 1
                    and not value.keywords
                ):
                    found[target.id] = frozenset(ast.literal_eval(value.args[0]))
    return found


def _validate_builder(errors: list[str]) -> None:
    try:
        source = BUILDER.read_text(encoding="utf-8")
    except OSError:
        errors.append("builder-unreadable")
        return
    constants = _constants(BUILDER)
    if constants.get("TARGET") != "/opt/sistemas/emporio/shared/control":
        errors.append("builder-target")
    if constants.get("PLATFORM") != "linux/amd64":
        errors.append("builder-platform")
    if constants.get("PYTHON_ABI") != "cp310":
        errors.append("builder-python-abi")
    if constants.get("REMOTE_USER") != "deploy-emporio":
        errors.append("builder-remote-user")
    # Identity must come from a Git object, never from the working tree.
    if "cat-file" not in source or "blob" not in source:
        errors.append("builder-must-read-git-objects")
    for forbidden in ("shutil.copytree(", "eval(", "shell=True"):
        if forbidden in source:
            errors.append(f"builder-forbidden:{forbidden}")
    for required in (
        "issym", "islnk", "ischr", "isblk", "isfifo",   # archive member refusals
        "geteuid",                                       # root-only installer
        "0o700",                                         # control root mode
        "fsync",                                         # durability
    ):
        if required not in source:
            errors.append(f"builder-missing-check:{required}")
    files = constants.get("SOURCE_FILES")
    if not isinstance(files, tuple) or "ops/deploy/deployment-remote.py" not in files:
        errors.append("builder-allowlist")
    for entry in files or ():
        if "/tests/" in str(entry) or str(entry).endswith("_test.py"):
            errors.append("builder-allowlist-includes-tests")
    executable = constants.get("EXECUTABLE_FILES")
    if executable != frozenset(
        {
            "ops/deploy/deploy-release.sh",
            "ops/deploy/deployment-remote.py",
            "ops/db/init-databases.sh",
        }
    ):
        errors.append("builder-executable-set")
    for required in (
        "manifest executable file set is not exactly contracted",
        "archive member mode is not contracted",
        "installed file mode does not match the manifest",
        "expected_owner=(owner.pw_uid, owner.pw_gid)",
    ):
        if required not in source:
            errors.append(f"builder-missing-mode-check:{required}")


def _validate_binding(errors: list[str]) -> None:
    try:
        helper = HELPER.read_text(encoding="utf-8")
        transport = TRANSPORT.read_text(encoding="utf-8")
        cli = CLI.read_text(encoding="utf-8")
    except OSError:
        errors.append("binding-unreadable")
        return
    if "controlSha" not in helper or "_installed_control_sha" not in helper:
        errors.append("helper-does-not-report-control-sha")
    if not helper.startswith("#!/usr/bin/env python3\n"):
        errors.append("helper-missing-python-shebang")
    if "sys.dont_write_bytecode = True" not in helper:
        errors.append("helper-can-write-bytecode-to-control-root")
    for required in ("REMOTE_CONTROL_MANIFEST_MISSING", "REMOTE_CONTROL_TAMPERED"):
        if required not in helper:
            errors.append(f"helper-missing-code:{required}")
    if "def capabilities(self, control_sha: str)" not in transport:
        errors.append("transport-capabilities-must-require-control-sha")
    if '"controlSha": control_sha' not in transport:
        errors.append("transport-must-compare-control-sha")
    if 'capabilities(request["controlSha"])' not in transport:
        errors.append("transport-must-use-request-control-sha")
    # capabilities must run before anything that can mutate the host.
    for mutation in ("transport.upload(", "client.snapshot("):
        index_capability = transport.find("capabilities(request[")
        index_mutation = transport.find(mutation)
        if index_mutation != -1 and index_capability != -1 and index_mutation < index_capability:
            errors.append(f"transport-mutation-before-capabilities:{mutation}")
    # Both remote entry points are separate interpreter processes on the host
    # (the SSH helper, and deploy-release.sh's exec'd CLI); each must put the
    # vendor directory ahead of the global site-packages before importing
    # anything that touches jsonschema, or the vendoring is inert.
    for name, source, anchor in (
        ("helper", helper, "import deployment_plan"),
        ("cli", cli, "import deployment_executor"),
    ):
        vendor_index = source.find('sys.path.insert(0, str(ROOT / "vendor"))')
        anchor_index = source.find(anchor)
        if vendor_index == -1:
            errors.append(f"{name}-does-not-prioritise-vendor")
        elif anchor_index != -1 and vendor_index > anchor_index:
            errors.append(f"{name}-vendor-inserted-after-import")


def validate() -> list[str]:
    errors: list[str] = []
    _validate_lock(errors)
    _validate_builder(errors)
    _validate_binding(errors)
    return sorted(set(errors))


def main() -> int:
    errors = validate()
    if errors:
        print("control-root-package-contract:invalid:" + ",".join(errors), file=sys.stderr)
        return 3
    print("control-root-package-contract:valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
