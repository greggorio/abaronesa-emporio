"""Causal mutants of the release-control image workflow contract.

Each test breaks exactly one property and requires the validator to notice.
Together they cover the rejection list of the slice contract.
"""

from __future__ import annotations

import copy
import unittest
from pathlib import Path
from typing import Any
import yaml

import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import validate_release_control_workflow as validator  # noqa: E402

WORKFLOW = Path(validator.WORKFLOW)
SOURCE = WORKFLOW.read_text(encoding="utf-8")
PARSED: dict[str, Any] = yaml.safe_load(SOURCE)
DOCKERFILE_SOURCE = Path(validator.DOCKERFILE).read_text(encoding="utf-8")


def run_with(workflow: dict[str, Any], source: str | None = None) -> list[str]:
    """Validate a mutant, either as a mutated tree or as mutated text."""
    text = yaml.safe_dump(workflow, sort_keys=False) if source is None else source
    return validator.validate(source=text)


class ReleaseControlWorkflowContractTests(unittest.TestCase):
    def test_00_current_workflow_is_valid(self) -> None:
        self.assertEqual([], validator.validate())

    def test_01_event_other_than_workflow_dispatch_is_rejected(self) -> None:
        mutant = copy.deepcopy(PARSED)
        mutant["on"] = {"push": {"branches": ["main"]}}
        self.assertIn("trigger-must-be-workflow-dispatch-only", run_with(mutant))

    def test_02_extra_trigger_is_rejected(self) -> None:
        mutant = copy.deepcopy(PARSED)
        mutant["on"] = {"workflow_dispatch": None, "schedule": [{"cron": "0 0 * * *"}]}
        self.assertIn("trigger-must-be-workflow-dispatch-only", run_with(mutant))

    def test_03_inputs_that_could_choose_sha_or_image_are_rejected(self) -> None:
        for name in ("sha", "tag", "image", "dockerfile", "command", "repository"):
            mutant = copy.deepcopy(PARSED)
            mutant["on"] = {"workflow_dispatch": {"inputs": {name: {"type": "string"}}}}
            with self.subTest(input=name):
                self.assertIn(
                    "workflow-dispatch-must-not-accept-inputs", run_with(mutant)
                )

    def test_04_packages_write_outside_publish_is_rejected(self) -> None:
        for job in ("trust", "verify", "outcome"):
            mutant = copy.deepcopy(PARSED)
            mutant["jobs"][job]["permissions"]["packages"] = "write"
            with self.subTest(job=job):
                self.assertIn(f"packages-write-outside-publish:{job}", run_with(mutant))

    def test_05_publish_without_packages_write_is_rejected(self) -> None:
        mutant = copy.deepcopy(PARSED)
        mutant["jobs"]["publish"]["permissions"].pop("packages")
        self.assertIn("publish-needs-packages-write", run_with(mutant))

    def test_06_top_level_write_permission_is_rejected(self) -> None:
        mutant = copy.deepcopy(PARSED)
        mutant["permissions"] = {"contents": "write"}
        self.assertIn("top-level-permissions", run_with(mutant))

    def test_07_action_without_full_sha_pin_is_rejected(self) -> None:
        mutant = copy.deepcopy(PARSED)
        mutant["jobs"]["trust"]["steps"][0]["uses"] = "actions/checkout@v6"
        self.assertIn("action-not-pinned:trust", run_with(mutant))

    def test_08_login_before_scan_is_rejected(self) -> None:
        mutant = copy.deepcopy(PARSED)
        steps = mutant["jobs"]["publish"]["steps"]
        login = next(
            index
            for index, step in enumerate(steps)
            if str(step.get("uses", "")).startswith("docker/login-action@")
        )
        scan = next(
            index
            for index, step in enumerate(steps)
            if str(step.get("uses", "")).startswith("aquasecurity/trivy-action@")
        )
        steps.insert(scan, steps.pop(login))
        self.assertIn("login-must-follow-scan", run_with(mutant))

    def test_09_second_build_between_scan_and_push_is_rejected(self) -> None:
        mutant = copy.deepcopy(PARSED)
        steps = mutant["jobs"]["publish"]["steps"]
        build = next(
            step
            for step in steps
            if str(step.get("uses", "")).startswith("docker/build-push-action@")
        )
        push = next(
            index for index, step in enumerate(steps) if "docker push" in str(step.get("run", ""))
        )
        steps.insert(push, copy.deepcopy(build))
        errors = run_with(mutant)
        self.assertTrue(
            "rebuild-between-scan-and-push" in errors
            or "publish-step-count:build" in errors
        )

    def test_10_build_that_pushes_directly_is_rejected(self) -> None:
        mutant = copy.deepcopy(PARSED)
        for step in mutant["jobs"]["publish"]["steps"]:
            if str(step.get("uses", "")).startswith("docker/build-push-action@"):
                step["with"]["push"] = True
        self.assertIn("build-must-load-without-push", run_with(mutant))

    def test_10b_build_context_must_include_canonical_schemas(self) -> None:
        mutant = copy.deepcopy(PARSED)
        for step in mutant["jobs"]["publish"]["steps"]:
            if str(step.get("uses", "")).startswith("docker/build-push-action@"):
                step["with"]["context"] = "release_control"
        self.assertIn("build-context-must-be-repository-root", run_with(mutant))

    def test_11_scan_ignoring_unfixed_or_severity_is_rejected(self) -> None:
        for key, value, expected in (
            ("ignore-unfixed", True, "scan-must-not-ignore-unfixed"),
            ("severity", "LOW", "scan-severity"),
            ("exit-code", "0", "scan-must-fail-closed"),
        ):
            mutant = copy.deepcopy(PARSED)
            for step in mutant["jobs"]["publish"]["steps"]:
                if str(step.get("uses", "")).startswith("aquasecurity/trivy-action@"):
                    step["with"][key] = value
            with self.subTest(key=key):
                self.assertIn(expected, run_with(mutant))

    def test_12_more_than_one_push_is_rejected(self) -> None:
        mutant = copy.deepcopy(PARSED)
        for step in mutant["jobs"]["publish"]["steps"]:
            if "docker push" in str(step.get("run", "")):
                step["run"] = str(step["run"]) + "\ndocker push again"
        self.assertIn("single-push", run_with(mutant))

    def test_13_artifact_without_sidecar_guarantees_is_rejected(self) -> None:
        for key, value, expected in (
            ("overwrite", True, "artifact-overwrite:publish"),
            ("if-no-files-found", "warn", "artifact-if-no-files-found:publish"),
            ("name", "other", "artifact-name:publish"),
        ):
            mutant = copy.deepcopy(PARSED)
            for step in mutant["jobs"]["publish"]["steps"]:
                if str(step.get("uses", "")).startswith("actions/upload-artifact@"):
                    step["with"][key] = value
            with self.subTest(key=key):
                self.assertIn(expected, run_with(mutant))

    def test_14_outcome_not_always_or_missing_dependencies_is_rejected(self) -> None:
        mutant = copy.deepcopy(PARSED)
        mutant["jobs"]["outcome"]["if"] = "success()"
        self.assertIn("outcome-must-be-always", run_with(mutant))
        mutant = copy.deepcopy(PARSED)
        mutant["jobs"]["outcome"]["needs"] = ["publish"]
        self.assertIn("outcome-needs-all", run_with(mutant))

    def test_15_publish_not_depending_on_trust_and_verify_is_rejected(self) -> None:
        mutant = copy.deepcopy(PARSED)
        mutant["jobs"]["publish"]["needs"] = ["trust"]
        self.assertIn("publish-needs-trust-and-verify", run_with(mutant))

    def test_16_foreign_allowlist_or_missing_allowlist_is_rejected(self) -> None:
        self.assertIn(
            "foreign-allowlist:RELEASE_PUBLISHER_ACTOR_IDS",
            run_with(PARSED, SOURCE.replace(validator.ALLOWLIST, "RELEASE_PUBLISHER_ACTOR_IDS")),
        )
        self.assertIn(
            "allowlist-missing",
            run_with(PARSED, SOURCE.replace(validator.ALLOWLIST, "SOMETHING_ELSE")),
        )

    def test_17_mutable_or_semver_tags_and_forbidden_actions_are_rejected(self) -> None:
        for injected, expected in (
            ("\n# :latest\n", "forbidden-token::latest"),
            ("\n# :v1.2.3\n", "semver-tag-forbidden"),
            ("\n# git tag\n", "forbidden-token:git tag"),
            ("\n# gh release\n", "forbidden-token:gh release"),
            ("\n# deploy-production\n", "forbidden-token:deploy-production"),
            ("\n# ssh\n", "forbidden-token:ssh"),
        ):
            with self.subTest(token=expected):
                self.assertIn(expected, run_with(PARSED, SOURCE + injected))

    def test_18_dockerfile_base_must_be_pinned_and_identical(self) -> None:
        original = DOCKERFILE_SOURCE
        unpinned = original.replace(
            "python:3.13-alpine3.23@sha256:9fdbf2e3e82628351513560b121e2ee6ce31cac212be9e070c5a5e2769fb5e76",
            "python:3.13-alpine3.23",
        )
        self.assertIn(
            "dockerfile-base-not-pinned", validator.validate(dockerfile=unpinned)
        )
        divergent = original.replace(
            "sha256:9fdbf2e3e82628351513560b121e2ee6ce31cac212be9e070c5a5e2769fb5e76",
            "sha256:" + "a" * 64,
            1,
        )
        self.assertIn(
            "dockerfile-base-digests-differ", validator.validate(dockerfile=divergent)
        )

    def test_19_dockerfile_user_healthcheck_and_labels_are_required(self) -> None:
        original = DOCKERFILE_SOURCE
        for needle, expected in (
            ("USER 10001:10001", "dockerfile-user"),
            ("HEALTHCHECK", "dockerfile-healthcheck"),
            ("org.opencontainers.image.revision", "dockerfile-label:image.revision"),
        ):
            with self.subTest(needle=needle):
                self.assertIn(
                    expected,
                    validator.validate(dockerfile=original.replace(needle, "X")),
                )

    def test_20_release_control_stays_out_of_the_commercial_bom(self) -> None:
        catalog = yaml.safe_load(
            (Path(validator.ROOT) / "ops/releases/components.yml").read_text(encoding="utf-8")
        )
        self.assertEqual(["release_control"], catalog["excluded_operational_components"])
        self.assertNotIn("release_control", catalog.get("components", {}))
        self.assertNotIn("release_control", catalog.get("canonical_order", []))


if __name__ == "__main__":
    unittest.main()
