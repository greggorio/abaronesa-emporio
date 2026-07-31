from __future__ import annotations

import io
import json
import zipfile
from collections.abc import Callable
from pathlib import Path
from typing import Any

import pytest

from emporio_release_control.artifacts import canonical, digest
from emporio_release_control.deployment_artifacts import (
    OUTCOME_FILE,
    validate_deployment_artifact,
    validate_deployment_outcome,
    validate_run_conclusion,
)
from emporio_release_control.errors import RuntimeFailure
from emporio_release_control.rollback_artifacts import (
    validate_rollback_artifact,
    validate_rollback_outcome,
    validate_rollback_run_conclusion,
)

ROOT = Path(__file__).resolve().parents[2]
REPOSITORY = "greggorio/abaronesa-emporio"
OPERATION = "dep_" + "a" * 32
RELEASE = "v1.2.3"
RUN_ID = 8123
ATTEMPT = 2
SHA = "1" * 40
ROLLBACK_OPERATION = "rbk_" + "b" * 32
ROLLBACK_RELEASE = "v1.2.3"
ROLLBACK_RUN_ID = 9123


def outcome(**updates: Any) -> dict[str, Any]:
    loaded = json.loads(
        (ROOT / "ops/deploy/examples/deployment-workflow-outcome.example.json").read_text()
    )
    assert isinstance(loaded, dict)
    value: dict[str, Any] = loaded
    value.update(
        {
            "operationId": OPERATION,
            "targetRelease": RELEASE,
            "workflowRunId": RUN_ID,
            "workflowRunAttempt": ATTEMPT,
            "controlSha": SHA,
        }
    )
    value.update(updates)
    return value


def outcome_zip(
    value: dict[str, Any] | None = None,
    *,
    data: bytes | None = None,
    name: str = OUTCOME_FILE,
    extra: bool = False,
    mode: int = 0o100600,
) -> bytes:
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        info = zipfile.ZipInfo(name)
        info.external_attr = mode << 16
        info.compress_type = zipfile.ZIP_DEFLATED
        archive.writestr(info, data if data is not None else canonical(value or outcome()))
        if extra:
            other = zipfile.ZipInfo("extra")
            other.external_attr = 0o100600 << 16
            archive.writestr(other, b"x")
    return output.getvalue()


def rollback_outcome(**updates: Any) -> dict[str, Any]:
    loaded = json.loads(
        (ROOT / "ops/deploy/examples/rollback-workflow-outcome.example.json").read_text()
    )
    assert isinstance(loaded, dict)
    value: dict[str, Any] = loaded
    value.update(
        {
            "operationId": ROLLBACK_OPERATION,
            "targetRelease": ROLLBACK_RELEASE,
            "workflowRunId": ROLLBACK_RUN_ID,
            "workflowRunAttempt": ATTEMPT,
            "controlSha": SHA,
        }
    )
    value.update(updates)
    return value


def rollback_outcome_zip(
    value: dict[str, Any] | None = None,
    *,
    data: bytes | None = None,
    name: str = "rollback-workflow-outcome.json",
) -> bytes:
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        info = zipfile.ZipInfo(name)
        info.external_attr = 0o100600 << 16
        info.compress_type = zipfile.ZIP_DEFLATED
        archive.writestr(info, data if data is not None else canonical(value or rollback_outcome()))
    return output.getvalue()


def artifact(**updates: Any) -> dict[str, Any]:
    value: dict[str, Any] = {
        "id": 900,
        "name": "deployment-workflow-outcome",
        "expired": False,
        "digest": "sha256:" + "2" * 64,
        "size_in_bytes": 4096,
        "url": f"https://api.github.com/repos/{REPOSITORY}/actions/artifacts/900",
        "archive_download_url": (
            f"https://api.github.com/repos/{REPOSITORY}/actions/artifacts/900/zip"
        ),
        "workflow_run": {"id": RUN_ID, "head_sha": SHA},
    }
    value.update(updates)
    return value


def test_deployment_artifact_and_outcome_positive() -> None:
    identity = validate_deployment_artifact(artifact(), run_id=RUN_ID, head_sha=SHA)
    raw = outcome_zip()
    assert identity.artifact_id == 900
    assert identity.size_in_bytes == 4096
    assert (
        validate_deployment_outcome(
            raw,
            digest(raw),
            operation_id=OPERATION,
            target_release=RELEASE,
            run_id=RUN_ID,
            attempt=ATTEMPT,
            control_sha=SHA,
        )
        == outcome()
    )
    validate_run_conclusion("success", outcome())
    validate_run_conclusion(
        "failure",
        outcome(
            deploymentState="FAILED",
            databaseRestoreRequired=False,
            errorCode="REMOTE_EXECUTION_FAILED",
        ),
    )


def test_rollback_artifact_and_outcome_positive() -> None:
    raw = rollback_outcome_zip()
    rollback_identity = validate_rollback_artifact(
        artifact(
            name="rollback-workflow-outcome",
            url=f"https://api.github.com/repos/{REPOSITORY}/actions/artifacts/900",
            archive_download_url=(
                f"https://api.github.com/repos/{REPOSITORY}/actions/artifacts/900/zip"
            ),
            workflow_run={"id": ROLLBACK_RUN_ID, "head_sha": SHA},
        ),
        run_id=ROLLBACK_RUN_ID,
        head_sha=SHA,
    )
    assert rollback_identity.artifact_id == 900
    assert (
        validate_rollback_outcome(
            raw,
            digest(raw),
            operation_id=ROLLBACK_OPERATION,
            target_release=ROLLBACK_RELEASE,
            run_id=ROLLBACK_RUN_ID,
            attempt=ATTEMPT,
            control_sha=SHA,
        )
        == rollback_outcome()
    )
    validate_rollback_run_conclusion("success", rollback_outcome())
    validate_rollback_run_conclusion(
        "failure", rollback_outcome(rollbackState="FAILED", errorCode="REMOTE_EXECUTION_FAILED")
    )


@pytest.mark.parametrize(
    "mutator",
    [
        lambda value: value.__setitem__("name", "other"),
        lambda value: value.__setitem__("expired", True),
        lambda value: value.__setitem__("digest", "sha256:bad"),
        lambda value: value.__setitem__("size_in_bytes", True),
        lambda value: value.__setitem__("size_in_bytes", 16 * 1024 * 1024 + 1),
        lambda value: value.__setitem__("url", "https://api.github.com/wrong"),
        lambda value: value.__setitem__("archive_download_url", "https://x.invalid"),
        lambda value: value.__setitem__("workflow_run", {"id": RUN_ID + 1, "head_sha": SHA}),
        lambda value: value.__setitem__("workflow_run", {"id": RUN_ID, "head_sha": "3" * 40}),
    ],
)
def test_deployment_artifact_rejects_rest_identity_mutants(
    mutator: Callable[[dict[str, Any]], None],
) -> None:
    value = artifact()
    mutator(value)
    with pytest.raises(RuntimeFailure, match="DEPLOYMENT_OUTCOME_INVALID"):
        validate_deployment_artifact(value, run_id=RUN_ID, head_sha=SHA)


@pytest.mark.parametrize(
    ("raw", "code"),
    [
        (outcome_zip(name="../outcome.json"), "DEPLOYMENT_OUTCOME_INVALID"),
        (outcome_zip(extra=True), "DEPLOYMENT_OUTCOME_INVALID"),
        (outcome_zip(mode=0o120777), "DEPLOYMENT_OUTCOME_INVALID"),
        (b"not-a-zip", "DEPLOYMENT_OUTCOME_INVALID"),
        (outcome_zip(data=b"{}\n"), "DEPLOYMENT_OUTCOME_INVALID"),
        (outcome_zip(data=b'{"schemaVersion":1}\n'), "DEPLOYMENT_OUTCOME_INVALID"),
        (outcome_zip(data=canonical({**outcome(), "extra": True})), "DEPLOYMENT_OUTCOME_INVALID"),
    ],
)
def test_deployment_outcome_rejects_zip_canonical_and_schema_mutants(raw: bytes, code: str) -> None:
    with pytest.raises(RuntimeFailure, match=code):
        validate_deployment_outcome(
            raw,
            digest(raw),
            operation_id=OPERATION,
            target_release=RELEASE,
            run_id=RUN_ID,
            attempt=ATTEMPT,
            control_sha=SHA,
        )


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("operationId", "dep_" + "b" * 32),
        ("targetRelease", "v1.2.4"),
        ("workflowRunId", RUN_ID + 1),
        ("workflowRunAttempt", ATTEMPT + 1),
        ("controlSha", "4" * 40),
    ],
)
def test_deployment_outcome_rejects_each_binding(field: str, value: Any) -> None:
    raw = outcome_zip(outcome(**{field: value}))
    with pytest.raises(RuntimeFailure, match="DEPLOYMENT_OUTCOME_BINDING_INVALID"):
        validate_deployment_outcome(
            raw,
            digest(raw),
            operation_id=OPERATION,
            target_release=RELEASE,
            run_id=RUN_ID,
            attempt=ATTEMPT,
            control_sha=SHA,
        )


@pytest.mark.parametrize(
    ("conclusion", "value"),
    [
        ("failure", outcome()),
        ("success", outcome(errorCode="REMOTE_CLEANUP_FAILED")),
        (
            "success",
            outcome(
                deploymentState="ROLLED_BACK",
                databaseRestoreRequired=True,
                errorCode="REMOTE_EXECUTION_FAILED",
            ),
        ),
        (
            "success",
            outcome(
                transportStatus="INDETERMINATE",
                deploymentState=None,
                databaseRestoreRequired=None,
                errorCode="REMOTE_RESULT_UNAVAILABLE",
            ),
        ),
    ],
)
def test_run_conclusion_must_match_outcome(conclusion: str, value: dict[str, Any]) -> None:
    with pytest.raises(RuntimeFailure, match="DEPLOYMENT_OUTCOME_CONCLUSION_INVALID"):
        validate_run_conclusion(conclusion, value)
