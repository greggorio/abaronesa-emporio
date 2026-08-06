from __future__ import annotations

import re
from datetime import UTC, datetime, timedelta
from pathlib import Path
from typing import get_args

import pytest
from alembic import command
from alembic.config import Config
from pydantic import ValidationError
from sqlalchemy import Engine, inspect, select, text
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, sessionmaker

from emporio_release_control.deployer_schemas import (
    ComponentPlanItem,
    DeployerCapabilitiesResponse,
    DeployerProblemCode,
    DeploymentPlan,
    DeploymentRequest,
    RollbackRequest,
)
from emporio_release_control.persistence import (
    CurrentInstallation,
    DeploymentIdempotencyKey,
    DeploymentOperation,
    RollbackBackup,
    migration_is_current,
    release_advisory_lock,
    release_deployer_advisory_lock,
    try_advisory_lock,
    try_deployer_advisory_lock,
)

ROOT = Path(__file__).resolve().parents[1]
NOW = datetime(2026, 7, 31, tzinfo=UTC)
WORKFLOW_BINDING = {
    "workflow_run_id": 101,
    "workflow_attempt": 2,
    "workflow_run_url": (
        "https://github.com/greggorio/abaronesa-emporio/actions/runs/101"
    ),
    "control_sha": "c" * 40,
}


def operation(operation_id: object = "dep_" + "1" * 32, **changes: object) -> DeploymentOperation:
    values: dict[str, object] = {
        "operation_id": operation_id,
        "operation_type": "deployment",
        "mode": "deployer",
        "state": "QUEUED",
        "actor_sub": "operator",
        "scopes": ["deployment:execute"],
        "target_release": "v1.0.0",
        "source_release": None,
        "rollback_reason": None,
        "request_json": {"release": "v1.0.0"},
        "request_hash": "a" * 64,
        "idempotency_hash": "b" * 64,
        "dispatch_state": "NOT_SENT",
        "created_at": NOW,
        "updated_at": NOW,
        "active_slot": 1,
        "version": 1,
    }
    values.update(changes)
    return DeploymentOperation(**values)


def test_migration_head_contains_exact_deployer_tables_and_preserves_publisher(
    engine: Engine,
) -> None:
    assert migration_is_current(engine)
    tables = set(inspect(engine).get_table_names())
    assert {
        "rc_deployment_operation",
        "rc_deployment_idempotency_key",
        "rc_current_installation",
        "rc_rollback_backup",
    } <= tables
    assert {
        "rc_publication_operation",
        "rc_idempotency_key",
        "rc_candidate_snapshot",
        "rc_release_snapshot",
        "rc_audit_event",
        "rc_sync_state",
    } <= tables
    operation_columns = inspect(engine).get_columns("rc_deployment_operation")
    assert {column["name"] for column in operation_columns} == {
        "operation_id", "operation_type", "mode", "state", "actor_sub", "scopes",
        "target_release", "source_release", "rollback_reason", "request_json",
        "request_hash", "idempotency_hash", "workflow_run_id", "workflow_attempt",
        "workflow_run_url", "control_sha", "dispatch_state", "remote_state",
        "transport_status", "database_restore_required", "source_state_sha256", "backup_id",
        "journal_json", "evidence_json", "outcome_sha256", "error_code",
        "error_message", "created_at", "updated_at", "finished_at", "active_slot", "version",
    }
    with engine.connect() as connection:
        assert connection.scalar(text("SELECT version_num FROM alembic_version")) == (
            "0004_candidate_commit_subject"
        )


def test_migration_downgrade_removes_only_s26_and_reupgrade_is_clean(engine: Engine) -> None:
    config = Config(str(ROOT / "alembic.ini"))
    config.set_main_option("script_location", str(ROOT / "migrations"))
    try:
        command.downgrade(config, "0001_publisher_runtime")
        tables = set(inspect(engine).get_table_names())
        assert not {
            "rc_deployment_operation",
            "rc_deployment_idempotency_key",
            "rc_current_installation",
        } & tables
        assert "rc_publication_operation" in tables
    finally:
        command.upgrade(config, "head")
    assert migration_is_current(engine)


def test_deployment_operation_and_idempotency_are_persisted_without_raw_key(
    factory: sessionmaker[Session],
) -> None:
    with factory.begin() as session:
        session.add(operation())
        session.flush()
        session.add(
            DeploymentIdempotencyKey(
                mode="deployer",
                route="POST:/api/deployment-control/v1/deployments",
                actor_sub="operator",
                key_hmac="c" * 64,
                request_hash="a" * 64,
                operation_id="dep_" + "1" * 32,
                created_at=NOW,
                expires_at=NOW + timedelta(days=365),
            )
        )
    with factory() as session:
        stored = session.get(DeploymentOperation, "dep_" + "1" * 32)
        key = session.scalar(select(DeploymentIdempotencyKey))
        assert stored is not None and stored.active_slot == 1
        assert key is not None and key.key_hmac == "c" * 64
        assert "raw-key" not in repr(key.__dict__)


def test_rollback_operation_backup_and_evidence_are_persisted_without_raw_key(
    factory: sessionmaker[Session],
) -> None:
    state_sha = "sha256:" + "a" * 64
    backup_id = "backup-v1.1.0-20260731"
    with factory.begin() as session:
        session.add(
            operation(
                operation_id="rbk_" + "1" * 32,
                operation_type="rollback",
                state="QUEUED",
                scopes=["deployment:rollback"],
                source_release="v1.1.0",
                target_release="v1.0.0",
                rollback_reason="operator requested rollback",
                request_json={"release": "v1.0.0", "reason": "operator requested rollback"},
                source_state_sha256=state_sha,
                backup_id=backup_id,
                database_restore_required=True,
                journal_json={"schemaVersion": 1, "events": [{"state": "QUEUED"}]},
                evidence_json={"backupId": backup_id},
            )
        )
        session.add(
            RollbackBackup(
                backup_id=backup_id,
                source_release="v1.1.0",
                source_state_sha256=state_sha,
                databases=["erp", "website"],
                artifact_sha256="sha256:" + "b" * 64,
                created_at=NOW,
                expires_at=NOW + timedelta(days=365),
                verified=True,
                evidence_json={"backupId": backup_id},
            )
        )
    with factory() as session:
        stored = session.get(DeploymentOperation, "rbk_" + "1" * 32)
        backup = session.get(RollbackBackup, backup_id)
        assert stored is not None and stored.operation_type == "rollback"
        assert stored.source_state_sha256 == state_sha
        assert stored.backup_id == backup_id
        assert stored.journal_json["schemaVersion"] == 1
        assert backup is not None and backup.databases == ["erp", "website"]
        assert "raw-key" not in repr(stored.__dict__)


@pytest.mark.parametrize(
    "changes",
    [
        {"operation_id": "dep_BAD", "operation_type": "deployment"},
        {"operation_id": "rbk_" + "1" * 32, "operation_type": "deployment"},
        {"mode": "publisher"},
        {"target_release": "v01.0.0"},
        {"request_hash": "not-a-hash"},
        {"state": "PULLING"},
        {"state": "QUEUED", "active_slot": None},
        {"workflow_run_id": 10, "workflow_attempt": None},
    ],
)
def test_operation_constraints_fail_closed(
    factory: sessionmaker[Session], changes: dict[str, object]
) -> None:
    with pytest.raises(IntegrityError):
        with factory.begin() as session:
            session.add(operation(**changes))


def test_unique_active_slot_is_independent_from_publisher(
    factory: sessionmaker[Session],
) -> None:
    with factory.begin() as session:
        session.add(operation())
    with pytest.raises(IntegrityError):
        with factory.begin() as session:
            session.add(operation("dep_" + "2" * 32))


def test_confirmed_dispatch_with_integral_workflow_binding_passes(
    factory: sessionmaker[Session],
) -> None:
    with factory.begin() as session:
        session.add(operation(dispatch_state="CONFIRMED", **WORKFLOW_BINDING))
    with factory() as session:
        stored = session.get(DeploymentOperation, "dep_" + "1" * 32)
        assert stored is not None
        assert stored.dispatch_state == "CONFIRMED"
        assert stored.workflow_run_id == 101


def test_confirmed_dispatch_without_workflow_binding_fails(
    factory: sessionmaker[Session],
) -> None:
    with pytest.raises(IntegrityError):
        with factory.begin() as session:
            session.add(operation(dispatch_state="CONFIRMED"))


def test_integral_workflow_binding_with_sent_dispatch_fails(
    factory: sessionmaker[Session],
) -> None:
    with pytest.raises(IntegrityError):
        with factory.begin() as session:
            session.add(operation(dispatch_state="SENT", **WORKFLOW_BINDING))


@pytest.mark.parametrize("dispatch_state", ["NOT_SENT", "SENT", "UNCERTAIN", "CONFIRMED"])
def test_partial_workflow_binding_fails_in_every_dispatch_state(
    factory: sessionmaker[Session], dispatch_state: str
) -> None:
    with pytest.raises(IntegrityError):
        with factory.begin() as session:
            session.add(
                operation(
                    dispatch_state=dispatch_state,
                    workflow_run_id=101,
                    workflow_attempt=2,
                    workflow_run_url=None,
                    control_sha="c" * 40,
                )
            )


def test_idempotency_scope_unique_and_foreign_key_restrict(
    factory: sessionmaker[Session],
) -> None:
    with factory.begin() as session:
        session.add(operation())
        session.flush()
        session.add(
            DeploymentIdempotencyKey(
                mode="deployer",
                route="route",
                actor_sub="actor",
                key_hmac="c" * 64,
                request_hash="a" * 64,
                operation_id="dep_" + "1" * 32,
                created_at=NOW,
                expires_at=NOW + timedelta(days=1),
            )
        )
    with pytest.raises(IntegrityError):
        with factory.begin() as session:
            session.add(
                DeploymentIdempotencyKey(
                    mode="deployer",
                    route="route",
                    actor_sub="actor",
                    key_hmac="c" * 64,
                    request_hash="a" * 64,
                    operation_id="dep_" + "1" * 32,
                    created_at=NOW,
                    expires_at=NOW + timedelta(days=1),
                )
            )
    with pytest.raises(IntegrityError):
        with factory.begin() as session:
            session.delete(session.get(DeploymentOperation, "dep_" + "1" * 32))


def test_current_installation_singleton_and_reconciled_constraints(
    factory: sessionmaker[Session],
) -> None:
    with pytest.raises(IntegrityError):
        with factory.begin() as session:
            session.add(CurrentInstallation(singleton_id=2, reconciled=False, updated_at=NOW))
    with pytest.raises(IntegrityError):
        with factory.begin() as session:
            session.add(
                CurrentInstallation(
                    singleton_id=1,
                    release="v1.0.0",
                    reconciled=True,
                    updated_at=NOW,
                )
            )
    with factory.begin() as session:
        session.add(
            CurrentInstallation(
                singleton_id=1,
                release="v1.0.0",
                source_commit="d" * 40,
                previous_release=None,
                installed_at=NOW,
                reconciled=True,
                uncertainty_code=None,
                last_operation_id="dep_" + "1" * 32,
                updated_at=NOW,
            )
        )
    with factory() as session:
        current = session.get(CurrentInstallation, 1)
        assert current is not None and current.reconciled


def test_deployer_and_publisher_advisory_locks_are_distinct(
    factory: sessionmaker[Session],
) -> None:
    with factory() as publisher, factory() as deployer, factory() as observer:
        assert try_advisory_lock(publisher)
        assert try_deployer_advisory_lock(deployer)
        assert not try_advisory_lock(observer)
        assert not try_deployer_advisory_lock(observer)
        release_advisory_lock(publisher)
        release_deployer_advisory_lock(deployer)


def test_deployer_public_models_are_strict_and_canonical() -> None:
    assert DeploymentRequest(release="v1.2.3").canonical_dict() == {"release": "v1.2.3"}
    with pytest.raises(ValidationError):
        DeploymentRequest.model_validate({"release": "v01.2.3"})
    with pytest.raises(ValidationError):
        RollbackRequest.model_validate({"release": "v1.2.3", "reason": "short"})
    with pytest.raises(ValidationError):
        DeployerCapabilitiesResponse.model_validate(
            {
                "apiVersion": "v1",
                "capabilities": ["deployment:read", "deployment:rollback"],
                "mode": "deployer",
            }
        )
    assert DeployerCapabilitiesResponse(
        apiVersion="v1",
        capabilities=("deployment:read", "deployment:execute", "deployment:rollback"),
        mode="deployer",
    ).capabilities == (
        "deployment:read",
        "deployment:execute",
        "deployment:rollback",
    )


def test_deployer_problem_code_literal_equals_openapi_enum_exactly() -> None:
    openapi = (
        ROOT.parent
        / "docs/infrastructure/deployment/release-control/api/deployer.openapi.yml"
    ).read_text(encoding="utf-8")
    match = re.search(r"enum: \[([^\]]+)\]", openapi[openapi.index("ProblemDetails:") :])
    assert match is not None
    openapi_codes = tuple(item.strip() for item in match.group(1).split(","))
    assert get_args(DeployerProblemCode) == openapi_codes
    assert "VERSION_RESERVATION_CONFLICT" not in openapi_codes


def test_deployment_plan_requires_six_components_in_order_and_backup_parity() -> None:
    components = [
        ComponentPlanItem(
            component=name,
            action="UPDATE",
            current_digest=None,
            target_digest="sha256:" + "a" * 64,
        )
        for name in (
            "backend",
            "website_back",
            "frontend",
            "website_front",
            "whatsapp_service",
            "gateway",
        )
    ]
    plan = DeploymentPlan(
        source_release=None,
        target_release="v1.0.0",
        components=components,
        migration_required=True,
        backup_required=True,
    )
    assert len(plan.components) == 6
    with pytest.raises(ValidationError):
        DeploymentPlan(
            source_release=None,
            target_release="v1.0.0",
            components=list(reversed(components)),
            migration_required=True,
            backup_required=True,
        )
    with pytest.raises(ValidationError):
        DeploymentPlan(
            source_release=None,
            target_release="v1.0.0",
            components=components,
            migration_required=True,
            backup_required=False,
        )
