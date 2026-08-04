"""Strict artifact and manifest validation without shell access."""

from __future__ import annotations

import hashlib
import io
import json
import re
import stat
import zipfile
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Any

import jsonschema

from .constants import REPOSITORY
from .errors import RuntimeFailure

ROOT = Path(__file__).resolve().parents[3]
CANDIDATE_SCHEMA = ROOT / "ops/releases/candidate-manifest.schema.json"
RELEASE_SCHEMA = ROOT / "ops/releases/global-release.schema.json"
OUTCOME_SCHEMA = ROOT / "ops/releases/release-publication-outcome.schema.json"
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
DIGEST_RE = re.compile(r"^sha256:[0-9a-f]{64}$")
SEMVER_RE = re.compile(r"^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$")


def canonical(value: Any) -> bytes:
    return (
        json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False) + "\n"
    ).encode()


def digest(data: bytes) -> str:
    return "sha256:" + hashlib.sha256(data).hexdigest()


def _schema(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_bytes())
    if not isinstance(value, dict):
        raise RuntimeFailure("SCHEMA_INVALID")
    return value


def validate_json(data: bytes, schema_path: Path, code: str) -> dict[str, Any]:
    try:
        value = json.loads(data)
        if not isinstance(value, dict) or canonical(value) != data:
            raise ValueError
        jsonschema.Draft202012Validator(
            _schema(schema_path), format_checker=jsonschema.FormatChecker()
        ).validate(value)
        return value
    except (ValueError, UnicodeDecodeError, jsonschema.ValidationError) as exc:
        raise RuntimeFailure(code) from exc


def extract_zip(
    raw: bytes,
    expected_digest: str,
    limits: dict[str, int],
    code: str,
) -> dict[str, bytes]:
    if len(raw) > 16 * 1024 * 1024 or digest(raw) != expected_digest:
        raise RuntimeFailure(code)
    result: dict[str, bytes] = {}
    try:
        with zipfile.ZipFile(io.BytesIO(raw)) as archive:
            infos = archive.infolist()
            if len(infos) != len(limits) or {item.filename for item in infos} != set(limits):
                raise RuntimeFailure(code)
            for info in infos:
                path = PurePosixPath(info.filename)
                mode = info.external_attr >> 16
                if (
                    info.is_dir()
                    or path.is_absolute()
                    or ".." in path.parts
                    or stat.S_ISLNK(mode)
                    or info.file_size > limits[info.filename]
                    or info.compress_size > limits[info.filename]
                    or (info.compress_size > 0 and info.file_size / info.compress_size > 100)
                ):
                    raise RuntimeFailure(code)
                data = archive.read(info)
                if len(data) != info.file_size:
                    raise RuntimeFailure(code)
                result[info.filename] = data
    except (zipfile.BadZipFile, EOFError, OSError, KeyError) as exc:
        raise RuntimeFailure(code) from exc
    return result


def validate_sidecar(data: bytes, sidecar: bytes, code: str) -> None:
    if sidecar != (hashlib.sha256(data).hexdigest() + "\n").encode():
        raise RuntimeFailure(code)


@dataclass(frozen=True, slots=True)
class CandidateEvidence:
    manifest: dict[str, Any]
    artifact_id: int
    artifact_digest: str


def validate_candidate_bundle(
    raw_zip: bytes,
    *,
    artifact_id: int,
    artifact_digest: str,
    candidate_id: str,
    run_id: int,
    attempt: int,
    commit_sha: str,
) -> CandidateEvidence:
    files = extract_zip(
        raw_zip,
        artifact_digest,
        {
            "candidate.json": 1024 * 1024,
            "candidate.json.sha256": 128,
            "metadata.json": 16 * 1024,
        },
        "CANDIDATE_ARTIFACT_INVALID",
    )
    manifest_data = files["candidate.json"]
    validate_sidecar(
        manifest_data, files["candidate.json.sha256"], "CANDIDATE_SIDECAR_INVALID"
    )
    manifest = validate_json(manifest_data, CANDIDATE_SCHEMA, "CANDIDATE_MANIFEST_INVALID")
    expected_metadata = {
        "schemaVersion": 1,
        "stage": "final",
        "candidateId": candidate_id,
        "repository": REPOSITORY,
        "commitSha": commit_sha,
        "workflowRunId": str(run_id),
        "workflowAttempt": attempt,
        "manifestSha256": digest(manifest_data),
    }
    try:
        metadata = json.loads(files["metadata.json"])
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        raise RuntimeFailure("CANDIDATE_METADATA_INVALID") from exc
    if not isinstance(metadata, dict) or canonical(metadata) != files["metadata.json"]:
        raise RuntimeFailure("CANDIDATE_METADATA_INVALID")
    if metadata != expected_metadata or (
        manifest.get("candidateId") != candidate_id
        or manifest.get("commitSha") != commit_sha
        or manifest.get("workflow", {}).get("runId") != str(run_id)
        or manifest.get("workflow", {}).get("attempt") != attempt
        or manifest.get("deployable") is not True
        or len(manifest.get("components", [])) != 6
    ):
        raise RuntimeFailure("CANDIDATE_BINDING_INVALID")
    return CandidateEvidence(manifest, artifact_id, artifact_digest)


def validate_outcome_bundle(
    raw_zip: bytes, artifact_digest: str, *, run_id: int, attempt: int
) -> dict[str, Any]:
    files = extract_zip(
        raw_zip,
        artifact_digest,
        {"outcome.json": 64 * 1024, "outcome.json.sha256": 128},
        "OUTCOME_ARTIFACT_INVALID",
    )
    data = files["outcome.json"]
    validate_sidecar(data, files["outcome.json.sha256"], "OUTCOME_SIDECAR_INVALID")
    value = json.loads(data)
    expected_keys = {
        "schemaVersion",
        "status",
        "repository",
        "commitSha",
        "workflowRunId",
        "workflowAttempt",
        "candidateId",
        "candidateArtifactId",
        "candidateArtifactDigest",
        "predecessorCandidateId",
    }
    if (
        not isinstance(value, dict)
        or canonical(value) != data
        or set(value) != expected_keys
        or value.get("schemaVersion") != 1
        or value.get("repository") != REPOSITORY
        or value.get("status") not in {"published", "already_published"}
        or value.get("workflowRunId") != str(run_id)
        or value.get("workflowAttempt") != attempt
        or SHA_RE.fullmatch(str(value.get("commitSha", ""))) is None
        or not isinstance(value.get("candidateId"), str)
        or re.fullmatch(r"[1-9][0-9]*", str(value.get("candidateArtifactId", ""))) is None
        or re.fullmatch(r"[0-9a-f]{64}", str(value.get("candidateArtifactDigest", ""))) is None
        or (
            value.get("status") == "already_published"
            and value.get("predecessorCandidateId") != value.get("candidateId")
        )
    ):
        raise RuntimeFailure("OUTCOME_BINDING_INVALID")
    return value


def validate_release_bundle(files: dict[str, bytes]) -> dict[str, Any]:
    if set(files) != {"release.json", "release.json.sha256", "metadata.json"}:
        raise RuntimeFailure("RELEASE_ASSETS_INVALID")
    data = files["release.json"]
    validate_sidecar(data, files["release.json.sha256"], "RELEASE_SIDECAR_INVALID")
    manifest = validate_json(data, RELEASE_SCHEMA, "RELEASE_MANIFEST_INVALID")
    metadata = json.loads(files["metadata.json"])
    expected = {
        "schemaVersion": 1,
        "stage": "final",
        "kind": "global-release",
        "release": manifest["release"],
        "repository": REPOSITORY,
        "sourceCommit": manifest["sourceCommit"],
        "publicationWorkflowRunId": manifest["publication"]["workflowRunId"],
        "publicationWorkflowAttempt": manifest["publication"]["workflowAttempt"],
        "manifestSha256": digest(data),
    }
    if canonical(metadata) != files["metadata.json"] or metadata != expected:
        raise RuntimeFailure("RELEASE_METADATA_INVALID")
    return manifest
