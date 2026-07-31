"""Transactional publication application service."""

from __future__ import annotations

import hashlib
import hmac
import json
import uuid
from collections.abc import Callable
from datetime import datetime, timedelta
from typing import Any

from sqlalchemy import and_, delete, desc, or_, select, text
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, sessionmaker

from .constants import PUBLISHER_MODE, TERMINAL_STATES, TRANSITIONS
from .errors import (
    PreDispatchFailure,
    RemoteHttpFailure,
    RemoteTransportFailure,
    RuntimeFailure,
)
from .github import GitHubClient
from .persistence import (
    AuditEvent,
    CandidateSnapshot,
    IdempotencyKey,
    PublicationOperation,
    ReleaseSnapshot,
    SyncState,
    migration_is_current,
    utc_now,
)
from .schemas import (
    CandidatePage,
    CandidateSummary,
    PublicationOperationResponse,
    PublishReleaseRequest,
    ReleasePage,
    ReleaseSummary,
)
from .security import CursorCodec, Principal

ROUTE = "POST:/api/release-publisher/v1/releases"


def canonical_request(value: dict[str, str]) -> bytes:
    return (
        json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False) + "\n"
    ).encode()


class PublisherService:
    def __init__(
        self,
        factory: sessionmaker[Session],
        github: GitHubClient,
        pepper: bytes,
        retention_days: int,
        revalidate_candidate: Callable[[str], None],
    ) -> None:
        self.factory = factory
        self.github = github
        self.pepper = pepper
        self.retention_days = retention_days
        self.revalidate_candidate = revalidate_candidate
        self.cursor = CursorCodec(pepper)

    def _key_hash(self, key: str) -> str:
        return hmac.new(self.pepper, key.encode(), hashlib.sha256).hexdigest()

    @staticmethod
    def _request_hash(request: dict[str, str]) -> str:
        return hashlib.sha256(canonical_request(request)).hexdigest()

    @staticmethod
    def _response(operation: PublicationOperation) -> PublicationOperationResponse:
        return PublicationOperationResponse(
            operation_id=operation.operation_id,
            state=operation.state,
            candidate_id=operation.candidate_id,
            release=operation.target_release,
            workflow_run_url=operation.workflow_run_url,
            created_at=operation.created_at,
            updated_at=operation.updated_at,
            error_code=operation.error_code,
        )

    def _find_idempotency(
        self, session: Session, actor: str, key_hash: str
    ) -> tuple[IdempotencyKey, PublicationOperation] | None:
        row = session.execute(
            select(IdempotencyKey, PublicationOperation)
            .join(
                PublicationOperation,
                PublicationOperation.operation_id == IdempotencyKey.operation_id,
            )
            .where(
                IdempotencyKey.mode == PUBLISHER_MODE,
                IdempotencyKey.route == ROUTE,
                IdempotencyKey.actor_sub == actor,
                IdempotencyKey.key_hmac == key_hash,
            )
        ).one_or_none()
        return row if row is None else (row[0], row[1])

    def _check_replay(
        self,
        session: Session,
        actor: str,
        key_hash: str,
        request_hash: str,
    ) -> PublicationOperation | None:
        existing = self._find_idempotency(session, actor, key_hash)
        if existing is None:
            return None
        record, operation = existing
        if record.request_hash != request_hash:
            raise RuntimeFailure("IDEMPOTENCY_CONFLICT", 409, "Conflict")
        return operation

    def create_publication(
        self,
        principal: Principal,
        idempotency_key: str,
        request_model: PublishReleaseRequest,
        trace_id: str,
    ) -> tuple[PublicationOperationResponse, bool]:
        request = request_model.canonical_dict()
        key_hash = self._key_hash(idempotency_key)
        request_hash = self._request_hash(request)
        with self.factory() as session:
            replay = self._check_replay(session, principal.sub, key_hash, request_hash)
            if replay is not None:
                return self._response(replay), True
            candidate = session.get(CandidateSnapshot, request_model.candidate_id)
            if candidate is None or candidate.eligibility != "READY":
                raise RuntimeFailure("NOT_FOUND", 404, "Not found")

        self.revalidate_candidate(request_model.candidate_id)

        operation_id = "pub_" + uuid.uuid4().hex
        now = utc_now()
        try:
            with self.factory.begin() as session:
                session.execute(
                    text("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))"),
                    {"key": f"{PUBLISHER_MODE}:{ROUTE}:{principal.sub}:{key_hash}"},
                )
                replay = self._check_replay(session, principal.sub, key_hash, request_hash)
                if replay is not None:
                    return self._response(replay), True
                candidate = session.get(
                    CandidateSnapshot, request_model.candidate_id, with_for_update=True
                )
                if candidate is None or candidate.eligibility != "READY":
                    raise RuntimeFailure("NOT_FOUND", 404, "Not found")
                active = session.scalar(
                    select(PublicationOperation.operation_id).where(
                        PublicationOperation.active_slot == 1
                    )
                )
                if active is not None:
                    raise RuntimeFailure("VERSION_RESERVATION_CONFLICT", 409, "Conflict")
                operation = PublicationOperation(
                    operation_id=operation_id,
                    state="REQUESTED",
                    actor_sub=principal.sub,
                    scopes=sorted(principal.scopes),
                    candidate_id=request_model.candidate_id,
                    request_json=request,
                    request_hash=request_hash,
                    idempotency_hash=key_hash,
                    dispatch_state="NOT_SENT",
                    active_slot=1,
                    created_at=now,
                    updated_at=now,
                )
                session.add(operation)
                session.flush()
                session.add(
                    IdempotencyKey(
                        mode=PUBLISHER_MODE,
                        route=ROUTE,
                        actor_sub=principal.sub,
                        key_hmac=key_hash,
                        request_hash=request_hash,
                        operation_id=operation_id,
                        created_at=now,
                        expires_at=now + timedelta(days=self.retention_days),
                    )
                )
                session.add(
                    AuditEvent(
                        trace_id=trace_id,
                        actor_sub=principal.sub,
                        action="publication.requested",
                        result="accepted",
                        operation_id=operation_id,
                        metadata_json={"candidateId": request_model.candidate_id},
                    )
                )
        except IntegrityError as exc:
            with self.factory() as session:
                replay = self._check_replay(
                    session, principal.sub, key_hash, request_hash
                )
                if replay is not None:
                    return self._response(replay), True
            raise RuntimeFailure("VERSION_RESERVATION_CONFLICT", 409, "Conflict") from exc

        try:
            self.github.dispatch_publication(operation_id, request)
        except PreDispatchFailure:
            self.fail(operation_id, "WORKFLOW_DISPATCH_NOT_SENT", trace_id)
        except RemoteTransportFailure as exc:
            if exc.uncertain:
                self._update_dispatch(operation_id, "UNCERTAIN")
            else:
                self.fail(operation_id, "WORKFLOW_DISPATCH_NOT_SENT", trace_id)
        except RemoteHttpFailure as exc:
            self.fail(operation_id, "WORKFLOW_DISPATCH_REJECTED", trace_id)
            _ = exc
        else:
            self._update_dispatch(operation_id, "SENT")
        return self.get_operation(operation_id), False

    def _update_dispatch(self, operation_id: str, state: str) -> None:
        with self.factory.begin() as session:
            operation = session.get(PublicationOperation, operation_id, with_for_update=True)
            if operation is None or operation.state in TERMINAL_STATES:
                return
            operation.dispatch_state = state
            operation.updated_at = utc_now()

    def get_operation(self, operation_id: str) -> PublicationOperationResponse:
        with self.factory() as session:
            operation = session.get(PublicationOperation, operation_id)
            if operation is None:
                raise RuntimeFailure("NOT_FOUND", 404, "Not found")
            return self._response(operation)

    def transition(
        self, operation_id: str, target: str, trace_id: str, metadata: dict[str, Any] | None = None
    ) -> None:
        with self.factory.begin() as session:
            operation = session.get(PublicationOperation, operation_id, with_for_update=True)
            if operation is None:
                raise RuntimeFailure("NOT_FOUND", 404, "Not found")
            if operation.state in TERMINAL_STATES or (operation.state, target) not in TRANSITIONS:
                raise RuntimeFailure("STATE_TRANSITION_INVALID")
            operation.state = target
            operation.updated_at = utc_now()
            if target in TERMINAL_STATES:
                operation.finished_at = operation.updated_at
                operation.active_slot = None
            session.add(
                AuditEvent(
                    trace_id=trace_id,
                    actor_sub=None,
                    action="publication.transition",
                    result=target,
                    operation_id=operation_id,
                    metadata_json=metadata or {},
                )
            )

    def fail(self, operation_id: str, code: str, trace_id: str) -> None:
        with self.factory.begin() as session:
            operation = session.get(PublicationOperation, operation_id, with_for_update=True)
            if operation is None or operation.state in TERMINAL_STATES:
                return
            operation.state = "FAILED"
            operation.error_code = code[:100]
            operation.error_message = "Remote publication failed"
            operation.updated_at = utc_now()
            operation.finished_at = operation.updated_at
            operation.active_slot = None
            session.add(
                AuditEvent(
                    trace_id=trace_id,
                    actor_sub=None,
                    action="publication.failed",
                    result="failed",
                    operation_id=operation_id,
                    metadata_json={"code": code[:100]},
                )
            )

    def cleanup_expired_idempotency(self) -> int:
        """Remove only expired keys whose operation is already terminal."""
        with self.factory.begin() as session:
            removable = select(IdempotencyKey.id).join(PublicationOperation).where(
                IdempotencyKey.expires_at < utc_now(),
                PublicationOperation.state.in_(TERMINAL_STATES),
            )
            ids = list(session.scalars(removable))
            if ids:
                session.execute(delete(IdempotencyKey).where(IdempotencyKey.id.in_(ids)))
            return len(ids)

    def list_candidates(
        self, limit: int, cursor: str | None, eligibility: str | None
    ) -> CandidatePage:
        with self.factory() as session:
            query = select(CandidateSnapshot)
            if eligibility is not None:
                query = query.where(CandidateSnapshot.eligibility == eligibility)
            if cursor:
                payload = self.cursor.decode(cursor)
                if set(payload) != {"createdAt", "candidateId"}:
                    raise RuntimeFailure("BAD_REQUEST", 400, "Bad request")
                try:
                    created_at = datetime.fromisoformat(str(payload["createdAt"]))
                except ValueError as exc:
                    raise RuntimeFailure("BAD_REQUEST", 400, "Bad request") from exc
                candidate_id = payload["candidateId"]
                if not isinstance(candidate_id, str):
                    raise RuntimeFailure("BAD_REQUEST", 400, "Bad request")
                query = query.where(
                    or_(
                        CandidateSnapshot.created_at < created_at,
                        and_(
                            CandidateSnapshot.created_at == created_at,
                            CandidateSnapshot.candidate_id > candidate_id,
                        ),
                    )
                )
            rows = list(
                session.scalars(
                    query.order_by(
                        desc(CandidateSnapshot.created_at), CandidateSnapshot.candidate_id
                    ).limit(limit + 1)
                )
            )
        items = rows[:limit]
        next_cursor = None
        if len(rows) > limit:
            last = items[-1]
            next_cursor = self.cursor.encode(
                {"createdAt": last.created_at.isoformat(), "candidateId": last.candidate_id}
            )
        return CandidatePage(
            items=[
                CandidateSummary(
                    candidate_id=item.candidate_id,
                    source_commit=item.source_commit,
                    eligibility=item.eligibility,
                    ci_status=item.ci_status,
                    manifest_status=item.manifest_status,
                    created_at=item.created_at,
                )
                for item in items
            ],
            next_cursor=next_cursor,
        )

    def list_releases(self, limit: int, cursor: str | None) -> ReleasePage:
        with self.factory() as session:
            rows = list(session.scalars(select(ReleaseSnapshot)))
        rows.sort(
            key=lambda item: tuple(int(part) for part in item.release[1:].split(".")),
            reverse=True,
        )
        start = 0
        if cursor:
            payload = self.cursor.decode(cursor)
            if set(payload) != {"release"} or not isinstance(payload["release"], str):
                raise RuntimeFailure("BAD_REQUEST", 400, "Bad request")
            names = [item.release for item in rows]
            if payload["release"] not in names:
                raise RuntimeFailure("BAD_REQUEST", 400, "Bad request")
            start = names.index(payload["release"]) + 1
        page = rows[start : start + limit]
        next_cursor = (
            self.cursor.encode({"release": page[-1].release})
            if start + limit < len(rows) and page
            else None
        )
        return ReleasePage(
            items=[
                ReleaseSummary(
                    release=item.release,
                    source_commit=item.source_commit,
                    state="PUBLISHED",
                    published_at=item.published_at,
                )
                for item in page
            ],
            next_cursor=next_cursor,
        )

    def ready(self, private_key_valid: bool) -> bool:
        try:
            engine = self.factory.kw["bind"]
            if not migration_is_current(engine):
                return False
            with self.factory() as session:
                session.execute(text("SELECT 1"))
                states = {
                    item.domain: item
                    for item in session.scalars(select(SyncState)).all()
                }
            return (
                private_key_valid
                and set(states) == {"candidates", "releases"}
                and all(
                    item.last_success_at is not None and not item.drift
                    for item in states.values()
                )
            )
        except Exception:
            return False
