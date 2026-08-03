#!/usr/bin/env python3
"""Independent operational cycle of the release-control image.

The release-control image is operational infrastructure, not a commercial
component: it never enters the candidate, the six-component BOM or the global
release. This module owns every fail-closed step of its workflow — trust, image
probe, canonical manifest and terminal outcome — so the workflow itself stays
declarative and no step can invent success.

A published image is identified by digest alone. The transport tag exists only
to move bytes and is deliberately absent from the manifest.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

REPOSITORY = "greggorio/abaronesa-emporio"
IMAGE_REPOSITORY = "ghcr.io/greggorio/abaronesa-emporio-release-control"
WORKFLOW_PATH = ".github/workflows/publish-release-control.yml"
KIND = "release-control-image"
OUTCOME_KIND = "release-control-image-outcome"
SCHEMA_VERSION = 1
EXPECTED_USER = "10001:10001"
REQUIRED_LABELS = (
    "org.opencontainers.image.source",
    "org.opencontainers.image.revision",
    "org.opencontainers.image.version",
)

KEYS = (
    "schemaVersion",
    "kind",
    "repository",
    "sourceSha",
    "imageRepository",
    "imageDigest",
    "immutableRef",
    "workflowRunId",
    "workflowAttempt",
    "actor",
    "actorId",
    "publishedAt",
)
OUTCOME_KEYS = (
    "schemaVersion",
    "kind",
    "repository",
    "status",
    "workflowRunId",
    "workflowAttempt",
    "manifestSha256",
    "imageDigest",
)

SHA_RE = re.compile(r"^[0-9a-f]{40}$")
DIGEST_RE = re.compile(r"^sha256:[0-9a-f]{64}$")
ACTOR_RE = re.compile(r"^[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?(?:\[bot\])?$")
DECIMAL_RE = re.compile(r"^[1-9][0-9]*$")
TIMESTAMP_RE = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$")


class ManifestError(Exception):
    """Stable, sanitized failure of the release-control image contract."""


def canonical(value: Any) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":")).encode() + b"\n"


def digest_of(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def transport_tag(source_sha: str, run_id: str, attempt: str) -> str:
    """Derive the only tag allowed to carry the image, from trusted context.

    It is a transport detail, never an identity: it is not SemVer, never
    `latest`, cannot be chosen by the operator and never reaches the manifest.
    """
    return f"{IMAGE_REPOSITORY}:src-{source_sha}-run-{run_id}-{attempt}"


def _positive(value: Any, field: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < 1:
        raise ManifestError(f"{field} must be a positive integer")
    return value


def _decimal(value: Any, field: str) -> str:
    if not isinstance(value, str) or DECIMAL_RE.fullmatch(value) is None:
        raise ManifestError(f"{field} must be a positive decimal string")
    return value


def parse_allowlist(raw: str | None) -> tuple[str, ...]:
    """Parse the decimal allowlist, refusing every sloppy form."""
    if not isinstance(raw, str) or raw == "":
        raise ManifestError("actor allowlist is missing")
    entries = raw.split(",")
    for entry in entries:
        if DECIMAL_RE.fullmatch(entry) is None:
            raise ManifestError("actor allowlist entry is not a positive decimal")
    if len(set(entries)) != len(entries):
        raise ManifestError("actor allowlist has duplicates")
    return tuple(entries)


def validate_identity(env: dict[str, str]) -> tuple[str, str]:
    """Fail closed on anything that is not this workflow, on main, by an allowed actor."""
    if env.get("TRUSTED_REPOSITORY") != REPOSITORY:
        raise ManifestError("repository is not the canonical one")
    workflow_ref = env.get("TRUSTED_WORKFLOW_REF", "")
    if not workflow_ref.startswith(f"{REPOSITORY}/{WORKFLOW_PATH}@"):
        raise ManifestError("workflow path is not the release-control workflow")
    if env.get("TRUSTED_EVENT") != "workflow_dispatch":
        raise ManifestError("event must be workflow_dispatch")
    if env.get("TRUSTED_REF") != "refs/heads/main":
        raise ManifestError("ref must be refs/heads/main")

    source_sha = env.get("TRUSTED_SHA", "")
    if SHA_RE.fullmatch(source_sha) is None:
        raise ManifestError("source sha is invalid")

    actor = env.get("TRUSTED_ACTOR", "")
    if ACTOR_RE.fullmatch(actor) is None:
        raise ManifestError("actor is invalid")
    actor_id = _decimal(env.get("TRUSTED_ACTOR_ID"), "actor id")
    if actor_id not in parse_allowlist(
        env.get("RELEASE_CONTROL_IMAGE_PUBLISHER_ACTOR_IDS")
    ):
        raise ManifestError("actor is not allowed to publish the release control image")

    run_id = _decimal(env.get("TRUSTED_RUN_ID"), "run id")
    attempt = _decimal(env.get("TRUSTED_RUN_ATTEMPT"), "run attempt")
    return source_sha, transport_tag(source_sha, run_id, attempt)


def validate(manifest: Any) -> dict[str, Any]:
    """Validate the closed manifest shape and every binding inside it."""
    if not isinstance(manifest, dict):
        raise ManifestError("manifest must be an object")
    if tuple(sorted(manifest)) != tuple(sorted(KEYS)):
        raise ManifestError("manifest keys are not exactly the contracted set")
    if manifest["schemaVersion"] != SCHEMA_VERSION:
        raise ManifestError("schemaVersion is invalid")
    if manifest["kind"] != KIND:
        raise ManifestError("kind is invalid")
    if manifest["repository"] != REPOSITORY:
        raise ManifestError("repository is invalid")
    if manifest["imageRepository"] != IMAGE_REPOSITORY:
        raise ManifestError("imageRepository is invalid")

    source_sha = manifest["sourceSha"]
    if not isinstance(source_sha, str) or SHA_RE.fullmatch(source_sha) is None:
        raise ManifestError("sourceSha must be 40 lowercase hex characters")

    image_digest = manifest["imageDigest"]
    if not isinstance(image_digest, str) or DIGEST_RE.fullmatch(image_digest) is None:
        raise ManifestError("imageDigest must be sha256 with 64 lowercase hex characters")

    if manifest["immutableRef"] != f"{IMAGE_REPOSITORY}@{image_digest}":
        raise ManifestError("immutableRef must join imageRepository and imageDigest")

    _positive(manifest["workflowRunId"], "workflowRunId")
    _positive(manifest["workflowAttempt"], "workflowAttempt")

    actor = manifest["actor"]
    if not isinstance(actor, str) or ACTOR_RE.fullmatch(actor) is None:
        raise ManifestError("actor is invalid")
    _decimal(manifest["actorId"], "actorId")

    published = manifest["publishedAt"]
    if not isinstance(published, str) or TIMESTAMP_RE.fullmatch(published) is None:
        raise ManifestError("publishedAt must be a normalized UTC timestamp")
    try:
        datetime.strptime(published, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=UTC)
    except ValueError as exc:
        raise ManifestError("publishedAt is not a real instant") from exc

    return manifest


def build(
    *,
    source_sha: str,
    image_digest: str,
    run_id: int,
    attempt: int,
    actor: str,
    actor_id: str,
    published_at: str,
) -> dict[str, Any]:
    return validate(
        {
            "schemaVersion": SCHEMA_VERSION,
            "kind": KIND,
            "repository": REPOSITORY,
            "sourceSha": source_sha,
            "imageRepository": IMAGE_REPOSITORY,
            "imageDigest": image_digest,
            "immutableRef": f"{IMAGE_REPOSITORY}@{image_digest}",
            "workflowRunId": run_id,
            "workflowAttempt": attempt,
            "actor": actor,
            "actorId": actor_id,
            "publishedAt": published_at,
        }
    )


def _run(command: list[str]) -> str:
    completed = subprocess.run(
        command, check=False, capture_output=True, text=True, timeout=180
    )
    if completed.returncode != 0:
        raise ManifestError(f"{command[0]} command failed")
    return completed.stdout


def resolve_remote_digest(reference: str) -> str:
    """Read the digest the registry recorded for the pushed transport tag."""
    if not reference.startswith(f"{IMAGE_REPOSITORY}:"):
        raise ManifestError("reference must belong to the release-control repository")
    payload = json.loads(
        _run(
            [
                "docker",
                "buildx",
                "imagetools",
                "inspect",
                "--format",
                "{{json .Manifest}}",
                reference,
            ]
        )
    )
    digest = payload.get("digest") if isinstance(payload, dict) else None
    if not isinstance(digest, str) or DIGEST_RE.fullmatch(digest) is None:
        raise ManifestError("remote digest is invalid")
    return digest


def probe_image(config: Any, source_sha: str) -> None:
    """Prove the built image really carries the contracted runtime shape."""
    if not isinstance(config, dict):
        raise ManifestError("image inspection is invalid")
    if config.get("User") != EXPECTED_USER:
        raise ManifestError(f"image user must be {EXPECTED_USER}")
    healthcheck = config.get("Healthcheck")
    if not isinstance(healthcheck, dict) or not healthcheck.get("Test"):
        raise ManifestError("image must declare a healthcheck")
    labels = config.get("Labels")
    if not isinstance(labels, dict):
        raise ManifestError("image labels are missing")
    for label in REQUIRED_LABELS:
        if not labels.get(label):
            raise ManifestError(f"image label {label} is missing")
    if labels["org.opencontainers.image.revision"] != source_sha:
        raise ManifestError("image revision label does not match the source sha")
    if labels["org.opencontainers.image.source"] != f"https://github.com/{REPOSITORY}":
        raise ManifestError("image source label is invalid")


def _write(output: Path, name: str, manifest: dict[str, Any]) -> bytes:
    payload = canonical(manifest)
    output.mkdir(parents=True, exist_ok=True)
    (output / f"{name}.json").write_bytes(payload)
    (output / f"{name}.json.sha256").write_bytes(digest_of(payload).encode() + b"\n")
    return payload


def _read(directory: Path, name: str) -> dict[str, Any]:
    payload = (directory / f"{name}.json").read_bytes()
    sidecar = (directory / f"{name}.json.sha256").read_bytes()
    if sidecar != digest_of(payload).encode() + b"\n":
        raise ManifestError("sidecar does not match the manifest")
    return json.loads(payload)


def _cli_trust(args: argparse.Namespace) -> int:
    source_sha, tag = validate_identity(dict(os.environ))
    head = _run(["git", "rev-parse", "origin/main"]).strip() if args.check_head else source_sha
    if head != source_sha:
        raise ManifestError("source sha is not the current head of main")
    Path(args.output).write_text(f"sha={source_sha}\ntag={tag}\n", encoding="utf-8")
    print("release-control-image:trusted")
    return 0


def _cli_probe(args: argparse.Namespace) -> int:
    payload = json.loads(
        _run(["docker", "image", "inspect", "--format", "{{json .Config}}", args.reference])
    )
    probe_image(payload, args.sha)
    print("release-control-image:probe:valid")
    return 0


def _cli_manifest(args: argparse.Namespace) -> int:
    manifest = build(
        source_sha=args.source_sha,
        image_digest=resolve_remote_digest(args.reference),
        run_id=int(args.run_id),
        attempt=int(args.attempt),
        actor=args.actor,
        actor_id=args.actor_id,
        published_at=datetime.now(UTC).strftime("%Y-%m-%dT%H:%M:%SZ"),
    )
    _write(Path(args.output), "release-control-image", manifest)
    print(f"release-control-image:published:{manifest['imageDigest']}")
    return 0


def _cli_validate(args: argparse.Namespace) -> int:
    directory = Path(args.manifest)
    manifest = validate(_read(directory, "release-control-image"))
    if canonical(manifest) != (directory / "release-control-image.json").read_bytes():
        raise ManifestError("manifest is not canonical")
    print("release-control-image:valid")
    return 0


def _cli_outcome(args: argparse.Namespace) -> int:
    """Record the terminal state, never upgrading a non-success into success."""
    env = dict(os.environ)
    results = {
        "trust": env.get("TRUST_RESULT", ""),
        "verify": env.get("VERIFY_RESULT", ""),
        "publish": env.get("PUBLISH_RESULT", ""),
    }
    succeeded = all(value == "success" for value in results.values())
    directory = Path(args.manifest)
    manifest: dict[str, Any] | None = None
    if succeeded:
        manifest = validate(_read(directory, "release-control-image"))
        payload = (directory / "release-control-image.json").read_bytes()
        manifest_sha = digest_of(payload)
    else:
        manifest_sha = None
    outcome = {
        "schemaVersion": SCHEMA_VERSION,
        "kind": OUTCOME_KIND,
        "repository": REPOSITORY,
        "status": "published" if succeeded else "failed",
        "workflowRunId": _positive(int(_decimal(env.get("TRUSTED_RUN_ID"), "run id")), "runId"),
        "workflowAttempt": _positive(
            int(_decimal(env.get("TRUSTED_RUN_ATTEMPT"), "run attempt")), "attempt"
        ),
        "manifestSha256": manifest_sha,
        "imageDigest": manifest["imageDigest"] if manifest is not None else None,
    }
    if tuple(sorted(outcome)) != tuple(sorted(OUTCOME_KEYS)):
        raise ManifestError("outcome keys are not exactly the contracted set")
    _write(Path(args.output), "release-control-image-outcome", outcome)
    print(f"release-control-image:outcome:{outcome['status']}")
    return 0 if succeeded else 3


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)

    trust = sub.add_parser("trust", help="validate the dispatch identity")
    trust.add_argument("--output", required=True)
    trust.add_argument("--check-head", action="store_true")
    trust.set_defaults(handler=_cli_trust)

    probe = sub.add_parser("probe", help="prove the built image shape")
    probe.add_argument("--reference", required=True)
    probe.add_argument("--sha", required=True)
    probe.set_defaults(handler=_cli_probe)

    manifest = sub.add_parser("manifest", help="build the manifest after a push")
    manifest.add_argument("--source-sha", required=True)
    manifest.add_argument("--reference", required=True)
    manifest.add_argument("--run-id", required=True)
    manifest.add_argument("--attempt", required=True)
    manifest.add_argument("--actor", required=True)
    manifest.add_argument("--actor-id", required=True)
    manifest.add_argument("--output", required=True)
    manifest.set_defaults(handler=_cli_manifest)

    check = sub.add_parser("validate", help="validate a manifest directory")
    check.add_argument("--manifest", required=True)
    check.set_defaults(handler=_cli_validate)

    outcome = sub.add_parser("outcome", help="record the terminal outcome")
    outcome.add_argument("--manifest", required=True)
    outcome.add_argument("--output", required=True)
    outcome.set_defaults(handler=_cli_outcome)

    args = parser.parse_args(argv)
    try:
        return int(args.handler(args))
    except (ManifestError, OSError, json.JSONDecodeError, ValueError) as exc:
        message = exc.args[0] if isinstance(exc, ManifestError) else type(exc).__name__
        print(f"release-control-image:invalid:{message}", file=sys.stderr)
        return 3


if __name__ == "__main__":
    raise SystemExit(main())
