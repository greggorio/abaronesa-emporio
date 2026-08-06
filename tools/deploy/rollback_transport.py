#!/usr/bin/env python3
"""Closed workflow-to-host transport for one commercial rollback."""
from __future__ import annotations

import json
import os
import re
import stat
import sys
import tempfile
from pathlib import Path

import deployment_transport as transport

OPERATION_RE = re.compile(r"^rbk_[0-9a-f]{32}$")
RELEASE_RE = re.compile(r"^v(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)$")


def positive(name: str) -> int:
    value = os.environ.get(name, "")
    if not value.isdecimal() or int(value) < 1:
        raise transport.DeploymentTransportError("INVALID_DISPATCH")
    return int(value)


def main() -> int:
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    operation = os.environ.get("TRUSTED_OPERATION_ID", "")
    release = os.environ.get("TRUSTED_RELEASE", "")
    sha = os.environ.get("TRUSTED_SHA", "")
    actor = positive("TRUSTED_ACTOR_ID")
    allowed = {int(x) for x in os.environ.get("DEPLOYER_ACTOR_IDS", "").split(",") if x.isdecimal()}
    if (
        os.environ.get("TRUSTED_REPOSITORY") != "greggorio/abaronesa-emporio"
        or os.environ.get("TRUSTED_EVENT") != "workflow_dispatch"
        or os.environ.get("TRUSTED_REF") != "refs/heads/main"
        or re.fullmatch(r"[0-9a-f]{40}", sha) is None
        or OPERATION_RE.fullmatch(operation) is None
        or RELEASE_RE.fullmatch(release) is None
        or actor not in allowed
    ):
        raise transport.DeploymentTransportError("INVALID_DISPATCH")
    run_id = positive("TRUSTED_RUN_ID")
    attempt = positive("TRUSTED_RUN_ATTEMPT")
    args.output.mkdir(mode=0o700, parents=False, exist_ok=False)
    ssh_dir = Path(tempfile.mkdtemp(prefix=".rollback-ssh-", dir=args.output.parent))
    ssh_dir.rmdir()
    try:
        config = transport.materialize_ssh_configuration(
            directory=ssh_dir,
            host=os.environ.get("PRODUCTION_SSH_HOST", ""),
            port=int(os.environ.get("PRODUCTION_SSH_PORT", "")),
            private_key=os.environ.get("PRODUCTION_SSH_PRIVATE_KEY", "").encode(),
            known_hosts=os.environ.get("PRODUCTION_SSH_KNOWN_HOSTS", "").encode(),
            expected_fingerprint=os.environ.get("PRODUCTION_SSH_PUBLIC_KEY_SHA256", ""),
            ssh_binary=transport.resolve_openssh("ssh"),
            scp_binary=transport.resolve_openssh("scp"),
            keygen_binary=transport.resolve_openssh("ssh-keygen"),
        )
        client = transport.OpenSshTransport(config, transport.SubprocessRunner())
        client.capabilities(sha)
        result = client._remote(("rollback", "--operation-id", operation, "--release", release), 900)
        value = transport._parse_single_json(result, "REMOTE_RESULT_INVALID")
        expected = {"databaseRestoreRequired", "operationId", "sourceRelease", "state", "targetRelease", "targetStateSha256"}
        if set(value) != expected or value["operationId"] != operation or value["targetRelease"] != release or value["state"] != "SUCCEEDED":
            raise transport.DeploymentTransportError("REMOTE_RESULT_INVALID")
        outcome = {
            "schemaVersion": 1, "kind": "rollback-workflow-outcome",
            "operationId": operation, "sourceRelease": value["sourceRelease"],
            "targetRelease": release, "rollbackState": "SUCCEEDED",
            "transportStatus": "CONFIRMED", "databaseRestoreRequired": False,
            "errorCode": None, "workflowRunId": run_id,
            "workflowRunAttempt": attempt, "controlSha": sha,
            "evidence": {"databaseRestore": "NOT_REQUIRED", "targetStateSha256": value["targetStateSha256"]},
        }
        transport._validate_schema(outcome, Path("ops/deploy/schemas/rollback-workflow-outcome.schema.json"), "REMOTE_RESULT_INVALID")
        path = args.output / "rollback-workflow-outcome.json"
        path.write_bytes(transport.canonical(outcome)); os.chmod(path, 0o600)
        return 0
    finally:
        if ssh_dir.exists():
            transport.ssh_material.cleanup_ssh_configuration(ssh_dir)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        code = getattr(exc, "code", "INTERNAL_ERROR")
        print(f"rollback-transport:{code}", file=sys.stderr)
        raise SystemExit(3)
