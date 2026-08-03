from __future__ import annotations

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
from emporio_release_control.artifacts import digest
from emporio_release_control.deployer_reconciliation import (
    DeployerReconcileLoop,
    DeployerReconciler,
)
from emporio_release_control.errors import RuntimeFailure
from emporio_release_control.persistence import (
    AuditEvent,
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
        raw: bytes | None = None,
        runs_failure: Exception | None = None,
    ) -> None:
        self.runs = runs or []
        self.artifacts = artifacts or []
        self.raw = raw or outcome_zip()
        self.runs_failure = runs_failure
        self.paths: list[str] = []

    def list_pages(self, path: str, key: str | None) -> list[dict[str, Any]]:
        self.paths.append(path)
        _ = key
        if path.endswith("/artifacts"):
            return self.artifacts
        if self.runs_failure is not None:
            raise self.runs_failure
        return self.runs

    def get_bytes(self, path: str) -> bytes:
        self.paths.append(path)
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
    }


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
