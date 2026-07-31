from __future__ import annotations

import threading
from datetime import UTC, datetime, timedelta
from typing import Any, cast

import pytest
from sqlalchemy import select, text
from sqlalchemy.exc import DBAPIError
from sqlalchemy.orm import Session, sessionmaker

from emporio_release_control.errors import (
    PreDispatchFailure,
    RemoteHttpFailure,
    RemoteTransportFailure,
    RuntimeFailure,
)
from emporio_release_control.persistence import (
    AuditEvent,
    CandidateSnapshot,
    IdempotencyKey,
    PublicationOperation,
    ReleaseSnapshot,
    migration_is_current,
    release_advisory_lock,
    try_advisory_lock,
)
from emporio_release_control.schemas import PublishReleaseRequest
from emporio_release_control.security import Principal
from emporio_release_control.service import PublisherService


class FakeGitHub:
    def __init__(self, failure: Exception | None = None) -> None:
        self.failure = failure
        self.dispatches: list[tuple[str, dict[str, str]]] = []

    def dispatch_publication(self, operation_id: str, request: dict[str, str]) -> None:
        self.dispatches.append((operation_id, request))
        if self.failure:
            raise self.failure


def seed_candidate(
    factory: sessionmaker[Session],
    candidate_id: str = "candidate-" + "1" * 40 + "-2-1",
) -> str:
    with factory.begin() as session:
        session.add(
            CandidateSnapshot(
                candidate_id=candidate_id,
                source_commit="1" * 40,
                eligibility="READY",
                ci_status="PASSED",
                manifest_status="VALID",
                created_at=datetime(2026, 7, 29, tzinfo=UTC),
                manifest={"candidateId": candidate_id},
                artifact_id=1,
                artifact_digest="sha256:" + "a" * 64,
            )
        )
    return candidate_id


def build_service(
    factory: sessionmaker[Session],
    github: FakeGitHub | None = None,
    revalidate: Any = lambda _candidate: None,
) -> PublisherService:
    return PublisherService(
        factory, cast(Any, github or FakeGitHub()), b"p" * 32, 365, revalidate
    )


def request(candidate_id: str) -> PublishReleaseRequest:
    return PublishReleaseRequest(
        candidate_id=candidate_id,
        version_bump="PATCH",
        description="Safe description",
        changelog="Safe changelog",
    )


def test_migration_upgrade_is_idempotent_and_tables_indexes_exist(
    factory: sessionmaker[Session]
) -> None:
    engine = factory.kw["bind"]
    assert migration_is_current(engine)
    with engine.connect() as connection:
        tables = set(
            connection.execute(
                text("SELECT tablename FROM pg_tables WHERE schemaname='public'")
            ).scalars()
        )
        assert {
            "rc_publication_operation",
            "rc_idempotency_key",
            "rc_candidate_snapshot",
            "rc_release_snapshot",
            "rc_audit_event",
        } <= tables
        indexes = set(
            connection.execute(
                text("SELECT indexname FROM pg_indexes WHERE schemaname='public'")
            ).scalars()
        )
        assert "uq_rc_publication_active_slot" in indexes
        assert "uq_rc_idempotency_scope" in indexes


def test_advisory_lock_is_exclusive_across_real_connections(
    factory: sessionmaker[Session]
) -> None:
    with factory() as first, factory() as second:
        assert try_advisory_lock(first) is True
        assert try_advisory_lock(second) is False
        release_advisory_lock(first)
        assert try_advisory_lock(second) is True
        release_advisory_lock(second)


def test_create_dispatch_and_replay_never_persist_raw_key(
    factory: sessionmaker[Session]
) -> None:
    candidate = seed_candidate(factory)
    github = FakeGitHub()
    service = build_service(factory, github)
    principal = Principal("actor", frozenset({"release:publish"}))
    first, replay = service.create_publication(
        principal, "key-123456789012", request(candidate), "trace"
    )
    second, replay_second = service.create_publication(
        principal, "key-123456789012", request(candidate), "trace"
    )
    assert replay is False and replay_second is True
    assert first.operation_id == second.operation_id
    assert len(github.dispatches) == 1
    with factory() as session:
        key = session.scalar(select(IdempotencyKey))
        assert key is not None
        assert key.key_hmac != "key-123456789012"
        assert len(key.key_hmac) == 64
        operation = session.get(PublicationOperation, first.operation_id)
        assert operation is not None and operation.dispatch_state == "SENT"
        assert operation.active_slot == 1


def test_idempotency_conflict_and_active_slot_conflict(
    factory: sessionmaker[Session]
) -> None:
    candidate = seed_candidate(factory)
    service = build_service(factory)
    principal = Principal("actor", frozenset({"release:publish"}))
    service.create_publication(principal, "key-123456789012", request(candidate), "trace")
    changed = request(candidate)
    changed.description = "Different"
    with pytest.raises(RuntimeFailure, match="IDEMPOTENCY_CONFLICT"):
        service.create_publication(principal, "key-123456789012", changed, "trace")
    with pytest.raises(RuntimeFailure, match="VERSION_RESERVATION_CONFLICT"):
        service.create_publication(
            principal, "another-key-1234", request(candidate), "trace"
        )


def test_concurrent_same_request_returns_one_operation(
    factory: sessionmaker[Session]
) -> None:
    candidate = seed_candidate(factory)
    github = FakeGitHub()
    barrier = threading.Barrier(2)
    service = build_service(factory, github, lambda _candidate: barrier.wait(timeout=5))
    principal = Principal("actor", frozenset({"release:publish"}))
    results: list[tuple[str, bool]] = []
    errors: list[Exception] = []

    def call() -> None:
        try:
            value, replay = service.create_publication(
                principal, "race-key-12345678", request(candidate), "trace"
            )
            results.append((value.operation_id, replay))
        except Exception as exc:
            errors.append(exc)

    threads = [threading.Thread(target=call) for _ in range(2)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    assert not errors
    assert len({item[0] for item in results}) == 1
    assert sorted(item[1] for item in results) == [False, True]
    assert len(github.dispatches) == 1


@pytest.mark.parametrize(
    ("failure", "dispatch_state", "terminal", "error_code"),
    [
        (PreDispatchFailure(), "NOT_SENT", True, "WORKFLOW_DISPATCH_NOT_SENT"),
        (RemoteTransportFailure(), "NOT_SENT", True, "WORKFLOW_DISPATCH_NOT_SENT"),
        (RemoteTransportFailure(uncertain=True), "UNCERTAIN", False, None),
        (RemoteHttpFailure(403), "NOT_SENT", True, "WORKFLOW_DISPATCH_REJECTED"),
    ],
)
def test_dispatch_failure_classification(
    factory: sessionmaker[Session],
    failure: Exception,
    dispatch_state: str,
    terminal: bool,
    error_code: str | None,
) -> None:
    candidate = seed_candidate(factory)
    service = build_service(factory, FakeGitHub(failure))
    principal = Principal("actor", frozenset({"release:publish"}))
    response, _ = service.create_publication(
        principal, "failure-key-1234", request(candidate), "trace"
    )
    assert (response.state == "FAILED") is terminal
    with factory() as session:
        operation = session.scalar(select(PublicationOperation))
        assert operation is not None
        assert operation.dispatch_state == dispatch_state
        assert (operation.state == "FAILED") is terminal
        assert (operation.active_slot is None) is terminal
        assert operation.error_code == error_code
    replayed, replay = service.create_publication(
        principal, "failure-key-1234", request(candidate), "trace"
    )
    assert replay and replayed.operation_id == response.operation_id


def test_transition_is_monotonic_and_terminal_releases_slot(
    factory: sessionmaker[Session]
) -> None:
    candidate = seed_candidate(factory)
    service = build_service(factory)
    principal = Principal("actor", frozenset({"release:publish"}))
    operation, _ = service.create_publication(
        principal, "state-key-123456", request(candidate), "trace"
    )
    service.transition(operation.operation_id, "VALIDATING", "trace")
    service.transition(operation.operation_id, "PUBLISHING", "trace")
    service.transition(operation.operation_id, "PUBLISHED", "trace")
    with pytest.raises(RuntimeFailure, match="STATE_TRANSITION_INVALID"):
        service.transition(operation.operation_id, "FAILED", "trace")
    with factory() as session:
        stored = session.get(PublicationOperation, operation.operation_id)
        assert stored is not None and stored.active_slot is None
        assert stored.finished_at is not None
    with factory.begin() as session:
        key = session.scalar(select(IdempotencyKey))
        assert key is not None
        key.expires_at = datetime.now(UTC) - timedelta(seconds=1)
    assert service.cleanup_expired_idempotency() == 1


def test_audit_table_is_database_append_only(factory: sessionmaker[Session]) -> None:
    with factory.begin() as session:
        event = AuditEvent(
            trace_id="trace",
            actor_sub="actor",
            action="safe",
            result="ok",
            operation_id=None,
            metadata_json={"id": "opaque"},
        )
        session.add(event)
    with pytest.raises(DBAPIError):
        with factory.begin() as session:
            loaded_event = session.scalar(select(AuditEvent))
            assert loaded_event is not None
            loaded_event.result = "mutated"


def test_candidate_and_release_keyset_pagination(factory: sessionmaker[Session]) -> None:
    service = build_service(factory)
    now = datetime(2026, 7, 29, tzinfo=UTC)
    with factory.begin() as session:
        for index in range(3):
            candidate = f"candidate-{'1' * 40}-{index + 10}-1"
            session.add(
                CandidateSnapshot(
                    candidate_id=candidate,
                    source_commit="1" * 40,
                    eligibility="READY" if index != 2 else "NOT_ELIGIBLE",
                    ci_status="PASSED",
                    manifest_status="VALID",
                    created_at=now - timedelta(seconds=index),
                    manifest={},
                    artifact_id=index + 1,
                    artifact_digest="sha256:" + "1" * 64,
                )
            )
        for release in ("v1.0.0", "v2.0.0", "v1.10.0"):
            session.add(
                ReleaseSnapshot(
                    release=release,
                    source_commit="1" * 40,
                    state="PUBLISHED",
                    published_at=now,
                    candidate_id=f"release-candidate-{release}",
                    manifest={},
                )
            )
    first = service.list_candidates(1, None, "READY")
    assert len(first.items) == 1 and first.next_cursor
    second = service.list_candidates(1, first.next_cursor, "READY")
    assert second.items[0].candidate_id != first.items[0].candidate_id
    releases = service.list_releases(2, None)
    assert [item.release for item in releases.items] == ["v2.0.0", "v1.10.0"]
    assert releases.next_cursor
    assert service.list_releases(2, releases.next_cursor).items[0].release == "v1.0.0"
    bad = service.cursor.encode({"createdAt": "bad", "candidateId": "x"})
    with pytest.raises(RuntimeFailure):
        service.list_candidates(1, bad, None)
    with pytest.raises(RuntimeFailure):
        service.list_releases(1, service.cursor.encode({"release": "v9.9.9"}))


def test_missing_operation_mutations_are_sanitized(
    factory: sessionmaker[Session],
) -> None:
    service = build_service(factory)
    with pytest.raises(RuntimeFailure, match="NOT_FOUND"):
        service.get_operation("pub_" + "0" * 32)
    with pytest.raises(RuntimeFailure, match="NOT_FOUND"):
        service.transition("pub_" + "0" * 32, "VALIDATING", "trace")
    service.fail("pub_" + "0" * 32, "IGNORED", "trace")
