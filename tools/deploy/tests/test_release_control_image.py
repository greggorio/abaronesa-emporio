"""Causal tests of the release-control image manifest, trust and outcome."""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path
from typing import Any
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import release_control_image as tool  # noqa: E402

SHA = "a" * 40
DIGEST = "sha256:" + "b" * 64
BASE_ENV = {
    "TRUSTED_REPOSITORY": tool.REPOSITORY,
    "TRUSTED_WORKFLOW_REF": f"{tool.REPOSITORY}/{tool.WORKFLOW_PATH}@refs/heads/main",
    "TRUSTED_EVENT": "workflow_dispatch",
    "TRUSTED_REF": "refs/heads/main",
    "TRUSTED_SHA": SHA,
    "TRUSTED_RUN_ID": "100",
    "TRUSTED_RUN_ATTEMPT": "1",
    "TRUSTED_ACTOR": "greggorio",
    "TRUSTED_ACTOR_ID": "35626201",
    "RELEASE_CONTROL_IMAGE_PUBLISHER_ACTOR_IDS": "35626201",
}


def manifest(**overrides: Any) -> dict[str, Any]:
    value = tool.build(
        source_sha=SHA,
        image_digest=DIGEST,
        run_id=100,
        attempt=1,
        actor="greggorio",
        actor_id="35626201",
        published_at="2026-08-03T10:00:00Z",
    )
    value.update(overrides)
    return value


class TrustTests(unittest.TestCase):
    def test_valid_identity_yields_sha_and_derived_tag(self) -> None:
        source_sha, tag = tool.validate_identity(dict(BASE_ENV))
        self.assertEqual(SHA, source_sha)
        self.assertEqual(f"{tool.IMAGE_REPOSITORY}:src-{SHA}-run-100-1", tag)

    def test_transport_tag_is_never_latest_or_semver(self) -> None:
        tag = tool.transport_tag(SHA, "100", "1")
        self.assertNotIn(":latest", tag)
        self.assertNotRegex(tag, r":v\d+\.\d+\.\d+")

    def test_event_ref_repository_and_workflow_path_are_enforced(self) -> None:
        for key, value in (
            ("TRUSTED_EVENT", "push"),
            ("TRUSTED_REF", "refs/heads/other"),
            ("TRUSTED_REPOSITORY", "other/repo"),
            ("TRUSTED_WORKFLOW_REF", f"{tool.REPOSITORY}/.github/workflows/ci.yml@refs/heads/main"),
            ("TRUSTED_SHA", "not-a-sha"),
        ):
            env = dict(BASE_ENV)
            env[key] = value
            with self.subTest(key=key), self.assertRaises(tool.ManifestError):
                tool.validate_identity(env)

    def test_actor_id_must_be_decimal_positive_and_allowed(self) -> None:
        for actor_id, allowlist in (
            ("", "35626201"),
            ("0", "35626201"),
            ("abc", "35626201"),
            ("35626201", ""),
            ("35626201", "99"),
            ("35626201", "35626201,35626201"),
            ("35626201", "0"),
        ):
            env = dict(BASE_ENV)
            env["TRUSTED_ACTOR_ID"] = actor_id
            env["RELEASE_CONTROL_IMAGE_PUBLISHER_ACTOR_IDS"] = allowlist
            with self.subTest(actor_id=actor_id, allowlist=allowlist):
                with self.assertRaises(tool.ManifestError):
                    tool.validate_identity(env)


class ManifestTests(unittest.TestCase):
    def test_valid_manifest_round_trips_canonically(self) -> None:
        value = manifest()
        self.assertEqual(value, tool.validate(json.loads(tool.canonical(value))))
        self.assertEqual(f"{tool.IMAGE_REPOSITORY}@{DIGEST}", value["immutableRef"])

    def test_extra_or_missing_key_is_rejected(self) -> None:
        extra = manifest()
        extra["transportTag"] = f"{tool.IMAGE_REPOSITORY}:src-{SHA}-run-100-1"
        with self.assertRaises(tool.ManifestError):
            tool.validate(extra)
        missing = manifest()
        del missing["imageDigest"]
        with self.assertRaises(tool.ManifestError):
            tool.validate(missing)

    def test_invalid_sha_digest_run_actor_or_timestamp_is_rejected(self) -> None:
        for key, value in (
            ("sourceSha", "A" * 40),
            ("sourceSha", "a" * 39),
            ("imageDigest", "sha256:" + "B" * 64),
            ("imageDigest", "b" * 64),
            ("workflowRunId", 0),
            ("workflowRunId", True),
            ("workflowAttempt", -1),
            ("actorId", "0"),
            ("actorId", 35626201),
            ("publishedAt", "2026-08-03 10:00:00"),
            ("publishedAt", "2026-13-03T10:00:00Z"),
            ("kind", "release-control"),
            ("repository", "other/repo"),
            ("imageRepository", "ghcr.io/greggorio/abaronesa-emporio-backend"),
        ):
            with self.subTest(key=key, value=value), self.assertRaises(tool.ManifestError):
                tool.validate(manifest(**{key: value}))

    def test_immutable_ref_must_match_repository_and_digest(self) -> None:
        for divergent in (
            f"{tool.IMAGE_REPOSITORY}@sha256:{'c' * 64}",
            f"ghcr.io/greggorio/other@{DIGEST}",
            f"{tool.IMAGE_REPOSITORY}:src-{SHA}-run-100-1",
        ):
            with self.subTest(ref=divergent), self.assertRaises(tool.ManifestError):
                tool.validate(manifest(immutableRef=divergent))

    def test_sidecar_must_match_the_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            directory = Path(raw)
            payload = tool._write(directory, "release-control-image", manifest())
            self.assertEqual(
                tool.digest_of(payload).encode() + b"\n",
                (directory / "release-control-image.json.sha256").read_bytes(),
            )
            (directory / "release-control-image.json.sha256").write_bytes(b"0" * 64 + b"\n")
            with self.assertRaises(tool.ManifestError):
                tool._read(directory, "release-control-image")


class ProbeTests(unittest.TestCase):
    def config(self, **overrides: Any) -> dict[str, Any]:
        value = {
            "User": "10001:10001",
            "Healthcheck": {"Test": ["CMD", "python", "-c", "pass"]},
            "Labels": {
                "org.opencontainers.image.source": f"https://github.com/{tool.REPOSITORY}",
                "org.opencontainers.image.revision": SHA,
                "org.opencontainers.image.version": "release-control-x",
            },
        }
        value.update(overrides)
        return value

    def test_expected_shape_passes(self) -> None:
        tool.probe_image(self.config(), SHA)

    def test_wrong_user_healthcheck_or_labels_fail(self) -> None:
        for overrides in (
            {"User": "root"},
            {"User": ""},
            {"Healthcheck": {}},
            {"Healthcheck": None},
            {"Labels": {}},
        ):
            with self.subTest(overrides=overrides), self.assertRaises(tool.ManifestError):
                tool.probe_image(self.config(**overrides), SHA)

    def test_revision_label_must_match_the_source_sha(self) -> None:
        config = self.config()
        config["Labels"]["org.opencontainers.image.revision"] = "c" * 40
        with self.assertRaises(tool.ManifestError):
            tool.probe_image(config, SHA)


class OutcomeTests(unittest.TestCase):
    def run_outcome(self, results: dict[str, str], *, with_manifest: bool) -> tuple[int, dict[str, Any]]:
        with tempfile.TemporaryDirectory() as raw:
            directory = Path(raw)
            manifest_dir = directory / "manifest"
            output = directory / "outcome"
            manifest_dir.mkdir()
            if with_manifest:
                tool._write(manifest_dir, "release-control-image", manifest())
            env = {
                "TRUST_RESULT": results.get("trust", "success"),
                "VERIFY_RESULT": results.get("verify", "success"),
                "PUBLISH_RESULT": results.get("publish", "success"),
                "TRUSTED_RUN_ID": "100",
                "TRUSTED_RUN_ATTEMPT": "1",
            }
            args = mock.Mock(manifest=str(manifest_dir), output=str(output))
            with mock.patch.dict(tool.os.environ, env, clear=True):
                code = tool._cli_outcome(args)
            payload = json.loads((output / "release-control-image-outcome.json").read_bytes())
            return code, payload

    def test_success_requires_every_predecessor_and_records_the_digest(self) -> None:
        code, payload = self.run_outcome({}, with_manifest=True)
        self.assertEqual(0, code)
        self.assertEqual("published", payload["status"])
        self.assertEqual(DIGEST, payload["imageDigest"])
        self.assertIsInstance(payload["manifestSha256"], str)

    def test_any_failed_or_skipped_predecessor_never_becomes_success(self) -> None:
        for job in ("trust", "verify", "publish"):
            for result in ("failure", "skipped", "cancelled", ""):
                with self.subTest(job=job, result=result):
                    code, payload = self.run_outcome({job: result}, with_manifest=False)
                    self.assertEqual(3, code)
                    self.assertEqual("failed", payload["status"])
                    self.assertIsNone(payload["imageDigest"])
                    self.assertIsNone(payload["manifestSha256"])


class SeparationTests(unittest.TestCase):
    def test_release_control_image_repository_is_not_a_commercial_component(self) -> None:
        self.assertTrue(
            tool.IMAGE_REPOSITORY.endswith("abaronesa-emporio-release-control")
        )
        for commercial in (
            "backend",
            "website-backend",
            "frontend",
            "website-frontend",
            "whatsapp-service",
            "gateway",
        ):
            self.assertNotEqual(
                f"ghcr.io/greggorio/abaronesa-emporio-{commercial}", tool.IMAGE_REPOSITORY
            )

    def test_manifest_never_carries_a_transport_tag(self) -> None:
        self.assertNotIn("transportTag", tool.KEYS)
        self.assertNotIn("tag", tool.KEYS)


if __name__ == "__main__":
    unittest.main()
