from __future__ import annotations

import hashlib
import json
from datetime import timedelta
from pathlib import Path
from typing import Any

import pytest
from sqlalchemy.orm import Session, sessionmaker
from test_remote_contract import CANDIDATE, REPOSITORY, SHA, zip_bytes

from emporio_release_control.artifacts import canonical, digest
from emporio_release_control.errors import RuntimeFailure
from emporio_release_control.persistence import (
    CandidateSnapshot,
    PublicationOperation,
    ReleaseSnapshot,
    utc_now,
)
from emporio_release_control.reconciliation import (
    ReconcileLoop,
    Reconciler,
    validate_publication_outcome,
)
from emporio_release_control.service import PublisherService

ROOT = Path(__file__).resolve().parents[2]
OPERATION = "pub_" + "a" * 32


def seed_operation(
    factory: sessionmaker[Session], *, age_seconds: int = 0, state: str = "REQUESTED"
) -> None:
    now = utc_now() - timedelta(seconds=age_seconds)
    with factory.begin() as session:
        session.add(
            CandidateSnapshot(
                candidate_id=CANDIDATE,
                source_commit=SHA,
                eligibility="READY",
                ci_status="PASSED",
                manifest_status="VALID",
                created_at=now,
                manifest={},
                artifact_id=1,
                artifact_digest="sha256:" + "1" * 64,
            )
        )
        session.add(
            PublicationOperation(
                operation_id=OPERATION,
                state=state,
                actor_sub="actor",
                scopes=["release:publish"],
                candidate_id=CANDIDATE,
                request_json={
                    "candidateId": CANDIDATE,
                    "versionBump": "PATCH",
                    "description": "d",
                    "changelog": "c",
                },
                request_hash="1" * 64,
                idempotency_hash="2" * 64,
                dispatch_state="SENT",
                active_slot=1,
                created_at=now,
                updated_at=now,
            )
        )


def release_manifest() -> dict[str, Any]:
    value: dict[str, Any] = json.loads(
        (ROOT / "ops/releases/examples/global-release.example.json").read_text()
    )
    return value


def outcome_bundle() -> tuple[bytes, str, dict[str, Any]]:
    release = release_manifest()
    value = json.loads(
        (ROOT / "ops/releases/examples/release-publication-outcome.example.json").read_text()
    )
    value["operationId"] = OPERATION
    value["candidateId"] = CANDIDATE
    value["workflow"]["runId"] = "400"
    value["workflow"]["attempt"] = 1
    value["workflow"]["url"] = f"https://github.com/{REPOSITORY}/actions/runs/400"
    value["manifestSha256"] = digest(canonical(release))
    data = canonical(value)
    metadata = canonical(
        {
            "schemaVersion": 1,
            "stage": "final",
            "kind": "release-publication-outcome",
            "repository": REPOSITORY,
            "operationId": OPERATION,
            "workflowRunId": "400",
            "workflowAttempt": 1,
            "outcomeSha256": digest(data),
        }
    )
    raw = zip_bytes(
        {
            "outcome.json": data,
            "outcome.json.sha256": (hashlib.sha256(data).hexdigest() + "\n").encode(),
            "metadata.json": metadata,
        }
    )
    return raw, digest(raw), value


def rebuild_outcome(value: dict[str, Any]) -> tuple[bytes, str]:
    data = canonical(value)
    metadata = canonical(
        {
            "schemaVersion": 1,
            "stage": "final",
            "kind": "release-publication-outcome",
            "repository": REPOSITORY,
            "operationId": OPERATION,
            "workflowRunId": "400",
            "workflowAttempt": 1,
            "outcomeSha256": digest(data),
        }
    )
    raw = zip_bytes(
        {
            "outcome.json": data,
            "outcome.json.sha256": (hashlib.sha256(data).hexdigest() + "\n").encode(),
            "metadata.json": metadata,
        }
    )
    return raw, digest(raw)


class FakeGitHub:
    def __init__(
        self,
        *,
        runs: list[dict[str, Any]] | None = None,
        artifacts: list[dict[str, Any]] | None = None,
        jobs: list[dict[str, Any]] | None = None,
    ) -> None:
        self.runs = runs or []
        self.artifacts = artifacts or []
        self.jobs = jobs or []
        self.raw, self.raw_digest, _ = outcome_bundle()

    def list_pages(self, path: str, key: str | None) -> list[dict[str, Any]]:
        _ = key
        if path.endswith("/artifacts"):
            return self.artifacts
        return self.runs

    def get_json(self, path: str) -> dict[str, Any]:
        assert path.endswith("/jobs?per_page=100")
        return {"jobs": self.jobs}

    def get_bytes(self, path: str) -> bytes:
        assert path.endswith("/700/zip")
        return self.raw


class FakeSync:
    def __init__(self, factory: sessionmaker[Session], fail: bool = False) -> None:
        self.factory = factory
        self.fail = fail
        self.calls: list[str] = []

    def sync_candidates(self, trace_id: str = "") -> None:
        self.calls.append(trace_id)
        if self.fail:
            raise RuntimeFailure("SYNC_INVALID")

    def sync_releases(self, trace_id: str = "") -> None:
        self.calls.append(trace_id)
        if self.fail:
            raise RuntimeFailure("SYNC_INVALID")
        manifest = release_manifest()
        with self.factory.begin() as session:
            session.merge(
                ReleaseSnapshot(
                    release="v0.0.1",
                    source_commit=SHA,
                    state="PUBLISHED",
                    published_at=utc_now(),
                    candidate_id=CANDIDATE,
                    manifest=manifest,
                )
            )


def run(
    *,
    operation: str = OPERATION,
    status: str = "completed",
    conclusion: str | None = "success",
) -> dict[str, Any]:
    return {
        "id": 400,
        "run_attempt": 1,
        "name": "Publish Release",
        "path": ".github/workflows/publish-release.yml@main",
        "event": "workflow_dispatch",
        "status": status,
        "conclusion": conclusion,
        "head_branch": "main",
        "head_sha": SHA,
        "display_title": f"publish-release-{operation}",
        "created_at": "2099-01-01T00:00:00Z",
        "repository": {"full_name": REPOSITORY},
        "head_repository": {"full_name": REPOSITORY},
    }


def artifact(raw_digest: str) -> dict[str, Any]:
    return {
        "id": 700,
        "name": "release-publication-outcome",
        "expired": False,
        "digest": raw_digest,
        "url": f"https://api.github.com/repos/{REPOSITORY}/actions/artifacts/700",
        "archive_download_url": (
            f"https://api.github.com/repos/{REPOSITORY}/actions/artifacts/700/zip"
        ),
        "workflow_run": {"id": 400, "head_sha": SHA},
    }


def service(factory: sessionmaker[Session], github: FakeGitHub) -> PublisherService:
    return PublisherService(factory, github, b"p" * 32, 365, lambda _candidate: None)  # type: ignore[arg-type]


def reconciler(
    factory: sessionmaker[Session],
    github: FakeGitHub,
    sync: FakeSync | None = None,
    timeout: int = 600,
) -> Reconciler:
    return Reconciler(
        factory,
        github,  # type: ignore[arg-type]
        service(factory, github),
        sync or FakeSync(factory),  # type: ignore[arg-type]
        timeout,
    )


def state(factory: sessionmaker[Session]) -> tuple[str, str | None]:
    with factory() as session:
        operation = session.get(PublicationOperation, OPERATION)
        assert operation is not None
        return operation.state, operation.error_code


def test_outcome_valid_and_canonical_binding_mutants(
    factory: sessionmaker[Session],
) -> None:
    seed_operation(factory)
    raw, raw_digest, value = outcome_bundle()
    with factory() as session:
        operation = session.get(PublicationOperation, OPERATION)
        assert operation is not None
        assert validate_publication_outcome(
            raw, raw_digest, operation=operation, run_id=400, attempt=1
        ) == value
        with pytest.raises(RuntimeFailure):
            validate_publication_outcome(
                raw, "sha256:" + "0" * 64, operation=operation, run_id=400, attempt=1
            )
    bad_metadata = canonical(
        {
            "schemaVersion": 1,
            "stage": "final",
            "kind": "release-publication-outcome",
            "repository": REPOSITORY,
            "operationId": OPERATION,
            "workflowRunId": "401",
            "workflowAttempt": 1,
            "outcomeSha256": digest(canonical(value)),
        }
    )
    mutant = zip_bytes(
        {
            "outcome.json": canonical(value),
            "outcome.json.sha256": (
                hashlib.sha256(canonical(value)).hexdigest() + "\n"
            ).encode(),
            "metadata.json": bad_metadata,
        }
    )
    with factory() as session:
        operation = session.get(PublicationOperation, OPERATION)
        assert operation is not None
        with pytest.raises(RuntimeFailure):
            validate_publication_outcome(
                mutant, digest(mutant), operation=operation, run_id=400, attempt=1
            )


@pytest.mark.parametrize("field", ["workflow-url", "tag-name", "release-url"])
def test_outcome_cross_binds_workflow_and_release_identities(
    factory: sessionmaker[Session], field: str
) -> None:
    seed_operation(factory)
    _, _, value = outcome_bundle()
    if field == "workflow-url":
        value["workflow"]["url"] = f"https://github.com/{REPOSITORY}/actions/runs/401"
    elif field == "tag-name":
        value["githubRelease"]["tagName"] = "v0.0.2"
    else:
        value["githubRelease"]["url"] = f"https://github.com/{REPOSITORY}/releases/tag/v0.0.2"
    raw, raw_digest = rebuild_outcome(value)
    with factory() as session:
        operation = session.get(PublicationOperation, OPERATION)
        assert operation is not None
        with pytest.raises(RuntimeFailure, match="PUBLICATION_OUTCOME_BINDING_INVALID"):
            validate_publication_outcome(
                raw, raw_digest, operation=operation, run_id=400, attempt=1
            )


def test_zero_run_before_and_after_timeout(factory: sessionmaker[Session]) -> None:
    seed_operation(factory, age_seconds=1)
    reconciler(factory, FakeGitHub(), timeout=600)._operation(OPERATION)
    assert state(factory) == ("REQUESTED", None)
    with factory.begin() as session:
        operation = session.get(PublicationOperation, OPERATION)
        assert operation is not None
        operation.created_at = utc_now() - timedelta(seconds=601)
    reconciler(factory, FakeGitHub(), timeout=600)._operation(OPERATION)
    assert state(factory) == ("FAILED", "WORKFLOW_DISPATCH_UNCONFIRMED")


def test_multiple_run_and_red_run_fail(factory: sessionmaker[Session]) -> None:
    seed_operation(factory)
    reconciler(factory, FakeGitHub(runs=[run(), run()]))._operation(OPERATION)
    assert state(factory) == ("FAILED", "WORKFLOW_RUN_AMBIGUOUS")

    with factory.begin() as session:
        session.delete(session.get(PublicationOperation, OPERATION))
        session.delete(session.get(CandidateSnapshot, CANDIDATE))
    seed_operation(factory)
    reconciler(factory, FakeGitHub(runs=[run(conclusion="failure")]))._operation(OPERATION)
    assert state(factory) == ("FAILED", "WORKFLOW_RUN_FAILED")


def test_nonterminal_run_advances_and_restart_does_not_dispatch(
    factory: sessionmaker[Session],
) -> None:
    seed_operation(factory)
    github = FakeGitHub(
        runs=[run(status="in_progress", conclusion=None)],
        jobs=[{"name": "publish", "status": "in_progress"}],
    )
    value = reconciler(factory, github)
    value._operation(OPERATION)
    assert state(factory) == ("PUBLISHING", None)
    value._operation(OPERATION)
    assert state(factory) == ("PUBLISHING", None)
    with factory() as session:
        operation = session.get(PublicationOperation, OPERATION)
        assert operation is not None
        assert operation.workflow_run_id == 400
        assert operation.dispatch_state == "CONFIRMED"


def test_success_requires_single_valid_outcome_and_release(
    factory: sessionmaker[Session],
) -> None:
    seed_operation(factory)
    github = FakeGitHub(runs=[run()])
    github.artifacts = [artifact(github.raw_digest)]
    sync = FakeSync(factory)
    reconciler(factory, github, sync)._operation(OPERATION)
    assert state(factory) == ("PUBLISHED", None)
    with factory() as session:
        operation = session.get(PublicationOperation, OPERATION)
        assert operation is not None
        assert operation.target_release == "v0.0.1"
        assert operation.active_slot is None


def test_missing_outcome_and_invalid_run_are_fail_closed(
    factory: sessionmaker[Session],
) -> None:
    seed_operation(factory)
    value = reconciler(factory, FakeGitHub(runs=[run()]))
    value._operation(OPERATION)
    assert state(factory) == ("FAILED", "PUBLICATION_OUTCOME_AMBIGUOUS")


def test_cycle_runs_both_syncs_and_converts_runtime_failure(
    factory: sessionmaker[Session],
) -> None:
    seed_operation(factory)
    bad_run = run()
    bad_run["head_sha"] = "bad"
    github = FakeGitHub(runs=[bad_run])
    sync = FakeSync(factory, fail=True)
    assert reconciler(factory, github, sync).cycle()
    assert sync.calls == ["reconcile-releases", "reconcile-candidates"]
    assert state(factory) == ("FAILED", "WORKFLOW_RUN_INVALID")


def test_reconcile_loop_bootstrap_periodic_failure_and_stop() -> None:
    class Cycles:
        def __init__(self) -> None:
            self.count = 0

        def cycle(self) -> bool:
            self.count += 1
            if self.count == 2:
                raise RuntimeFailure("EXPECTED")
            return True

    cycles = Cycles()
    loop = ReconcileLoop(cycles, 0)  # type: ignore[arg-type]
    loop.start()
    while cycles.count < 3:
        pass
    loop.stop()
    assert cycles.count >= 3
