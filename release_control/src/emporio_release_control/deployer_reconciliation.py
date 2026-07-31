"""Restart-safe deployer reconciliation driven only by S21 evidence."""

from __future__ import annotations

import logging
import re
import threading
from datetime import timedelta
from typing import TYPE_CHECKING, Any

from sqlalchemy import select, text
from sqlalchemy.orm import Session, sessionmaker

from .constants import (
    DEPLOYER_ADVISORY_LOCK_ID,
    DEPLOYMENT_TERMINAL_STATES,
    REPOSITORY,
    ROLLBACK_WORKFLOW,
)
from .deployment_artifacts import (
    OUTCOME_NAME,
    validate_deployment_artifact,
    validate_deployment_outcome,
    validate_run_conclusion,
)
from .errors import RuntimeFailure
from .github import GitHubClient
from .persistence import (
    AuditEvent,
    DeploymentOperation,
    SyncState,
    release_deployer_advisory_lock,
    try_deployer_advisory_lock,
    utc_now,
)
from .rollback_artifacts import (
    OUTCOME_NAME as ROLLBACK_OUTCOME_NAME,
)
from .rollback_artifacts import (
    validate_rollback_artifact,
    validate_rollback_outcome,
    validate_rollback_run_conclusion,
)
from .sync import Synchronizer, parse_time, positive_id

if TYPE_CHECKING:
    from .deployer_service import DeployerService

LOGGER = logging.getLogger("emporio_release_control.deployer_reconciliation")
WORKFLOW_RUNS_PATH = (
    f"/repos/{REPOSITORY}/actions/workflows/deploy-production.yml/runs"
    "?branch=main&event=workflow_dispatch"
)
ROLLBACK_WORKFLOW_RUNS_PATH = (
    f"/repos/{REPOSITORY}/actions/workflows/{ROLLBACK_WORKFLOW}/runs"
    "?branch=main&event=workflow_dispatch"
)
SHA_RE = re.compile(r"^[0-9a-f]{40}$")


class DeployerReconciler:
    def __init__(
        self,
        factory: sessionmaker[Session],
        github: GitHubClient,
        service: DeployerService,
        synchronizer: Synchronizer,
        discovery_timeout_seconds: int = 600,
    ) -> None:
        self.factory = factory
        self.github = github
        self.service = service
        self.synchronizer = synchronizer
        self.discovery_timeout_seconds = discovery_timeout_seconds

    def cycle(self) -> bool:
        """Run one lock-protected cycle; one bad operation never stops the rest."""

        with self.factory() as session:
            connection = session.connection()
            if not try_deployer_advisory_lock(session):
                return False
            green = True
            try:
                try:
                    self.synchronizer.sync_releases(trace_id="deployer-releases")
                except Exception:
                    green = False
                    LOGGER.error("synchronization_failed", extra={"code": "SYNC_FAILED"})
                try:
                    self.service.cleanup_expired_idempotency()
                except Exception:
                    green = False
                    LOGGER.error(
                        "idempotency_cleanup_failed",
                        extra={"code": "RECONCILE_FAILED"},
                    )
                try:
                    operation_ids = list(
                        session.scalars(
                            select(DeploymentOperation.operation_id).where(
                                DeploymentOperation.state.not_in(DEPLOYMENT_TERMINAL_STATES)
                            )
                        )
                    )
                except Exception:
                    green = False
                    operation_ids = []
                    LOGGER.error(
                        "deployment_query_failed",
                        extra={"code": "RECONCILE_FAILED"},
                    )
                for operation_id in operation_ids:
                    try:
                        green = self._operation(operation_id) and green
                    except RuntimeFailure as exc:
                        green = False
                        self._audit_failure(operation_id, exc.code)
                    except Exception:
                        green = False
                        self._audit_failure(operation_id, "RECONCILE_FAILED")
                try:
                    self._set_domain(green)
                except Exception:
                    LOGGER.error(
                        "deployment_domain_update_failed",
                        extra={"code": "RECONCILE_FAILED"},
                    )
                return True
            finally:
                try:
                    release_deployer_advisory_lock(session)
                except Exception:
                    LOGGER.error(
                        "deployment_lock_release_failed",
                        extra={"code": "RECONCILE_FAILED"},
                    )
                    try:
                        connection.rollback()
                        connection.execute(
                            text("SELECT pg_advisory_unlock(:key)"),
                            {"key": DEPLOYER_ADVISORY_LOCK_ID},
                        )
                    except Exception:
                        LOGGER.error(
                            "deployment_lock_release_fallback_failed",
                            extra={"code": "RECONCILE_FAILED"},
                        )

    def _operation(self, operation_id: str) -> bool:
        with self.factory() as session:
            operation = session.get(DeploymentOperation, operation_id)
            if operation is None or operation.state in DEPLOYMENT_TERMINAL_STATES:
                return True
            if operation.operation_type == "rollback":
                return self._rollback_operation(operation_id, operation.created_at)
            created_at = operation.created_at

        # Failure here precedes discovery of any candidate and only makes the
        # deployments domain drift; it must not invent per-operation evidence.
        runs = self.github.list_pages(WORKFLOW_RUNS_PATH, "workflow_runs")
        title = f"deploy-production-{operation_id}"
        matches: list[dict[str, Any]] = []
        lineage_failures: set[str] = set()
        for run in runs:
            if run.get("display_title") != title:
                continue
            try:
                run_created = parse_time(run.get("created_at"), "WORKFLOW_RUN_INVALID")
                self._validate_run(run, operation_id)
                if run_created >= created_at - timedelta(seconds=5):
                    matches.append(run)
            except RuntimeFailure as exc:
                lineage_failures.add(exc.code)
            except Exception:
                lineage_failures.add("RECONCILE_FAILED")

        if lineage_failures:
            code = (
                "RECONCILE_FAILED"
                if "RECONCILE_FAILED" in lineage_failures
                else sorted(lineage_failures)[0]
            )
            self.service.mark_uncertain(operation_id, code, "deployer-reconcile")
            return False

        if not matches:
            if utc_now() - created_at > timedelta(seconds=self.discovery_timeout_seconds):
                self.service.mark_uncertain(
                    operation_id,
                    "WORKFLOW_DISPATCH_UNCONFIRMED",
                    "deployer-reconcile",
                )
                return False
            return True
        if len(matches) != 1:
            self.service.mark_uncertain(
                operation_id,
                "WORKFLOW_RUN_AMBIGUOUS",
                "deployer-reconcile",
            )
            return False

        run = matches[0]
        try:
            run_id, attempt, control_sha = self._bind_run(operation_id, run)
            if run.get("status") != "completed":
                return True
            artifacts = self.github.list_pages(
                f"/repos/{REPOSITORY}/actions/runs/{run_id}/artifacts",
                "artifacts",
            )
            outcomes = [
                value
                for value in artifacts
                if value.get("name") == OUTCOME_NAME and value.get("expired") is False
            ]
            if len(outcomes) != 1:
                code = (
                    "DEPLOYMENT_OUTCOME_UNAVAILABLE"
                    if not outcomes
                    else "DEPLOYMENT_OUTCOME_AMBIGUOUS"
                )
                raise RuntimeFailure(code)
            artifact = validate_deployment_artifact(
                outcomes[0], run_id=run_id, head_sha=control_sha
            )
            with self.factory() as session:
                operation = session.get(DeploymentOperation, operation_id)
                if operation is None or operation.state in DEPLOYMENT_TERMINAL_STATES:
                    return True
                target_release = operation.target_release
            raw_zip = self.github.get_bytes(
                f"/repos/{REPOSITORY}/actions/artifacts/{artifact.artifact_id}/zip"
            )
            if len(raw_zip) != artifact.size_in_bytes:
                raise RuntimeFailure("DEPLOYMENT_OUTCOME_INVALID")
            outcome = validate_deployment_outcome(
                raw_zip,
                artifact.artifact_digest,
                operation_id=operation_id,
                target_release=target_release,
                run_id=run_id,
                attempt=attempt,
                control_sha=control_sha,
            )
            validate_run_conclusion(run.get("conclusion"), outcome)
            self.service.apply_outcome(
                operation_id,
                outcome,
                artifact.artifact_digest,
                "deployer-reconcile",
            )
        except RuntimeFailure as exc:
            self.service.mark_uncertain(operation_id, exc.code, "deployer-reconcile")
            return False
        except Exception:
            self.service.mark_uncertain(operation_id, "RECONCILE_FAILED", "deployer-reconcile")
            return False
        return True

    def _rollback_operation(self, operation_id: str, created_at: Any) -> bool:
        runs = self.github.list_pages(ROLLBACK_WORKFLOW_RUNS_PATH, "workflow_runs")
        title = f"rollback-production-{operation_id}"
        matches: list[dict[str, Any]] = []
        lineage_failures: set[str] = set()
        for run in runs:
            if run.get("display_title") != title:
                continue
            try:
                run_created = parse_time(run.get("created_at"), "WORKFLOW_RUN_INVALID")
                self._validate_rollback_run(run, operation_id)
                if run_created >= created_at - timedelta(seconds=5):
                    matches.append(run)
            except RuntimeFailure as exc:
                lineage_failures.add(exc.code)
            except Exception:
                lineage_failures.add("RECONCILE_FAILED")
        if lineage_failures:
            code = (
                "RECONCILE_FAILED"
                if "RECONCILE_FAILED" in lineage_failures
                else sorted(lineage_failures)[0]
            )
            self.service.mark_uncertain(operation_id, code, "deployer-reconcile")
            return False
        if not matches:
            if utc_now() - created_at > timedelta(seconds=self.discovery_timeout_seconds):
                self.service.mark_uncertain(
                    operation_id,
                    "WORKFLOW_DISPATCH_UNCONFIRMED",
                    "deployer-reconcile",
                )
                return False
            return True
        if len(matches) != 1:
            self.service.mark_uncertain(
                operation_id, "WORKFLOW_RUN_AMBIGUOUS", "deployer-reconcile"
            )
            return False
        run = matches[0]
        try:
            run_id, attempt, control_sha = self._bind_run(operation_id, run)
            if run.get("status") != "completed":
                return True
            artifacts = self.github.list_pages(
                f"/repos/{REPOSITORY}/actions/runs/{run_id}/artifacts", "artifacts"
            )
            outcomes = [
                value
                for value in artifacts
                if value.get("name") == ROLLBACK_OUTCOME_NAME and value.get("expired") is False
            ]
            if len(outcomes) != 1:
                raise RuntimeFailure(
                    "ROLLBACK_OUTCOME_UNAVAILABLE" if not outcomes else "ROLLBACK_OUTCOME_AMBIGUOUS"
                )
            artifact = validate_rollback_artifact(outcomes[0], run_id=run_id, head_sha=control_sha)
            with self.factory() as session:
                operation = session.get(DeploymentOperation, operation_id)
                if operation is None or operation.state in DEPLOYMENT_TERMINAL_STATES:
                    return True
                target_release = operation.target_release
            raw_zip = self.github.get_bytes(
                f"/repos/{REPOSITORY}/actions/artifacts/{artifact.artifact_id}/zip"
            )
            if len(raw_zip) != artifact.size_in_bytes:
                raise RuntimeFailure("ROLLBACK_OUTCOME_INVALID")
            outcome = validate_rollback_outcome(
                raw_zip,
                artifact.artifact_digest,
                operation_id=operation_id,
                target_release=target_release,
                run_id=run_id,
                attempt=attempt,
                control_sha=control_sha,
            )
            validate_rollback_run_conclusion(run.get("conclusion"), outcome)
            self.service.apply_rollback_outcome(
                operation_id, outcome, artifact.artifact_digest, "deployer-reconcile"
            )
        except RuntimeFailure as exc:
            self.service.mark_uncertain(operation_id, exc.code, "deployer-reconcile")
            return False
        except Exception:
            self.service.mark_uncertain(operation_id, "RECONCILE_FAILED", "deployer-reconcile")
            return False
        return True

    @staticmethod
    def _validate_run(run: dict[str, Any], operation_id: str) -> None:
        code = "WORKFLOW_RUN_INVALID"
        run_id = positive_id(run.get("id"), code)
        positive_id(run.get("run_attempt"), code)
        head_sha = run.get("head_sha")
        repository = run.get("repository")
        head_repository = run.get("head_repository")
        if (
            run.get("name") != "Deploy Production"
            or run.get("path") != ".github/workflows/deploy-production.yml@main"
            or run.get("event") != "workflow_dispatch"
            or run.get("head_branch") != "main"
            or run.get("display_title") != f"deploy-production-{operation_id}"
            or run.get("html_url") != f"https://github.com/{REPOSITORY}/actions/runs/{run_id}"
            or not isinstance(head_sha, str)
            or SHA_RE.fullmatch(head_sha) is None
            or not isinstance(repository, dict)
            or repository.get("full_name") != REPOSITORY
            or not isinstance(head_repository, dict)
            or head_repository.get("full_name") != REPOSITORY
            or run.get("status") not in {"queued", "in_progress", "completed"}
        ):
            raise RuntimeFailure(code)

    @staticmethod
    def _validate_rollback_run(run: dict[str, Any], operation_id: str) -> None:
        code = "WORKFLOW_RUN_INVALID"
        run_id = positive_id(run.get("id"), code)
        positive_id(run.get("run_attempt"), code)
        head_sha = run.get("head_sha")
        repository = run.get("repository")
        head_repository = run.get("head_repository")
        if (
            run.get("name") != "Rollback Production"
            or run.get("path") != f".github/workflows/{ROLLBACK_WORKFLOW}@main"
            or run.get("event") != "workflow_dispatch"
            or run.get("head_branch") != "main"
            or run.get("display_title") != f"rollback-production-{operation_id}"
            or run.get("html_url") != f"https://github.com/{REPOSITORY}/actions/runs/{run_id}"
            or not isinstance(head_sha, str)
            or SHA_RE.fullmatch(head_sha) is None
            or not isinstance(repository, dict)
            or repository.get("full_name") != REPOSITORY
            or not isinstance(head_repository, dict)
            or head_repository.get("full_name") != REPOSITORY
            or run.get("status") not in {"queued", "in_progress", "completed"}
        ):
            raise RuntimeFailure(code)

    def _bind_run(self, operation_id: str, run: dict[str, Any]) -> tuple[int, int, str]:
        run_id = positive_id(run.get("id"), "WORKFLOW_RUN_INVALID")
        attempt = positive_id(run.get("run_attempt"), "WORKFLOW_RUN_INVALID")
        control_sha = str(run["head_sha"])
        run_url = f"https://github.com/{REPOSITORY}/actions/runs/{run_id}"
        with self.factory.begin() as session:
            operation = session.get(DeploymentOperation, operation_id, with_for_update=True)
            if operation is None or operation.state in DEPLOYMENT_TERMINAL_STATES:
                return run_id, attempt, control_sha
            if operation.workflow_run_id not in {None, run_id}:
                raise RuntimeFailure("WORKFLOW_RUN_AMBIGUOUS")
            if operation.control_sha not in {None, control_sha}:
                raise RuntimeFailure("WORKFLOW_RUN_BINDING_INVALID")
            if operation.workflow_run_url not in {None, run_url}:
                raise RuntimeFailure("WORKFLOW_RUN_BINDING_INVALID")
            if operation.workflow_attempt is not None and attempt < operation.workflow_attempt:
                raise RuntimeFailure("WORKFLOW_ATTEMPT_REGRESSION")
            status_rank = {"queued": 0, "in_progress": 1, "completed": 2}
            previous_rank = status_rank.get(str(operation.remote_state))
            current_rank = status_rank.get(str(run.get("status")))
            if (
                operation.workflow_attempt == attempt
                and previous_rank is not None
                and current_rank is not None
                and current_rank < previous_rank
            ):
                raise RuntimeFailure("WORKFLOW_RUN_BINDING_INVALID")
            operation.workflow_run_id = run_id
            operation.workflow_attempt = attempt
            operation.workflow_run_url = run_url
            operation.control_sha = control_sha
            operation.dispatch_state = "CONFIRMED"
            operation.remote_state = str(run.get("status"))
        return run_id, attempt, control_sha

    def _audit_failure(self, operation_id: str, code: str) -> None:
        try:
            with self.factory.begin() as session:
                session.add(
                    AuditEvent(
                        trace_id="deployer-reconcile",
                        actor_sub=None,
                        action="deployment.reconcile",
                        result="FAILED",
                        operation_id=operation_id,
                        metadata_json={"code": code},
                    )
                )
        except Exception:
            LOGGER.error("reconciliation_audit_failed", extra={"code": "AUDIT_FAILED"})

    def _set_domain(self, green: bool) -> None:
        with self.factory.begin() as session:
            state = session.get(SyncState, "deployments")
            if state is None:
                state = SyncState(domain="deployments")
                session.add(state)
            state.drift = not green
            state.error_code = None if green else "RECONCILE_FAILED"
            if green:
                state.last_success_at = utc_now()
            state.updated_at = utc_now()


class DeployerReconcileLoop:
    def __init__(self, reconciler: DeployerReconciler, interval: int) -> None:
        self.reconciler = reconciler
        self.interval = interval
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None

    def start(self) -> None:
        self.reconciler.cycle()
        self._thread = threading.Thread(target=self._run, daemon=True)
        self._thread.start()

    def _run(self) -> None:
        while not self._stop.wait(self.interval):
            try:
                self.reconciler.cycle()
            except Exception:
                LOGGER.error(
                    "reconciliation_cycle_failed",
                    extra={"code": "RECONCILE_FAILED"},
                )

    def stop(self) -> None:
        self._stop.set()
        if self._thread is not None:
            self._thread.join(timeout=self.interval + 1)
