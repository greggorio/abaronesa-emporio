from __future__ import annotations

import json
import os
import shutil
import stat
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from tools.deploy import deployment_engine_rehearsal as rehearsal
from tools.deploy import deployment_cli
from tools.deploy import deployment_plan
from tools.deploy.validate_deployment_engine_workflow import validate

ROOT = Path(__file__).resolve().parents[3]
SHA = "a" * 40


def environment() -> dict[str, str]:
    return {
        "TRUSTED_REPOSITORY": rehearsal.REPOSITORY,
        "TRUSTED_WORKFLOW_REF": f"{rehearsal.REPOSITORY}/{rehearsal.WORKFLOW}@{rehearsal.REF}",
        "TRUSTED_EVENT": "workflow_dispatch",
        "TRUSTED_REF": rehearsal.REF,
        "TRUSTED_SHA": SHA,
        "TRUSTED_RUN_ID": "123",
        "TRUSTED_RUN_ATTEMPT": "1",
        "TRUSTED_ACTOR": "emporio-deployer[bot]",
        "TRUSTED_ACTOR_ID": "313092947",
        "TRUSTED_TRIGGERING_ACTOR": "emporio-deployer[bot]",
        "TRUSTED_SENDER_ID": "313092947",
        "DEPLOYER_ACTOR_IDS": "313092947",
        "TRUST_RESULT": "success",
        "REHEARSAL_RESULT": "success",
    }


class DeploymentEngineWorkflowTest(unittest.TestCase):
    def package_copy(self) -> tuple[tempfile.TemporaryDirectory[str], Path]:
        temporary = tempfile.TemporaryDirectory()
        root = Path(temporary.name)
        for relative in (
            ".github/workflows/verify-deployment-engine.yml",
            "tools/deploy/deployment_engine_rehearsal.py",
        ):
            target = root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / relative, target)
        return temporary, root

    def test_baseline_is_valid(self) -> None:
        self.assertEqual([], validate(ROOT))

    def test_workflow_and_runtime_mutants_fail_closed(self) -> None:
        mutants = (
            ("workflow", "workflow_dispatch:\n", "workflow_dispatch:\n    inputs:\n      command:\n"),
            ("workflow", "packages: read", "packages: write"),
            ("workflow", "gh release download v0.1.1", "gh release download v0.1.2"),
            ("workflow", "id: rehearsal\n        continue-on-error: true", "id: rehearsal\n        continue-on-error: false"),
            ("runtime", '"down", "-v", "--remove-orphans"', '"down"'),
            ("runtime", '"image", "rm"', '"system", "prune"'),
            ("runtime", "deployment_plan.generate_bundle(", "fake_bundle("),
            ("runtime", "root = _prepare_root(bound[\"runId\"])", 'root = Path(_required("RUNNER_TEMP"))'),
            ("runtime", 'failed_stage = "BUNDLE_GENERATION"', 'failed_stage = "PREPARE_ROOT"'),
            ("runtime", "current_path=None", 'current_path=root / "current"'),
            (
                "runtime",
                "current_manifest_path=None",
                'current_manifest_path=root / "releases/v0.1.0/manifest.json"',
            ),
            ("runtime", "deployment_cli.py", "fake_cli.py"),
            ("runtime", "_remove_ephemeral_root(root, run_id=run_id)", "pass # leaked root"),
        )
        for target_name, old, new in mutants:
            with self.subTest(old=old):
                temporary, root = self.package_copy()
                self.addCleanup(temporary.cleanup)
                relative = ".github/workflows/verify-deployment-engine.yml" if target_name == "workflow" else "tools/deploy/deployment_engine_rehearsal.py"
                path = root / relative
                source = path.read_text(encoding="utf-8")
                self.assertIn(old, source)
                path.write_text(source.replace(old, new, 1), encoding="utf-8")
                self.assertTrue(validate(root), old)

    def test_canonical_artifacts_bind_success_and_cleanup(self) -> None:
        with tempfile.TemporaryDirectory() as raw, mock.patch.dict(os.environ, environment(), clear=True):
            self.assertTrue(Path(raw).is_dir())
            value = {
                "schemaVersion": 1,
                "kind": "deployment-engine-rehearsal",
                **rehearsal.binding(),
                "release": rehearsal.RELEASE,
                "releaseId": rehearsal.RELEASE_ID,
                "previousRelease": rehearsal.PREVIOUS_RELEASE,
                "operationId": rehearsal.OPERATION,
                "status": "SUCCESS",
                "errorCode": None,
                "failedStage": None,
                "journalSha256": "sha256:" + "a" * 64,
                "installedStateSha256": "sha256:" + "b" * 64,
                "steps": [{"name": name, "status": "SUCCEEDED"} for name in rehearsal.EXPECTED_STEPS],
                "backup": [{"id": "erp", "size": 1, "sha256": "sha256:" + "c" * 64}, {"id": "website", "size": 1, "sha256": "sha256:" + "d" * 64}],
                "services": [{"id": str(index), "immutableRef": "sha256:" + str(index) * 64} for index in range(1, 8)],
                "current": rehearsal.RELEASE,
                "previous": None,
                "replay": {"journalUnchanged": True, "backupUnchanged": True, "containersUnchanged": True},
                "cleanup": {"containers": 0, "volumes": 0, "networks": 0, "images": 0},
            }
            rehearsal._validate_rehearsal(value, success=True)
            value["cleanup"]["volumes"] = 1
            with self.assertRaises(rehearsal.RehearsalError):
                rehearsal._validate_rehearsal(value, success=True)

    def test_ephemeral_root_passes_both_real_guards(self) -> None:
        with mock.patch.dict(os.environ, {"RUNNER_TEMP": "/outside-checkout"}):
            root = rehearsal._prepare_root(123)
        self.addCleanup(
            lambda: root.exists()
            and rehearsal._remove_ephemeral_root(root, run_id=123)
        )
        details = root.lstat()
        self.assertEqual(root.parent.resolve(), ROOT.resolve())
        self.assertTrue(root.name.startswith(".s46-engine-123-"))
        self.assertTrue(stat.S_ISDIR(details.st_mode))
        self.assertEqual(stat.S_IMODE(details.st_mode), 0o700)
        self.assertEqual(details.st_uid, os.geteuid())
        self.assertEqual(
            deployment_plan._validate_output_path(root / "bundle"),
            root / "bundle",
        )
        self.assertEqual(deployment_cli._validate_root(root), root)

    def test_ephemeral_root_mutants_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory(dir=ROOT) as raw:
            root = Path(raw)
            root.chmod(0o700)
            with self.assertRaisesRegex(rehearsal.RehearsalError, "PREPARE_ROOT_FAILED"):
                rehearsal._validate_ephemeral_root(root, run_id=123)
        outside = Path(tempfile.mkdtemp(prefix=".s46-engine-123-", dir="/tmp"))
        outside.chmod(0o700)
        self.addCleanup(shutil.rmtree, outside, True)
        with self.assertRaisesRegex(rehearsal.RehearsalError, "PREPARE_ROOT_FAILED"):
            rehearsal._validate_ephemeral_root(outside, run_id=123)
        root = rehearsal._prepare_root(123)
        self.addCleanup(shutil.rmtree, root, True)
        root.chmod(0o770)
        with self.assertRaisesRegex(rehearsal.RehearsalError, "PREPARE_ROOT_FAILED"):
            rehearsal._validate_ephemeral_root(root, run_id=123)
        root.chmod(0o700)
        target = ROOT / ".s46-engine-123-symlink-target"
        link = ROOT / ".s46-engine-123-symlink"
        target.mkdir(mode=0o700)
        link.symlink_to(target, target_is_directory=True)
        self.addCleanup(link.unlink)
        self.addCleanup(target.rmdir)
        with self.assertRaisesRegex(rehearsal.RehearsalError, "PREPARE_ROOT_FAILED"):
            rehearsal._validate_ephemeral_root(link, run_id=123)

    def test_failed_stage_and_error_code_are_closed(self) -> None:
        with mock.patch.dict(os.environ, environment(), clear=True):
            bound = rehearsal.binding()
        baseline = {
            "schemaVersion": 1,
            "kind": "deployment-engine-rehearsal",
            **bound,
            "release": rehearsal.RELEASE,
            "releaseId": rehearsal.RELEASE_ID,
            "previousRelease": rehearsal.PREVIOUS_RELEASE,
            "operationId": rehearsal.OPERATION,
            "status": "FAILED",
            "journalSha256": None,
            "installedStateSha256": None,
            "steps": [],
            "backup": [],
            "services": [],
            "current": None,
            "previous": None,
            "replay": {"journalUnchanged": False, "backupUnchanged": False, "containersUnchanged": False},
            "cleanup": {"containers": 0, "volumes": 0, "networks": 0, "images": 0},
        }
        with mock.patch.dict(os.environ, environment(), clear=True):
            for stage, code in rehearsal.STAGE_ERRORS.items():
                with self.subTest(stage=stage):
                    value = {**baseline, "failedStage": stage, "errorCode": code}
                    rehearsal._validate_rehearsal(value, success=False)
                    mutated = {**value, "errorCode": "REHEARSAL_FAILED"}
                    with self.assertRaises(rehearsal.RehearsalError):
                        rehearsal._validate_rehearsal(mutated, success=False)
            for invalid in ("", "INTERNAL", "/absolute/path"):
                value = {**baseline, "failedStage": invalid, "errorCode": "REHEARSAL_FAILED"}
                with self.assertRaises(rehearsal.RehearsalError):
                    rehearsal._validate_rehearsal(value, success=False)

    def test_runtime_classifies_each_failure_stage_without_internal_detail(self) -> None:
        zero = {"containers": 0, "volumes": 0, "networks": 0, "images": 0}
        manifest = {
            "components": [
                {"immutableRef": f"ghcr.io/example/component-{index}@sha256:" + "a" * 64}
                for index in range(6)
            ]
        }
        with mock.patch.dict(os.environ, environment(), clear=True):
            bound = rehearsal.binding()
            trust = {
                "schemaVersion": 1,
                "kind": "deployment-engine-trust",
                **bound,
                "status": "TRUSTED",
            }
            cases = (
                ("PREPARE_ROOT", OSError("private path"), None, None),
                ("BUNDLE_GENERATION", None, deployment_plan.DeploymentPlanError("UNSAFE_PATH"), None),
                ("DEPLOYMENT_CLI", None, None, rehearsal.subprocess.CompletedProcess((), 3, b"", b"private")),
                ("TRANSACTION_EVIDENCE", None, None, rehearsal.subprocess.CompletedProcess((), 0, b"", b"")),
                ("CLEANUP", None, deployment_plan.DeploymentPlanError("UNSAFE_PATH"), None),
            )
            for stage, prepare_error, bundle_error, command_result in cases:
                with self.subTest(stage=stage), tempfile.TemporaryDirectory(dir=ROOT) as raw:
                    parent = Path(raw)
                    output = parent / "artifact"
                    root = None if prepare_error else rehearsal._prepare_root(123)
                    cleanup_effect: object = (
                        rehearsal.RehearsalError("private cleanup detail")
                        if stage == "CLEANUP"
                        else zero
                    )
                    with (
                        mock.patch.object(rehearsal, "_load_bundle", return_value=trust),
                        mock.patch.object(rehearsal, "_release", return_value=manifest),
                        mock.patch.object(
                            rehearsal,
                            "_prepare_root",
                            side_effect=prepare_error,
                            return_value=root,
                        ),
                        mock.patch.object(
                            rehearsal,
                            "_env",
                            return_value=(parent / "env", parent / "identity", {}),
                        ),
                        mock.patch.object(
                            rehearsal.deployment_plan,
                            "generate_bundle",
                            side_effect=bundle_error,
                        ),
                        mock.patch.object(
                            rehearsal,
                            "_run",
                            return_value=command_result,
                        ),
                        mock.patch.object(
                            rehearsal,
                            "_cleanup",
                            side_effect=cleanup_effect if isinstance(cleanup_effect, Exception) else None,
                            return_value=cleanup_effect if isinstance(cleanup_effect, dict) else None,
                        ),
                    ):
                        with self.assertRaises(rehearsal.RehearsalError):
                            rehearsal.rehearse(parent / "trust", parent / "assets", output)
                    value = json.loads((output / rehearsal.REHEARSAL_FILE).read_bytes())
                    self.assertEqual(value["failedStage"], stage)
                    self.assertEqual(value["errorCode"], rehearsal.STAGE_ERRORS[stage])
                    rendered = json.dumps(value, sort_keys=True)
                    self.assertNotIn("private", rendered)
                    self.assertNotIn(os.fspath(parent), rendered)
                    if root is not None and root.exists():
                        shutil.rmtree(root)


if __name__ == "__main__":
    unittest.main()
