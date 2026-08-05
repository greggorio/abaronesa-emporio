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
from tools.deploy import deployment_executor
from tools.deploy import deployment_plan
from tools.deploy import production_adapter
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
            ("workflow", "timeout-minutes: 90", "timeout-minutes: 45"),
            ("workflow", 'chmod 0700 "$HOME/.docker"', "true # directory normalization omitted"),
            ("workflow", "gh release download v0.1.1", "gh release download v0.1.2"),
            ("workflow", "id: rehearsal\n        continue-on-error: true", "id: rehearsal\n        continue-on-error: false"),
            ("runtime", '"down", "-v", "--remove-orphans"', '"down"'),
            ("runtime", '"image", "rm"', '"system", "prune"'),
            ("runtime", "deployment_plan.generate_bundle(", "fake_bundle("),
            ("runtime", "root = _prepare_root(bound[\"runId\"])", 'root = Path(_required("RUNNER_TEMP"))'),
            ("runtime", 'failed_stage = "BUNDLE_GENERATION"', 'failed_stage = "PREPARE_ROOT"'),
            (
                "runtime",
                "postgres:16.10-alpine3.22@sha256:029660641a0cfc575b14f336ba448fb8a75fd595d42e1fa316b9fb4378742297",
                "postgres@sha256:029660641a0cfc575b14f336ba448fb8a75fd595d42e1fa316b9fb4378742297",
            ),
            (
                "runtime",
                ', "COMMIT_STATE", "ROLLBACK")',
                ', "COMMIT_STATE")',
            ),
            ("runtime", "current_path=None", 'current_path=root / "current"'),
            (
                "runtime",
                "current_manifest_path=None",
                'current_manifest_path=root / "releases/v0.1.0/manifest.json"',
            ),
            ("runtime", "deployment_cli.py", "fake_cli.py"),
            ("runtime", "destination.chmod(0o755)", "destination.chmod(0o700)"),
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
                "transactionStatus": "SUCCESS",
                "cleanupStatus": "SUCCESS",
                "status": "SUCCESS",
                "errorCode": None,
                "failedStage": None,
                "cliExit": 0,
                "causeCode": None,
                "journal": {
                    "state": "SUCCEEDED",
                    "errorCode": None,
                    "rollbackErrorCode": None,
                    "databaseRestoreRequired": True,
                    "steps": [
                        {
                            "name": name,
                            "status": "PENDING" if name == "ROLLBACK" else "SUCCEEDED",
                            "errorCode": None,
                        }
                        for name in rehearsal.EXPECTED_STEPS
                    ],
                },
                "postgresManifestResolved": True,
                "journalSha256": "sha256:" + "a" * 64,
                "installedStateSha256": "sha256:" + "b" * 64,
                "steps": [
                    {
                        "name": name,
                        "status": "PENDING" if name == "ROLLBACK" else "SUCCEEDED",
                    }
                    for name in rehearsal.EXPECTED_STEPS
                ],
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

    def test_database_initializer_preserves_the_versioned_executable_mode(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            releases = Path(raw) / "releases"
            releases.mkdir(mode=0o700)
            destination = rehearsal._materialize_database_initializer(releases)
            observed = stat.S_IMODE(destination.stat().st_mode)
            versioned = stat.S_IMODE(
                (ROOT / "ops/db/init-databases.sh").stat().st_mode
            )
            self.assertEqual(0o755, observed)
            self.assertEqual(versioned, observed)
            self.assertEqual(0, observed & 0o022)

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
            "transactionStatus": "FAILED",
            "cleanupStatus": "SUCCESS",
            "status": "FAILED",
            "cliExit": 6,
            "causeCode": rehearsal.CLI_CAUSE_UNAVAILABLE,
            "journal": None,
            "postgresManifestResolved": False,
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
                    value = {
                        **baseline,
                        "failedStage": stage,
                        "errorCode": code,
                        "cleanupStatus": "FAILED" if stage == "CLEANUP" else "SUCCESS",
                    }
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
                            "_capture_baseline",
                            return_value={
                                "containers": frozenset(),
                                "images": {},
                                "volumes": {},
                                "networks": {},
                            },
                        ),
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
                            "_resolve_postgres_manifest",
                            return_value=False,
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
                    self.assertNotIn("init-databases.sh", rendered)
                    self.assertNotIn('"0755"', rendered)
                    self.assertNotIn('"owner"', rendered)
                    self.assertNotIn("ON_ERROR_STOP", rendered)
                    if root is not None and root.exists():
                        shutil.rmtree(root)

    def test_postgres_image_and_executor_steps_are_causally_aligned(self) -> None:
        runner = mock.Mock()
        runner.run.return_value = production_adapter.ProcessResult(1, b"")
        instance = object.__new__(production_adapter.ProductionDeploymentAdapter)
        instance.runner = runner
        instance.docker = Path("/usr/bin/docker")
        self.assertEqual(instance._image_probe(rehearsal.POSTGRES_IMAGE), "ABSENT")
        self.assertEqual(
            instance._image_probe(
                "postgres@sha256:"
                + rehearsal.POSTGRES_IMAGE.rsplit("@sha256:", 1)[1]
            ),
            "UNKNOWN",
        )
        self.assertEqual(rehearsal.EXPECTED_STEPS, deployment_executor.STEPS)
        self.assertEqual(runner.run.call_count, 1)

    def test_success_transaction_requires_restore_true_and_rollback_pending(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            (root / "releases/v0.1.1").mkdir(parents=True)
            (root / "current").symlink_to("releases/v0.1.1")
            journal = {
                "state": "SUCCEEDED",
                "errorCode": None,
                "databaseRestoreRequired": True,
                "steps": [
                    {
                        "name": name,
                        "status": "PENDING" if name == "ROLLBACK" else "SUCCEEDED",
                    }
                    for name in rehearsal.EXPECTED_STEPS
                ],
            }
            state = {"release": rehearsal.RELEASE, "reconciled": True}
            backup = {
                "databases": [
                    {"id": "erp", "size": 1},
                    {"id": "website", "size": 1},
                ]
            }
            self.assertTrue(
                rehearsal._transaction_valid(journal, state, backup, root)
            )
            restore_mutant = json.loads(json.dumps(journal))
            restore_mutant["databaseRestoreRequired"] = False
            self.assertFalse(
                rehearsal._transaction_valid(restore_mutant, state, backup, root)
            )
            rollback_mutant = json.loads(json.dumps(journal))
            rollback_mutant["steps"][-1]["status"] = "SUCCEEDED"
            self.assertFalse(
                rehearsal._transaction_valid(rollback_mutant, state, backup, root)
            )

    def test_cli_evidence_is_closed_for_prejournal_terminal_and_invalid_output(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            journal_path = Path(raw) / "journal.json"
            prejournal = rehearsal._cli_evidence(
                rehearsal.subprocess.CompletedProcess(
                    (), 4, b"", rehearsal.canonical({"errorCode": "UNSAFE_PATH"})
                ),
                journal_path,
            )
            self.assertEqual(
                prejournal,
                {"cliExit": 4, "causeCode": "UNSAFE_PATH", "journal": None},
            )
            journal = {
                "state": "FAILED",
                "errorCode": "PULL_FAILED",
                "rollbackErrorCode": None,
                "databaseRestoreRequired": True,
                "steps": [
                    {
                        "name": name,
                        "status": "FAILED" if name == "PULL" else "PENDING",
                        "errorCode": "PULL_FAILED" if name == "PULL" else None,
                    }
                    for name in rehearsal.EXPECTED_STEPS
                ],
            }
            journal_path.write_bytes(rehearsal.canonical(journal))
            terminal = rehearsal._cli_evidence(
                rehearsal.subprocess.CompletedProcess(
                    (),
                    21,
                    rehearsal.canonical(
                        {
                            "databaseRestoreRequired": True,
                            "errorCode": "PULL_FAILED",
                            "operationId": rehearsal.OPERATION,
                            "state": "FAILED",
                        }
                    ),
                    b"",
                ),
                journal_path,
            )
            self.assertEqual(terminal["causeCode"], "PULL_FAILED")
            self.assertEqual(terminal["journal"]["steps"][0]["status"], "FAILED")
            invalid = rehearsal._cli_evidence(
                rehearsal.subprocess.CompletedProcess(
                    (), 3, b"/private/path\n", b'{"errorCode":"UNSAFE_PATH"}\ntraceback\n'
                ),
                journal_path,
            )
            self.assertEqual(invalid["causeCode"], rehearsal.CLI_CAUSE_UNAVAILABLE)
            self.assertIsNone(invalid["journal"])
            rendered = json.dumps(invalid, sort_keys=True)
            self.assertNotIn("private", rendered)
            self.assertNotIn("traceback", rendered)

    def test_cleanup_preserves_baseline_image_and_removes_created_image(self) -> None:
        image = rehearsal.POSTGRES_IMAGE
        calls: list[tuple[str, ...]] = []
        present = {image: True}

        def run(
            argv: tuple[str, ...],
            *,
            environment: dict[str, str] | None = None,
            timeout: int = 2700,
        ) -> rehearsal.subprocess.CompletedProcess[bytes]:
            del environment, timeout
            calls.append(argv)
            if argv[1:3] == ("ps", "-aq"):
                return rehearsal.subprocess.CompletedProcess(argv, 0, b"", b"")
            if argv[1:3] == ("image", "inspect"):
                return rehearsal.subprocess.CompletedProcess(
                    argv, 0 if present[image] else 1, b"", b""
                )
            if argv[1:3] == ("image", "rm"):
                present[image] = False
                return rehearsal.subprocess.CompletedProcess(argv, 0, b"", b"")
            raise AssertionError(argv)

        with mock.patch.object(rehearsal, "_run", side_effect=run):
            preserved = rehearsal._cleanup(
                None,
                None,
                None,
                None,
                [image],
                {},
                {
                    "containers": frozenset(),
                    "images": {image: True},
                    "volumes": {},
                    "networks": {},
                },
                run_id=123,
            )
            self.assertEqual(preserved["images"], 0)
            self.assertTrue(present[image])
            self.assertFalse(any(call[1:3] == ("image", "rm") for call in calls))

            calls.clear()
            created = rehearsal._cleanup(
                None,
                None,
                None,
                None,
                [image],
                {},
                {
                    "containers": frozenset(),
                    "images": {image: False},
                    "volumes": {},
                    "networks": {},
                },
                run_id=123,
            )
            self.assertEqual(created["images"], 0)
            self.assertFalse(present[image])
            self.assertTrue(any(call[1:3] == ("image", "rm") for call in calls))

    def test_cleanup_nonzero_down_image_rm_and_shred_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            base = Path(raw)
            bundle = base / "bundle"
            bundle.mkdir()
            (bundle / "release.env").write_text("X=y\n", encoding="utf-8")
            (bundle / "compose.prod.yml").write_text("services: {}\n", encoding="utf-8")
            env_file = base / ".env"
            env_file.write_text("X=y\n", encoding="utf-8")

            def down_failure(
                argv: tuple[str, ...], **_kwargs: object
            ) -> rehearsal.subprocess.CompletedProcess[bytes]:
                if "down" in argv:
                    return rehearsal.subprocess.CompletedProcess(argv, 1, b"", b"")
                if argv[1:3] == ("ps", "-aq"):
                    return rehearsal.subprocess.CompletedProcess(argv, 0, b"", b"")
                if argv[0] == "/usr/bin/shred":
                    Path(argv[-1]).unlink(missing_ok=True)
                    return rehearsal.subprocess.CompletedProcess(argv, 0, b"", b"")
                raise AssertionError(argv)

            with mock.patch.object(rehearsal, "_run", side_effect=down_failure):
                with self.assertRaises(rehearsal.CleanupError):
                    rehearsal._cleanup(
                        None,
                        bundle,
                        env_file,
                        None,
                        [],
                        {},
                        {
                            "containers": frozenset(),
                            "images": {},
                            "volumes": {},
                            "networks": {},
                        },
                        run_id=123,
                    )

            image = rehearsal.POSTGRES_IMAGE

            def image_rm_failure(
                argv: tuple[str, ...], **_kwargs: object
            ) -> rehearsal.subprocess.CompletedProcess[bytes]:
                if argv[1:3] == ("ps", "-aq"):
                    return rehearsal.subprocess.CompletedProcess(argv, 0, b"", b"")
                if argv[1:3] == ("image", "inspect"):
                    return rehearsal.subprocess.CompletedProcess(argv, 0, b"", b"")
                if argv[1:3] == ("image", "rm"):
                    return rehearsal.subprocess.CompletedProcess(argv, 1, b"", b"")
                raise AssertionError(argv)

            with mock.patch.object(rehearsal, "_run", side_effect=image_rm_failure):
                with self.assertRaises(rehearsal.CleanupError):
                    rehearsal._cleanup(
                        None,
                        None,
                        None,
                        None,
                        [image],
                        {},
                        {
                            "containers": frozenset(),
                            "images": {image: False},
                            "volumes": {},
                            "networks": {},
                        },
                        run_id=123,
                    )

            secret = base / "secret"
            secret.write_text("opaque\n", encoding="utf-8")

            def shred_failure(
                argv: tuple[str, ...], **_kwargs: object
            ) -> rehearsal.subprocess.CompletedProcess[bytes]:
                if argv[1:3] == ("ps", "-aq"):
                    return rehearsal.subprocess.CompletedProcess(argv, 0, b"", b"")
                if argv[0] == "/usr/bin/shred":
                    return rehearsal.subprocess.CompletedProcess(argv, 1, b"", b"")
                raise AssertionError(argv)

            with mock.patch.object(rehearsal, "_run", side_effect=shred_failure):
                with self.assertRaises(rehearsal.CleanupError):
                    rehearsal._cleanup(
                        None,
                        None,
                        None,
                        secret,
                        [],
                        {},
                        {
                            "containers": frozenset(),
                            "images": {},
                            "volumes": {},
                            "networks": {},
                        },
                        run_id=123,
                    )

    def test_journal_capture_precedes_cleanup_and_receipt_follows_cleanup(self) -> None:
        sequence: list[str] = []
        captured: dict[str, object] = {}
        manifest = {
            "components": [
                {"id": f"service-{index}", "immutableRef": f"ghcr.io/example/service-{index}@sha256:" + "a" * 64}
                for index in range(6)
            ]
        }
        with tempfile.TemporaryDirectory(dir=ROOT) as raw, mock.patch.dict(
            os.environ, environment(), clear=True
        ):
            root = Path(raw) / "root"
            root.mkdir(mode=0o700)
            trust = {
                "schemaVersion": 1,
                "kind": "deployment-engine-trust",
                **rehearsal.binding(),
                "status": "TRUSTED",
            }

            def fake_env(
                selected: Path, _manifest: dict[str, object], run_id: int
            ) -> tuple[Path, Path, dict[str, str]]:
                shared = selected / "shared"
                shared.mkdir(mode=0o700)
                env_file = shared / ".env"
                identity = shared / "identity"
                env_file.write_text("FICTITIOUS=true\n", encoding="utf-8")
                identity.write_text("opaque\n", encoding="utf-8")
                env_file.chmod(0o600)
                identity.chmod(0o600)
                return env_file, identity, rehearsal._resource_names(run_id)

            real_cli_evidence = rehearsal._cli_evidence

            def capture_cli(
                completed: rehearsal.subprocess.CompletedProcess[bytes],
                journal_path: Path,
            ) -> dict[str, object]:
                sequence.append("journal")
                return real_cli_evidence(completed, journal_path)

            def cleanup(*_args: object, **_kwargs: object) -> dict[str, int]:
                sequence.append("cleanup")
                return {"containers": 0, "volumes": 0, "networks": 0, "images": 0}

            def write(
                _directory: Path, _name: str, value: dict[str, object]
            ) -> None:
                sequence.append("receipt")
                captured.update(value)

            with (
                mock.patch.object(rehearsal, "_load_bundle", return_value=trust),
                mock.patch.object(rehearsal, "_release", return_value=manifest),
                mock.patch.object(
                    rehearsal,
                    "_capture_baseline",
                    return_value={
                        "containers": frozenset(),
                        "images": {},
                        "volumes": {},
                        "networks": {},
                    },
                ),
                mock.patch.object(rehearsal, "_prepare_root", return_value=root),
                mock.patch.object(rehearsal, "_env", side_effect=fake_env),
                mock.patch.object(rehearsal.deployment_plan, "generate_bundle"),
                mock.patch.object(
                    rehearsal, "_resolve_postgres_manifest", return_value=False
                ),
                mock.patch.object(
                    rehearsal,
                    "_run",
                    return_value=rehearsal.subprocess.CompletedProcess(
                        (), 4, b"", rehearsal.canonical({"errorCode": "UNSAFE_PATH"})
                    ),
                ),
                mock.patch.object(rehearsal, "_cli_evidence", side_effect=capture_cli),
                mock.patch.object(rehearsal, "_cleanup", side_effect=cleanup),
                mock.patch.object(rehearsal, "_write_bundle", side_effect=write),
            ):
                with self.assertRaises(rehearsal.RehearsalError):
                    rehearsal.rehearse(Path(raw) / "trust", Path(raw) / "assets", Path(raw) / "output")
            self.assertEqual(sequence, ["journal", "cleanup", "receipt"])
            self.assertEqual(captured["transactionStatus"], "FAILED")
            self.assertEqual(captured["cleanupStatus"], "SUCCESS")
            self.assertEqual(captured["status"], "FAILED")
            self.assertEqual(captured["causeCode"], "UNSAFE_PATH")

    def test_global_success_requires_transaction_and_cleanup_success(self) -> None:
        self.assertEqual(
            rehearsal._overall_status("SUCCESS", "SUCCESS"), "SUCCESS"
        )
        self.assertEqual(
            rehearsal._overall_status("SUCCESS", "FAILED"), "FAILED"
        )
        self.assertEqual(
            rehearsal._overall_status("FAILED", "SUCCESS"), "FAILED"
        )
        self.assertEqual(
            rehearsal._overall_status("FAILED", "FAILED"), "FAILED"
        )


if __name__ == "__main__":
    unittest.main()
