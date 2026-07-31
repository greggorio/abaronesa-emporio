"""Strict validation for the terminal deployment workflow artifact."""

from __future__ import annotations

import io
import re
import stat
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .artifacts import DIGEST_RE, extract_zip, validate_json
from .constants import REPOSITORY
from .errors import RuntimeFailure
from .sync import positive_id

ROOT = Path(__file__).resolve().parents[3]
DEPLOYMENT_OUTCOME_SCHEMA = ROOT / "ops/deploy/schemas/deployment-workflow-outcome.schema.json"
OUTCOME_NAME = "deployment-workflow-outcome"
OUTCOME_FILE = "deployment-workflow-outcome.json"
MAX_OUTCOME_ZIP_BYTES = 16 * 1024 * 1024
MAX_OUTCOME_BYTES = 64 * 1024
SHA_RE = re.compile(r"^[0-9a-f]{40}$")


@dataclass(frozen=True, slots=True)
class DeploymentArtifact:
    artifact_id: int
    artifact_digest: str
    size_in_bytes: int


def validate_deployment_artifact(
    value: dict[str, Any], *, run_id: int, head_sha: str
) -> DeploymentArtifact:
    """Validate REST metadata before any artifact-controlled download."""

    code = "DEPLOYMENT_OUTCOME_INVALID"
    artifact_id = positive_id(value.get("id"), code)
    artifact_digest = value.get("digest")
    size = value.get("size_in_bytes")
    workflow_run = value.get("workflow_run")
    if (
        value.get("name") != OUTCOME_NAME
        or value.get("expired") is not False
        or not isinstance(artifact_digest, str)
        or DIGEST_RE.fullmatch(artifact_digest) is None
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
    return DeploymentArtifact(artifact_id, artifact_digest, size)


def validate_deployment_outcome(
    raw_zip: bytes,
    artifact_digest: str,
    *,
    operation_id: str,
    target_release: str,
    run_id: int,
    attempt: int,
    control_sha: str,
) -> dict[str, Any]:
    """Validate the ZIP, canonical JSON, schema, and all trusted bindings."""

    try:
        with zipfile.ZipFile(io.BytesIO(raw_zip)) as archive:
            infos = archive.infolist()
            if len(infos) != 1 or infos[0].filename != OUTCOME_FILE:
                raise RuntimeFailure("DEPLOYMENT_OUTCOME_INVALID")
            mode = infos[0].external_attr >> 16
            if not stat.S_ISREG(mode):
                raise RuntimeFailure("DEPLOYMENT_OUTCOME_INVALID")
    except (zipfile.BadZipFile, EOFError, OSError) as exc:
        raise RuntimeFailure("DEPLOYMENT_OUTCOME_INVALID") from exc
    files = extract_zip(
        raw_zip,
        artifact_digest,
        {OUTCOME_FILE: MAX_OUTCOME_BYTES},
        "DEPLOYMENT_OUTCOME_INVALID",
    )
    outcome = validate_json(
        files[OUTCOME_FILE],
        DEPLOYMENT_OUTCOME_SCHEMA,
        "DEPLOYMENT_OUTCOME_INVALID",
    )
    expected = {
        "operationId": operation_id,
        "targetRelease": target_release,
        "workflowRunId": run_id,
        "workflowRunAttempt": attempt,
        "controlSha": control_sha,
    }
    if any(outcome.get(key) != value for key, value in expected.items()):
        raise RuntimeFailure("DEPLOYMENT_OUTCOME_BINDING_INVALID")
    return outcome


def validate_run_conclusion(conclusion: Any, outcome: dict[str, Any]) -> None:
    """Require the GitHub conclusion to agree with the S21 outcome semantics."""

    clean_success = (
        outcome.get("transportStatus") == "CONFIRMED"
        and outcome.get("deploymentState") == "SUCCEEDED"
        and outcome.get("errorCode") is None
    )
    if (clean_success and conclusion != "success") or (
        not clean_success and conclusion != "failure"
    ):
        raise RuntimeFailure("DEPLOYMENT_OUTCOME_CONCLUSION_INVALID")
