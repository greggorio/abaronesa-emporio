"""Transactional application service for the isolated deployer runtime."""

from __future__ import annotations

import hashlib
import hmac
import json
import re
import uuid
from collections.abc import Callable
from datetime import timedelta
from typing import Any

from sqlalchemy import delete, select, text
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, sessionmaker

from .constants import (
    DEPLOYER_MODE,
    DEPLOYMENT_TERMINAL_STATES,
    REPOSITORY,
    ROLLBACK_TERMINAL_STATES,
    ROLLBACK_TRANSITIONS,
)
from .deployer_schemas import (
    ComponentPlanItem,
    CurrentInstallationResponse,
    DeploymentOperationResponse,
    DeploymentPlan,
    DeploymentRequest,
    GlobalReleasePage,
    GlobalReleaseSummary,
    RollbackOperationResponse,
    RollbackRequest,
)
from .errors import (
    PreDispatchFailure,
    RemoteHttpFailure,
    RemoteTransportFailure,
    RuntimeFailure,
)
from .github import GitHubClient
from .persistence import (
    AuditEvent,
    CurrentInstallation,
    DeploymentIdempotencyKey,
    DeploymentOperation,
    ReleaseSnapshot,
    RollbackBackup,
    SyncState,
    migration_is_current,
    utc_now,
)
from .security import CursorCodec, Principal

DEPLOYMENT_ROUTE = "POST:/api/deployment-control/v1/deployments"
ROLLBACK_ROUTE = "POST:/api/deployment-control/v1/rollbacks"
RUN_URL_RE = re.compile(
    rf"^https://github\.com/{re.escape(REPOSITORY)}/actions/runs/[1-9][0-9]*$"
)
CONTROL_SHA_RE = re.compile(r"^[0-9a-f]{40}$")
COMPONENTS = (
    "backend",
    "website_back",
    "frontend",
    "website_front",
    "whatsapp_service",
    "gateway",
)


class ActiveOperationFailure(RuntimeFailure):
    def __init__(self, operation_id: str) -> None:
        super().__init__("PRODUCTION_OPERATION_ACTIVE", 409, "Conflict")
        self.active_operation_id = operation_id


def _semver(value: str) -> tuple[int, int, int]:
    return tuple(int(part) for part in value[1:].split("."))  # type: ignore[return-value]


def _canonical_request(value: dict[str, str]) -> bytes:
    return (
        json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
        + "\n"
    ).encode()


def _migrations_are_prefix(current: dict[str, Any], target: dict[str, Any]) -> bool:
    current_databases = current.get("databases")
    target_databases = target.get("databases")
    if not isinstance(current_databases, list) or not isinstance(target_databases, list):
        return False
    current_by_id = {
        item.get("id"): item for item in current_databases if isinstance(item, dict)
    }
    target_by_id = {
        item.get("id"): item for item in target_databases if isinstance(item, dict)
    }
    if set(current_by_id) != set(target_by_id):
        return False
    for database_id, current_database in current_by_id.items():
        current_migrations = current_database.get("migrations")
        target_migrations = target_by_id[database_id].get("migrations")
        if (
            not isinstance(current_migrations, list)
            or not isinstance(target_migrations, list)
            or target_migrations[: len(current_migrations)] != current_migrations
        ):
            return False
    return True


class DeployerService:
    def __init__(
        self,
        factory: sessionmaker[Session],
        github: GitHubClient,
        pepper: bytes,
        retention_days: int,
        revalidate_release: Callable[[str], None],
    ) -> None:
        self.factory = factory
        self.github = github
        self.pepper = pepper
        self.retention_days = retention_days
        self.revalidate_release = revalidate_release
        self.cursor = CursorCodec(pepper)

    def _key_hash(self, key: str) -> str:
        return hmac.new(self.pepper, key.encode(), hashlib.sha256).hexdigest()

    @staticmethod
    def _request_hash(request: dict[str, str]) -> str:
        return hashlib.sha256(_canonical_request(request)).hexdigest()

    @staticmethod
    def _response(operation: DeploymentOperation) -> DeploymentOperationResponse:
        return DeploymentOperationResponse(
            operation_id=operation.operation_id,
            operation_type=operation.operation_type,
            state=operation.state,
            target_release=operation.target_release,
            workflow_run_url=operation.workflow_run_url,
            created_at=operation.created_at,
            updated_at=operation.updated_at,
            error_code=operation.error_code,
        )

    @staticmethod
    def _rollback_response(operation: DeploymentOperation) -> RollbackOperationResponse:
        if operation.source_release is None or operation.database_restore_required is None:
            raise RuntimeFailure("INTERNAL_ERROR")
        return RollbackOperationResponse(
            operation_id=operation.operation_id,
            operation_type="rollback",
            state=operation.state,
            source_release=operation.source_release,
            target_release=operation.target_release,
            database_restore_required=operation.database_restore_required,
            workflow_run_url=operation.workflow_run_url,
            created_at=operation.created_at,
            updated_at=operation.updated_at,
            error_code=operation.error_code,
        )

    @classmethod
    def _current_evidence(
        cls, session: Session, *, lock: bool = False
    ) -> tuple[CurrentInstallation | None, bool]:
        current = session.get(CurrentInstallation, 1, with_for_update=lock)
        if current is None:
            return None, True
        snapshot = (
            session.get(ReleaseSnapshot, current.release)
            if current.release is not None
            else None
        )
        consistent = (
            current.reconciled
            and current.release is not None
            and current.source_commit is not None
            and current.installed_at is not None
            and current.last_operation_id is not None
            and snapshot is not None
            and snapshot.source_commit == current.source_commit
            and cls._release_domain_green(session)
        )
        return current, consistent

    @classmethod
    def _current_or_clean(
        cls, session: Session, *, lock: bool = False
    ) -> CurrentInstallation | None:
        current, consistent = cls._current_evidence(session, lock=lock)
        if current is not None and not consistent:
            raise RuntimeFailure("CURRENT_INSTALLATION_UNRECONCILED", 409, "Conflict")
        return current

    @staticmethod
    def _release_domain_green(session: Session) -> bool:
        state = session.get(SyncState, "releases")
        return state is not None and state.last_success_at is not None and not state.drift

    @classmethod
    def _eligible(
        cls,
        session: Session,
        target: ReleaseSnapshot,
        current: CurrentInstallation | None,
    ) -> bool:
        if not cls._release_domain_green(session):
            return False
        previous = target.manifest.get("previousRelease")
        if current is None:
            releases = list(session.scalars(select(ReleaseSnapshot)))
            if not releases or target.release != max(
                (item.release for item in releases), key=_semver
            ):
                return False
            by_release = {item.release: item for item in releases}
            visited: set[str] = set()
            cursor = target
            while True:
                if cursor.release in visited:
                    return False
                visited.add(cursor.release)
                predecessor_name = cursor.manifest.get("previousRelease")
                if predecessor_name is None:
                    return True
                if not isinstance(predecessor_name, str):
                    return False
                predecessor = by_release.get(predecessor_name)
                if predecessor is None or _semver(predecessor.release) >= _semver(
                    cursor.release
                ):
                    return False
                if not _migrations_are_prefix(
                    predecessor.manifest, cursor.manifest
                ):
                    return False
                cursor = predecessor
        if current.release is None or previous != current.release:
            return False
        if _semver(target.release) <= _semver(current.release):
            return False
        current_release = session.get(ReleaseSnapshot, current.release)
        return current_release is not None and _migrations_are_prefix(
            current_release.manifest, target.manifest
        )

    @classmethod
    def _require_eligible(
        cls,
        session: Session,
        release: str,
        current: CurrentInstallation | None,
    ) -> ReleaseSnapshot:
        target = session.get(ReleaseSnapshot, release)
        if target is None:
            raise RuntimeFailure("NOT_FOUND", 404, "Not found")
        if not cls._eligible(session, target, current):
            raise RuntimeFailure("RELEASE_NOT_ELIGIBLE", 409, "Conflict")
        return target

    @classmethod
    def _rollback_migration_delta(
        cls, target: dict[str, Any], current: dict[str, Any]
    ) -> tuple[bool, bool]:
        """Return (valid_chain, database_restore_required).

        A rollback may omit database restoration only when every migration being
        removed carries an integral, explicit reversible proof.  The proof is
        deliberately metadata-only; the executor still verifies it remotely.
        """
        target_databases = target.get("databases")
        current_databases = current.get("databases")
        if not isinstance(target_databases, list) or not isinstance(current_databases, list):
            return False, True
        target_by_id = {
            item.get("id"): item for item in target_databases if isinstance(item, dict)
        }
        current_by_id = {
            item.get("id"): item for item in current_databases if isinstance(item, dict)
        }
        if set(target_by_id) != {"erp", "website"} or set(current_by_id) != {"erp", "website"}:
            return False, True
        restore_required = False
        for database_id in ("erp", "website"):
            target_migrations = target_by_id[database_id].get("migrations")
            current_migrations = current_by_id[database_id].get("migrations")
            if not isinstance(target_migrations, list) or not isinstance(current_migrations, list):
                return False, True
            if current_migrations[: len(target_migrations)] != target_migrations:
                return False, True
            delta = current_migrations[len(target_migrations) :]
            if delta and not all(
                isinstance(item, dict)
                and item.get("reversible") is True
                and isinstance(item.get("rollbackProof"), str)
                and bool(item["rollbackProof"])
                for item in delta
            ):
                restore_required = True
        return True, restore_required

    @classmethod
    def _rollback_target(
        cls,
        session: Session,
        target_release: str,
        current: CurrentInstallation,
    ) -> tuple[ReleaseSnapshot, bool, RollbackBackup | None]:
        target = session.get(ReleaseSnapshot, target_release)
        current_snapshot = session.get(ReleaseSnapshot, current.release)
        if target is None or current_snapshot is None:
            raise RuntimeFailure("RELEASE_NOT_ELIGIBLE", 409, "Conflict")
        manifest = target.manifest
        if (
            target.state != "PUBLISHED"
            or manifest.get("kind") != "global-release"
            or manifest.get("deployable") is not True
            or manifest.get("immutable") is not True
            or current.release is None
            or _semver(target.release) >= _semver(current.release)
            or current_snapshot.manifest.get("previousRelease") != target.release
        ):
            raise RuntimeFailure("RELEASE_NOT_ELIGIBLE", 409, "Conflict")
        valid, restore_required = cls._rollback_migration_delta(
            manifest, current_snapshot.manifest
        )
        if not valid:
            raise RuntimeFailure("RELEASE_NOT_ELIGIBLE", 409, "Conflict")
        backup: RollbackBackup | None = None
        if restore_required:
            backup_query = select(RollbackBackup).where(
                RollbackBackup.source_release == current.release,
                RollbackBackup.expires_at > utc_now(),
                RollbackBackup.verified.is_(True),
            )
            if current.state_sha256 is not None:
                backup_query = backup_query.where(
                    RollbackBackup.source_state_sha256 == current.state_sha256
                )
            backup = session.scalar(backup_query.order_by(RollbackBackup.created_at.desc()))
            if backup is None or not cls._backup_is_canonical(backup):
                raise RuntimeFailure("RELEASE_NOT_ELIGIBLE", 409, "Conflict")
        return target, restore_required, backup

    @staticmethod
    def _backup_is_canonical(backup: RollbackBackup) -> bool:
        evidence = backup.evidence_json
        forbidden = {
            "path",
            "credential",
            "credentials",
            "dump",
            "dumpContent",
            "privateUrl",
            "url",
        }
        return (
            backup.databases == ["erp", "website"]
            and backup.expires_at >= backup.created_at + timedelta(days=365)
            and isinstance(evidence, dict)
            and not forbidden.intersection(evidence)
            and evidence.get("backupId") == backup.backup_id
            and evidence.get("sourceRelease") == backup.source_release
            and evidence.get("sourceStateSha256") == backup.source_state_sha256
            and evidence.get("artifactSha256") == backup.artifact_sha256
            and evidence.get("databases") == ["erp", "website"]
        )

    def get_current(self) -> CurrentInstallationResponse:
        with self.factory() as session:
            current = self._current_or_clean(session)
            if current is None:
                raise RuntimeFailure("NOT_FOUND", 404, "Not found")
            if (
                current.release is None
                or current.source_commit is None
                or current.installed_at is None
            ):
                raise RuntimeFailure("CURRENT_INSTALLATION_UNRECONCILED", 409, "Conflict")
            return CurrentInstallationResponse(
                release=current.release,
                source_commit=current.source_commit,
                installed_at=current.installed_at,
                reconciled=True,
            )

    def list_releases(self, limit: int, cursor: str | None) -> GlobalReleasePage:
        with self.factory() as session:
            current, consistent = self._current_evidence(session)
            rows = list(session.scalars(select(ReleaseSnapshot)))
            rows.sort(key=lambda item: _semver(item.release), reverse=True)
            start = 0
            if cursor is not None:
                payload = self.cursor.decode(cursor)
                if set(payload) != {"release"} or not isinstance(payload["release"], str):
                    raise RuntimeFailure("BAD_REQUEST", 400, "Bad request")
                names = [item.release for item in rows]
                if payload["release"] not in names:
                    raise RuntimeFailure("BAD_REQUEST", 400, "Bad request")
                start = names.index(payload["release"]) + 1
            page = rows[start : start + limit]
            items = [
                GlobalReleaseSummary(
                    release=item.release,
                    source_commit=item.source_commit,
                    published_at=item.published_at,
                    eligible=(
                        consistent and self._eligible(session, item, current)
                    ),
                )
                for item in page
            ]
        next_cursor = (
            self.cursor.encode({"release": page[-1].release})
            if page and start + limit < len(rows)
            else None
        )
        return GlobalReleasePage(items=items, next_cursor=next_cursor)

    def get_plan(self, release: str) -> DeploymentPlan:
        with self.factory() as session:
            current = self._current_or_clean(session)
            target = self._require_eligible(session, release, current)
            current_snapshot = (
                session.get(ReleaseSnapshot, current.release)
                if current is not None and current.release is not None
                else None
            )
            current_components = {
                item["id"]: item["digest"]
                for item in current_snapshot.manifest.get("components", [])
            } if current_snapshot is not None else {}
            target_components = {
                item["id"]: item["digest"] for item in target.manifest["components"]
            }
            components = [
                ComponentPlanItem(
                    component=component,
                    action=(
                        "KEEP"
                        if current_components.get(component) == target_components[component]
                        else "UPDATE"
                    ),
                    current_digest=current_components.get(component),
                    target_digest=target_components[component],
                )
                for component in COMPONENTS
            ]
            migration_required = current_snapshot is None or any(
                len(target_database["migrations"])
                > len(
                    next(
                        database["migrations"]
                        for database in current_snapshot.manifest["databases"]
                        if database["id"] == target_database["id"]
                    )
                )
                for target_database in target.manifest["databases"]
            )
            return DeploymentPlan(
                source_release=current.release if current is not None else None,
                target_release=target.release,
                components=components,
                migration_required=migration_required,
                backup_required=migration_required,
            )

    def _find_idempotency(
        self, session: Session, actor: str, key_hash: str
    ) -> tuple[DeploymentIdempotencyKey, DeploymentOperation] | None:
        row = session.execute(
            select(DeploymentIdempotencyKey, DeploymentOperation)
            .join(
                DeploymentOperation,
                DeploymentOperation.operation_id
                == DeploymentIdempotencyKey.operation_id,
            )
            .where(
                DeploymentIdempotencyKey.mode == DEPLOYER_MODE,
                DeploymentIdempotencyKey.route == DEPLOYMENT_ROUTE,
                DeploymentIdempotencyKey.actor_sub == actor,
                DeploymentIdempotencyKey.key_hmac == key_hash,
            )
        ).one_or_none()
        return None if row is None else (row[0], row[1])

    def _replay(
        self, session: Session, actor: str, key_hash: str, request_hash: str
    ) -> DeploymentOperation | None:
        found = self._find_idempotency(session, actor, key_hash)
        if found is None:
            return None
        key, operation = found
        if key.request_hash != request_hash:
            raise RuntimeFailure("IDEMPOTENCY_CONFLICT", 409, "Conflict")
        return operation

    def _resolve_integrity_race(
        self, actor: str, key_hash: str, request_hash: str
    ) -> tuple[DeploymentOperationResponse, bool]:
        """Classify a failed insert using fresh, authoritative database state."""
        with self.factory() as session:
            replay = self._replay(session, actor, key_hash, request_hash)
            if replay is not None:
                return self._response(replay), True
            active = session.scalar(
                select(DeploymentOperation.operation_id).where(
                    DeploymentOperation.active_slot == 1
                )
            )
            if active is not None:
                raise ActiveOperationFailure(active)
        raise RuntimeFailure("INTERNAL_ERROR", 500, "Internal server error")

    def create_deployment(
        self,
        principal: Principal,
        idempotency_key: str,
        request_model: DeploymentRequest,
        trace_id: str,
    ) -> tuple[DeploymentOperationResponse, bool]:
        request = request_model.canonical_dict()
        key_hash = self._key_hash(idempotency_key)
        request_hash = self._request_hash(request)
        with self.factory() as session:
            replay = self._replay(session, principal.sub, key_hash, request_hash)
            if replay is not None:
                return self._response(replay), True
            current = self._current_or_clean(session)
            self._require_eligible(session, request_model.release, current)

        self.revalidate_release(request_model.release)
        operation_id = "dep_" + uuid.uuid4().hex
        now = utc_now()
        try:
            with self.factory.begin() as session:
                session.execute(
                    text("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))"),
                    {"key": "production_global"},
                )
                replay = self._replay(session, principal.sub, key_hash, request_hash)
                if replay is not None:
                    return self._response(replay), True
                current = self._current_or_clean(session, lock=True)
                target = self._require_eligible(session, request_model.release, current)
                active = session.scalar(
                    select(DeploymentOperation.operation_id).where(
                        DeploymentOperation.active_slot == 1
                    )
                )
                if active is not None:
                    raise ActiveOperationFailure(active)
                operation = DeploymentOperation(
                    operation_id=operation_id,
                    operation_type="deployment",
                    mode=DEPLOYER_MODE,
                    state="QUEUED",
                    actor_sub=principal.sub,
                    scopes=sorted(principal.scopes),
                    target_release=target.release,
                    source_release=current.release if current is not None else None,
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
                    DeploymentIdempotencyKey(
                        mode=DEPLOYER_MODE,
                        route=DEPLOYMENT_ROUTE,
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
                        action="deployment.requested",
                        result="accepted",
                        operation_id=operation_id,
                        metadata_json={"release": target.release, "state": "QUEUED"},
                    )
                )
        except IntegrityError as exc:
            try:
                return self._resolve_integrity_race(
                    principal.sub, key_hash, request_hash
                )
            except RuntimeFailure as failure:
                raise failure from exc

        try:
            self.github.dispatch_deployment(operation_id, request_model.release)
        except PreDispatchFailure:
            self._fail_dispatch(operation_id, "WORKFLOW_DISPATCH_NOT_SENT", trace_id)
        except RemoteTransportFailure as exc:
            if exc.uncertain:
                self._set_dispatch(operation_id, "UNCERTAIN")
            else:
                self._fail_dispatch(operation_id, "WORKFLOW_DISPATCH_NOT_SENT", trace_id)
        except RemoteHttpFailure as exc:
            if exc.remote_status in {400, 401, 403, 404, 422, 429}:
                self._fail_dispatch(operation_id, "WORKFLOW_DISPATCH_REJECTED", trace_id)
            else:
                self._set_dispatch(operation_id, "UNCERTAIN")
        else:
            self._set_dispatch(operation_id, "SENT")
        return self.get_operation(operation_id), False

    def _find_rollback_idempotency(
        self, session: Session, actor: str, key_hash: str
    ) -> tuple[DeploymentIdempotencyKey, DeploymentOperation] | None:
        row = session.execute(
            select(DeploymentIdempotencyKey, DeploymentOperation)
            .join(
                DeploymentOperation,
                DeploymentOperation.operation_id == DeploymentIdempotencyKey.operation_id,
            )
            .where(
                DeploymentIdempotencyKey.mode == DEPLOYER_MODE,
                DeploymentIdempotencyKey.route == ROLLBACK_ROUTE,
                DeploymentIdempotencyKey.actor_sub == actor,
                DeploymentIdempotencyKey.key_hmac == key_hash,
            )
        ).one_or_none()
        return None if row is None else (row[0], row[1])

    def _rollback_replay(
        self, session: Session, actor: str, key_hash: str, request_hash: str
    ) -> DeploymentOperation | None:
        found = self._find_rollback_idempotency(session, actor, key_hash)
        if found is None:
            return None
        key, operation = found
        if key.request_hash != request_hash:
            raise RuntimeFailure("IDEMPOTENCY_CONFLICT", 409, "Conflict")
        return operation

    def create_rollback(
        self,
        principal: Principal,
        idempotency_key: str,
        request_model: RollbackRequest,
        trace_id: str,
    ) -> tuple[RollbackOperationResponse, bool]:
        request = request_model.canonical_dict()
        key_hash = self._key_hash(idempotency_key)
        request_hash = self._request_hash(request)
        with self.factory() as session:
            replay = self._rollback_replay(session, principal.sub, key_hash, request_hash)
            if replay is not None:
                return self._rollback_response(replay), True
            current = self._current_or_clean(session)
            if current is None:
                raise RuntimeFailure("RELEASE_NOT_ELIGIBLE", 409, "Conflict")
            self._rollback_target(session, request_model.release, current)

        self.revalidate_release(request_model.release)
        operation_id = "rbk_" + uuid.uuid4().hex
        now = utc_now()
        try:
            with self.factory.begin() as session:
                session.execute(
                    text("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))"),
                    {"key": "production_global"},
                )
                replay = self._rollback_replay(session, principal.sub, key_hash, request_hash)
                if replay is not None:
                    return self._rollback_response(replay), True
                current = self._current_or_clean(session, lock=True)
                if current is None:
                    raise RuntimeFailure("RELEASE_NOT_ELIGIBLE", 409, "Conflict")
                target, restore_required, backup = self._rollback_target(
                    session, request_model.release, current
                )
                active = session.scalar(
                    select(DeploymentOperation.operation_id).where(
                        DeploymentOperation.active_slot == 1
                    )
                )
                if active is not None:
                    raise ActiveOperationFailure(active)
                journal = {
                    "schemaVersion": 1,
                    "operationType": "rollback",
                    "events": [{"state": "QUEUED", "at": now.isoformat()}],
                }
                evidence = {
                    "eligibility": {
                        "currentRelease": current.release,
                        "targetRelease": target.release,
                        "sameChain": True,
                        "immediatePredecessor": True,
                    },
                    "databaseRestoreRequired": restore_required,
                }
                if backup is not None:
                    evidence["backup"] = {
                        "backupId": backup.backup_id,
                        "sourceRelease": backup.source_release,
                        "sourceStateSha256": backup.source_state_sha256,
                        "artifactSha256": backup.artifact_sha256,
                        "expiresAt": backup.expires_at.isoformat(),
                    }
                operation = DeploymentOperation(
                    operation_id=operation_id,
                    operation_type="rollback",
                    mode=DEPLOYER_MODE,
                    state="QUEUED",
                    actor_sub=principal.sub,
                    scopes=sorted(principal.scopes),
                    target_release=target.release,
                    source_release=current.release,
                    rollback_reason=request_model.reason,
                    request_json=request,
                    request_hash=request_hash,
                    idempotency_hash=key_hash,
                    dispatch_state="NOT_SENT",
                    database_restore_required=restore_required,
                    source_state_sha256=(
                        current.state_sha256
                        if current.state_sha256 is not None
                        else backup.source_state_sha256
                        if backup is not None
                        else None
                    ),
                    backup_id=backup.backup_id if backup is not None else None,
                    journal_json=journal,
                    evidence_json=evidence,
                    active_slot=1,
                    created_at=now,
                    updated_at=now,
                )
                session.add(operation)
                session.flush()
                session.add(
                    DeploymentIdempotencyKey(
                        mode=DEPLOYER_MODE,
                        route=ROLLBACK_ROUTE,
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
                        action="rollback.requested",
                        result="accepted",
                        operation_id=operation_id,
                        metadata_json={
                            "release": target.release,
                            "sourceRelease": current.release,
                            "state": "QUEUED",
                            "databaseRestoreRequired": restore_required,
                        },
                    )
                )
        except IntegrityError as exc:
            with self.factory() as session:
                replay = self._rollback_replay(session, principal.sub, key_hash, request_hash)
                if replay is not None:
                    return self._rollback_response(replay), True
                active = session.scalar(
                    select(DeploymentOperation.operation_id).where(
                        DeploymentOperation.active_slot == 1
                    )
                )
                if active is not None:
                    raise ActiveOperationFailure(active) from exc
            raise RuntimeFailure("INTERNAL_ERROR", 500, "Internal server error") from exc

        try:
            dispatch = self.github.dispatch_rollback
            dispatch(operation_id, request_model.release)
        except PreDispatchFailure:
            self._fail_dispatch(operation_id, "WORKFLOW_DISPATCH_NOT_SENT", trace_id)
        except RemoteTransportFailure as exc:
            if exc.uncertain:
                self.mark_uncertain(operation_id, "WORKFLOW_DISPATCH_UNCERTAIN", trace_id)
            else:
                self._fail_dispatch(operation_id, "WORKFLOW_DISPATCH_NOT_SENT", trace_id)
        except RemoteHttpFailure as exc:
            if exc.remote_status in {400, 401, 403, 404, 422, 429}:
                self._fail_dispatch(operation_id, "WORKFLOW_DISPATCH_REJECTED", trace_id)
            else:
                self.mark_uncertain(operation_id, "WORKFLOW_DISPATCH_UNCERTAIN", trace_id)
        else:
            self._set_dispatch(operation_id, "SENT")
        return self.get_rollback_operation(operation_id), False

    def _set_dispatch(self, operation_id: str, state: str) -> None:
        with self.factory.begin() as session:
            operation = session.get(DeploymentOperation, operation_id, with_for_update=True)
            if operation is None or operation.state in DEPLOYMENT_TERMINAL_STATES:
                return
            operation.dispatch_state = state
            operation.updated_at = utc_now()

    def _fail_dispatch(self, operation_id: str, code: str, trace_id: str) -> None:
        with self.factory.begin() as session:
            operation = session.get(DeploymentOperation, operation_id, with_for_update=True)
            if operation is None or operation.state in DEPLOYMENT_TERMINAL_STATES:
                return
            operation.state = "FAILED"
            operation.dispatch_state = "NOT_SENT"
            operation.error_code = code
            operation.error_message = "Remote deployment dispatch failed"
            operation.updated_at = utc_now()
            operation.finished_at = operation.updated_at
            operation.active_slot = None
            session.add(
                AuditEvent(
                    trace_id=trace_id,
                    actor_sub=None,
                    action="deployment.failed",
                    result="failed",
                    operation_id=operation_id,
                    metadata_json={"code": code, "state": "FAILED"},
                )
            )

    def get_operation(self, operation_id: str) -> DeploymentOperationResponse:
        with self.factory() as session:
            operation = session.get(DeploymentOperation, operation_id)
            if operation is None:
                raise RuntimeFailure("NOT_FOUND", 404, "Not found")
            return self._response(operation)

    def get_rollback_operation(self, operation_id: str) -> RollbackOperationResponse:
        with self.factory() as session:
            operation = session.get(DeploymentOperation, operation_id)
            if operation is None or operation.operation_type != "rollback":
                raise RuntimeFailure("NOT_FOUND", 404, "Not found")
            return self._rollback_response(operation)

    def reject_rollback(
        self, principal: Principal, request: RollbackRequest, trace_id: str
    ) -> None:
        with self.factory.begin() as session:
            session.add(
                AuditEvent(
                    trace_id=trace_id,
                    actor_sub=principal.sub,
                    action="rollback.rejected",
                    result="rejected",
                    operation_id=None,
                    metadata_json={
                        "release": request.release,
                        "code": "RELEASE_NOT_ELIGIBLE",
                    },
                )
            )

    def cleanup_expired_idempotency(self) -> int:
        with self.factory.begin() as session:
            ids = list(
                session.scalars(
                    select(DeploymentIdempotencyKey.id)
                    .join(DeploymentOperation)
                    .where(
                        DeploymentIdempotencyKey.expires_at < utc_now(),
                        DeploymentOperation.state.in_(DEPLOYMENT_TERMINAL_STATES),
                    )
                )
            )
            if ids:
                session.execute(
                    delete(DeploymentIdempotencyKey).where(
                        DeploymentIdempotencyKey.id.in_(ids)
                    )
                )
            return len(ids)

    def mark_uncertain(self, operation_id: str, code: str, trace_id: str) -> None:
        """Persist unknown production state without falsely terminalizing the operation."""
        with self.factory.begin() as session:
            operation = session.get(DeploymentOperation, operation_id, with_for_update=True)
            if operation is None or operation.state in DEPLOYMENT_TERMINAL_STATES:
                return
            if operation.workflow_run_id is None:
                operation.dispatch_state = "UNCERTAIN"
            operation.transport_status = "INDETERMINATE"
            operation.error_code = code[:100]
            operation.error_message = "Remote deployment result is uncertain"
            operation.updated_at = utc_now()
            if operation.operation_type == "rollback":
                operation.state = "UNCERTAIN"
                operation.active_slot = None
                operation.finished_at = operation.updated_at
            current = session.get(CurrentInstallation, 1, with_for_update=True)
            if current is None:
                current = CurrentInstallation(singleton_id=1)
                session.add(current)
            current.reconciled = False
            current.uncertainty_code = code[:100]
            current.last_operation_id = operation_id
            current.updated_at = utc_now()
            session.add(
                AuditEvent(
                    trace_id=trace_id,
                    actor_sub=None,
                    action="deployment.uncertain",
                    result="INDETERMINATE",
                    operation_id=operation_id,
                    metadata_json={"code": code[:100], "transportStatus": "INDETERMINATE"},
                )
            )

    @staticmethod
    def _require_workflow_binding(operation: DeploymentOperation, transport: str) -> None:
        """Refuse a CONFIRMED outcome that is not bound to a discovered run.

        Only the reconciler applies outcomes today, and it binds the run before
        calling. This keeps the invariant owned by the service as well, so a
        CONFIRMED outcome can never mutate an operation, the current
        installation, the journal or the audit trail while the binding is
        missing or partial. INDETERMINATE keeps the existing UNCERTAIN path,
        which legitimately has no run yet.
        """
        if transport != "CONFIRMED":
            return
        run_id = operation.workflow_run_id
        attempt = operation.workflow_attempt
        run_url = operation.workflow_run_url
        control_sha = operation.control_sha
        if (
            not isinstance(run_id, int)
            or isinstance(run_id, bool)
            or run_id < 1
            or not isinstance(attempt, int)
            or isinstance(attempt, bool)
            or attempt < 1
            or not isinstance(run_url, str)
            or RUN_URL_RE.fullmatch(run_url) is None
            or not isinstance(control_sha, str)
            or CONTROL_SHA_RE.fullmatch(control_sha) is None
        ):
            raise RuntimeFailure("WORKFLOW_RUN_BINDING_INVALID")

    @staticmethod
    def _append_journal(operation: DeploymentOperation, state: str, now: Any) -> None:
        current = operation.journal_json if isinstance(operation.journal_json, dict) else {}
        events = current.get("events")
        events = list(events) if isinstance(events, list) else []
        events.append({"state": state, "at": now.isoformat()})
        # journal_json is a plain JSONB column, so mutating the stored dict in
        # place and assigning the same object back is not seen as a change and
        # never reaches the database. Always assign a fresh object.
        operation.journal_json = {
            **current,
            "schemaVersion": 1,
            "operationType": "rollback",
            "events": events,
        }

    def apply_rollback_outcome(
        self,
        operation_id: str,
        outcome: dict[str, Any],
        outcome_digest: str,
        trace_id: str,
    ) -> None:
        """Apply one bound versioned rollback outcome without repeating effects."""
        with self.factory.begin() as session:
            operation = session.get(DeploymentOperation, operation_id, with_for_update=True)
            if operation is None or operation.operation_type != "rollback":
                raise RuntimeFailure("NOT_FOUND", 404, "Not found")
            state = str(outcome["rollbackState"])
            transport = str(outcome["transportStatus"])
            restore_required = bool(outcome["databaseRestoreRequired"])
            error_code = outcome.get("errorCode")
            self._require_workflow_binding(operation, transport)
            if operation.state in ROLLBACK_TERMINAL_STATES:
                if (
                    operation.outcome_sha256 == outcome_digest
                    and operation.state == state
                    and operation.transport_status == transport
                ):
                    return
                raise RuntimeFailure("STATE_TRANSITION_INVALID")
            if operation.database_restore_required is True and not restore_required:
                evidence = outcome.get("evidence")
                restored = (
                    isinstance(evidence, dict)
                    and evidence.get("databaseRestore") == "RESTORED"
                )
                if not restored:
                    raise RuntimeFailure("DATABASE_RESTORE_REQUIRED")
            if state not in {
                "QUEUED",
                "PRECHECKING",
                "RESTORING",
                "SWITCHING",
                "VERIFYING",
                "SUCCEEDED",
                "ROLLING_BACK",
                "ROLLED_BACK",
                "FAILED",
                "UNCERTAIN",
            }:
                raise RuntimeFailure("STATE_TRANSITION_INVALID")
            if transport not in {"CONFIRMED", "INDETERMINATE"}:
                raise RuntimeFailure("STATE_TRANSITION_INVALID")
            if transport == "INDETERMINATE":
                state = "UNCERTAIN"
            elif state != operation.state and (operation.state, state) not in ROLLBACK_TRANSITIONS:
                raise RuntimeFailure("STATE_TRANSITION_INVALID")
            now = utc_now()
            operation.outcome_sha256 = outcome_digest
            operation.transport_status = transport
            operation.remote_state = state
            operation.database_restore_required = (
                operation.database_restore_required or restore_required
            )
            operation.error_code = error_code
            operation.evidence_json = (
                outcome.get("evidence")
                if isinstance(outcome.get("evidence"), dict)
                else {}
            )
            operation.updated_at = now
            self._append_journal(operation, state, now)
            if state in ROLLBACK_TERMINAL_STATES:
                operation.state = state
                operation.dispatch_state = "CONFIRMED" if transport == "CONFIRMED" else "UNCERTAIN"
                operation.active_slot = None
                operation.finished_at = now
                operation.error_message = (
                    None
                    if state == "SUCCEEDED"
                    else "Rollback did not complete commercially"
                )
            else:
                operation.state = state
            current = session.get(CurrentInstallation, 1, with_for_update=True)
            if state == "UNCERTAIN":
                if current is None:
                    current = CurrentInstallation(singleton_id=1)
                    session.add(current)
                current.reconciled = False
                current.uncertainty_code = str(error_code or "ROLLBACK_RESULT_INDETERMINATE")[:100]
                current.last_operation_id = operation_id
                current.updated_at = now
            elif state == "SUCCEEDED":
                target = session.get(ReleaseSnapshot, operation.target_release)
                if target is None:
                    raise RuntimeFailure("RELEASE_NOT_ELIGIBLE")
                evidence = operation.evidence_json
                target_state_hash = (
                    evidence.get("targetStateSha256")
                    if isinstance(evidence, dict)
                    else None
                )
                if not isinstance(target_state_hash, str) or not target_state_hash.startswith(
                    "sha256:"
                ):
                    target_state_hash = None
                if current is None:
                    current = CurrentInstallation(singleton_id=1)
                    session.add(current)
                current.release = target.release
                current.source_commit = target.source_commit
                current.previous_release = target.manifest.get("previousRelease")
                current.state_sha256 = target_state_hash
                current.installed_at = now
                current.reconciled = True
                current.uncertainty_code = None
                current.last_operation_id = operation_id
                current.updated_at = now
            elif state == "ROLLED_BACK" and current is not None:
                if (
                    isinstance(operation.evidence_json, dict)
                    and operation.evidence_json.get("reconciledCurrent") is True
                ):
                    current.reconciled = True
                    current.uncertainty_code = None
                    current.updated_at = now
            session.add(
                AuditEvent(
                    trace_id=trace_id,
                    actor_sub=None,
                    action="rollback.outcome",
                    result=state,
                    operation_id=operation_id,
                    metadata_json={
                        "state": state,
                        "transportStatus": transport,
                        "databaseRestoreRequired": operation.database_restore_required,
                    },
                )
            )

    def apply_outcome(
        self,
        operation_id: str,
        outcome: dict[str, Any],
        outcome_digest: str,
        trace_id: str,
    ) -> None:
        """Apply only a validated S21 terminal outcome, atomically and idempotently."""
        with self.factory.begin() as session:
            operation = session.get(DeploymentOperation, operation_id, with_for_update=True)
            if operation is None:
                raise RuntimeFailure("NOT_FOUND", 404, "Not found")
            transport = outcome["transportStatus"]
            state = outcome["deploymentState"]
            restore_required = outcome["databaseRestoreRequired"]
            error_code = outcome["errorCode"]
            self._require_workflow_binding(operation, str(transport))
            if operation.state in DEPLOYMENT_TERMINAL_STATES:
                if (
                    operation.outcome_sha256 == outcome_digest
                    and operation.state == state
                    and operation.transport_status == transport
                    and operation.database_restore_required == restore_required
                    and operation.error_code == error_code
                ):
                    return
                raise RuntimeFailure("STATE_TRANSITION_INVALID")
            if transport == "CONFIRMED" and state == "SUCCEEDED" and restore_required is True:
                raise RuntimeFailure("DEPLOYMENT_OUTCOME_RESTORE_CONFLICT")
            operation.outcome_sha256 = outcome_digest
            operation.transport_status = transport
            operation.database_restore_required = restore_required
            operation.remote_state = state
            operation.error_code = error_code
            operation.updated_at = utc_now()
            if transport == "INDETERMINATE":
                if operation.workflow_run_id is None:
                    operation.dispatch_state = "UNCERTAIN"
                operation.error_message = "Remote deployment result is uncertain"
                current = session.get(CurrentInstallation, 1, with_for_update=True)
                if current is None:
                    current = CurrentInstallation(singleton_id=1)
                    session.add(current)
                current.reconciled = False
                current.uncertainty_code = str(error_code or "REMOTE_RESULT_INDETERMINATE")[:100]
                current.last_operation_id = operation_id
                current.updated_at = utc_now()
                audit_result = "INDETERMINATE"
            else:
                if state not in DEPLOYMENT_TERMINAL_STATES:
                    raise RuntimeFailure("DEPLOYMENT_OUTCOME_INVALID")
                operation.state = state
                operation.dispatch_state = "CONFIRMED"
                operation.active_slot = None
                operation.finished_at = operation.updated_at
                operation.error_message = None if state == "SUCCEEDED" else "Deployment failed"
                current = session.get(CurrentInstallation, 1, with_for_update=True)
                if state == "SUCCEEDED":
                    target = session.get(ReleaseSnapshot, operation.target_release)
                    if target is None:
                        raise RuntimeFailure("RELEASE_NOT_ELIGIBLE")
                    if current is None:
                        current = CurrentInstallation(singleton_id=1)
                        session.add(current)
                    current.release = target.release
                    current.source_commit = target.source_commit
                    current.previous_release = operation.source_release
                    manifest_state_hash = target.manifest.get("stateSha256")
                    current.state_sha256 = (
                        manifest_state_hash
                        if isinstance(manifest_state_hash, str)
                        and manifest_state_hash.startswith("sha256:")
                        else None
                    )
                    current.installed_at = operation.updated_at
                    current.reconciled = True
                    current.uncertainty_code = None
                    current.last_operation_id = operation_id
                    current.updated_at = operation.updated_at
                elif restore_required is True:
                    if current is None:
                        current = CurrentInstallation(singleton_id=1)
                        session.add(current)
                    current.reconciled = False
                    current.uncertainty_code = str(error_code or "DATABASE_RESTORE_REQUIRED")[:100]
                    current.last_operation_id = operation_id
                    current.updated_at = operation.updated_at
                audit_result = str(state)
            session.add(
                AuditEvent(
                    trace_id=trace_id,
                    actor_sub=None,
                    action="deployment.outcome",
                    result=audit_result,
                    operation_id=operation_id,
                    metadata_json={
                        "state": state,
                        "transportStatus": transport,
                        "code": error_code,
                    },
                )
            )

    def ready(self, private_key_valid: bool) -> bool:
        try:
            engine = self.factory.kw["bind"]
            if not private_key_valid or not migration_is_current(engine):
                return False
            with self.factory() as session:
                session.execute(text("SELECT 1"))
                releases = session.get(SyncState, "releases")
                deployments = session.get(SyncState, "deployments")
                current, current_consistent = self._current_evidence(session)
                return (
                    releases is not None
                    and deployments is not None
                    and releases.last_success_at is not None
                    and deployments.last_success_at is not None
                    and not releases.drift
                    and not deployments.drift
                    and (current is None or current_consistent)
                )
        except Exception:
            return False
