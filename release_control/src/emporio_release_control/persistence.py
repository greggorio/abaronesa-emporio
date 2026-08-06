"""PostgreSQL models and transaction primitives."""

from __future__ import annotations

from collections.abc import Iterator
from datetime import UTC, datetime
from typing import Any

from sqlalchemy import (
    BigInteger,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    String,
    UniqueConstraint,
    create_engine,
    text,
)
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.engine import Engine
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column, sessionmaker

from .constants import DEPLOYER_ADVISORY_LOCK_ID, PUBLISHER_ADVISORY_LOCK_ID


def utc_now() -> datetime:
    return datetime.now(UTC)


class Base(DeclarativeBase):
    pass


class PublicationOperation(Base):
    __tablename__ = "rc_publication_operation"

    operation_id: Mapped[str] = mapped_column(String(36), primary_key=True)
    operation_type: Mapped[str] = mapped_column(String(20), default="PUBLICATION")
    mode: Mapped[str] = mapped_column(String(20), default="publisher")
    state: Mapped[str] = mapped_column(String(20), index=True)
    actor_sub: Mapped[str] = mapped_column(String(255))
    scopes: Mapped[list[str]] = mapped_column(JSONB)
    candidate_id: Mapped[str] = mapped_column(String(128), index=True)
    request_json: Mapped[dict[str, Any]] = mapped_column(JSONB)
    request_hash: Mapped[str] = mapped_column(String(64))
    idempotency_hash: Mapped[str] = mapped_column(String(64))
    target_release: Mapped[str | None] = mapped_column(String(64), nullable=True)
    source_commit: Mapped[str | None] = mapped_column(String(40), nullable=True)
    workflow_run_id: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
    workflow_attempt: Mapped[int | None] = mapped_column(Integer, nullable=True)
    workflow_run_url: Mapped[str | None] = mapped_column(String(512), nullable=True)
    remote_state: Mapped[str | None] = mapped_column(String(30), nullable=True)
    dispatch_state: Mapped[str] = mapped_column(String(20), default="NOT_SENT")
    error_code: Mapped[str | None] = mapped_column(String(100), nullable=True)
    error_message: Mapped[str | None] = mapped_column(String(300), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=utc_now, onupdate=utc_now
    )
    finished_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    active_slot: Mapped[int | None] = mapped_column(Integer, nullable=True)
    version: Mapped[int] = mapped_column(Integer, default=1, nullable=False)

    __mapper_args__ = {"version_id_col": version}
    __table_args__ = (
        Index(
            "uq_rc_publication_active_slot",
            "active_slot",
            unique=True,
            postgresql_where=text("active_slot = 1"),
        ),
    )


class IdempotencyKey(Base):
    __tablename__ = "rc_idempotency_key"
    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    mode: Mapped[str] = mapped_column(String(20))
    route: Mapped[str] = mapped_column(String(160))
    actor_sub: Mapped[str] = mapped_column(String(255))
    key_hmac: Mapped[str] = mapped_column(String(64))
    request_hash: Mapped[str] = mapped_column(String(64))
    operation_id: Mapped[str] = mapped_column(
        ForeignKey("rc_publication_operation.operation_id", ondelete="RESTRICT")
    )
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    __table_args__ = (
        UniqueConstraint("mode", "route", "actor_sub", "key_hmac", name="uq_rc_idempotency_scope"),
    )


class DeploymentOperation(Base):
    __tablename__ = "rc_deployment_operation"

    operation_id: Mapped[str] = mapped_column(String(36), primary_key=True)
    operation_type: Mapped[str] = mapped_column(String(20), default="deployment")
    mode: Mapped[str] = mapped_column(String(20), default="deployer")
    state: Mapped[str] = mapped_column(String(20), index=True)
    actor_sub: Mapped[str] = mapped_column(String(255))
    scopes: Mapped[list[str]] = mapped_column(JSONB)
    target_release: Mapped[str] = mapped_column(String(64), index=True)
    source_release: Mapped[str | None] = mapped_column(String(64), nullable=True)
    rollback_reason: Mapped[str | None] = mapped_column(String(1000), nullable=True)
    request_json: Mapped[dict[str, Any]] = mapped_column(JSONB)
    request_hash: Mapped[str] = mapped_column(String(64))
    idempotency_hash: Mapped[str] = mapped_column(String(64))
    workflow_run_id: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
    workflow_attempt: Mapped[int | None] = mapped_column(Integer, nullable=True)
    workflow_run_url: Mapped[str | None] = mapped_column(String(512), nullable=True)
    control_sha: Mapped[str | None] = mapped_column(String(40), nullable=True)
    dispatch_state: Mapped[str] = mapped_column(String(20), default="NOT_SENT")
    remote_state: Mapped[str | None] = mapped_column(String(30), nullable=True)
    transport_status: Mapped[str | None] = mapped_column(String(20), nullable=True)
    database_restore_required: Mapped[bool | None] = mapped_column(nullable=True)
    source_state_sha256: Mapped[str | None] = mapped_column(String(71), nullable=True)
    backup_id: Mapped[str | None] = mapped_column(String(128), nullable=True)
    journal_json: Mapped[dict[str, Any]] = mapped_column(JSONB, default=dict)
    evidence_json: Mapped[dict[str, Any]] = mapped_column(JSONB, default=dict)
    outcome_sha256: Mapped[str | None] = mapped_column(String(71), nullable=True)
    error_code: Mapped[str | None] = mapped_column(String(100), nullable=True)
    error_message: Mapped[str | None] = mapped_column(String(300), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=utc_now, onupdate=utc_now
    )
    finished_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    active_slot: Mapped[int | None] = mapped_column(Integer, nullable=True)
    version: Mapped[int] = mapped_column(Integer, default=1, nullable=False)

    __mapper_args__ = {"version_id_col": version}
    __table_args__ = (
        Index(
            "uq_rc_deployment_active_slot",
            "active_slot",
            unique=True,
            postgresql_where=text("active_slot = 1"),
        ),
    )


class DeploymentIdempotencyKey(Base):
    __tablename__ = "rc_deployment_idempotency_key"
    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    mode: Mapped[str] = mapped_column(String(20), default="deployer")
    route: Mapped[str] = mapped_column(String(160))
    actor_sub: Mapped[str] = mapped_column(String(255))
    key_hmac: Mapped[str] = mapped_column(String(64))
    request_hash: Mapped[str] = mapped_column(String(64))
    operation_id: Mapped[str] = mapped_column(
        ForeignKey("rc_deployment_operation.operation_id", ondelete="RESTRICT")
    )
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    __table_args__ = (
        UniqueConstraint(
            "mode",
            "route",
            "actor_sub",
            "key_hmac",
            name="uq_rc_deployment_idempotency_scope",
        ),
    )


class CurrentInstallation(Base):
    __tablename__ = "rc_current_installation"

    singleton_id: Mapped[int] = mapped_column(Integer, primary_key=True, default=1)
    release: Mapped[str | None] = mapped_column(String(64), nullable=True)
    source_commit: Mapped[str | None] = mapped_column(String(40), nullable=True)
    state_sha256: Mapped[str | None] = mapped_column(String(71), nullable=True)
    previous_release: Mapped[str | None] = mapped_column(String(64), nullable=True)
    installed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    reconciled: Mapped[bool] = mapped_column(default=False)
    uncertainty_code: Mapped[str | None] = mapped_column(String(100), nullable=True)
    last_operation_id: Mapped[str | None] = mapped_column(String(36), nullable=True)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=utc_now, onupdate=utc_now
    )
    version: Mapped[int] = mapped_column(Integer, default=1, nullable=False)

    __mapper_args__ = {"version_id_col": version}


class RollbackBackup(Base):
    __tablename__ = "rc_rollback_backup"

    backup_id: Mapped[str] = mapped_column(String(128), primary_key=True)
    source_release: Mapped[str] = mapped_column(String(64), index=True)
    source_state_sha256: Mapped[str] = mapped_column(String(71))
    databases: Mapped[list[str]] = mapped_column(JSONB)
    artifact_sha256: Mapped[str] = mapped_column(String(71))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)
    verified: Mapped[bool] = mapped_column(default=False)
    evidence_json: Mapped[dict[str, Any]] = mapped_column(JSONB, default=dict)


class CandidateSnapshot(Base):
    __tablename__ = "rc_candidate_snapshot"
    candidate_id: Mapped[str] = mapped_column(String(128), primary_key=True)
    source_commit: Mapped[str] = mapped_column(String(40))
    eligibility: Mapped[str] = mapped_column(String(20), index=True)
    ci_status: Mapped[str] = mapped_column(String(20))
    manifest_status: Mapped[str] = mapped_column(String(20))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)
    # Assunto do commit que originou o candidato. Sem ele a unica coisa que a
    # interface consegue oferecer para escolher e um hash, o que torna a lista
    # ilegivel assim que passa de meia duzia de itens. Anulavel porque a evidencia
    # e complementar: um candidato sem assunto continua publicavel.
    commit_subject: Mapped[str | None] = mapped_column(String(200), nullable=True)
    manifest: Mapped[dict[str, Any]] = mapped_column(JSONB)
    artifact_id: Mapped[int] = mapped_column(BigInteger)
    artifact_digest: Mapped[str] = mapped_column(String(71))
    synchronized_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class ReleaseSnapshot(Base):
    __tablename__ = "rc_release_snapshot"
    release: Mapped[str] = mapped_column(String(64), primary_key=True)
    source_commit: Mapped[str] = mapped_column(String(40))
    state: Mapped[str] = mapped_column(String(20), default="PUBLISHED")
    published_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)
    candidate_id: Mapped[str] = mapped_column(String(128), unique=True)
    manifest: Mapped[dict[str, Any]] = mapped_column(JSONB)
    synchronized_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class AuditEvent(Base):
    __tablename__ = "rc_audit_event"
    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    trace_id: Mapped[str] = mapped_column(String(128), index=True)
    actor_sub: Mapped[str | None] = mapped_column(String(255), nullable=True)
    action: Mapped[str] = mapped_column(String(100))
    result: Mapped[str] = mapped_column(String(30))
    operation_id: Mapped[str | None] = mapped_column(String(36), nullable=True, index=True)
    metadata_json: Mapped[dict[str, Any]] = mapped_column(JSONB)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class SyncState(Base):
    __tablename__ = "rc_sync_state"
    domain: Mapped[str] = mapped_column(String(20), primary_key=True)
    last_success_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
    drift: Mapped[bool] = mapped_column(default=True)
    error_code: Mapped[str | None] = mapped_column(String(100), nullable=True)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=utc_now, onupdate=utc_now
    )


def build_engine(database_url: str) -> Engine:
    return create_engine(database_url, pool_pre_ping=True, future=True)


def build_session_factory(engine: Engine) -> sessionmaker[Session]:
    return sessionmaker(engine, expire_on_commit=False)


def session_scope(factory: sessionmaker[Session]) -> Iterator[Session]:
    with factory.begin() as session:
        yield session


def try_advisory_lock(session: Session) -> bool:
    return bool(
        session.scalar(
            text("SELECT pg_try_advisory_lock(:key)"),
            {"key": PUBLISHER_ADVISORY_LOCK_ID},
        )
    )


def release_advisory_lock(session: Session) -> None:
    session.execute(
        text("SELECT pg_advisory_unlock(:key)"),
        {"key": PUBLISHER_ADVISORY_LOCK_ID},
    )


def try_deployer_advisory_lock(session: Session) -> bool:
    return bool(
        session.scalar(
            text("SELECT pg_try_advisory_lock(:key)"),
            {"key": DEPLOYER_ADVISORY_LOCK_ID},
        )
    )


def release_deployer_advisory_lock(session: Session) -> None:
    session.execute(
        text("SELECT pg_advisory_unlock(:key)"),
        {"key": DEPLOYER_ADVISORY_LOCK_ID},
    )


def migration_is_current(engine: Engine) -> bool:
    with engine.connect() as connection:
        revision = connection.execute(
            text("SELECT version_num FROM alembic_version")
        ).scalar_one_or_none()
    return revision == "0004_candidate_commit_subject"
