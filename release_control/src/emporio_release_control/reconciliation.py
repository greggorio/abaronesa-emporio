"""Restart-safe reconciliation driven exclusively by remote evidence."""

from __future__ import annotations

import hashlib
import json
import logging
import re
import threading
from datetime import timedelta
from typing import Any

import jsonschema
from sqlalchemy import select
from sqlalchemy.orm import Session, sessionmaker

from .artifacts import OUTCOME_SCHEMA, canonical, digest, extract_zip
from .constants import REPOSITORY, TERMINAL_STATES
from .errors import RuntimeFailure
from .github import GitHubClient
from .persistence import (
    AuditEvent,
    PublicationOperation,
    ReleaseSnapshot,
    release_advisory_lock,
    try_advisory_lock,
    utc_now,
)
from .service import PublisherService
from .sync import Synchronizer, parse_time, positive_id

LOGGER = logging.getLogger("emporio_release_control.reconciliation")


def validate_publication_outcome(
    raw_zip: bytes,
    artifact_digest: str,
    *,
    operation: PublicationOperation,
    run_id: int,
    attempt: int,
) -> dict[str, Any]:
    files = extract_zip(
        raw_zip,
        artifact_digest,
        {
            "outcome.json": 64 * 1024,
            "outcome.json.sha256": 128,
            "metadata.json": 16 * 1024,
        },
        "PUBLICATION_OUTCOME_INVALID",
    )
    outcome_data = files["outcome.json"]
    expected_sidecar = (hashlib.sha256(outcome_data).hexdigest() + "\n").encode()
    if files["outcome.json.sha256"] != expected_sidecar:
        raise RuntimeFailure("PUBLICATION_OUTCOME_INVALID")
    try:
        outcome = json.loads(outcome_data)
        metadata = json.loads(files["metadata.json"])
        jsonschema.Draft202012Validator(
            json.loads(OUTCOME_SCHEMA.read_bytes()),
            format_checker=jsonschema.FormatChecker(),
        ).validate(outcome)
    except (ValueError, UnicodeDecodeError, jsonschema.ValidationError) as exc:
        raise RuntimeFailure("PUBLICATION_OUTCOME_INVALID") from exc
    expected_metadata = {
        "schemaVersion": 1,
        "stage": "final",
        "kind": "release-publication-outcome",
        "repository": REPOSITORY,
        "operationId": operation.operation_id,
        "workflowRunId": str(run_id),
        "workflowAttempt": attempt,
        "outcomeSha256": digest(outcome_data),
    }
    if (
        canonical(outcome) != outcome_data
        or canonical(metadata) != files["metadata.json"]
        or metadata != expected_metadata
        or outcome["operationId"] != operation.operation_id
        or outcome["candidateId"] != operation.candidate_id
        or outcome["workflow"]["runId"] != str(run_id)
        or outcome["workflow"]["attempt"] != attempt
        or outcome["workflow"]["url"]
        != f"https://github.com/{REPOSITORY}/actions/runs/{run_id}"
        or outcome["githubRelease"]["tagName"] != outcome["release"]
        or outcome["githubRelease"]["url"]
        != (
            f"https://github.com/{REPOSITORY}/releases/tag/"
            f"{outcome['release']}"
        )
    ):
        raise RuntimeFailure("PUBLICATION_OUTCOME_BINDING_INVALID")
    return dict(outcome)


class Reconciler:
    def __init__(
        self,
        factory: sessionmaker[Session],
        github: GitHubClient,
        service: PublisherService,
        synchronizer: Synchronizer,
        discovery_timeout_seconds: int,
    ) -> None:
        self.factory = factory
        self.github = github
        self.service = service
        self.synchronizer = synchronizer
        self.discovery_timeout_seconds = discovery_timeout_seconds

    def cycle(self) -> bool:
        with self.factory() as session:
            if not try_advisory_lock(session):
                return False
            try:
                for sync_call, trace in (
                    (self.synchronizer.sync_releases, "reconcile-releases"),
                    (self.synchronizer.sync_candidates, "reconcile-candidates"),
                ):
                    try:
                        sync_call(trace_id=trace)
                    except Exception:
                        LOGGER.error("synchronization_failed", extra={"code": "SYNC_FAILED"})
                self.service.cleanup_expired_idempotency()
                operation_ids = list(
                    session.scalars(
                        select(PublicationOperation.operation_id).where(
                            PublicationOperation.state.not_in(TERMINAL_STATES)
                        )
                    )
                )
                for operation_id in operation_ids:
                    try:
                        self._operation(operation_id)
                    except RuntimeFailure as exc:
                        self.service.fail(operation_id, exc.code, "reconcile")
                return True
            finally:
                release_advisory_lock(session)

    def _operation(self, operation_id: str) -> None:
        with self.factory() as session:
            operation = session.get(PublicationOperation, operation_id)
            if operation is None or operation.state in TERMINAL_STATES:
                return
            created_at = operation.created_at
        runs = self.github.list_pages(
            f"/repos/{REPOSITORY}/actions/workflows/publish-release.yml/runs"
            "?branch=main&event=workflow_dispatch",
            "workflow_runs",
        )
        title = f"publish-release-{operation_id}"
        matches = [
            run
            for run in runs
            if run.get("display_title") == title
            and parse_time(run.get("created_at"), "WORKFLOW_RUN_INVALID")
            >= created_at - timedelta(seconds=5)
        ]
        if not matches:
            if utc_now() - created_at > timedelta(seconds=self.discovery_timeout_seconds):
                self.service.fail(
                    operation_id, "WORKFLOW_DISPATCH_UNCONFIRMED", "reconcile"
                )
            return
        if len(matches) != 1:
            self.service.fail(operation_id, "WORKFLOW_RUN_AMBIGUOUS", "reconcile")
            return
        run = matches[0]
        run_id, attempt = self._bind_run(operation_id, run)
        conclusion = run.get("conclusion")
        status = run.get("status")
        if status != "completed":
            self._advance(operation_id, "VALIDATING")
            jobs = self.github.get_json(
                f"/repos/{REPOSITORY}/actions/runs/{run_id}/jobs?per_page=100"
            ).get("jobs")
            if not isinstance(jobs, list):
                raise RuntimeFailure("WORKFLOW_JOBS_INVALID")
            publish_jobs = [item for item in jobs if item.get("name") == "publish"]
            if len(publish_jobs) == 1 and publish_jobs[0].get("status") in {
                "in_progress",
                "completed",
            }:
                self._advance(operation_id, "PUBLISHING")
            return
        if conclusion != "success":
            self.service.fail(operation_id, "WORKFLOW_RUN_FAILED", "reconcile")
            return
        self._advance(operation_id, "VALIDATING")
        self._advance(operation_id, "PUBLISHING")
        artifacts = self.github.list_pages(
            f"/repos/{REPOSITORY}/actions/runs/{run_id}/artifacts", "artifacts"
        )
        outcomes = [
            item for item in artifacts if item.get("name") == "release-publication-outcome"
        ]
        if len(outcomes) != 1:
            self.service.fail(operation_id, "PUBLICATION_OUTCOME_AMBIGUOUS", "reconcile")
            return
        artifact = outcomes[0]
        artifact_id = positive_id(artifact.get("id"), "PUBLICATION_OUTCOME_INVALID")
        artifact_digest = artifact.get("digest")
        workflow_run = artifact.get("workflow_run")
        if (
            artifact.get("expired") is not False
            or not isinstance(artifact_digest, str)
            or re.fullmatch(r"sha256:[0-9a-f]{64}", artifact_digest) is None
            or artifact.get("url")
            != f"https://api.github.com/repos/{REPOSITORY}/actions/artifacts/{artifact_id}"
            or artifact.get("archive_download_url")
            != f"https://api.github.com/repos/{REPOSITORY}/actions/artifacts/{artifact_id}/zip"
            or not isinstance(workflow_run, dict)
            or workflow_run.get("id") != run_id
            or workflow_run.get("head_sha") != run.get("head_sha")
        ):
            raise RuntimeFailure("PUBLICATION_OUTCOME_INVALID")
        with self.factory() as session:
            operation = session.get(PublicationOperation, operation_id)
            if operation is None:
                return
            outcome = validate_publication_outcome(
                self.github.get_bytes(
                    f"/repos/{REPOSITORY}/actions/artifacts/{artifact_id}/zip"
                ),
                artifact_digest,
                operation=operation,
                run_id=run_id,
                attempt=attempt,
            )
        self.synchronizer.sync_releases(trace_id="publication-reconcile")
        with self.factory.begin() as session:
            operation = session.get(PublicationOperation, operation_id, with_for_update=True)
            release = session.get(ReleaseSnapshot, outcome["release"])
            if (
                operation is None
                or operation.state in TERMINAL_STATES
                or release is None
                or release.release != outcome["release"]
                or release.candidate_id != operation.candidate_id
                or release.source_commit != outcome["sourceCommit"]
                or digest(canonical(release.manifest)) != outcome["manifestSha256"]
            ):
                raise RuntimeFailure("PUBLICATION_RELEASE_EVIDENCE_INVALID")
            operation.state = "PUBLISHED"
            operation.target_release = release.release
            operation.source_commit = release.source_commit
            operation.remote_state = "success"
            operation.active_slot = None
            operation.finished_at = utc_now()
            operation.updated_at = operation.finished_at
            session.add(
                AuditEvent(
                    trace_id="reconcile",
                    actor_sub=None,
                    action="publication.transition",
                    result="PUBLISHED",
                    operation_id=operation_id,
                    metadata_json={"release": release.release},
                )
            )

    def _bind_run(self, operation_id: str, run: dict[str, Any]) -> tuple[int, int]:
        run_id = positive_id(run.get("id"), "WORKFLOW_RUN_INVALID")
        attempt = positive_id(run.get("run_attempt"), "WORKFLOW_RUN_INVALID")
        if (
            run.get("name") != "Publish Release"
            or run.get("path") != ".github/workflows/publish-release.yml@main"
            or run.get("event") != "workflow_dispatch"
            or run.get("head_branch") != "main"
            or run.get("display_title") != f"publish-release-{operation_id}"
            or not isinstance(run.get("head_sha"), str)
            or re.fullmatch(r"[0-9a-f]{40}", run["head_sha"]) is None
            or not isinstance(run.get("repository"), dict)
            or run["repository"].get("full_name") != REPOSITORY
            or not isinstance(run.get("head_repository"), dict)
            or run["head_repository"].get("full_name") != REPOSITORY
        ):
            raise RuntimeFailure("WORKFLOW_RUN_INVALID")
        with self.factory.begin() as session:
            operation = session.get(PublicationOperation, operation_id, with_for_update=True)
            if operation is None or operation.state in TERMINAL_STATES:
                return run_id, attempt
            if operation.workflow_run_id not in {None, run_id}:
                raise RuntimeFailure("WORKFLOW_RUN_AMBIGUOUS")
            operation.workflow_run_id = run_id
            operation.workflow_attempt = attempt
            operation.workflow_run_url = (
                f"https://github.com/{REPOSITORY}/actions/runs/{run_id}"
            )
            operation.dispatch_state = "CONFIRMED"
            operation.remote_state = str(run.get("status"))
        return run_id, attempt

    def _advance(self, operation_id: str, target: str) -> None:
        with self.factory() as session:
            operation = session.get(PublicationOperation, operation_id)
            current = operation.state if operation is not None else None
        rank = {"REQUESTED": 0, "VALIDATING": 1, "PUBLISHING": 2}
        if (
            current == target
            or current in TERMINAL_STATES
            or (
                current in rank
                and target in rank
                and rank[current] > rank[target]
            )
        ):
            return
        if current == "REQUESTED" and target == "PUBLISHING":
            self.service.transition(operation_id, "VALIDATING", "reconcile")
        self.service.transition(operation_id, target, "reconcile")


class ReconcileLoop:
    def __init__(self, reconciler: Reconciler, interval: int) -> None:
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
                LOGGER.error("reconciliation_cycle_failed", extra={"code": "RECONCILE_FAILED"})
                continue

    def stop(self) -> None:
        self._stop.set()
        if self._thread is not None:
            self._thread.join(timeout=self.interval + 1)
