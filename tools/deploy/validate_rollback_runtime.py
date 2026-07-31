"""Static contract validator for the locally activated S26 runtime."""

from __future__ import annotations

import ast
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class ValidationError(ValueError):
    pass


def require(condition: bool, code: str) -> None:
    if not condition:
        raise ValidationError(code)


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def validate() -> None:
    api = text("release_control/src/emporio_release_control/deployer_api.py")
    service = text("release_control/src/emporio_release_control/deployer_service.py")
    persistence = text("release_control/src/emporio_release_control/persistence.py")
    reconciliation = text("release_control/src/emporio_release_control/deployer_reconciliation.py")
    constants = text("release_control/src/emporio_release_control/constants.py")
    protocol = text("ops/deploy/rollback_protocol.py")
    require('"/api/deployment-control/v1/rollbacks"' in api, "rollback-post-route")
    require('"/api/deployment-control/v1/rollbacks/{operation_id}"' in api, "rollback-get-route")
    require('"deployment:rollback"' in api, "rollback-capability")
    require("ROLLBACK_IDEMPOTENCY_RE" in api, "rollback-idempotency")
    for marker in (
        "create_rollback",
        "get_rollback_operation",
        'operation_type="rollback"',
        '"production_global"',
        "_rollback_target",
        "databaseRestoreRequired",
        "apply_rollback_outcome",
        "ROLLBACK_TRANSITIONS",
    ):
        require(marker in service, f"service:{marker}")
    for marker in ("RollbackBackup", "source_state_sha256", "journal_json", "evidence_json"):
        require(marker in persistence, f"persistence:{marker}")
    for marker in ("ROLLBACK_WORKFLOW", "ROLLBACK_STATES", "ROLLBACK_TERMINAL_STATES"):
        require(marker in constants, f"constants:{marker}")
    require("_rollback_operation" in reconciliation, "reconciliation")
    require('revision: str = "0003_commercial_rollback"' in text("release_control/migrations/versions/0003_commercial_rollback.py"), "migration")
    require("emporio-commercial-rollback-transport" in protocol, "protocol-name")
    require("socket" not in protocol and "subprocess" not in protocol, "protocol-offline")
    outcome = json.loads(text("ops/deploy/schemas/rollback-workflow-outcome.schema.json"))
    require(outcome["additionalProperties"] is False, "outcome-closed")
    require(outcome["properties"]["kind"]["const"] == "rollback-workflow-outcome", "outcome-kind")
    require((ROOT / ".github/workflows/rollback-production.yml").is_file(), "workflow")
    require((ROOT / "docs/infrastructure/deployment/release-control/ROLLBACK_RUNTIME.md").is_file(), "runtime-doc")
    ast.parse(api)
    ast.parse(service)


def main() -> int:
    try:
        validate()
    except (OSError, UnicodeError, ValueError, json.JSONDecodeError) as exc:
        print(f"rollback-runtime:invalid:{exc}", file=sys.stderr)
        return 3
    print("rollback-runtime:valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
