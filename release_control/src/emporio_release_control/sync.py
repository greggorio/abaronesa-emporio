"""Fail-closed candidate and global-release synchronization."""

from __future__ import annotations

import re
from datetime import UTC, datetime
from typing import Any

from sqlalchemy import delete, select
from sqlalchemy.orm import Session, sessionmaker

from .artifacts import (
    DIGEST_RE,
    SEMVER_RE,
    CandidateEvidence,
    digest,
    validate_candidate_bundle,
    validate_outcome_bundle,
    validate_release_bundle,
)
from .constants import REPOSITORY
from .errors import RuntimeFailure
from .github import GitHubClient
from .persistence import (
    AuditEvent,
    CandidateSnapshot,
    ReleaseSnapshot,
    SyncState,
    utc_now,
)

RELEASE_ASSETS = {
    "release.json": (2097152, "application/json"),
    "release.json.sha256": (128, "text/plain"),
    "metadata.json": (16384, "application/json"),
}


def parse_time(value: Any, code: str) -> datetime:
    if not isinstance(value, str) or not value.endswith("Z"):
        raise RuntimeFailure(code)
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise RuntimeFailure(code) from exc
    return parsed.astimezone(UTC)


def positive_id(value: Any, code: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < 1:
        raise RuntimeFailure(code)
    return int(value)


class Synchronizer:
    def __init__(self, factory: sessionmaker[Session], github: GitHubClient) -> None:
        self.factory = factory
        self.github = github

    @staticmethod
    def _run(value: dict[str, Any], name: str) -> tuple[int, int, str, datetime]:
        code = "WORKFLOW_RUN_INVALID"
        run_id = positive_id(value.get("id"), code)
        attempt = positive_id(value.get("run_attempt"), code)
        sha = value.get("head_sha")
        repository = value.get("repository")
        head_repository = value.get("head_repository")
        if (
            value.get("name") != name
            or value.get("event") != "workflow_run"
            or value.get("status") != "completed"
            or value.get("conclusion") != "success"
            or value.get("head_branch") != "main"
            or not isinstance(sha, str)
            or re.fullmatch(r"[0-9a-f]{40}", sha) is None
            or not isinstance(repository, dict)
            or repository.get("full_name") != REPOSITORY
            or not isinstance(head_repository, dict)
            or head_repository.get("full_name") != REPOSITORY
        ):
            raise RuntimeFailure(code)
        return run_id, attempt, sha, parse_time(value.get("created_at"), code)

    @staticmethod
    def _artifact(
        value: dict[str, Any], *, name: str, run_id: int, sha: str
    ) -> tuple[int, str]:
        code = "ARTIFACT_BINDING_INVALID"
        artifact_id = positive_id(value.get("id"), code)
        digest = value.get("digest")
        workflow_run = value.get("workflow_run")
        if (
            value.get("name") != name
            or value.get("expired") is not False
            or not isinstance(digest, str)
            or DIGEST_RE.fullmatch(digest) is None
            or not isinstance(workflow_run, dict)
            or workflow_run.get("id") != run_id
            or workflow_run.get("head_sha") != sha
            or value.get("url")
            != f"https://api.github.com/repos/{REPOSITORY}/actions/artifacts/{artifact_id}"
            or value.get("archive_download_url")
            != f"https://api.github.com/repos/{REPOSITORY}/actions/artifacts/{artifact_id}/zip"
        ):
            raise RuntimeFailure(code)
        return artifact_id, digest

    def _download_candidate(
        self,
        artifact: dict[str, Any],
        *,
        candidate_id: str,
        run_id: int,
        attempt: int,
        sha: str,
    ) -> CandidateEvidence:
        artifact_id, artifact_digest = self._artifact(
            artifact, name="candidate-manifest", run_id=run_id, sha=sha
        )
        raw = self.github.get_bytes(
            f"/repos/{REPOSITORY}/actions/artifacts/{artifact_id}/zip"
        )
        return validate_candidate_bundle(
            raw,
            artifact_id=artifact_id,
            artifact_digest=artifact_digest,
            candidate_id=candidate_id,
            run_id=run_id,
            attempt=attempt,
            commit_sha=sha,
        )

    def _candidate_from_run(self, run: dict[str, Any]) -> tuple[CandidateEvidence, datetime]:
        run_id, attempt, sha, created_at = self._run(run, "Publish Candidate")
        artifacts = self.github.list_pages(
            f"/repos/{REPOSITORY}/actions/runs/{run_id}/artifacts", "artifacts"
        )
        outcomes = [item for item in artifacts if item.get("name") == "candidate-outcome"]
        if len(outcomes) != 1:
            raise RuntimeFailure("OUTCOME_ARTIFACT_INVALID")
        outcome_id, outcome_digest = self._artifact(
            outcomes[0], name="candidate-outcome", run_id=run_id, sha=sha
        )
        outcome = validate_outcome_bundle(
            self.github.get_bytes(
                f"/repos/{REPOSITORY}/actions/artifacts/{outcome_id}/zip"
            ),
            outcome_digest,
            run_id=run_id,
            attempt=attempt,
        )
        candidate_id = str(outcome["candidateId"])
        artifact_id = int(outcome["candidateArtifactId"])
        artifact_digest = "sha256:" + str(outcome["candidateArtifactDigest"])
        if outcome["status"] == "published":
            matches = [item for item in artifacts if item.get("name") == "candidate-manifest"]
            if (
                len(matches) != 1
                or matches[0].get("id") != artifact_id
                or matches[0].get("digest") != artifact_digest
            ):
                raise RuntimeFailure("CANDIDATE_ARTIFACT_INVALID")
            artifact = matches[0]
            manifest_run, manifest_attempt, manifest_sha = run_id, attempt, sha
        else:
            artifact = self.github.get_json(
                f"/repos/{REPOSITORY}/actions/artifacts/{artifact_id}"
            )
            workflow_run = artifact.get("workflow_run")
            if not isinstance(workflow_run, dict):
                raise RuntimeFailure("CANDIDATE_ARTIFACT_INVALID")
            manifest_run = positive_id(workflow_run.get("id"), "CANDIDATE_ARTIFACT_INVALID")
            inherited_run = self.github.get_json(
                f"/repos/{REPOSITORY}/actions/runs/{manifest_run}"
            )
            inherited_id, manifest_attempt, inherited_sha, _ = self._run(
                inherited_run, "Publish Candidate"
            )
            raw_manifest_sha = workflow_run.get("head_sha")
            if (
                not isinstance(raw_manifest_sha, str)
                or inherited_id != manifest_run
                or inherited_sha != raw_manifest_sha
            ):
                raise RuntimeFailure("CANDIDATE_ARTIFACT_INVALID")
            manifest_sha = raw_manifest_sha
        if artifact.get("digest") != artifact_digest:
            raise RuntimeFailure("CANDIDATE_ARTIFACT_INVALID")
        candidate = self._download_candidate(
            artifact,
            candidate_id=candidate_id,
            run_id=manifest_run,
            attempt=manifest_attempt,
            sha=manifest_sha,
        )
        if (
            outcome["status"] == "published"
            and outcome["predecessorCandidateId"]
            != candidate.manifest["predecessor"]["candidateId"]
        ):
            raise RuntimeFailure("CANDIDATE_PREDECESSOR_INVALID")
        return candidate, created_at

    def sync_candidates(self, trace_id: str = "sync-candidates") -> None:
        try:
            runs = self.github.list_pages(
                f"/repos/{REPOSITORY}/actions/workflows/publish-candidate.yml/runs"
                "?branch=main&status=success",
                "workflow_runs",
            )
            evidence = [self._candidate_from_run(run) for run in runs]
            with self.factory.begin() as session:
                published = set(session.scalars(select(ReleaseSnapshot.candidate_id)))
                observed: set[str] = set()
                for candidate, created_at in evidence:
                    candidate_id = str(candidate.manifest["candidateId"])
                    observed.add(candidate_id)
                    session.merge(
                        CandidateSnapshot(
                            candidate_id=candidate_id,
                            source_commit=str(candidate.manifest["commitSha"]),
                            eligibility=(
                                "READY"
                                if candidate_id not in published
                                and candidate.manifest["deployable"] is True
                                else "NOT_ELIGIBLE"
                            ),
                            ci_status="PASSED",
                            manifest_status="VALID",
                            created_at=created_at,
                            manifest=candidate.manifest,
                            artifact_id=candidate.artifact_id,
                            artifact_digest=candidate.artifact_digest,
                            synchronized_at=utc_now(),
                        )
                    )
                for stale in session.scalars(select(CandidateSnapshot)).all():
                    if stale.candidate_id not in observed:
                        stale.eligibility = "NOT_ELIGIBLE"
                self._green(session, "candidates")
        except Exception as exc:
            self._drift("candidates", getattr(exc, "code", "CANDIDATE_SYNC_FAILED"), trace_id)
            raise

    @staticmethod
    def _release_identity(value: dict[str, Any]) -> tuple[int, str]:
        release_id = positive_id(value.get("id"), "RELEASE_INVALID")
        tag = value.get("tag_name")
        if (
            not isinstance(tag, str)
            or SEMVER_RE.fullmatch(tag) is None
            or value.get("name") != tag
            or value.get("draft") is not False
            or value.get("prerelease") is not False
            or value.get("url")
            != f"https://api.github.com/repos/{REPOSITORY}/releases/{release_id}"
        ):
            raise RuntimeFailure("RELEASE_INVALID")
        return release_id, tag

    def sync_releases(self, trace_id: str = "sync-releases") -> None:
        try:
            releases = self.github.list_pages(f"/repos/{REPOSITORY}/releases", None)
            refs = self.github.list_pages(
                f"/repos/{REPOSITORY}/git/matching-refs/tags/v", None
            )
            tags: dict[str, str] = {}
            for ref in refs:
                raw_ref = ref.get("ref")
                obj = ref.get("object")
                if not isinstance(raw_ref, str) or not raw_ref.startswith("refs/tags/"):
                    raise RuntimeFailure("RELEASE_REF_INVALID")
                tag = raw_ref.removeprefix("refs/tags/")
                sha = obj.get("sha") if isinstance(obj, dict) else None
                if (
                    SEMVER_RE.fullmatch(tag) is None
                    or not isinstance(sha, str)
                    or re.fullmatch(r"[0-9a-f]{40}", sha) is None
                    or not isinstance(obj, dict)
                    or obj.get("type") != "commit"
                    or ref.get("url")
                    != f"https://api.github.com/repos/{REPOSITORY}/git/refs/tags/{tag}"
                    or obj.get("url")
                    != f"https://api.github.com/repos/{REPOSITORY}/git/commits/{sha}"
                    or tag in tags
                ):
                    raise RuntimeFailure("RELEASE_REF_INVALID")
                tags[tag] = sha
            manifests: list[dict[str, Any]] = []
            for release in releases:
                _release_id, tag = self._release_identity(release)
                assets = release.get("assets")
                if not isinstance(assets, list) or len(assets) != 3:
                    raise RuntimeFailure("RELEASE_ASSETS_INVALID")
                validated_assets: dict[str, tuple[int, int, str]] = {}
                asset_ids: set[int] = set()
                for asset in assets:
                    asset_id = positive_id(asset.get("id"), "RELEASE_ASSETS_INVALID")
                    name = asset.get("name")
                    size = asset.get("size")
                    expected = RELEASE_ASSETS.get(name) if isinstance(name, str) else None
                    if (
                        expected is None
                        or name in validated_assets
                        or asset_id in asset_ids
                        or asset.get("state") != "uploaded"
                        or asset.get("url")
                        != f"https://api.github.com/repos/{REPOSITORY}/releases/assets/{asset_id}"
                        or isinstance(size, bool)
                        or not isinstance(size, int)
                        or not 1 <= size <= expected[0]
                        or asset.get("content_type") != expected[1]
                        or not isinstance(asset.get("digest"), str)
                        or DIGEST_RE.fullmatch(asset["digest"]) is None
                    ):
                        raise RuntimeFailure("RELEASE_ASSETS_INVALID")
                    validated_assets[name] = (asset_id, size, asset["digest"])
                    asset_ids.add(asset_id)
                if set(validated_assets) != set(RELEASE_ASSETS):
                    raise RuntimeFailure("RELEASE_ASSETS_INVALID")
                files: dict[str, bytes] = {}
                for name in RELEASE_ASSETS:
                    asset_id, size, asset_digest = validated_assets[name]
                    raw = self.github.get_bytes(
                        f"/repos/{REPOSITORY}/releases/assets/{asset_id}"
                    )
                    if len(raw) != size or digest(raw) != asset_digest:
                        raise RuntimeFailure("RELEASE_ASSETS_INVALID")
                    files[name] = raw
                manifest = validate_release_bundle(files)
                if (
                    manifest["release"] != tag
                    or tags.get(tag) != manifest["sourceCommit"]
                ):
                    raise RuntimeFailure("RELEASE_BINDING_INVALID")
                manifests.append(manifest)
            manifests.sort(key=lambda item: tuple(int(v) for v in item["release"][1:].split(".")))
            previous: str | None = None
            for manifest in manifests:
                if manifest["previousRelease"] != previous:
                    raise RuntimeFailure("RELEASE_CHAIN_INVALID")
                previous = str(manifest["release"])
            if set(tags) != {str(item["release"]) for item in manifests}:
                raise RuntimeFailure("RELEASE_REF_SET_INVALID")
            with self.factory.begin() as session:
                session.execute(delete(ReleaseSnapshot))
                for manifest in manifests:
                    session.merge(
                        ReleaseSnapshot(
                            release=str(manifest["release"]),
                            source_commit=str(manifest["sourceCommit"]),
                            state="PUBLISHED",
                            published_at=parse_time(
                                manifest["publishedAt"], "RELEASE_MANIFEST_INVALID"
                            ),
                            candidate_id=str(manifest["candidate"]["candidateId"]),
                            manifest=manifest,
                            synchronized_at=utc_now(),
                        )
                    )
                self._green(session, "releases")
        except Exception as exc:
            self._drift("releases", getattr(exc, "code", "RELEASE_SYNC_FAILED"), trace_id)
            raise

    @staticmethod
    def _green(session: Session, domain: str) -> None:
        state = session.get(SyncState, domain) or SyncState(domain=domain)
        state.last_success_at = utc_now()
        state.drift = False
        state.error_code = None
        state.updated_at = utc_now()
        session.add(state)

    def _drift(self, domain: str, code: str, trace_id: str) -> None:
        with self.factory.begin() as session:
            state = session.get(SyncState, domain) or SyncState(domain=domain)
            state.drift = True
            state.error_code = str(code)[:100]
            state.updated_at = utc_now()
            session.add(state)
            session.add(
                AuditEvent(
                    trace_id=trace_id,
                    actor_sub=None,
                    action=f"sync.{domain}",
                    result="invalid",
                    operation_id=None,
                    metadata_json={"code": str(code)[:100]},
                )
            )

    def revalidate_candidate(self, candidate_id: str) -> None:
        self.sync_candidates(trace_id="candidate-revalidation")
        with self.factory() as session:
            candidate = session.get(CandidateSnapshot, candidate_id)
            if candidate is None or candidate.eligibility != "READY":
                raise RuntimeFailure("NOT_FOUND", 404, "Not found")

    def revalidate_release(self, release: str) -> None:
        """Rebuild the verified release chain and require the requested member."""
        self.sync_releases(trace_id="release-revalidation")
        with self.factory() as session:
            if session.get(ReleaseSnapshot, release) is None:
                raise RuntimeFailure("NOT_FOUND", 404, "Not found")
