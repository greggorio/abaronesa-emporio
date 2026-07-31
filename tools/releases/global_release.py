#!/usr/bin/env python3
"""Offline global-release contract, SemVer resolver and Flyway inventory."""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
import re
import shutil
import sys
import tempfile
from datetime import datetime
from pathlib import Path
from typing import Any, Callable

import jsonschema

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/candidates"))

import artifact_io
import candidate_manifest
import catalog

REPOSITORY = "greggorio/abaronesa-emporio"
RELEASE_SCHEMA = ROOT / "ops/releases/global-release.schema.json"
REQUEST_SCHEMA = ROOT / "ops/releases/release-request.schema.json"
RELEASE_EXAMPLE = ROOT / "ops/releases/examples/global-release.example.json"
REQUEST_EXAMPLE = ROOT / "ops/releases/examples/release-request.example.json"
CANDIDATE_EXAMPLE = ROOT / "ops/releases/examples/candidate-manifest.example.json"
MAX_PART = 2_147_483_647
SEMVER_RE = re.compile(r"v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)")
MIGRATION_RE = re.compile(r"V([0-9]+(?:[._][0-9]+)*)__([^/]+)\.sql")
POSITIVE_RE = re.compile(r"[1-9][0-9]*")
DIGEST_RE = re.compile(r"sha256:[0-9a-f]{64}")
TIME_RE = re.compile(r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z")
DATABASES = (
    ("erp", "backend", "backend/src/main/resources/db/migration"),
    ("website", "website_back", "website_back/src/main/resources/db/migration"),
)
RELEASE_KEYS = {
    "schemaVersion", "kind", "deployable", "release", "repository",
    "sourceCommit", "publishedAt", "description", "changelog", "candidate",
    "publication", "previousRelease", "catalog", "components", "databases",
}
METADATA_KEYS = {
    "schemaVersion", "stage", "kind", "release", "repository", "sourceCommit",
    "publicationWorkflowRunId", "publicationWorkflowAttempt", "manifestSha256",
}
BUNDLE_FILES = {"release.json", "release.json.sha256", "metadata.json"}


class GlobalReleaseError(ValueError):
    pass


def read_json(path: Path) -> dict[str, Any]:
    value = json.loads(Path(path).read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise GlobalReleaseError("JSON root must be object")
    return value


def canonical(value: Any) -> bytes:
    return artifact_io.canonical(value)


def _schema_errors(value: Any, schema_path: Path) -> list[str]:
    schema = read_json(schema_path)
    validator = jsonschema.Draft202012Validator(
        schema, format_checker=jsonschema.FormatChecker()
    )
    return [
        "schema:" + "/".join(map(str, error.path)) + ":" + error.message
        for error in sorted(validator.iter_errors(value), key=lambda item: list(item.path))
    ]


def _clean_text(value: Any, multiline: bool, maximum: int) -> bool:
    if not isinstance(value, str) or not value or len(value) > maximum:
        return False
    if value != value.strip() or not value.strip():
        return False
    for character in value:
        code = ord(character)
        if code == 127:
            return False
        if code < 32 and not (multiline and character in "\n\t"):
            return False
    return True


def _utc(value: Any) -> bool:
    if not isinstance(value, str) or not TIME_RE.fullmatch(value):
        return False
    try:
        datetime.fromisoformat(value[:-1] + "+00:00")
        return True
    except ValueError:
        return False


def validate_request(value: Any, candidate: dict[str, Any] | None = None) -> list[str]:
    errors = _schema_errors(value, REQUEST_SCHEMA)
    if errors:
        return errors
    if not _clean_text(value["description"], False, 500):
        errors.append("REQUEST_DESCRIPTION")
    if not _clean_text(value["changelog"], True, 10000):
        errors.append("REQUEST_CHANGELOG")
    if candidate is not None and value["candidateId"] != candidate.get("candidateId"):
        errors.append("REQUEST_CANDIDATE")
    if candidate is not None and not re.fullmatch(
        r"candidate-[a-z0-9._-]+", value["candidateId"]
    ):
        errors.append("REQUEST_CANDIDATE_PATTERN")
    return errors


def parse_semver(value: str) -> tuple[int, int, int]:
    match = SEMVER_RE.fullmatch(str(value))
    if not match:
        raise GlobalReleaseError("invalid SemVer")
    parts = tuple(int(part) for part in match.groups())
    if any(part > MAX_PART for part in parts):
        raise GlobalReleaseError("SemVer component overflow")
    return parts


def format_semver(parts: tuple[int, int, int]) -> str:
    if any(part < 0 or part > MAX_PART for part in parts):
        raise GlobalReleaseError("SemVer component overflow")
    return "v" + ".".join(str(part) for part in parts)


def bump_version(previous: str | None, bump: str) -> str:
    major, minor, patch = parse_semver(previous or "v0.0.0")
    if bump == "MAJOR":
        if major == MAX_PART:
            raise GlobalReleaseError("SemVer MAJOR overflow")
        return format_semver((major + 1, 0, 0))
    if bump == "MINOR":
        if minor == MAX_PART:
            raise GlobalReleaseError("SemVer MINOR overflow")
        return format_semver((major, minor + 1, 0))
    if bump == "PATCH":
        if patch == MAX_PART:
            raise GlobalReleaseError("SemVer PATCH overflow")
        return format_semver((major, minor, patch + 1))
    raise GlobalReleaseError("invalid version bump")


def _flyway_key(version: str) -> tuple[int, ...]:
    parts = [int(part) for part in re.split(r"[._]", version)]
    while len(parts) > 1 and parts[-1] == 0:
        parts.pop()
    return tuple(parts)


def inventory_database(
    workspace: Path, database_id: str, owner: str, location: str
) -> dict[str, Any]:
    root = Path(workspace) / location
    if not root.is_dir() or root.is_symlink():
        raise GlobalReleaseError("migration root missing or invalid")
    migrations: list[tuple[tuple[int, ...], dict[str, str]]] = []
    versions: set[tuple[int, ...]] = set()
    for entry in root.iterdir():
        if entry.name == ".gitkeep" and entry.is_file() and not entry.is_symlink():
            continue
        if entry.is_symlink() or not entry.is_file():
            raise GlobalReleaseError("unexpected migration entry")
        match = MIGRATION_RE.fullmatch(entry.name)
        if not match:
            raise GlobalReleaseError("invalid migration filename")
        version = match.group(1)
        key = _flyway_key(version)
        if key in versions:
            raise GlobalReleaseError("duplicate normalized Flyway version")
        versions.add(key)
        relative = entry.relative_to(workspace).as_posix()
        item = {
            "version": version,
            "path": relative,
            "sha256": artifact_io.digest(entry.read_bytes()),
        }
        migrations.append((key, item))
    if not migrations:
        raise GlobalReleaseError("empty migration set")
    migrations.sort(key=lambda pair: pair[0])
    items = [item for _, item in migrations]
    return {
        "id": database_id,
        "ownerComponent": owner,
        "engine": "flyway",
        "location": location,
        "latestVersion": items[-1]["version"],
        "migrationSetSha256": artifact_io.digest(canonical(items)),
        "backupPolicy": "required_on_change",
        "rollbackPolicy": "restore_required",
        "migrations": items,
    }


def inventories(workspace: Path = ROOT) -> list[dict[str, Any]]:
    return [
        inventory_database(workspace, database_id, owner, location)
        for database_id, owner, location in DATABASES
    ]


def _validate_database(value: dict[str, Any], contract: tuple[str, str, str]) -> list[str]:
    errors: list[str] = []
    database_id, owner, location = contract
    if (
        value.get("id") != database_id
        or value.get("ownerComponent") != owner
        or value.get("engine") != "flyway"
        or value.get("location") != location
    ):
        errors.append("DATABASE_IDENTITY")
    if value.get("backupPolicy") != "required_on_change":
        errors.append("DATABASE_BACKUP_POLICY")
    if value.get("rollbackPolicy") != "restore_required":
        errors.append("DATABASE_ROLLBACK_POLICY")
    migrations = value.get("migrations")
    if not isinstance(migrations, list) or not migrations:
        return errors + ["DATABASE_MIGRATIONS"]
    keys: list[tuple[int, ...]] = []
    for item in migrations:
        if not isinstance(item, dict) or set(item) != {"version", "path", "sha256"}:
            errors.append("MIGRATION_SHAPE")
            continue
        match = MIGRATION_RE.fullmatch(Path(str(item.get("path", ""))).name)
        expected_path = location + "/" + Path(str(item.get("path", ""))).name
        if (
            not match
            or match.group(1) != item.get("version")
            or item.get("path") != expected_path
            or not DIGEST_RE.fullmatch(str(item.get("sha256", "")))
        ):
            errors.append("MIGRATION_BINDING")
            continue
        keys.append(_flyway_key(item["version"]))
    if len(keys) != len(set(keys)) or keys != sorted(keys):
        errors.append("MIGRATION_ORDER_OR_DUPLICATE")
    if migrations and value.get("latestVersion") != migrations[-1].get("version"):
        errors.append("DATABASE_LATEST")
    if value.get("migrationSetSha256") != artifact_io.digest(canonical(migrations)):
        errors.append("DATABASE_SET_DIGEST")
    return errors


def _validate_components(components: Any) -> list[str]:
    errors: list[str] = []
    if not isinstance(components, list) or [item.get("id") for item in components if isinstance(item, dict)] != catalog.CANONICAL:
        return ["COMPONENT_ORDER"]
    contracts = catalog.load_yaml()["components"]
    for item in components:
        component_id = item["id"]
        repository = contracts[component_id]["image_repository"]
        digest = item.get("digest")
        immutable = item.get("immutableRef")
        if item.get("imageRepository") != repository:
            errors.append(component_id + ":REPOSITORY")
        if not DIGEST_RE.fullmatch(str(digest)) or immutable != repository + "@" + str(digest):
            errors.append(component_id + ":IMMUTABLE")
        commit = item.get("commitSha")
        run = item.get("workflowRunId")
        attempt = item.get("workflowAttempt")
        if item.get("tag") != f"{repository}:sha-{commit}":
            errors.append(component_id + ":TAG")
        expected_labels = {
            "org.opencontainers.image.source": "https://github.com/" + REPOSITORY,
            "org.opencontainers.image.revision": commit,
            "org.opencontainers.image.version": f"candidate-{commit}-{run}-{attempt}",
            "org.opencontainers.image.created": item.get("builtAt"),
        }
        if item.get("labels") != expected_labels:
            errors.append(component_id + ":LABELS")
        provenance = item.get("provenance", {})
        attestation_id = str(provenance.get("attestationId", ""))
        if (
            provenance.get("verifiedSubject") != immutable
            or provenance.get("attestationUrl")
            != f"https://github.com/{REPOSITORY}/attestations/{attestation_id}"
        ):
            errors.append(component_id + ":PROVENANCE")
        if item.get("state") == "built" and item.get("originCandidateId") is not None:
            errors.append(component_id + ":BUILT_ORIGIN")
        if item.get("state") == "inherited" and not re.fullmatch(
            r"candidate-[a-z0-9._-]+", str(item.get("originCandidateId", ""))
        ):
            errors.append(component_id + ":INHERITED_ORIGIN")
    return errors


def validate_release(
    value: Any,
    *,
    candidate: dict[str, Any] | None = None,
    request: dict[str, Any] | None = None,
    expected_databases: list[dict[str, Any]] | None = None,
    expected_previous: str | None | object = ...,
    expected_version: str | None = None,
) -> list[str]:
    errors = _schema_errors(value, RELEASE_SCHEMA)
    if errors:
        return errors
    if set(value) != RELEASE_KEYS:
        errors.append("RELEASE_SHAPE")
    try:
        current_semver = parse_semver(value["release"])
        if value["previousRelease"] is not None:
            previous_semver = parse_semver(value["previousRelease"])
            if previous_semver >= current_semver:
                errors.append("PREVIOUS_RELEASE_ORDER")
    except GlobalReleaseError:
        errors.append("RELEASE_SEMVER")
    if not _utc(value["publishedAt"]):
        errors.append("RELEASE_TIME")
    if not _clean_text(value["description"], False, 500):
        errors.append("RELEASE_DESCRIPTION")
    if not _clean_text(value["changelog"], True, 10000):
        errors.append("RELEASE_CHANGELOG")
    errors.extend(_validate_components(value["components"]))
    databases = value["databases"]
    if [item.get("id") for item in databases] != ["erp", "website"]:
        errors.append("DATABASE_ORDER")
    else:
        for database, contract in zip(databases, DATABASES):
            errors.extend(_validate_database(database, contract))
    if expected_databases is not None and databases != expected_databases:
        errors.append("DATABASE_INVENTORY")
    candidate_binding = value["candidate"]
    publication = value["publication"]
    if publication["workflowRunId"] == candidate_binding["workflowRunId"]:
        errors.append("PUBLICATION_CANDIDATE_RUN")
    if not _clean_text(publication["actor"], False, 100):
        errors.append("PUBLICATION_ACTOR")
    if candidate is not None:
        candidate_errors = candidate_manifest.validate_manifest(candidate)
        if candidate_errors:
            errors.append("CANDIDATE_INVALID")
        expected_binding = {
            "candidateId": candidate.get("candidateId"),
            "manifestSha256": artifact_io.digest(canonical(candidate)),
            "artifactId": candidate_binding.get("artifactId"),
            "artifactDigest": candidate_binding.get("artifactDigest"),
            "workflowRunId": candidate.get("workflow", {}).get("runId"),
            "workflowAttempt": candidate.get("workflow", {}).get("attempt"),
        }
        if candidate_binding != expected_binding:
            errors.append("CANDIDATE_BINDING")
        if (
            value["sourceCommit"] != candidate.get("commitSha")
            or value["catalog"] != candidate.get("catalog")
            or value["components"] != candidate.get("components")
        ):
            errors.append("CANDIDATE_BOM")
    if request is not None:
        if validate_request(request, candidate):
            errors.append("REQUEST_INVALID")
        if (
            value["description"] != request.get("description")
            or value["changelog"] != request.get("changelog")
        ):
            errors.append("REQUEST_TEXT_BINDING")
    if expected_previous is not ... and value["previousRelease"] != expected_previous:
        errors.append("PREVIOUS_RELEASE")
    if expected_version is not None and value["release"] != expected_version:
        errors.append("RELEASE_VERSION")
    return errors


def metadata_for(release: dict[str, Any], data: bytes) -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "stage": "final",
        "kind": "global-release",
        "release": release["release"],
        "repository": release["repository"],
        "sourceCommit": release["sourceCommit"],
        "publicationWorkflowRunId": release["publication"]["workflowRunId"],
        "publicationWorkflowAttempt": release["publication"]["workflowAttempt"],
        "manifestSha256": artifact_io.digest(data),
    }


def load_existing_release(path: Path) -> dict[str, Any]:
    path = Path(path)
    sidecar = path.with_name(path.name + ".sha256")
    metadata_path = path.with_name("metadata.json")
    if not path.is_file() or not sidecar.is_file() or not metadata_path.is_file():
        raise GlobalReleaseError("existing release bundle incomplete")
    raw = path.read_bytes()
    if not artifact_io.verify_pair(path):
        raise GlobalReleaseError("existing release sidecar")
    value = json.loads(raw.decode("utf-8"))
    if raw != canonical(value):
        raise GlobalReleaseError("existing release is not canonical JSON")
    errors = validate_release(value)
    if errors:
        raise GlobalReleaseError("existing release invalid:" + ",".join(errors))
    metadata_raw = metadata_path.read_bytes()
    metadata = json.loads(metadata_raw.decode("utf-8"))
    if not isinstance(metadata, dict) or metadata_raw != canonical(metadata):
        raise GlobalReleaseError("existing release metadata canonicality")
    if set(metadata) != METADATA_KEYS or metadata != metadata_for(value, raw):
        raise GlobalReleaseError("existing release metadata")
    return value


def _ordered_existing(releases: list[dict[str, Any]], candidate_id: str) -> list[dict[str, Any]]:
    versions: dict[tuple[int, int, int], dict[str, Any]] = {}
    candidates: set[str] = set()
    for release in releases:
        errors = validate_release(release)
        if errors:
            raise GlobalReleaseError("existing release invalid:" + ",".join(errors))
        parsed = parse_semver(release["release"])
        if parsed in versions:
            raise GlobalReleaseError("duplicate existing release version")
        versions[parsed] = release
        prior_candidate = release["candidate"]["candidateId"]
        if prior_candidate in candidates:
            raise GlobalReleaseError("candidate used by duplicate existing releases")
        candidates.add(prior_candidate)
        if prior_candidate == candidate_id:
            raise GlobalReleaseError("candidate already released")
    return [versions[key] for key in sorted(versions)]


def validate_release_chain(releases: list[dict[str, Any]]) -> list[str]:
    """Validate immediate historical links and unique publication identities."""
    errors: list[str] = []
    ordered: list[tuple[tuple[int, int, int], dict[str, Any]]] = []
    candidates: set[str] = set()
    publication_runs: set[str] = set()
    for release in releases:
        current = validate_release(release)
        if current:
            errors.extend("CHAIN_" + item for item in current)
            continue
        try:
            version = parse_semver(release["release"])
        except GlobalReleaseError:
            errors.append("CHAIN_SEMVER")
            continue
        candidate_id = release["candidate"]["candidateId"]
        run_id = release["publication"]["workflowRunId"]
        if candidate_id in candidates:
            errors.append("CHAIN_CANDIDATE_DUPLICATE")
        if run_id in publication_runs:
            errors.append("CHAIN_PUBLICATION_RUN_DUPLICATE")
        candidates.add(candidate_id)
        publication_runs.add(run_id)
        ordered.append((version, release))
    ordered.sort(key=lambda pair: pair[0])
    previous = None
    for _, release in ordered:
        if release["previousRelease"] != previous:
            errors.append("CHAIN_PREVIOUS_RELEASE")
        previous = release["release"]
    return errors


def build_release(
    candidate: dict[str, Any],
    request: dict[str, Any],
    existing: list[dict[str, Any]],
    *,
    candidate_artifact_id: str,
    candidate_artifact_digest: str,
    published_at: str,
    workflow_run_id: str,
    workflow_attempt: int,
    actor: str,
    actor_id: str,
    workspace: Path = ROOT,
) -> dict[str, Any]:
    candidate_errors = candidate_manifest.validate_manifest(candidate)
    if candidate_errors:
        raise GlobalReleaseError("candidate invalid:" + ",".join(candidate_errors))
    request_errors = validate_request(request, candidate)
    if request_errors:
        raise GlobalReleaseError("request invalid:" + ",".join(request_errors))
    if not POSITIVE_RE.fullmatch(str(candidate_artifact_id)) or not DIGEST_RE.fullmatch(str(candidate_artifact_digest)):
        raise GlobalReleaseError("candidate artifact binding")
    if not POSITIVE_RE.fullmatch(str(workflow_run_id)) or not isinstance(workflow_attempt, int) or workflow_attempt < 1:
        raise GlobalReleaseError("publication run binding")
    if not POSITIVE_RE.fullmatch(str(actor_id)) or not _clean_text(actor, False, 100):
        raise GlobalReleaseError("publication actor binding")
    if str(workflow_run_id) == candidate["workflow"]["runId"]:
        raise GlobalReleaseError("publication run equals candidate run")
    if not _utc(published_at):
        raise GlobalReleaseError("publication timestamp")
    ordered = _ordered_existing(existing, candidate["candidateId"])
    previous = ordered[-1]["release"] if ordered else None
    version = bump_version(previous, request["versionBump"])
    database_values = inventories(workspace)
    candidate_data = canonical(candidate)
    release = {
        "schemaVersion": 1,
        "kind": "global-release",
        "deployable": True,
        "release": version,
        "repository": REPOSITORY,
        "sourceCommit": candidate["commitSha"],
        "publishedAt": published_at,
        "description": request["description"],
        "changelog": request["changelog"],
        "candidate": {
            "candidateId": candidate["candidateId"],
            "manifestSha256": artifact_io.digest(candidate_data),
            "artifactId": str(candidate_artifact_id),
            "artifactDigest": str(candidate_artifact_digest),
            "workflowRunId": candidate["workflow"]["runId"],
            "workflowAttempt": candidate["workflow"]["attempt"],
        },
        "publication": {
            "workflowRunId": str(workflow_run_id),
            "workflowAttempt": workflow_attempt,
            "actor": actor,
            "actorId": str(actor_id),
            "event": "workflow_dispatch",
        },
        "previousRelease": previous,
        "catalog": copy.deepcopy(candidate["catalog"]),
        "components": copy.deepcopy(candidate["components"]),
        "databases": database_values,
    }
    errors = validate_release(
        release,
        candidate=candidate,
        request=request,
        expected_databases=database_values,
        expected_previous=previous,
        expected_version=version,
    )
    if errors:
        raise GlobalReleaseError("generated release invalid:" + ",".join(errors))
    return release


def _restore_bundle(directory: Path, previous: dict[str, bytes]) -> None:
    for name, data in previous.items():
        staged = artifact_io._stage(directory, "restore-" + name, data)
        os.replace(staged, directory / name)
    descriptor = os.open(directory, os.O_RDONLY | getattr(os, "O_DIRECTORY", 0))
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def write_release_bundle(
    directory: Path,
    release: dict[str, Any],
    *,
    overwrite: bool = False,
    replacer: Callable[[Path, Path], None] = os.replace,
) -> None:
    directory = Path(directory)
    data = canonical(release)
    files = {
        "release.json": data,
        "release.json.sha256": artifact_io.sidecar(data),
        "metadata.json": canonical(metadata_for(release, data)),
    }
    if not overwrite:
        if directory.exists() and any(directory.iterdir()):
            raise GlobalReleaseError("release bundle destination is not empty")
        artifact_io.atomic_bundle(directory, files)
        return
    if not directory.is_dir() or {path.name for path in directory.iterdir()} != BUNDLE_FILES:
        raise GlobalReleaseError("overwrite requires complete prior bundle")
    previous = {name: (directory / name).read_bytes() for name in BUNDLE_FILES}
    staging = Path(tempfile.mkdtemp(prefix=".global-release-", dir=directory.parent))
    committed = False
    try:
        artifact_io.atomic_bundle(staging, files)
        for name in ("release.json", "release.json.sha256", "metadata.json"):
            replacer(staging / name, directory / name)
        descriptor = os.open(directory, os.O_RDONLY | getattr(os, "O_DIRECTORY", 0))
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)
        committed = True
        if any((directory / name).read_bytes() != payload for name, payload in files.items()):
            raise GlobalReleaseError("bundle post-write verification")
    except Exception:
        _restore_bundle(directory, previous)
        raise
    finally:
        shutil.rmtree(staging, ignore_errors=True)
    if not committed:
        raise GlobalReleaseError("bundle overwrite failed")


def generate_to_directory(args: argparse.Namespace) -> dict[str, Any]:
    candidate = read_json(args.candidate)
    request = read_json(args.request)
    existing = [load_existing_release(path) for path in args.existing_release]
    release = build_release(
        candidate,
        request,
        existing,
        candidate_artifact_id=args.candidate_artifact_id,
        candidate_artifact_digest=args.candidate_artifact_digest,
        published_at=args.published_at,
        workflow_run_id=args.workflow_run_id,
        workflow_attempt=args.workflow_attempt,
        actor=args.actor,
        actor_id=args.actor_id,
    )
    write_release_bundle(args.output, release)
    return release


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    validate_parser = subparsers.add_parser("validate")
    validate_parser.add_argument("--manifest", type=Path, required=True)
    generate_parser = subparsers.add_parser("generate")
    generate_parser.add_argument("--candidate", type=Path, required=True)
    generate_parser.add_argument("--candidate-artifact-id", required=True)
    generate_parser.add_argument("--candidate-artifact-digest", required=True)
    generate_parser.add_argument("--request", type=Path, required=True)
    generate_parser.add_argument("--published-at", required=True)
    generate_parser.add_argument("--workflow-run-id", required=True)
    generate_parser.add_argument("--workflow-attempt", type=int, required=True)
    generate_parser.add_argument("--actor", required=True)
    generate_parser.add_argument("--actor-id", required=True)
    generate_parser.add_argument("--existing-release", type=Path, action="append", default=[])
    generate_parser.add_argument("--output", type=Path, required=True)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        if args.command == "validate":
            value = read_json(args.manifest)
            errors = validate_release(
                value,
                expected_databases=inventories(ROOT) if args.manifest.resolve() == RELEASE_EXAMPLE.resolve() else None,
            )
            if errors:
                raise GlobalReleaseError(",".join(errors))
            print("global-release:valid")
            return 0
        generate_to_directory(args)
        print("global-release:generated")
        return 0
    except Exception as error:
        print("global-release:invalid:" + str(error), file=sys.stderr)
        return 3


if __name__ == "__main__":
    raise SystemExit(main())
