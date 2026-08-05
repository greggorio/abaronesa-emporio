from __future__ import annotations

import io
import stat
import zipfile
from datetime import timedelta
from typing import Any

import pytest
from sqlalchemy import text
from sqlalchemy.orm import Session, sessionmaker
from test_deployer_remote_contract import (
    ATTEMPT,
    OPERATION,
    RELEASE,
    REPOSITORY,
    ROLLBACK_OPERATION,
    ROLLBACK_RELEASE,
    ROLLBACK_RUN_ID,
    RUN_ID,
    SHA,
    artifact,
    outcome,
    outcome_zip,
    rollback_outcome,
    rollback_outcome_zip,
)

from emporio_release_control import deployer_reconciliation as reconciliation_module
from emporio_release_control.artifacts import canonical, digest
from emporio_release_control.deployer_reconciliation import (
    DeployerReconcileLoop,
    DeployerReconciler,
)
from emporio_release_control.deployer_service import DeployerService
from emporio_release_control.errors import RuntimeFailure
from emporio_release_control.persistence import (
    AuditEvent,
    CurrentInstallation,
    DeploymentIdempotencyKey,
    DeploymentOperation,
    SyncState,
    utc_now,
)


class FakeService:
    def __init__(
        self,
        *,
        cleanup_failure: Exception | None = None,
        apply_failure: Exception | None = None,
    ) -> None:
        self.uncertain: list[tuple[str, str, str]] = []
        self.applied: list[tuple[str, dict[str, Any], str, str]] = []
        self.rollback_applied: list[tuple[str, dict[str, Any], str, str]] = []
        self.predeploy_applied: list[tuple[str, dict[str, Any], str]] = []
        self.cleanup_calls = 0
        self.cleanup_failure = cleanup_failure
        self.apply_failure = apply_failure

    def cleanup_expired_idempotency(self) -> int:
        self.cleanup_calls += 1
        if self.cleanup_failure is not None:
            raise self.cleanup_failure
        return 0

    def mark_uncertain(self, operation_id: str, code: str, trace_id: str) -> None:
        self.uncertain.append((operation_id, code, trace_id))

    def apply_outcome(
        self,
        operation_id: str,
        value: dict[str, Any],
        outcome_digest: str,
        trace_id: str,
    ) -> None:
        if self.apply_failure is not None:
            raise self.apply_failure
        self.applied.append((operation_id, value, outcome_digest, trace_id))

    def apply_rollback_outcome(
        self,
        operation_id: str,
        value: dict[str, Any],
        outcome_digest: str,
        trace_id: str,
    ) -> None:
        if self.apply_failure is not None:
            raise self.apply_failure
        self.rollback_applied.append((operation_id, value, outcome_digest, trace_id))

    def apply_predeploy_failure(
        self, operation_id: str, evidence: dict[str, Any], trace_id: str
    ) -> None:
        if self.apply_failure is not None:
            raise self.apply_failure
        self.predeploy_applied.append((operation_id, evidence, trace_id))


class FakeSynchronizer:
    def __init__(self, fail: bool = False) -> None:
        self.calls: list[str] = []
        self.fail = fail

    def sync_releases(self, trace_id: str = "") -> None:
        self.calls.append(trace_id)
        if self.fail:
            raise RuntimeFailure("SYNC_INVALID")


class FakeGitHub:
    def __init__(
        self,
        *,
        runs: list[dict[str, Any]] | None = None,
        artifacts: list[dict[str, Any]] | None = None,
        jobs: list[dict[str, Any]] | None = None,
        raw: bytes | None = None,
        runs_failure: Exception | None = None,
        jobs_failure: Exception | None = None,
    ) -> None:
        self.runs = runs or []
        self.artifacts = artifacts or []
        self.jobs = jobs or []
        self.raw = raw or outcome_zip()
        self.runs_failure = runs_failure
        self.jobs_failure = jobs_failure
        self.paths: list[str] = []

    def list_pages(self, path: str, key: str | None) -> list[dict[str, Any]]:
        self.paths.append(path)
        _ = key
        if path.endswith("/artifacts"):
            return self.artifacts
        if "/jobs?" in path:
            if self.jobs_failure is not None:
                raise self.jobs_failure
            return self.jobs
        if self.runs_failure is not None:
            raise self.runs_failure
        return self.runs

    def get_bytes(self, path: str, limit: int | None = None) -> bytes:
        self.paths.append(path)
        if limit is not None and len(self.raw) > limit:
            raise RuntimeFailure("GITHUB_RESPONSE_INVALID")
        return self.raw


def run(
    *,
    operation_id: str = OPERATION,
    attempt: int = ATTEMPT,
    status: str = "completed",
    conclusion: str | None = "success",
    run_id: int = RUN_ID,
    sha: str = SHA,
) -> dict[str, Any]:
    return {
        "id": run_id,
        "run_attempt": attempt,
        "name": f"deploy-production-{operation_id}",
        "path": ".github/workflows/deploy-production.yml",
        "event": "workflow_dispatch",
        "status": status,
        "conclusion": conclusion,
        "head_branch": "main",
        "head_sha": sha,
        "html_url": f"https://github.com/{REPOSITORY}/actions/runs/{run_id}",
        "display_title": f"deploy-production-{operation_id}",
        "created_at": "2099-01-01T00:00:00Z",
        "repository": {"full_name": REPOSITORY},
        "head_repository": {"full_name": REPOSITORY},
        "actor": {"id": 313092947},
    }


def predeploy_job(
    name: str,
    conclusion: str,
    *,
    operation_id: str = OPERATION,
    run_id: int = RUN_ID,
    attempt: int = ATTEMPT,
    sha: str = SHA,
    status: str = "completed",
    job_id: int | None = None,
) -> dict[str, Any]:
    resolved_id = job_id or {
        "trust": 901,
        "prepare": 902,
        "deploy": 903,
        "outcome": 904,
    }.get(name, 905)
    return {
        "id": resolved_id,
        "run_id": run_id,
        "run_attempt": attempt,
        "workflow_name": f"deploy-production-{operation_id}",
        "head_branch": "main",
        "head_sha": sha,
        "run_url": f"https://api.github.com/repos/{REPOSITORY}/actions/runs/{run_id}",
        "url": f"https://api.github.com/repos/{REPOSITORY}/actions/jobs/{resolved_id}",
        "html_url": (
            f"https://github.com/{REPOSITORY}/actions/runs/{run_id}/job/{resolved_id}"
        ),
        "status": status,
        "conclusion": conclusion,
        "created_at": "2099-01-01T00:00:00Z",
        "started_at": "2099-01-01T00:00:01Z",
        "completed_at": "2099-01-01T00:00:02Z",
        "name": name,
    }


def predeploy_jobs() -> list[dict[str, Any]]:
    return [
        predeploy_job("trust", "success"),
        predeploy_job("prepare", "failure"),
        predeploy_job("deploy", "skipped"),
        predeploy_job("outcome", "failure"),
    ]


def trust_zip(
    *,
    operation_id: str = OPERATION,
    run_id: int = RUN_ID,
    attempt: int = ATTEMPT,
    sha: str = SHA,
    actor_id: int = 313092947,
    target_release: str = RELEASE,
) -> bytes:
    value = {
        "schemaVersion": 1,
        "kind": "deployment-trust",
        "repository": REPOSITORY,
        "operationId": operation_id,
        "targetRelease": target_release,
        "controlSha": sha,
        "workflowRunId": run_id,
        "workflowRunAttempt": attempt,
        "requestedActorId": actor_id,
    }
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        info = zipfile.ZipInfo("deployment-trust.json")
        info.external_attr = (stat.S_IFREG | 0o644) << 16
        archive.writestr(info, canonical(value))
    return stream.getvalue()


def trust_artifact(raw: bytes, **overrides: Any) -> dict[str, Any]:
    artifact_id = overrides.pop("id", 900)
    value: dict[str, Any] = {
        "id": artifact_id,
        "name": "deployment-trust",
        "size_in_bytes": len(raw),
        "url": f"https://api.github.com/repos/{REPOSITORY}/actions/artifacts/{artifact_id}",
        "archive_download_url": (
            f"https://api.github.com/repos/{REPOSITORY}/actions/artifacts/{artifact_id}/zip"
        ),
        "expired": False,
        "digest": digest(raw),
        "created_at": "2099-01-01T00:00:00Z",
        "updated_at": "2099-01-01T00:00:01Z",
        "expires_at": "2099-01-02T00:00:00Z",
        "workflow_run": {
            "id": RUN_ID,
            "repository_id": 100,
            "head_repository_id": 100,
            "head_branch": "main",
            "head_sha": SHA,
        },
    }
    value.update(overrides)
    return value


def rollback_run(
    *,
    operation_id: str = ROLLBACK_OPERATION,
    attempt: int = ATTEMPT,
    status: str = "completed",
    conclusion: str | None = "success",
    run_id: int = ROLLBACK_RUN_ID,
    sha: str = SHA,
) -> dict[str, Any]:
    return {
        "id": run_id,
        "run_attempt": attempt,
        # rollback-production.yml declares no run-name, so `name` stays the workflow name
        "name": "Rollback Production",
        "path": ".github/workflows/rollback-production.yml",
        "event": "workflow_dispatch",
        "status": status,
        "conclusion": conclusion,
        "head_branch": "main",
        "head_sha": sha,
        "html_url": f"https://github.com/{REPOSITORY}/actions/runs/{run_id}",
        "display_title": f"rollback-production-{operation_id}",
        "created_at": "2099-01-01T00:00:00Z",
        "repository": {"full_name": REPOSITORY},
        "head_repository": {"full_name": REPOSITORY},
    }


def seed_operation(
    factory: sessionmaker[Session],
    *,
    operation_id: str = OPERATION,
    age_seconds: int = 0,
    state: str = "QUEUED",
    operation_type: str = "deployment",
    source_release: str | None = None,
    target_release: str = RELEASE,
) -> None:
    timestamp = utc_now() - timedelta(seconds=age_seconds)
    with factory.begin() as session:
        session.add(
            DeploymentOperation(
                operation_id=operation_id,
                operation_type=operation_type,
                mode="deployer",
                state=state,
                actor_sub="actor",
                scopes=[
                    "deployment:rollback"
                    if operation_type == "rollback"
                    else "deployment:execute"
                ],
                target_release=target_release,
                source_release=source_release,
                rollback_reason="operator requested rollback"
                if operation_type == "rollback"
                else None,
                request_json=(
                    {"release": target_release, "reason": "operator requested rollback"}
                    if operation_type == "rollback"
                    else {"release": target_release}
                ),
                request_hash="1" * 64,
                idempotency_hash="2" * 64,
                dispatch_state="SENT",
                active_slot=None if state != "QUEUED" else 1,
                created_at=timestamp,
                updated_at=timestamp,
                finished_at=timestamp if state != "QUEUED" else None,
            )
        )


def seed_historical_uncertain(factory: sessionmaker[Session]) -> None:
    seed_operation(factory)
    now = utc_now()
    with factory.begin() as session:
        operation = session.get(DeploymentOperation, OPERATION)
        assert operation is not None
        operation.workflow_run_id = RUN_ID
        operation.workflow_attempt = ATTEMPT
        operation.workflow_run_url = f"https://github.com/{REPOSITORY}/actions/runs/{RUN_ID}"
        operation.control_sha = SHA
        operation.dispatch_state = "CONFIRMED"
        operation.remote_state = "completed"
        operation.transport_status = "INDETERMINATE"
        operation.error_code = "DEPLOYMENT_OUTCOME_UNAVAILABLE"
        operation.error_message = "Remote deployment result is uncertain"
        operation.journal_json = {}
        operation.evidence_json = {}
        session.add(
            CurrentInstallation(
                singleton_id=1,
                reconciled=False,
                uncertainty_code="DEPLOYMENT_OUTCOME_UNAVAILABLE",
                last_operation_id=OPERATION,
                updated_at=now,
            )
        )
        session.add(
            DeploymentIdempotencyKey(
                mode="deployer",
                route="POST:/api/deployment-control/v1/deployments",
                actor_sub="actor",
                key_hmac="3" * 64,
                request_hash="1" * 64,
                operation_id=OPERATION,
                created_at=now,
                expires_at=now + timedelta(days=365),
            )
        )
        session.add(
            AuditEvent(
                trace_id="historical",
                actor_sub=None,
                action="deployment.uncertain",
                result="INDETERMINATE",
                operation_id=OPERATION,
                metadata_json={"code": "DEPLOYMENT_OUTCOME_UNAVAILABLE"},
            )
        )


def reconciler(
    factory: sessionmaker[Session],
    github: FakeGitHub,
    service: FakeService | None = None,
    timeout: int = 600,
) -> tuple[DeployerReconciler, FakeService]:
    target = service or FakeService()
    return (
        DeployerReconciler(
            factory,
            github,  # type: ignore[arg-type]
            target,  # type: ignore[arg-type]
            FakeSynchronizer(),  # type: ignore[arg-type]
            timeout,
        ),
        target,
    )


def bound(factory: sessionmaker[Session]) -> tuple[int | None, int | None, str | None]:
    with factory() as session:
        operation = session.get(DeploymentOperation, OPERATION)
        assert operation is not None
        return (
            operation.workflow_run_id,
            operation.workflow_attempt,
            operation.control_sha,
        )


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("name", "Other"),
        ("path", ".github/workflows/deploy-production.yml@dev"),
        ("event", "push"),
        ("head_branch", "dev"),
        ("display_title", "wrong"),
        ("head_sha", "bad"),
        ("html_url", "https://github.com/wrong"),
        ("repository", {"full_name": "wrong/repo"}),
        ("head_repository", {"full_name": "wrong/repo"}),
        ("status", "unknown"),
    ],
)
def test_run_identity_is_fully_validated(field: str, value: Any) -> None:
    mutant = run()
    mutant[field] = value
    with pytest.raises(RuntimeFailure, match="WORKFLOW_RUN_INVALID"):
        DeployerReconciler._validate_run(mutant, OPERATION)


def test_discovery_zero_before_timeout_and_after_timeout(
    factory: sessionmaker[Session],
) -> None:
    seed_operation(factory, age_seconds=10)
    target, service = reconciler(factory, FakeGitHub(), timeout=600)
    target._operation(OPERATION)
    assert service.uncertain == []

    with factory.begin() as session:
        operation = session.get(DeploymentOperation, OPERATION)
        assert operation is not None
        operation.created_at = utc_now() - timedelta(seconds=601)
    target._operation(OPERATION)
    assert service.uncertain[-1][1] == "WORKFLOW_DISPATCH_UNCONFIRMED"


def test_discovery_ambiguous_marks_uncertain(factory: sessionmaker[Session]) -> None:
    seed_operation(factory)
    target, service = reconciler(factory, FakeGitHub(runs=[run(), run(run_id=RUN_ID + 1)]))
    target._operation(OPERATION)
    assert service.uncertain[-1][1] == "WORKFLOW_RUN_AMBIGUOUS"
    assert bound(factory) == (None, None, None)


def test_binding_and_same_run_rerun_attempt_are_monotonic(
    factory: sessionmaker[Session],
) -> None:
    seed_operation(factory)
    target, service = reconciler(
        factory,
        FakeGitHub(runs=[run(status="in_progress", conclusion=None)]),
    )
    target._operation(OPERATION)
    assert bound(factory) == (RUN_ID, ATTEMPT, SHA)
    target.github = FakeGitHub(  # type: ignore[assignment]
        runs=[run(attempt=ATTEMPT + 1, status="in_progress", conclusion=None)]
    )
    target._operation(OPERATION)
    assert bound(factory) == (RUN_ID, ATTEMPT + 1, SHA)
    target.github = FakeGitHub(  # type: ignore[assignment]
        runs=[run(attempt=ATTEMPT, status="in_progress", conclusion=None)]
    )
    assert target._operation(OPERATION) is False
    assert service.uncertain[-1][1] == "WORKFLOW_ATTEMPT_REGRESSION"


def test_other_run_or_control_sha_cannot_replace_binding(
    factory: sessionmaker[Session],
) -> None:
    seed_operation(factory)
    target, service = reconciler(
        factory,
        FakeGitHub(runs=[run(status="in_progress", conclusion=None)]),
    )
    target._operation(OPERATION)
    target.github = FakeGitHub(  # type: ignore[assignment]
        runs=[run(run_id=RUN_ID + 1, status="in_progress", conclusion=None)]
    )
    assert target._operation(OPERATION) is False
    assert service.uncertain[-1][1] == "WORKFLOW_RUN_AMBIGUOUS"
    target.github = FakeGitHub(  # type: ignore[assignment]
        runs=[run(sha="3" * 40, status="in_progress", conclusion=None)]
    )
    assert target._operation(OPERATION) is False
    assert service.uncertain[-1][1] == "WORKFLOW_RUN_BINDING_INVALID"


@pytest.mark.parametrize(
    ("value", "conclusion"),
    [
        (outcome(), "success"),
        (outcome(errorCode="REMOTE_CLEANUP_FAILED"), "failure"),
        (
            outcome(
                deploymentState="FAILED",
                databaseRestoreRequired=False,
                errorCode="REMOTE_EXECUTION_FAILED",
            ),
            "failure",
        ),
        (
            outcome(
                deploymentState="ROLLED_BACK",
                databaseRestoreRequired=True,
                errorCode="REMOTE_EXECUTION_FAILED",
            ),
            "failure",
        ),
        (
            outcome(
                transportStatus="INDETERMINATE",
                deploymentState=None,
                databaseRestoreRequired=None,
                errorCode="REMOTE_RESULT_UNAVAILABLE",
            ),
            "failure",
        ),
    ],
)
def test_completed_run_applies_each_valid_outcome(
    factory: sessionmaker[Session], value: dict[str, Any], conclusion: str
) -> None:
    seed_operation(factory)
    raw = outcome_zip(value)
    github = FakeGitHub(
        runs=[run(conclusion=conclusion)],
        artifacts=[artifact(digest=digest(raw), size_in_bytes=len(raw))],
        raw=raw,
    )
    target, service = reconciler(factory, github)
    target._operation(OPERATION)
    assert service.uncertain == []
    assert service.applied == [(OPERATION, value, digest(raw), "deployer-reconcile")]


@pytest.mark.parametrize(
    "github_factory",
    [
        lambda: FakeGitHub(runs=[run()], artifacts=[]),
        lambda: FakeGitHub(
            runs=[run()],
            artifacts=[artifact(), artifact(id=901)],
        ),
        lambda: FakeGitHub(
            runs=[run()],
            artifacts=[artifact(size_in_bytes=17 * 1024 * 1024)],
        ),
        lambda: FakeGitHub(
            runs=[run()],
            artifacts=[
                artifact(
                    digest=digest(outcome_zip()),
                    size_in_bytes=len(outcome_zip()) + 1,
                )
            ],
            raw=outcome_zip(),
        ),
        lambda: FakeGitHub(
            runs=[run()],
            artifacts=[
                artifact(digest=digest(outcome_zip(outcome(operationId="dep_" + "b" * 32))))
            ],
            raw=outcome_zip(outcome(operationId="dep_" + "b" * 32)),
        ),
    ],
)
def test_invalid_or_missing_artifact_never_terminalizes(
    factory: sessionmaker[Session], github_factory: Any
) -> None:
    seed_operation(factory)
    target, service = reconciler(factory, github_factory())
    target._operation(OPERATION)
    assert service.applied == []
    assert service.uncertain


def test_real_predeploy_failure_is_atomic_preserves_history_and_is_idempotent(
    factory: sessionmaker[Session],
) -> None:
    seed_historical_uncertain(factory)
    raw = trust_zip()
    github = FakeGitHub(
        runs=[run(conclusion="failure")],
        jobs=predeploy_jobs(),
        artifacts=[trust_artifact(raw)],
        raw=raw,
    )
    service = DeployerService(
        factory,
        github,  # type: ignore[arg-type]
        b"p" * 32,
        365,
        lambda _release: None,
    )
    target = DeployerReconciler(
        factory,
        github,  # type: ignore[arg-type]
        service,
        FakeSynchronizer(),  # type: ignore[arg-type]
    )

    assert target._operation(OPERATION) is True
    with factory() as session:
        operation = session.get(DeploymentOperation, OPERATION)
        assert operation is not None
        assert (
            operation.state,
            operation.dispatch_state,
            operation.transport_status,
            operation.remote_state,
            operation.database_restore_required,
            operation.error_code,
            operation.active_slot,
        ) == (
            "FAILED",
            "CONFIRMED",
            "CONFIRMED",
            "FAILED",
            False,
            "WORKFLOW_PRE_DEPLOY_FAILED",
            None,
        )
        assert operation.workflow_run_id == RUN_ID
        assert operation.workflow_attempt == ATTEMPT
        assert operation.workflow_run_url == (
            f"https://github.com/{REPOSITORY}/actions/runs/{RUN_ID}"
        )
        assert operation.control_sha == SHA
        assert operation.request_hash == "1" * 64
        assert operation.idempotency_hash == "2" * 64
        assert operation.finished_at is not None
        assert operation.evidence_json["reason"] == "deploy_skipped"
        assert operation.evidence_json["jobs"] == {
            "deploy": "skipped",
            "outcome": "failure",
            "prepare": "failure",
            "trust": "success",
        }
        assert session.get(CurrentInstallation, 1) is None
        assert session.query(DeploymentIdempotencyKey).count() == 1
        audits = session.query(AuditEvent).order_by(AuditEvent.id).all()
        assert [item.action for item in audits] == [
            "deployment.uncertain",
            "deployment.predeploy_failed",
        ]
        assert audits[-1].metadata_json == {
            "code": "WORKFLOW_PRE_DEPLOY_FAILED",
            "reason": "deploy_skipped",
            "runId": RUN_ID,
            "runAttempt": ATTEMPT,
        }

    paths = list(github.paths)
    assert target._operation(OPERATION) is True
    assert github.paths == paths
    with factory() as session:
        assert session.query(AuditEvent).count() == 2


@pytest.mark.parametrize(
    ("deploy_status", "deploy_conclusion"),
    [
        ("completed", "success"),
        ("completed", "failure"),
        ("completed", "cancelled"),
        ("in_progress", None),
    ],
)
def test_deploy_not_proven_skipped_stays_indeterminate(
    factory: sessionmaker[Session],
    deploy_status: str,
    deploy_conclusion: str | None,
) -> None:
    seed_historical_uncertain(factory)
    raw = trust_zip()
    jobs = predeploy_jobs()
    jobs[2] = predeploy_job(
        "deploy",
        str(deploy_conclusion) if deploy_conclusion is not None else "",
        status=deploy_status,
    )
    if deploy_conclusion is None:
        jobs[2]["conclusion"] = None
    github = FakeGitHub(
        runs=[run(conclusion="failure")], jobs=jobs,
        artifacts=[trust_artifact(raw)], raw=raw,
    )
    service = DeployerService(
        factory, github, b"p" * 32, 365, lambda _release: None  # type: ignore[arg-type]
    )
    target = DeployerReconciler(
        factory, github, service, FakeSynchronizer()  # type: ignore[arg-type]
    )
    assert target._operation(OPERATION) is False
    with factory() as session:
        operation = session.get(DeploymentOperation, OPERATION)
        assert operation is not None
        assert operation.state == "QUEUED"
        assert operation.transport_status == "INDETERMINATE"
        assert operation.active_slot == 1
        assert session.get(CurrentInstallation, 1) is not None
        assert not session.query(AuditEvent).filter_by(
            action="deployment.predeploy_failed"
        ).all()


@pytest.mark.parametrize(
    "artifact_mutation",
    ["extra", "expired", "handoff", "result", "outcome", "wrong_attempt"],
)
def test_nonexclusive_or_invalid_trust_artifact_stays_indeterminate(
    factory: sessionmaker[Session], artifact_mutation: str
) -> None:
    seed_historical_uncertain(factory)
    raw = trust_zip(attempt=ATTEMPT + 1) if artifact_mutation == "wrong_attempt" else trust_zip()
    artifacts = [trust_artifact(raw)]
    if artifact_mutation == "expired":
        artifacts[0]["expired"] = True
    elif artifact_mutation in {"handoff", "result", "outcome"}:
        artifacts.append({"name": f"deployment-{artifact_mutation}"})
    elif artifact_mutation == "extra":
        artifacts.append({"name": "unexpected"})
    github = FakeGitHub(
        runs=[run(conclusion="failure")], jobs=predeploy_jobs(), artifacts=artifacts, raw=raw
    )
    service = DeployerService(
        factory, github, b"p" * 32, 365, lambda _release: None  # type: ignore[arg-type]
    )
    target = DeployerReconciler(
        factory, github, service, FakeSynchronizer()  # type: ignore[arg-type]
    )
    assert target._operation(OPERATION) is False
    with factory() as session:
        operation = session.get(DeploymentOperation, OPERATION)
        assert operation is not None and operation.active_slot == 1
        assert operation.transport_status == "INDETERMINATE"


@pytest.mark.parametrize("job_mutation", ["extra", "missing", "duplicate", "wrong_attempt"])
def test_noncanonical_job_inventory_stays_indeterminate(
    factory: sessionmaker[Session], job_mutation: str
) -> None:
    seed_historical_uncertain(factory)
    raw = trust_zip()
    jobs = predeploy_jobs()
    if job_mutation == "extra":
        jobs.append(predeploy_job("unexpected", "success"))
    elif job_mutation == "missing":
        jobs.pop()
    elif job_mutation == "duplicate":
        jobs[-1] = predeploy_job("trust", "success", job_id=999)
    else:
        jobs[0]["run_attempt"] = ATTEMPT + 1
    github = FakeGitHub(
        runs=[run(conclusion="failure")], jobs=jobs,
        artifacts=[trust_artifact(raw)], raw=raw,
    )
    service = DeployerService(
        factory, github, b"p" * 32, 365, lambda _release: None  # type: ignore[arg-type]
    )
    target = DeployerReconciler(
        factory, github, service, FakeSynchronizer()  # type: ignore[arg-type]
    )
    assert target._operation(OPERATION) is False
    with factory() as session:
        operation = session.get(DeploymentOperation, OPERATION)
        assert operation is not None and operation.active_slot == 1
        assert operation.transport_status == "INDETERMINATE"


@pytest.mark.parametrize("current_mutation", ["release", "uncertainty", "last_operation"])
def test_partial_or_divergent_current_blocks_predeploy_terminalization(
    factory: sessionmaker[Session], current_mutation: str
) -> None:
    seed_historical_uncertain(factory)
    with factory.begin() as session:
        current = session.get(CurrentInstallation, 1)
        assert current is not None
        if current_mutation == "release":
            current.release = RELEASE
        elif current_mutation == "uncertainty":
            current.uncertainty_code = "OTHER_UNCERTAINTY"
        else:
            current.last_operation_id = "dep_" + "b" * 32
    raw = trust_zip()
    github = FakeGitHub(
        runs=[run(conclusion="failure")], jobs=predeploy_jobs(),
        artifacts=[trust_artifact(raw)], raw=raw,
    )
    service = DeployerService(
        factory, github, b"p" * 32, 365, lambda _release: None  # type: ignore[arg-type]
    )
    target = DeployerReconciler(
        factory, github, service, FakeSynchronizer()  # type: ignore[arg-type]
    )
    assert target._operation(OPERATION) is False
    with factory() as session:
        operation = session.get(DeploymentOperation, OPERATION)
        assert operation is not None and operation.active_slot == 1
        assert operation.transport_status == "INDETERMINATE"
        assert session.get(CurrentInstallation, 1) is not None


def test_jobs_transport_failure_preserves_uncertain_operation_and_readiness(
    factory: sessionmaker[Session],
) -> None:
    seed_historical_uncertain(factory)
    github = FakeGitHub(
        runs=[run(conclusion="failure")],
        artifacts=[trust_artifact(trust_zip())],
        jobs_failure=RuntimeFailure("GITHUB_TRANSPORT_FAILED"),
    )
    service = DeployerService(
        factory, github, b"p" * 32, 365, lambda _release: None  # type: ignore[arg-type]
    )
    target = DeployerReconciler(
        factory, github, service, FakeSynchronizer()  # type: ignore[arg-type]
    )

    assert target._operation(OPERATION) is False
    with factory() as session:
        operation = session.get(DeploymentOperation, OPERATION)
        current = session.get(CurrentInstallation, 1)
        assert operation is not None and operation.state == "QUEUED"
        assert operation.active_slot == 1
        assert operation.transport_status == "INDETERMINATE"
        assert current is not None and current.reconciled is False
        assert current.uncertainty_code == "GITHUB_TRANSPORT_FAILED"
        assert not session.query(AuditEvent).filter_by(
            action="deployment.predeploy_failed"
        ).all()


def test_terminal_operation_is_not_reprocessed(factory: sessionmaker[Session]) -> None:
    seed_operation(factory, state="SUCCEEDED")
    github = FakeGitHub(runs=[run()])
    target, service = reconciler(factory, github)
    target._operation(OPERATION)
    assert github.paths == []
    assert service.applied == []
    assert service.uncertain == []


def test_cycle_uses_deployer_lock_sync_cleanup_and_green_domain(
    factory: sessionmaker[Session],
) -> None:
    seed_operation(factory)
    service = FakeService()
    sync = FakeSynchronizer()
    target = DeployerReconciler(
        factory,
        FakeGitHub(),  # type: ignore[arg-type]
        service,  # type: ignore[arg-type]
        sync,  # type: ignore[arg-type]
    )
    assert target.cycle() is True
    assert sync.calls == ["deployer-releases"]
    assert service.cleanup_calls == 1
    with factory() as session:
        domain = session.get(SyncState, "deployments")
        assert domain is not None
        assert domain.drift is False
        assert domain.last_success_at is not None


def test_cycle_sync_failure_marks_domain_drift_and_continues_cleanup(
    factory: sessionmaker[Session],
) -> None:
    service = FakeService()
    target = DeployerReconciler(
        factory,
        FakeGitHub(),  # type: ignore[arg-type]
        service,  # type: ignore[arg-type]
        FakeSynchronizer(fail=True),  # type: ignore[arg-type]
    )
    assert target.cycle() is True
    assert service.cleanup_calls == 1
    with factory() as session:
        domain = session.get(SyncState, "deployments")
        assert domain is not None
        assert domain.drift is True
        assert domain.error_code == "RECONCILE_FAILED"


@pytest.mark.parametrize(
    "failure",
    [RuntimeFailure("CAUSAL_FAILURE"), ValueError("must-not-leak")],
)
def test_cycle_audits_each_operation_failure_without_raising(
    factory: sessionmaker[Session], monkeypatch: pytest.MonkeyPatch, failure: Exception
) -> None:
    seed_operation(factory)
    target, _ = reconciler(factory, FakeGitHub())

    def fail(_operation_id: str) -> bool:
        raise failure

    monkeypatch.setattr(target, "_operation", fail)
    assert target.cycle() is True
    with factory() as session:
        audit = session.query(AuditEvent).one()
        assert audit.action == "deployment.reconcile"
        assert audit.metadata_json["code"] in {"CAUSAL_FAILURE", "RECONCILE_FAILED"}
        domain = session.get(SyncState, "deployments")
        assert domain is not None and domain.drift is True


def test_deployer_loop_start_stop_and_exception_isolation() -> None:
    class FakeReconciler:
        def __init__(self) -> None:
            self.calls = 0

        def cycle(self) -> bool:
            self.calls += 1
            return True

    reconciler_fake = FakeReconciler()
    loop = DeployerReconcileLoop(reconciler_fake, 1)  # type: ignore[arg-type]
    loop.start()
    loop.stop()
    assert reconciler_fake.calls >= 1

    class RaisingReconciler:
        def cycle(self) -> bool:
            raise RuntimeFailure("RECONCILE_TEST")

    class OneCycleStop:
        def __init__(self) -> None:
            self.calls = 0

        def wait(self, _interval: int) -> bool:
            self.calls += 1
            return self.calls > 1

    failing = DeployerReconcileLoop(RaisingReconciler(), 1)  # type: ignore[arg-type]
    failing._stop = OneCycleStop()  # type: ignore[assignment]
    failing._run()


def test_binding_missing_operation_is_a_safe_noop(
    factory: sessionmaker[Session],
) -> None:
    target, _ = reconciler(factory, FakeGitHub())
    assert target._bind_run(OPERATION, run()) == (RUN_ID, ATTEMPT, SHA)


def test_cycle_classifies_entire_lineage_and_marks_invalid_run_uncertain(
    factory: sessionmaker[Session],
) -> None:
    seed_operation(factory)
    invalid = run()
    invalid["repository"] = {"full_name": "wrong/repository"}
    service = FakeService()
    target = DeployerReconciler(
        factory,
        FakeGitHub(runs=[invalid, run(status="in_progress", conclusion=None)]),  # type: ignore[arg-type]
        service,  # type: ignore[arg-type]
        FakeSynchronizer(),  # type: ignore[arg-type]
    )
    assert target.cycle() is True
    assert service.uncertain == [(OPERATION, "WORKFLOW_RUN_INVALID", "deployer-reconcile")]
    assert bound(factory) == (None, None, None)
    with factory() as session:
        domain = session.get(SyncState, "deployments")
        assert domain is not None and domain.drift is True


def test_rollback_operation_uses_versioned_workflow_and_applies_outcome(
    factory: sessionmaker[Session],
) -> None:
    seed_operation(
        factory,
        operation_id=ROLLBACK_OPERATION,
        operation_type="rollback",
        source_release="v1.2.4",
        target_release=ROLLBACK_RELEASE,
    )
    raw = rollback_outcome_zip()
    service = FakeService()
    target = DeployerReconciler(
        factory,
        FakeGitHub(
            runs=[rollback_run()],
            artifacts=[
                artifact(
                    name="rollback-workflow-outcome",
                    digest=digest(raw),
                    size_in_bytes=len(raw),
                    workflow_run={"id": ROLLBACK_RUN_ID, "head_sha": SHA},
                )
            ],
            raw=raw,
        ),  # type: ignore[arg-type]
        service,  # type: ignore[arg-type]
        FakeSynchronizer(),  # type: ignore[arg-type]
    )
    assert target._operation(ROLLBACK_OPERATION) is True
    assert service.uncertain == []
    assert service.rollback_applied == [
        (ROLLBACK_OPERATION, rollback_outcome(), digest(raw), "deployer-reconcile")
    ]
    assert target.github.paths[0] == reconciliation_module.ROLLBACK_WORKFLOW_RUNS_PATH
    assert not any("/jobs?" in path for path in target.github.paths)
    assert not any("deployment-trust" in path for path in target.github.paths)


@pytest.mark.parametrize(
    ("binding_mutator", "expected_code"),
    [
        ("run_id", "WORKFLOW_RUN_AMBIGUOUS"),
        ("control_sha", "WORKFLOW_RUN_BINDING_INVALID"),
        ("attempt", "WORKFLOW_ATTEMPT_REGRESSION"),
        ("equal_status", "WORKFLOW_RUN_BINDING_INVALID"),
    ],
)
def test_cycle_marks_every_binding_divergence_uncertain(
    factory: sessionmaker[Session], binding_mutator: str, expected_code: str
) -> None:
    seed_operation(factory)
    with factory.begin() as session:
        operation = session.get(DeploymentOperation, OPERATION)
        assert operation is not None
        operation.workflow_run_id = RUN_ID
        operation.workflow_attempt = ATTEMPT
        operation.workflow_run_url = f"https://github.com/{REPOSITORY}/actions/runs/{RUN_ID}"
        operation.control_sha = SHA
        operation.dispatch_state = "CONFIRMED"
        operation.remote_state = "in_progress"

    incoming = run(status="in_progress", conclusion=None)
    if binding_mutator == "run_id":
        incoming = run(run_id=RUN_ID + 1, status="in_progress", conclusion=None)
    elif binding_mutator == "control_sha":
        incoming = run(sha="3" * 40, status="in_progress", conclusion=None)
    elif binding_mutator == "attempt":
        incoming = run(attempt=ATTEMPT - 1, status="in_progress", conclusion=None)
    elif binding_mutator == "equal_status":
        incoming = run(status="queued", conclusion=None)
    service = FakeService()
    target = DeployerReconciler(
        factory,
        FakeGitHub(runs=[incoming]),  # type: ignore[arg-type]
        service,  # type: ignore[arg-type]
        FakeSynchronizer(),  # type: ignore[arg-type]
    )
    assert target.cycle() is True
    assert service.uncertain[-1][1] == expected_code
    with factory() as session:
        operation = session.get(DeploymentOperation, OPERATION)
        domain = session.get(SyncState, "deployments")
        assert operation is not None and operation.active_slot == 1
        assert domain is not None and domain.drift is True


@pytest.mark.parametrize(
    ("failure", "expected_code"),
    [
        (
            RuntimeFailure("DEPLOYMENT_OUTCOME_RESTORE_CONFLICT"),
            "DEPLOYMENT_OUTCOME_RESTORE_CONFLICT",
        ),
        (RuntimeFailure("RELEASE_NOT_ELIGIBLE"), "RELEASE_NOT_ELIGIBLE"),
        (OSError("must-not-leak"), "RECONCILE_FAILED"),
    ],
)
def test_cycle_marks_apply_outcome_failure_uncertain(
    factory: sessionmaker[Session], failure: Exception, expected_code: str
) -> None:
    seed_operation(factory)
    raw = outcome_zip()
    service = FakeService(apply_failure=failure)
    target = DeployerReconciler(
        factory,
        FakeGitHub(
            runs=[run()],
            artifacts=[artifact(digest=digest(raw), size_in_bytes=len(raw))],
            raw=raw,
        ),  # type: ignore[arg-type]
        service,  # type: ignore[arg-type]
        FakeSynchronizer(),  # type: ignore[arg-type]
    )
    assert target.cycle() is True
    assert service.applied == []
    assert service.uncertain[-1][1] == expected_code
    with factory() as session:
        operation = session.get(DeploymentOperation, OPERATION)
        assert operation is not None
        assert operation.state == "QUEUED"
        assert operation.active_slot == 1


def test_list_runs_failure_marks_domain_only(factory: sessionmaker[Session]) -> None:
    seed_operation(factory)
    service = FakeService()
    target = DeployerReconciler(
        factory,
        FakeGitHub(runs_failure=RuntimeFailure("GITHUB_TRANSPORT_FAILED")),  # type: ignore[arg-type]
        service,  # type: ignore[arg-type]
        FakeSynchronizer(),  # type: ignore[arg-type]
    )
    assert target.cycle() is True
    assert service.uncertain == []
    with factory() as session:
        domain = session.get(SyncState, "deployments")
        audit = session.query(AuditEvent).one()
        assert domain is not None and domain.drift is True
        assert audit.metadata_json == {"code": "GITHUB_TRANSPORT_FAILED"}


def test_cleanup_failure_does_not_skip_query_and_domain_update(
    factory: sessionmaker[Session],
) -> None:
    seed_operation(factory)
    service = FakeService(cleanup_failure=OSError("must-not-leak"))
    github = FakeGitHub()
    target = DeployerReconciler(
        factory,
        github,  # type: ignore[arg-type]
        service,  # type: ignore[arg-type]
        FakeSynchronizer(),  # type: ignore[arg-type]
    )
    assert target.cycle() is True
    assert github.paths == [reconciliation_module.WORKFLOW_RUNS_PATH]
    with factory() as session:
        domain = session.get(SyncState, "deployments")
        assert domain is not None and domain.drift is True


def test_operation_query_failure_still_sets_domain_red(
    factory: sessionmaker[Session], monkeypatch: pytest.MonkeyPatch
) -> None:
    target, _ = reconciler(factory, FakeGitHub())
    original_scalars = Session.scalars

    def fail_query(session: Session, *_args: Any, **_kwargs: Any) -> Any:
        session.execute(text("SELECT 1 / 0"))
        raise AssertionError("unreachable")

    monkeypatch.setattr(Session, "scalars", fail_query)
    assert target.cycle() is True
    with factory() as session:
        domain = session.get(SyncState, "deployments")
        assert domain is not None and domain.drift is True
    monkeypatch.setattr(Session, "scalars", original_scalars)
    assert target.cycle() is True


def test_individual_failure_does_not_skip_remaining_operations(
    factory: sessionmaker[Session], monkeypatch: pytest.MonkeyPatch
) -> None:
    second = "dep_" + "b" * 32
    seed_operation(factory)
    target, _ = reconciler(factory, FakeGitHub())
    observed: list[str] = []

    monkeypatch.setattr(Session, "scalars", lambda *_args, **_kwargs: [OPERATION, second])

    def operation(operation_id: str) -> bool:
        observed.append(operation_id)
        if operation_id == OPERATION:
            raise RuntimeFailure("CAUSAL_FAILURE")
        return True

    monkeypatch.setattr(target, "_operation", operation)
    assert target.cycle() is True
    assert set(observed) == {OPERATION, second}
    with factory() as session:
        audit = session.query(AuditEvent).one()
        domain = session.get(SyncState, "deployments")
        assert audit.operation_id == OPERATION
        assert domain is not None and domain.drift is True


def test_set_domain_failure_and_lock_release_failure_do_not_escape(
    factory: sessionmaker[Session],
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    target, _ = reconciler(factory, FakeGitHub())
    released: list[bool] = []
    logged: list[tuple[str, dict[str, Any]]] = []

    def fail_domain(_green: bool) -> None:
        raise OSError("sensitive-domain-detail")

    def fail_release(_session: Session) -> None:
        released.append(True)
        raise OSError("sensitive-lock-detail")

    monkeypatch.setattr(target, "_set_domain", fail_domain)
    monkeypatch.setattr(reconciliation_module, "release_deployer_advisory_lock", fail_release)
    monkeypatch.setattr(
        reconciliation_module.LOGGER,
        "error",
        lambda event, *, extra: logged.append((event, extra)),
    )
    assert target.cycle() is True
    assert released == [True]
    assert logged == [
        ("deployment_domain_update_failed", {"code": "RECONCILE_FAILED"}),
        ("deployment_lock_release_failed", {"code": "RECONCILE_FAILED"}),
    ]


def test_lock_not_acquired_returns_false_without_domain_change(
    factory: sessionmaker[Session], monkeypatch: pytest.MonkeyPatch
) -> None:
    target, _ = reconciler(factory, FakeGitHub())
    monkeypatch.setattr(reconciliation_module, "try_deployer_advisory_lock", lambda _session: False)
    assert target.cycle() is False
    with factory() as session:
        assert session.get(SyncState, "deployments") is None
