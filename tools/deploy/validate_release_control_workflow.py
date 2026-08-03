#!/usr/bin/env python3
"""Static contract of the independent release-control image workflow.

Every rule here exists because breaking it would let an image reach GHCR with a
weaker guarantee than the commercial pipeline: an unpinned action, a login
before the scan, a second build between scan and push, a mutable tag, or a
manifest that identifies the image by anything other than its digest.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path
from typing import Any

import yaml

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/publish-release-control.yml"
DOCKERFILE = ROOT / "release_control/Dockerfile"

NAME = "Publish Release Control Image"
IMAGE_REPOSITORY = "ghcr.io/greggorio/abaronesa-emporio-release-control"
ALLOWLIST = "RELEASE_CONTROL_IMAGE_PUBLISHER_ACTOR_IDS"
FORBIDDEN_ALLOWLISTS = ("RELEASE_PUBLISHER_ACTOR_IDS", "DEPLOYER_ACTOR_IDS")
JOBS = ("trust", "verify", "publish", "outcome")
ACTION_PIN = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+@[0-9a-f]{40}$")
FROM_PIN = re.compile(r"^FROM\s+\S+@sha256:[0-9a-f]{64}\s", re.MULTILINE)


def _on(workflow: dict[str, Any]) -> Any:
    return workflow.get("on", workflow.get(True))


def _steps(job: Any) -> list[dict[str, Any]]:
    steps = job.get("steps") if isinstance(job, dict) else None
    return [step for step in steps if isinstance(step, dict)] if isinstance(steps, list) else []


def _validate_trigger(workflow: dict[str, Any], errors: list[str]) -> None:
    if workflow.get("name") != NAME:
        errors.append("workflow-name")
    trigger = _on(workflow)
    if not isinstance(trigger, dict) or set(trigger) != {"workflow_dispatch"}:
        errors.append("trigger-must-be-workflow-dispatch-only")
        return
    dispatch = trigger["workflow_dispatch"]
    # No input may let an operator choose sha, tag, image, Dockerfile or command.
    if dispatch not in (None, {}) and (
        not isinstance(dispatch, dict) or dispatch.get("inputs")
    ):
        errors.append("workflow-dispatch-must-not-accept-inputs")


def _validate_permissions(workflow: dict[str, Any], errors: list[str]) -> None:
    if workflow.get("permissions") != {"contents": "read"}:
        errors.append("top-level-permissions")
    jobs = workflow.get("jobs")
    if not isinstance(jobs, dict) or tuple(jobs) != JOBS:
        errors.append("jobs-set")
        return
    for name, job in jobs.items():
        permissions = job.get("permissions") if isinstance(job, dict) else None
        if not isinstance(permissions, dict):
            errors.append(f"permissions-missing:{name}")
            continue
        if permissions.get("contents") != "read":
            errors.append(f"contents-not-read:{name}")
        writes = permissions.get("packages")
        if name == "publish":
            if writes != "write":
                errors.append("publish-needs-packages-write")
        elif writes is not None:
            errors.append(f"packages-write-outside-publish:{name}")


def _validate_dependencies(jobs: dict[str, Any], errors: list[str]) -> None:
    if jobs.get("verify", {}).get("needs") != "trust":
        errors.append("verify-needs-trust")
    if jobs.get("publish", {}).get("needs") != ["trust", "verify"]:
        errors.append("publish-needs-trust-and-verify")
    outcome = jobs.get("outcome", {})
    if outcome.get("needs") != ["trust", "verify", "publish"]:
        errors.append("outcome-needs-all")
    if outcome.get("if") != "always()":
        errors.append("outcome-must-be-always")


def _validate_pins(jobs: dict[str, Any], errors: list[str]) -> None:
    for name, job in jobs.items():
        for step in _steps(job):
            uses = step.get("uses")
            if uses is not None and ACTION_PIN.fullmatch(str(uses)) is None:
                errors.append(f"action-not-pinned:{name}")


def _validate_publish_order(jobs: dict[str, Any], errors: list[str]) -> None:
    """Build once, prove, scan, only then authenticate, then push once."""
    steps = _steps(jobs.get("publish", {}))
    order: list[str] = []
    for step in steps:
        uses = str(step.get("uses") or "")
        run = str(step.get("run") or "")
        if uses.startswith("docker/build-push-action@"):
            order.append("build")
            with_ = step.get("with") if isinstance(step.get("with"), dict) else {}
            if with_.get("push") is not False or with_.get("load") is not True:
                errors.append("build-must-load-without-push")
            if with_.get("platforms") != "linux/amd64":
                errors.append("build-platform")
            tags = str(with_.get("tags") or "")
            if "needs.trust.outputs.tag" not in tags:
                errors.append("build-tag-must-come-from-trust")
        elif uses.startswith("aquasecurity/trivy-action@"):
            order.append("scan")
            with_ = step.get("with") if isinstance(step.get("with"), dict) else {}
            severity = str(with_.get("severity") or "")
            if "HIGH" not in severity or "CRITICAL" not in severity:
                errors.append("scan-severity")
            if with_.get("ignore-unfixed") is not False:
                errors.append("scan-must-not-ignore-unfixed")
            if str(with_.get("exit-code")) != "1":
                errors.append("scan-must-fail-closed")
        elif uses.startswith("docker/login-action@"):
            order.append("login")
        elif "docker push" in run:
            order.append("push")
            if run.count("docker push") != 1:
                errors.append("single-push")
        elif "release_control_image.py probe" in run:
            order.append("probe")

    for required in ("build", "probe", "scan", "login", "push"):
        if order.count(required) != 1:
            errors.append(f"publish-step-count:{required}")
            return
    if order.index("build") > order.index("probe"):
        errors.append("probe-after-build")
    if order.index("probe") > order.index("scan"):
        errors.append("scan-after-probe")
    if order.index("scan") > order.index("login"):
        errors.append("login-must-follow-scan")
    if order.index("login") > order.index("push"):
        errors.append("push-must-follow-login")
    if any(entry == "build" for entry in order[order.index("scan") :]):
        errors.append("rebuild-between-scan-and-push")


def _validate_artifacts(jobs: dict[str, Any], errors: list[str]) -> None:
    expected = {"publish": "release-control-image-manifest", "outcome": "release-control-image-outcome"}
    for job_name, artifact in expected.items():
        found = False
        for step in _steps(jobs.get(job_name, {})):
            if not str(step.get("uses") or "").startswith("actions/upload-artifact@"):
                continue
            with_ = step.get("with") if isinstance(step.get("with"), dict) else {}
            if with_.get("name") != artifact:
                errors.append(f"artifact-name:{job_name}")
                continue
            found = True
            if with_.get("overwrite") is not False:
                errors.append(f"artifact-overwrite:{job_name}")
            if with_.get("if-no-files-found") != "error":
                errors.append(f"artifact-if-no-files-found:{job_name}")
            if not isinstance(with_.get("retention-days"), int):
                errors.append(f"artifact-retention:{job_name}")
        if not found:
            errors.append(f"artifact-missing:{job_name}")


def _validate_isolation(source: str, errors: list[str]) -> None:
    if ALLOWLIST not in source:
        errors.append("allowlist-missing")
    for forbidden in FORBIDDEN_ALLOWLISTS:
        if forbidden in source:
            errors.append(f"foreign-allowlist:{forbidden}")
    if f"{IMAGE_REPOSITORY}" in source:
        errors.append("image-repository-must-come-from-the-tool")
    for forbidden in (":latest", "gh release", "git tag", "deploy-production", "rollback-production", "ssh"):
        if forbidden in source:
            errors.append(f"forbidden-token:{forbidden}")
    if re.search(r":v\d+\.\d+\.\d+", source):
        errors.append("semver-tag-forbidden")


def _validate_dockerfile(errors: list[str], dockerfile: str | None = None) -> None:
    if dockerfile is None:
        try:
            dockerfile = DOCKERFILE.read_text(encoding="utf-8")
        except OSError:
            errors.append("dockerfile-unreadable")
            return
    text = dockerfile
    froms = [line for line in text.splitlines() if line.startswith("FROM ")]
    if len(froms) != 2:
        errors.append("dockerfile-stage-count")
        return
    digests = re.findall(r"@(sha256:[0-9a-f]{64})", "\n".join(froms))
    if len(digests) != 2:
        errors.append("dockerfile-base-not-pinned")
        return
    if digests[0] != digests[1]:
        errors.append("dockerfile-base-digests-differ")
    if "USER 10001:10001" not in text:
        errors.append("dockerfile-user")
    if "HEALTHCHECK" not in text:
        errors.append("dockerfile-healthcheck")
    for label in ("image.source", "image.revision", "image.version"):
        if f"org.opencontainers.{label}" not in text:
            errors.append(f"dockerfile-label:{label}")


def validate(source: str | None = None, dockerfile: str | None = None) -> list[str]:
    """Validate the workflow, optionally against supplied sources for mutants."""
    errors: list[str] = []
    if source is None:
        try:
            source = WORKFLOW.read_text(encoding="utf-8")
        except OSError:
            return ["workflow-unreadable"]
    workflow = yaml.safe_load(source)
    if not isinstance(workflow, dict):
        return ["workflow-invalid"]

    _validate_trigger(workflow, errors)
    _validate_permissions(workflow, errors)
    jobs = workflow.get("jobs") if isinstance(workflow.get("jobs"), dict) else {}
    _validate_dependencies(jobs, errors)
    _validate_pins(jobs, errors)
    _validate_publish_order(jobs, errors)
    _validate_artifacts(jobs, errors)
    _validate_isolation(source, errors)
    _validate_dockerfile(errors, dockerfile)
    return errors


def main() -> int:
    errors = validate()
    if errors:
        print("release-control-workflow:invalid:" + ",".join(sorted(set(errors))), file=sys.stderr)
        return 3
    print("release-control-workflow:valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
