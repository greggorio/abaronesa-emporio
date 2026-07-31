"""Validation for the versioned commercial rollback workflow outcome."""

from __future__ import annotations

import io
import stat
import zipfile
from pathlib import Path
from typing import Any

from .artifacts import DIGEST_RE, extract_zip, validate_json
from .constants import REPOSITORY, ROLLBACK_STATES
from .deployment_artifacts import DeploymentArtifact
from .errors import RuntimeFailure
from .sync import positive_id

ROOT = Path(__file__).resolve().parents[3]
ROLLBACK_OUTCOME_SCHEMA = ROOT / "ops/deploy/schemas/rollback-workflow-outcome.schema.json"
OUTCOME_NAME = "rollback-workflow-outcome"
OUTCOME_FILE = "rollback-workflow-outcome.json"
MAX_OUTCOME_ZIP_BYTES = 16 * 1024 * 1024
MAX_OUTCOME_BYTES = 64 * 1024


def validate_rollback_artifact(
    value: dict[str, Any], *, run_id: int, head_sha: str
) -> DeploymentArtifact:
    code = "ROLLBACK_OUTCOME_INVALID"
    artifact_id = positive_id(value.get("id"), code)
    digest = value.get("digest")
    size = value.get("size_in_bytes")
    workflow_run = value.get("workflow_run")
    if (
        value.get("name") != OUTCOME_NAME
        or value.get("expired") is not False
        or not isinstance(digest, str)
        or DIGEST_RE.fullmatch(digest) is None
        or isinstance(size, bool)
        or not isinstance(size, int)
        or size < 1
        or size > MAX_OUTCOME_ZIP_BYTES
        or value.get("url")
        != f"https://api.github.com/repos/{REPOSITORY}/actions/artifacts/{artifact_id}"
        or value.get("archive_download_url")
        != f"https://api.github.com/repos/{REPOSITORY}/actions/artifacts/{artifact_id}/zip"
        or not isinstance(workflow_run, dict)
        or workflow_run.get("id") != run_id
        or workflow_run.get("head_sha") != head_sha
    ):
        raise RuntimeFailure(code)
    return DeploymentArtifact(artifact_id, digest, size)


def validate_rollback_outcome(
    raw_zip: bytes,
    artifact_digest: str,
    *,
    operation_id: str,
    target_release: str,
    run_id: int,
    attempt: int,
    control_sha: str,
) -> dict[str, Any]:
    code = "ROLLBACK_OUTCOME_INVALID"
    try:
        with zipfile.ZipFile(io.BytesIO(raw_zip)) as archive:
            infos = archive.infolist()
            if len(infos) != 1 or infos[0].filename != OUTCOME_FILE:
                raise RuntimeFailure(code)
            if not stat.S_ISREG(infos[0].external_attr >> 16):
                raise RuntimeFailure(code)
    except (zipfile.BadZipFile, EOFError, OSError) as exc:
        raise RuntimeFailure(code) from exc
    outcome = validate_json(
        extract_zip(
            raw_zip, artifact_digest, {OUTCOME_FILE: MAX_OUTCOME_BYTES}, code
        )[OUTCOME_FILE],
        ROLLBACK_OUTCOME_SCHEMA,
        code,
    )
    expected = {
        "operationId": operation_id,
        "targetRelease": target_release,
        "workflowRunId": run_id,
        "workflowRunAttempt": attempt,
        "controlSha": control_sha,
    }
    if any(outcome.get(key) != value for key, value in expected.items()):
        raise RuntimeFailure("ROLLBACK_OUTCOME_BINDING_INVALID")
    if outcome.get("rollbackState") not in ROLLBACK_STATES:
        raise RuntimeFailure(code)
    return outcome


def validate_rollback_run_conclusion(conclusion: Any, outcome: dict[str, Any]) -> None:
    success = (
        outcome.get("transportStatus") == "CONFIRMED"
        and outcome.get("rollbackState") == "SUCCEEDED"
        and outcome.get("errorCode") is None
    )
    if (success and conclusion != "success") or (not success and conclusion != "failure"):
        raise RuntimeFailure("ROLLBACK_OUTCOME_CONCLUSION_INVALID")
