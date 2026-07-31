from __future__ import annotations

from datetime import UTC, datetime, timedelta
from typing import Any, cast

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import select
from sqlalchemy.orm import Session, sessionmaker

from emporio_release_control.config import Settings
from emporio_release_control.deployer_api import create_deployer_app
from emporio_release_control.deployer_service import DeployerService, _migrations_are_prefix
from emporio_release_control.errors import (
    PreDispatchFailure,
    RemoteHttpFailure,
    RemoteTransportFailure,
)
from emporio_release_control.github import GitHubClient
from emporio_release_control.persistence import (
    AuditEvent,
    CurrentInstallation,
    DeploymentOperation,
    ReleaseSnapshot,
    RollbackBackup,
    SyncState,
)
from emporio_release_control.security import Principal

COMPONENTS = (
    "backend",
    "website_back",
    "frontend",
    "website_front",
    "whatsapp_service",
    "gateway",
)
NOW = datetime(2026, 7, 31, tzinfo=UTC)


class FakeVerifier:
    def verify(self, token: str) -> Principal:
        if token == "invalid":  # noqa: S105 - deliberately invalid fake bearer
            from emporio_release_control.errors import RuntimeFailure

            raise RuntimeFailure("UNAUTHORIZED", 401, "Unauthorized")
        return Principal("operator", frozenset(token.split(",")))


class FakeGitHub:
    def __init__(self) -> None:
        self.dispatches: list[tuple[str, str]] = []
        self.rollback_dispatches: list[tuple[str, str]] = []
        self.failure: Exception | None = None

    def dispatch_deployment(self, operation_id: str, release: str) -> None:
        self.dispatches.append((operation_id, release))
        if self.failure is not None:
            raise self.failure

    def dispatch_rollback(self, operation_id: str, release: str) -> None:
        self.rollback_dispatches.append((operation_id, release))
        if self.failure is not None:
            raise self.failure


def manifest(
    release: str,
    previous: str | None,
    *,
    digest_offset: int = 0,
    migrations: tuple[str, ...] = ("1",),
) -> dict[str, Any]:
    return {
        "release": release,
        "previousRelease": previous,
        "kind": "global-release",
        "deployable": True,
        "immutable": True,
        "sourceCommit": release[-1] * 40,
        "components": [
            {"id": name, "digest": f"sha256:{index + digest_offset:064x}"}
            for index, name in enumerate(COMPONENTS, 1)
        ],
        "databases": [
            {
                "id": name,
                "migrations": [
                    {"version": version, "path": f"{name}/V{version}.sql"}
                    for version in migrations
                ],
            }
            for name in ("erp", "website")
        ],
    }


def add_release(
    factory: sessionmaker[Session],
    release: str,
    previous: str | None,
    **options: Any,
) -> ReleaseSnapshot:
    value = manifest(release, previous, **options)
    row = ReleaseSnapshot(
        release=release,
        source_commit=value["sourceCommit"],
        state="PUBLISHED",
        published_at=NOW,
        candidate_id=f"candidate-{release}",
        manifest=value,
        synchronized_at=NOW,
    )
    with factory.begin() as session:
        session.add(row)
        session.merge(
            SyncState(
                domain="releases", last_success_at=NOW, drift=False, updated_at=NOW
            )
        )
        session.merge(
            SyncState(
                domain="deployments", last_success_at=NOW, drift=False, updated_at=NOW
            )
        )
    return row


@pytest.fixture()
def deployer(
    factory: sessionmaker[Session], settings: Settings
) -> tuple[TestClient, DeployerService, FakeGitHub, sessionmaker[Session]]:
    github = FakeGitHub()
    service = DeployerService(
        factory,
        cast(GitHubClient, github),
        b"p" * 32,
        365,
        revalidate_release=lambda _release: None,
    )
    app = create_deployer_app(
        settings.model_copy(update={"mode": "deployer"}),
        service,
        cast(Any, FakeVerifier()),
    )
    return TestClient(app, raise_server_exceptions=False), service, github, factory


def headers(scope: str = "deployment:read") -> dict[str, str]:
    return {"Authorization": f"Bearer {scope}"}


def test_exact_deployer_routes_and_capabilities(deployer: tuple[Any, ...]) -> None:
    client = deployer[0]
    paths = {route.path for route in client.app.routes}
    assert paths == {
        "/health/live",
        "/health/ready",
        "/api/release-control/v1/capabilities",
        "/api/deployment-control/v1/current",
        "/api/deployment-control/v1/releases",
        "/api/deployment-control/v1/releases/{release_id}/plan",
        "/api/deployment-control/v1/deployments",
        "/api/deployment-control/v1/deployments/{deployment_id}",
        "/api/deployment-control/v1/rollbacks",
        "/api/deployment-control/v1/rollbacks/{operation_id}",
    }
    response = client.get(
        "/api/release-control/v1/capabilities", headers=headers()
    )
    assert response.json() == {
        "apiVersion": "v1",
        "capabilities": [
            "deployment:read",
            "deployment:execute",
            "deployment:rollback",
        ],
        "mode": "deployer",
    }


@pytest.mark.parametrize(
    ("method", "path", "scope"),
    [
        ("get", "/api/deployment-control/v1/current", "deployment:execute"),
        ("get", "/api/deployment-control/v1/releases", "deployment:rollback"),
        ("post", "/api/deployment-control/v1/deployments", "deployment:read"),
        ("post", "/api/deployment-control/v1/rollbacks", "deployment:execute"),
        (
            "get",
            "/api/deployment-control/v1/rollbacks/rbk_11111111111111111111111111111111",
            "deployment:execute",
        ),
    ],
)
def test_routes_require_exact_scope(
    deployer: tuple[Any, ...], method: str, path: str, scope: str
) -> None:
    response = deployer[0].request(
        method.upper(),
        path,
        headers={**headers(scope), "Idempotency-Key": "valid-key-123456"},
        json={"release": "v1.0.0", "reason": "a sufficiently long reason"},
    )
    assert response.status_code == 403
    assert response.json()["code"] == "FORBIDDEN"


def test_health_is_public_and_readiness_fails_without_sync(
    deployer: tuple[Any, ...]
) -> None:
    client, _, _, factory = deployer
    assert client.get("/health/live").json() == {"status": "ok"}
    with factory.begin() as session:
        session.query(SyncState).delete()
    assert client.get("/health/ready").status_code == 503


def test_current_absent_reconciled_and_uncertain(deployer: tuple[Any, ...]) -> None:
    client, _, _, factory = deployer
    path = "/api/deployment-control/v1/current"
    assert client.get(path, headers=headers()).status_code == 404
    snapshot = add_release(factory, "v1.0.0", None)
    with factory.begin() as session:
        session.add(
            CurrentInstallation(
                singleton_id=1,
                release="v1.0.0",
                source_commit=snapshot.source_commit,
                installed_at=NOW,
                reconciled=True,
                last_operation_id="dep_" + "1" * 32,
                updated_at=NOW,
            )
        )
    response = client.get(path, headers=headers())
    assert response.status_code == 200
    assert set(response.json()) == {"release", "sourceCommit", "installedAt", "reconciled"}
    with factory.begin() as session:
        current = session.get(CurrentInstallation, 1)
        assert current is not None
        current.reconciled = False
    response = client.get(path, headers=headers())
    assert response.status_code == 409
    assert response.json()["code"] == "CURRENT_INSTALLATION_UNRECONCILED"


@pytest.mark.parametrize("inconsistency", ["explicit", "missing_snapshot", "commit"])
def test_inconsistent_current_is_fail_closed_without_mutating_evidence(
    deployer: tuple[Any, ...], inconsistency: str
) -> None:
    client, service, github, factory = deployer
    installed = add_release(factory, "v1.0.0", None)
    add_release(factory, "v1.1.0", "v1.0.0", migrations=("1", "2"))
    release = "v9.9.9" if inconsistency == "missing_snapshot" else installed.release
    source_commit = (
        "f" * 40 if inconsistency == "commit" else installed.source_commit
    )
    with factory.begin() as session:
        session.add(
            CurrentInstallation(
                singleton_id=1,
                release=release,
                source_commit=source_commit,
                installed_at=NOW,
                reconciled=inconsistency != "explicit",
                last_operation_id="dep_" + "1" * 32,
                updated_at=NOW,
            )
        )

    current = client.get("/api/deployment-control/v1/current", headers=headers())
    assert current.status_code == 409
    assert current.json()["code"] == "CURRENT_INSTALLATION_UNRECONCILED"
    releases = client.get("/api/deployment-control/v1/releases", headers=headers())
    assert releases.status_code == 200
    assert releases.json()["items"]
    assert all(item["eligible"] is False for item in releases.json()["items"])
    plan = client.get(
        "/api/deployment-control/v1/releases/v1.1.0/plan", headers=headers()
    )
    assert plan.status_code == 409
    deployment = client.post(
        "/api/deployment-control/v1/deployments",
        headers={
            **headers("deployment:execute"),
            "Idempotency-Key": f"inconsistent-{inconsistency}-key",
        },
        json={"release": "v1.1.0"},
    )
    assert deployment.status_code == 409
    assert github.dispatches == []
    assert service.ready(True) is False
    with factory() as session:
        preserved = session.get(CurrentInstallation, 1)
        assert preserved is not None
        assert preserved.release == release
        assert preserved.source_commit == source_commit
        assert preserved.reconciled is (inconsistency != "explicit")


def test_release_listing_eligibility_cursor_and_plan(deployer: tuple[Any, ...]) -> None:
    client, _, _, factory = deployer
    add_release(factory, "v1.0.0", None)
    add_release(factory, "v1.1.0", "v1.0.0", digest_offset=10, migrations=("1", "2"))
    response = client.get(
        "/api/deployment-control/v1/releases?limit=1", headers=headers()
    )
    assert response.status_code == 200
    assert response.json()["items"][0]["release"] == "v1.1.0"
    assert response.json()["items"][0]["eligible"] is False
    cursor = response.json()["nextCursor"]
    second = client.get(
        f"/api/deployment-control/v1/releases?limit=1&cursor={cursor}",
        headers=headers(),
    )
    assert second.json()["items"][0]["eligible"] is True
    plan = client.get(
        "/api/deployment-control/v1/releases/v1.0.0/plan", headers=headers()
    )
    assert plan.status_code == 200
    assert [item["action"] for item in plan.json()["components"]] == ["UPDATE"] * 6
    assert plan.json()["migrationRequired"] is True
    assert plan.json()["backupRequired"] is True


@pytest.mark.parametrize(
    ("content_type", "body", "key", "status"),
    [
        ("text/plain", b'{}', "valid-key-123456", 422),
        ("application/json", b'{}', "valid-key-123456", 422),
        ("application/json", b'{', "valid-key-123456", 422),
        ("application/json", b'{"release":"v1.0.0"}', "short", 400),
    ],
)
def test_deployment_request_validation(
    deployer: tuple[Any, ...],
    content_type: str,
    body: bytes,
    key: str,
    status: int,
) -> None:
    response = deployer[0].post(
        "/api/deployment-control/v1/deployments",
        headers={
            **headers("deployment:execute"),
            "Idempotency-Key": key,
            "Content-Type": content_type,
        },
        content=body,
    )
    assert response.status_code == status


@pytest.mark.parametrize(
    ("body", "key", "status"),
    [
        ({"release": "v1.0.0"}, "deployer-rollback-12345678-1234-4234-8234-123456789abc", 422),
        (
            {
                "release": "v1.0.0",
                "reason": "operator requested rollback",
                "extra": "rejected",
            },
            "deployer-rollback-12345678-1234-4234-8234-123456789abc",
            422,
        ),
        ({"release": "v1.0.0", "reason": "operator requested rollback"}, "short-key", 400),
    ],
)
def test_rollback_request_is_closed_and_key_is_uuid_v4(
    deployer: tuple[Any, ...], body: dict[str, Any], key: str, status: int
) -> None:
    response = deployer[0].post(
        "/api/deployment-control/v1/rollbacks",
        headers={**headers("deployment:rollback"), "Idempotency-Key": key},
        json=body,
    )
    assert response.status_code == status


def test_deploy_replay_conflict_and_dispatch(deployer: tuple[Any, ...]) -> None:
    client, _, github, factory = deployer
    add_release(factory, "v1.0.0", None)
    request_headers = {
        **headers("deployment:execute"),
        "Idempotency-Key": "deploy-key-123456",
    }
    first = client.post(
        "/api/deployment-control/v1/deployments",
        headers=request_headers,
        json={"release": "v1.0.0"},
    )
    assert first.status_code == 202
    assert first.headers["Idempotency-Replayed"] == "false"
    assert first.json()["state"] == "QUEUED"
    assert len(github.dispatches) == 1
    replay = client.post(
        "/api/deployment-control/v1/deployments",
        headers=request_headers,
        json={"release": "v1.0.0"},
    )
    assert replay.headers["Idempotency-Replayed"] == "true"
    assert replay.json()["operationId"] == first.json()["operationId"]
    add_release(factory, "v1.1.0", "v1.0.0")
    conflict = client.post(
        "/api/deployment-control/v1/deployments",
        headers=request_headers,
        json={"release": "v1.1.0"},
    )
    assert conflict.status_code == 409
    assert conflict.json()["code"] == "IDEMPOTENCY_CONFLICT"


@pytest.mark.parametrize(
    ("failure", "state", "dispatch"),
    [
        (PreDispatchFailure(), "FAILED", "NOT_SENT"),
        (RemoteHttpFailure(422), "FAILED", "NOT_SENT"),
        (RemoteHttpFailure(500), "QUEUED", "UNCERTAIN"),
        (RemoteTransportFailure(True), "QUEUED", "UNCERTAIN"),
        (RemoteTransportFailure(False), "FAILED", "NOT_SENT"),
    ],
)
def test_dispatch_failure_classification(
    deployer: tuple[Any, ...], failure: Exception, state: str, dispatch: str
) -> None:
    client, _, github, factory = deployer
    add_release(factory, "v1.0.0", None)
    github.failure = failure
    response = client.post(
        "/api/deployment-control/v1/deployments",
        headers={
            **headers("deployment:execute"),
            "Idempotency-Key": "dispatch-key-1234",
        },
        json={"release": "v1.0.0"},
    )
    assert response.status_code == 202
    with factory() as session:
        operation = session.scalar(select(DeploymentOperation))
        assert operation is not None
        assert (operation.state, operation.dispatch_state) == (state, dispatch)


def test_active_operation_conflict_exposes_only_id(deployer: tuple[Any, ...]) -> None:
    client, _, _, factory = deployer
    add_release(factory, "v1.0.0", None)
    with factory.begin() as session:
        session.add(
            DeploymentOperation(
                operation_id="dep_" + "f" * 32,
                operation_type="deployment",
                mode="deployer",
                state="QUEUED",
                actor_sub="other",
                scopes=["deployment:execute"],
                target_release="v1.0.0",
                request_json={"release": "v1.0.0"},
                request_hash="a" * 64,
                idempotency_hash="b" * 64,
                dispatch_state="SENT",
                active_slot=1,
                created_at=NOW,
                updated_at=NOW,
            )
        )
    response = client.post(
        "/api/deployment-control/v1/deployments",
        headers={
            **headers("deployment:execute"),
            "Idempotency-Key": "another-key-12345",
        },
        json={"release": "v1.0.0"},
    )
    assert response.status_code == 409
    assert response.json()["activeOperationId"] == "dep_" + "f" * 32
    assert set(response.json()) == {
        "type",
        "title",
        "status",
        "code",
        "traceId",
        "activeOperationId",
    }


def test_rollback_rejects_without_reconciled_current(deployer: tuple[Any, ...]) -> None:
    client, _, github, factory = deployer
    response = client.post(
        "/api/deployment-control/v1/rollbacks",
        headers={
            **headers("deployment:rollback"),
            "Idempotency-Key": "deployer-rollback-12345678-1234-4234-8234-123456789abc",
        },
        json={"release": "v1.0.0", "reason": "operator requested rollback"},
    )
    assert response.status_code == 409
    assert response.json()["code"] == "RELEASE_NOT_ELIGIBLE"
    with factory() as session:
        assert session.scalar(select(DeploymentOperation)) is None
        assert session.scalar(select(AuditEvent)) is None
    assert github.rollback_dispatches == []


def test_rollback_persists_dispatches_replays_and_supports_get(
    deployer: tuple[Any, ...],
) -> None:
    client, _, github, factory = deployer
    current_snapshot = add_release(factory, "v1.1.0", "v1.0.0", migrations=("1", "2"))
    add_release(factory, "v1.0.0", None, migrations=("1",))
    state_sha = "sha256:" + "a" * 64
    with factory.begin() as session:
        session.add(
            CurrentInstallation(
                singleton_id=1,
                release=current_snapshot.release,
                source_commit=current_snapshot.source_commit,
                state_sha256=state_sha,
                installed_at=NOW,
                reconciled=True,
                last_operation_id="dep_" + "1" * 32,
                updated_at=NOW,
            )
        )
        session.add(
            RollbackBackup(
                backup_id="backup-v1.1.0-20260731",
                source_release="v1.1.0",
                source_state_sha256=state_sha,
                databases=["erp", "website"],
                artifact_sha256="sha256:" + "b" * 64,
                created_at=NOW,
                expires_at=NOW + timedelta(days=365),
                verified=True,
                evidence_json={
                    "backupId": "backup-v1.1.0-20260731",
                    "sourceRelease": "v1.1.0",
                    "sourceStateSha256": state_sha,
                    "artifactSha256": "sha256:" + "b" * 64,
                    "databases": ["erp", "website"],
                },
            )
        )
    request_headers = {
        **headers("deployment:rollback"),
        "Idempotency-Key": "deployer-rollback-12345678-1234-4234-8234-123456789abc",
    }
    body = {"release": "v1.0.0", "reason": "operator requested rollback"}
    first = client.post("/api/deployment-control/v1/rollbacks", headers=request_headers, json=body)
    assert first.status_code == 202
    assert first.headers["Idempotency-Replayed"] == "false"
    assert first.json()["operationType"] == "rollback"
    assert first.json()["sourceRelease"] == "v1.1.0"
    assert first.json()["targetRelease"] == "v1.0.0"
    assert first.json()["databaseRestoreRequired"] is True
    assert len(github.rollback_dispatches) == 1
    with factory() as session:
        stored = session.get(DeploymentOperation, first.json()["operationId"])
        assert stored is not None
        assert stored.operation_type == "rollback"
        assert stored.active_slot == 1
        assert stored.backup_id == "backup-v1.1.0-20260731"
        assert stored.journal_json["events"][0]["state"] == "QUEUED"
    replay = client.post("/api/deployment-control/v1/rollbacks", headers=request_headers, json=body)
    assert replay.status_code == 202
    assert replay.headers["Idempotency-Replayed"] == "true"
    assert replay.json()["operationId"] == first.json()["operationId"]
    fetched = client.get(
        f"/api/deployment-control/v1/rollbacks/{first.json()['operationId']}",
        headers=headers(),
    )
    assert fetched.status_code == 200
    assert fetched.json()["operationId"] == first.json()["operationId"]
    conflict = client.post(
        "/api/deployment-control/v1/rollbacks",
        headers=request_headers,
        json={"release": "v1.0.0", "reason": "a different operator requested rollback"},
    )
    assert conflict.status_code == 409
    assert conflict.json()["code"] == "IDEMPOTENCY_CONFLICT"


@pytest.mark.parametrize(
    ("current", "target", "expected"),
    [
        ({}, {}, False),
        ({"databases": []}, {"databases": [{"id": "erp"}]}, False),
        (
            {"databases": [{"id": "erp", "migrations": "bad"}]},
            {"databases": [{"id": "erp", "migrations": []}]},
            False,
        ),
        (
            {"databases": [{"id": "erp", "migrations": [{"version": "2"}]}]},
            {"databases": [{"id": "erp", "migrations": [{"version": "1"}]}]},
            False,
        ),
        (
            {"databases": [{"id": "erp", "migrations": [{"version": "1"}]}]},
            {
                "databases": [
                    {
                        "id": "erp",
                        "migrations": [{"version": "1"}, {"version": "2"}],
                    }
                ]
            },
            True,
        ),
    ],
)
def test_migration_prefix_is_fail_closed(
    current: dict[str, Any], target: dict[str, Any], expected: bool
) -> None:
    assert _migrations_are_prefix(current, target) is expected


def create_operation(
    factory: sessionmaker[Session],
    *,
    operation_id: str = "dep_" + "d" * 32,
    operation_type: str = "deployment",
    source_release: str | None = None,
    target_release: str = "v1.0.0",
    bound: bool = False,
) -> DeploymentOperation:
    operation = DeploymentOperation(
        operation_id=operation_id,
        operation_type=operation_type,
        mode="deployer",
        state="QUEUED",
        actor_sub="operator",
        scopes=[
            "deployment:rollback" if operation_type == "rollback" else "deployment:execute"
        ],
        source_release=source_release,
        target_release=target_release,
        request_json={"release": target_release},
        rollback_reason="operator requested rollback" if operation_type == "rollback" else None,
        request_hash="a" * 64,
        idempotency_hash="b" * 64,
        workflow_run_id=100 if bound else None,
        workflow_attempt=1 if bound else None,
        workflow_run_url=(
            "https://github.com/greggorio/abaronesa-emporio/actions/runs/100"
            if bound
            else None
        ),
        control_sha="c" * 40 if bound else None,
        dispatch_state="CONFIRMED" if bound else "SENT",
        active_slot=1,
        created_at=NOW,
        updated_at=NOW,
    )
    with factory.begin() as session:
        session.add(operation)
    return operation


def outcome(
    state: str | None,
    *,
    transport: str = "CONFIRMED",
    restore: bool | None = False,
    code: str | None = None,
) -> dict[str, Any]:
    return {
        "transportStatus": transport,
        "deploymentState": state,
        "databaseRestoreRequired": restore,
        "errorCode": code,
    }


def test_uncertainty_preserves_known_installation_and_active_operation(
    deployer: tuple[Any, ...]
) -> None:
    _, service, _, factory = deployer
    create_operation(factory)
    service.mark_uncertain("dep_" + "d" * 32, "WORKFLOW_RUN_AMBIGUOUS", "trace")
    with factory() as session:
        operation = session.get(DeploymentOperation, "dep_" + "d" * 32)
        current = session.get(CurrentInstallation, 1)
        assert operation is not None and operation.state == "QUEUED"
        assert operation.active_slot == 1
        assert operation.dispatch_state == "UNCERTAIN"
        assert current is not None and current.reconciled is False
        assert current.uncertainty_code == "WORKFLOW_RUN_AMBIGUOUS"
    service.mark_uncertain("dep_" + "0" * 32, "IGNORED", "trace")


def test_rollback_uncertain_is_terminal_and_blocks_reconciliation(
    deployer: tuple[Any, ...],
) -> None:
    _, service, _, factory = deployer
    operation_id = "rbk_" + "c" * 32
    create_operation(
        factory,
        operation_id=operation_id,
        operation_type="rollback",
        source_release="v1.1.0",
        target_release="v1.0.0",
    )
    service.mark_uncertain(operation_id, "WORKFLOW_RESULT_UNKNOWN", "trace")
    with factory() as session:
        operation = session.get(DeploymentOperation, operation_id)
        current = session.get(CurrentInstallation, 1)
        assert operation is not None and operation.state == "UNCERTAIN"
        assert operation.active_slot is None
        assert current is not None and current.reconciled is False
        assert current.uncertainty_code == "WORKFLOW_RESULT_UNKNOWN"


def test_rollback_state_machine_restore_recovery_and_terminal_replay(
    deployer: tuple[Any, ...],
) -> None:
    _, service, _, factory = deployer
    add_release(factory, "v1.0.0", None)
    operation_id = "rbk_" + "e" * 32
    with factory.begin() as session:
        session.add(
            DeploymentOperation(
                operation_id=operation_id,
                operation_type="rollback",
                mode="deployer",
                state="QUEUED",
                actor_sub="operator",
                scopes=["deployment:rollback"],
                source_release="v1.1.0",
                target_release="v1.0.0",
                rollback_reason="operator requested rollback",
                request_json={"release": "v1.0.0", "reason": "operator requested rollback"},
                request_hash="a" * 64,
                idempotency_hash="b" * 64,
                dispatch_state="CONFIRMED",
                database_restore_required=True,
                journal_json={"schemaVersion": 1, "operationType": "rollback", "events": []},
                evidence_json={},
                active_slot=1,
                created_at=NOW,
                updated_at=NOW,
            )
        )

    def rollback_outcome_value(
        state: str, *, restore: bool = True, evidence: dict[str, Any] | None = None
    ) -> dict[str, Any]:
        return {
            "rollbackState": state,
            "transportStatus": "CONFIRMED",
            "databaseRestoreRequired": restore,
            "errorCode": None,
            "evidence": evidence or {},
        }

    for index, state in enumerate(("PRECHECKING", "RESTORING", "SWITCHING", "VERIFYING"), 1):
        service.apply_rollback_outcome(
            operation_id,
            rollback_outcome_value(state),
            "sha256:" + f"{index:064x}",
            "trace",
        )
    final = rollback_outcome_value(
        "SUCCEEDED",
        evidence={
            "databaseRestore": "RESTORED",
            "targetStateSha256": "sha256:" + "c" * 64,
        },
    )
    final_digest = "sha256:" + "f" * 64
    service.apply_rollback_outcome(operation_id, final, final_digest, "trace")
    service.apply_rollback_outcome(operation_id, final, final_digest, "trace-replay")
    with factory() as session:
        operation = session.get(DeploymentOperation, operation_id)
        current = session.get(CurrentInstallation, 1)
        assert operation is not None and operation.state == "SUCCEEDED"
        assert operation.active_slot is None
        assert [event["state"] for event in operation.journal_json["events"]] == [
            "PRECHECKING",
            "RESTORING",
            "SWITCHING",
            "VERIFYING",
            "SUCCEEDED",
        ]
        assert current is not None and current.release == "v1.0.0" and current.reconciled


def test_uncertain_outcome_does_not_regress_confirmed_dispatch(
    deployer: tuple[Any, ...]
) -> None:
    _, service, _, factory = deployer
    create_operation(factory, bound=True)
    with factory.begin() as session:
        operation = session.get(DeploymentOperation, "dep_" + "d" * 32)
        assert operation is not None
        operation.workflow_run_id = 100
        operation.workflow_attempt = 1
        operation.workflow_run_url = "https://github.com/greggorio/abaronesa-emporio/actions/runs/100"
        operation.control_sha = "c" * 40
        operation.dispatch_state = "CONFIRMED"
    service.apply_outcome(
        "dep_" + "d" * 32,
        outcome(None, transport="INDETERMINATE", restore=None, code="REMOTE_RESULT_UNKNOWN"),
        "sha256:" + "1" * 64,
        "trace",
    )
    with factory() as session:
        operation = session.get(DeploymentOperation, "dep_" + "d" * 32)
        assert operation is not None
        assert operation.state == "QUEUED"
        assert operation.dispatch_state == "CONFIRMED"
        assert operation.transport_status == "INDETERMINATE"


def test_success_outcome_updates_current_and_replay_is_noop(
    deployer: tuple[Any, ...]
) -> None:
    _, service, _, factory = deployer
    add_release(factory, "v1.0.0", None)
    create_operation(factory, bound=True)
    value = outcome("SUCCEEDED")
    digest = "sha256:" + "2" * 64
    service.apply_outcome("dep_" + "d" * 32, value, digest, "trace")
    service.apply_outcome("dep_" + "d" * 32, value, digest, "trace-replay")
    with factory() as session:
        operation = session.get(DeploymentOperation, "dep_" + "d" * 32)
        current = session.get(CurrentInstallation, 1)
        assert operation is not None and operation.state == "SUCCEEDED"
        assert operation.active_slot is None
        assert current is not None and current.release == "v1.0.0"
        assert current.reconciled is True and current.previous_release is None
        audits = list(session.scalars(select(AuditEvent)))
        assert len(audits) == 1
    with pytest.raises(Exception, match="STATE_TRANSITION_INVALID"):
        service.apply_outcome(
            "dep_" + "d" * 32,
            outcome("FAILED", code="FAILED"),
            "sha256:" + "3" * 64,
            "trace",
        )


@pytest.mark.parametrize(("state", "identifier"), [("FAILED", "f"), ("ROLLED_BACK", "e")])
def test_confirmed_non_success_preserves_clean_current(
    deployer: tuple[Any, ...], state: str, identifier: str
) -> None:
    _, service, _, factory = deployer
    add_release(factory, "v1.0.0", None)
    with factory.begin() as session:
        session.add(
            CurrentInstallation(
                singleton_id=1,
                release="v1.0.0",
                source_commit="0" * 40,
                installed_at=NOW,
                reconciled=True,
                last_operation_id="dep_" + "0" * 32,
                updated_at=NOW,
            )
        )
    operation_id = "dep_" + identifier * 32
    create_operation(
        factory,
        operation_id=operation_id,
        source_release="v1.0.0",
        target_release="v1.1.0",
        bound=True,
    )
    service.apply_outcome(
        operation_id,
        outcome(state, code="DEPLOYMENT_FAILED"),
        "sha256:" + "4" * 64,
        "trace",
    )
    with factory() as session:
        current = session.get(CurrentInstallation, 1)
        operation = session.get(DeploymentOperation, operation_id)
        assert current is not None and current.release == "v1.0.0" and current.reconciled
        assert operation is not None and operation.state == state


def test_restore_required_marks_current_unreconciled(deployer: tuple[Any, ...]) -> None:
    _, service, _, factory = deployer
    create_operation(factory, bound=True)
    service.apply_outcome(
        "dep_" + "d" * 32,
        outcome("FAILED", restore=True, code="DATABASE_RESTORE_REQUIRED"),
        "sha256:" + "5" * 64,
        "trace",
    )
    with factory() as session:
        current = session.get(CurrentInstallation, 1)
        assert current is not None and current.reconciled is False


def test_confirmed_success_requiring_restore_is_rejected_before_any_write(
    deployer: tuple[Any, ...]
) -> None:
    _, service, _, factory = deployer
    add_release(factory, "v1.0.0", None)
    create_operation(factory, bound=True)
    with pytest.raises(Exception, match="DEPLOYMENT_OUTCOME_RESTORE_CONFLICT"):
        service.apply_outcome(
            "dep_" + "d" * 32,
            outcome("SUCCEEDED", restore=True),
            "sha256:" + "9" * 64,
            "trace",
        )
    with factory() as session:
        operation = session.get(DeploymentOperation, "dep_" + "d" * 32)
        assert operation is not None
        assert operation.state == "QUEUED"
        assert operation.active_slot == 1
        assert operation.outcome_sha256 is None
        assert operation.transport_status is None
        assert session.get(CurrentInstallation, 1) is None


def test_integrity_race_reports_real_active_operation(deployer: tuple[Any, ...]) -> None:
    _, service, _, factory = deployer
    active_id = "dep_" + "8" * 32
    create_operation(factory, operation_id=active_id)
    with pytest.raises(Exception) as captured:
        service._resolve_integrity_race("operator", "a" * 64, "b" * 64)
    assert getattr(captured.value, "code", None) == "PRODUCTION_OPERATION_ACTIVE"
    assert getattr(captured.value, "active_operation_id", None) == active_id


def test_unrelated_integrity_failure_is_sanitized_internal_error(
    deployer: tuple[Any, ...]
) -> None:
    _, service, _, _ = deployer
    with pytest.raises(Exception) as captured:
        service._resolve_integrity_race("operator", "a" * 64, "b" * 64)
    assert getattr(captured.value, "code", None) == "INTERNAL_ERROR"
    assert getattr(captured.value, "status", None) == 500
    assert "sql" not in str(captured.value).lower()


def test_operation_lookup_cleanup_and_plan_after_installation(
    deployer: tuple[Any, ...]
) -> None:
    client, service, _, factory = deployer
    first = add_release(factory, "v1.0.0", None)
    add_release(factory, "v1.1.0", "v1.0.0", digest_offset=3, migrations=("1", "2"))
    with factory.begin() as session:
        session.add(
            CurrentInstallation(
                singleton_id=1,
                release="v1.0.0",
                source_commit=first.source_commit,
                installed_at=NOW,
                reconciled=True,
                last_operation_id="dep_" + "0" * 32,
                updated_at=NOW,
            )
        )
    plan = service.get_plan("v1.1.0")
    assert plan.source_release == "v1.0.0"
    assert plan.migration_required is True
    assert any(item.action == "UPDATE" for item in plan.components)
    assert client.get(
        "/api/deployment-control/v1/deployments/dep_" + "0" * 32,
        headers=headers(),
    ).status_code == 404
    with pytest.raises(Exception, match="BAD_REQUEST"):
        service.list_releases(10, service.cursor.encode({"bad": "v1.0.0"}))
    with pytest.raises(Exception, match="BAD_REQUEST"):
        service.list_releases(10, service.cursor.encode({"release": "v9.9.9"}))
