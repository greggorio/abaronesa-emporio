"""Closed, versioned command envelope for commercial rollback transport.

This module is intentionally an offline protocol boundary.  It validates the
envelope that a future remote adapter may execute; it never opens a network
connection,
invokes a process, reads a secret, or mutates a deployment root.
"""

from __future__ import annotations

import argparse
import json
import re
from typing import Any

PROTOCOL = "emporio-commercial-rollback-transport"
SCHEMA_VERSION = 1
STATES = (
    "PRECHECKING",
    "RESTORING",
    "SWITCHING",
    "VERIFYING",
    "ROLLING_BACK",
)
OPERATION_RE = re.compile(r"^rbk_[0-9a-f]{32}$")
RELEASE_RE = re.compile(r"^v(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)$")
DIGEST_RE = re.compile(r"^sha256:[0-9a-f]{64}$")


class ProtocolError(ValueError):
    pass


def command(operation_id: str, state: str, target_release: str, evidence_sha256: str) -> dict[str, Any]:
    if not OPERATION_RE.fullmatch(operation_id) or state not in STATES or not RELEASE_RE.fullmatch(target_release):
        raise ProtocolError("command-binding-invalid")
    if not DIGEST_RE.fullmatch(evidence_sha256):
        raise ProtocolError("evidence-digest-invalid")
    return {
        "protocol": PROTOCOL,
        "schemaVersion": SCHEMA_VERSION,
        "operationId": operation_id,
        "state": state,
        "targetRelease": target_release,
        "evidenceSha256": evidence_sha256,
    }


def validate(value: object) -> dict[str, Any]:
    if not isinstance(value, dict) or set(value) != {
        "protocol", "schemaVersion", "operationId", "state", "targetRelease", "evidenceSha256"
    }:
        raise ProtocolError("command-shape-invalid")
    if value["protocol"] != PROTOCOL or value["schemaVersion"] != SCHEMA_VERSION:
        raise ProtocolError("protocol-version-invalid")
    expected = command(
        str(value["operationId"]),
        str(value["state"]),
        str(value["targetRelease"]),
        str(value["evidenceSha256"]),
    )
    if value != expected:
        raise ProtocolError("command-binding-invalid")
    return expected


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--operation-id", required=True)
    parser.add_argument("--state", choices=STATES, required=True)
    parser.add_argument("--release", required=True)
    parser.add_argument("--evidence-sha256", required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(command(args.operation_id, args.state, args.release, args.evidence_sha256), sort_keys=True))
    except ProtocolError:
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
