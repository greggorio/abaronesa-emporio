from __future__ import annotations

import copy
import fcntl
import hashlib
import importlib.util
import json
import os
import shutil
import stat
import sys
import tempfile
import unittest
from pathlib import Path
from typing import Any
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[3]


def load_module(name: str, path: Path) -> Any:
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    assert spec and spec.loader
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


planner = load_module("deployment_plan", ROOT / "tools/deploy/deployment_plan.py")
executor = load_module(
    "deployment_executor", ROOT / "tools/deploy/deployment_executor.py"
)

TARGET_EXAMPLE = ROOT / "ops/releases/examples/global-release.example.json"
COMPOSE = ROOT / "ops/compose/compose.prod.yml"
OPERATION_ID = "deployment_0123456789abcdef"
COMPONENTS = (
    "backend",
    "website_back",
    "frontend",
    "website_front",
    "whatsapp_service",
    "gateway",
)
DATABASES = ("erp", "website")
ADAPTER_ACTIONS = ("PULL", "BACKUP", "MIGRATE", "UPDATE", "VERIFY")


class SimulatedCrash(BaseException):
    """Crash boundary deliberately outside the executor's Exception handling."""


class FakeClock:
    def __init__(self, values: list[str] | None = None) -> None:
        self.values = list(values or [])
        self.last = "2026-07-29T17:00:00Z"
        self.calls = 0

    def now(self) -> str:
        self.calls += 1
        if self.values:
            self.last = self.values.pop(0)
        return self.last


class FakeAdapter:
    def __init__(
        self,
        scripts: dict[str, list[Any]] | None = None,
        execute_errors: dict[str, Exception] | None = None,
        execute_results: dict[str, Any] | None = None,
        state_path: Path | None = None,
        journal_path: Path | None = None,
    ) -> None:
        self.scripts = {key: list(value) for key, value in (scripts or {}).items()}
        self.execute_errors = execute_errors or {}
        self.execute_results = execute_results or {}
        self.state_path = state_path
        self.journal_path = journal_path
        self.events: list[tuple[str, str]] = []
        self.contexts: list[Any] = []
        self.state_presence: list[tuple[str, bool]] = []

    def probe(self, context: Any) -> Any:
        self.events.append(("probe", context.action))
        self.contexts.append(context)
        if self.state_path is not None:
            self.state_presence.append(
                (f"probe:{context.action}", self.state_path.exists())
            )
        values = self.scripts.setdefault(
            context.action,
            [
                executor.ProbeResult(
                    "ABSENT", "2026-07-29T17:00:00Z", None
                ),
                executor.ProbeResult(
                    "SUCCEEDED",
                    "2026-07-29T17:00:00Z",
                    f"evidence_{context.action.lower()}",
                ),
            ],
        )
        if not values:
            return executor.ProbeResult(
                "SUCCEEDED",
                "2026-07-29T17:00:00Z",
                f"evidence_{context.action.lower()}",
            )
        value = values.pop(0)
        if isinstance(value, Exception):
            raise value
        return value

    def execute(self, context: Any) -> Any:
        self.events.append(("execute", context.action))
        self.contexts.append(context)
        if self.journal_path is not None:
            self.state_presence.append(
                (f"journal:{context.action}", self.journal_path.exists())
            )
        if context.action in self.execute_errors:
            raise self.execute_errors[context.action]
        return self.execute_results.get(context.action)


def succeeded(action: str) -> Any:
    return executor.ProbeResult(
        "SUCCEEDED", "2026-07-29T17:00:00Z", f"evidence_{action.lower()}"
    )


def absent() -> Any:
    return executor.ProbeResult("ABSENT", "2026-07-29T17:00:00Z", None)


def failed() -> Any:
    return executor.ProbeResult("FAILED", "2026-07-29T17:00:00Z", None)


def unknown() -> Any:
    return executor.ProbeResult("UNKNOWN", "2026-07-29T17:00:00Z", None)


class DeploymentExecutorTest(unittest.TestCase):
    def setUp(self) -> None:
        self.root = Path(tempfile.mkdtemp(prefix="s19-executor-"))
        self.addCleanup(shutil.rmtree, self.root, True)
        os.chmod(self.root, 0o700)
        self.journals = self.root / "journals"
        self.state_parent = self.root / "state"
        self.journals.mkdir(mode=0o700)
        self.state_parent.mkdir(mode=0o700)
        self.state_path = self.state_parent / "installed-state.json"
        self.bundle = self.root / "bundle"
        planner.generate_bundle(
            target_path=TARGET_EXAMPLE,
            current_path=None,
            current_manifest_path=None,
            compose_path=COMPOSE,
            planned_at="2026-07-29T16:00:00Z",
            output_path=self.bundle,
        )

    @property
    def journal_path(self) -> Path:
        return self.journals / f"{OPERATION_ID}.json"

    def execute(
        self,
        adapter: FakeAdapter | None = None,
        clock: FakeClock | None = None,
        operation_id: str = OPERATION_ID,
        bundle: Path | None = None,
    ) -> dict[str, Any]:
        return executor.execute_deployment(
            bundle=bundle or self.bundle,
            operation_id=operation_id,
            journal_dir=self.journals,
            installed_state_path=self.state_path,
            adapter=adapter or FakeAdapter(),
            clock=clock or FakeClock(),
        )

    def current_from_target(self, target: dict[str, Any]) -> dict[str, Any]:
        value = planner.build_next_state(target, "2026-07-29T16:10:00Z")
        value["reconciled"] = True
        value["installedAt"] = "2026-07-29T16:20:00Z"
        return value

    def update_bundle(self) -> tuple[Path, dict[str, Any], dict[str, Any]]:
        current_manifest = planner.load_target(TARGET_EXAMPLE)
        current = self.current_from_target(current_manifest)
        target = copy.deepcopy(current_manifest)
        target["release"] = "v0.0.2"
        target["previousRelease"] = "v0.0.1"
        target["publishedAt"] = "2026-07-29T16:30:00Z"
        current_path = self.root / "current.json"
        current_manifest_path = self.root / "current-manifest.json"
        target_path = self.root / "target.json"
        current_path.write_bytes(planner.canonical_bytes(current) + b"\n")
        current_manifest_path.write_bytes(
            planner.global_release.canonical(current_manifest)
        )
        target_path.write_bytes(planner.global_release.canonical(target))
        os.chmod(current_path, 0o600)
        output = self.root / "update-bundle"
        planner.generate_bundle(
            target_path=target_path,
            current_path=current_path,
            current_manifest_path=current_manifest_path,
            compose_path=COMPOSE,
            planned_at="2026-07-29T17:00:00Z",
            output_path=output,
        )
        return output, current, target

    def assert_error(self, code: str, call: Any) -> None:
        with self.assertRaises(executor.DeploymentExecutionError) as caught:
            call()
        self.assertEqual(caught.exception.code, code)
        self.assertEqual(str(caught.exception), code)

    def leave_pull_running(self) -> bytes:
        with self.assertRaises(SimulatedCrash):
            self.execute(
                FakeAdapter(execute_errors={"PULL": SimulatedCrash()})
            )
        raw = self.journal_path.read_bytes()
        journal = json.loads(raw)
        pull = next(
            item for item in journal["steps"] if item["name"] == "PULL"
        )
        self.assertEqual((journal["state"], pull["status"]), ("PULLING", "RUNNING"))
        return raw

    def write_conflicting_state(self) -> None:
        self.state_path.write_bytes(b"{}\n")
        os.chmod(self.state_path, 0o600)

    def spread_success_timestamps(self) -> dict[str, Any]:
        self.execute()
        journal = json.loads(self.journal_path.read_bytes())
        transition_times = (
            "2026-07-29T17:00:00Z",
            "2026-07-29T17:00:10Z",
            "2026-07-29T17:00:20Z",
            "2026-07-29T17:00:30Z",
            "2026-07-29T17:00:40Z",
            "2026-07-29T17:00:50Z",
            "2026-07-29T17:01:20Z",
        )
        for transition, timestamp in zip(
            journal["transitions"], transition_times, strict=True
        ):
            transition["at"] = timestamp
        step_times = {
            "PULL": ("2026-07-29T17:00:11Z", "2026-07-29T17:00:12Z"),
            "BACKUP": ("2026-07-29T17:00:21Z", "2026-07-29T17:00:22Z"),
            "MIGRATE": ("2026-07-29T17:00:31Z", "2026-07-29T17:00:32Z"),
            "UPDATE": ("2026-07-29T17:00:41Z", "2026-07-29T17:00:42Z"),
            "VERIFY": ("2026-07-29T17:00:51Z", "2026-07-29T17:00:52Z"),
            "COMMIT_STATE": (
                "2026-07-29T17:00:53Z",
                "2026-07-29T17:00:54Z",
            ),
        }
        for step in journal["steps"]:
            if step["name"] not in step_times:
                continue
            started_at, finished_at = step_times[step["name"]]
            step["startedAt"] = started_at
            step["finishedAt"] = finished_at
            step["evidence"]["observedAt"] = finished_at
        journal["createdAt"] = transition_times[0]
        journal["updatedAt"] = transition_times[-1]
        journal["finishedAt"] = transition_times[-1]
        executor._validate_journal(journal)
        return journal

    def test_01_complete_flow_has_exact_order_and_succeeds(self) -> None:
        adapter = FakeAdapter()
        journal = self.execute(adapter)
        self.assertEqual(journal["state"], "SUCCEEDED")
        self.assertEqual(
            adapter.events,
            [
                event
                for action in ADAPTER_ACTIONS
                for event in (("probe", action), ("execute", action), ("probe", action))
            ],
        )

    def test_02_first_installation_commits_only_after_verify(self) -> None:
        adapter = FakeAdapter(state_path=self.state_path)
        self.execute(adapter)
        self.assertTrue(
            all(not exists for label, exists in adapter.state_presence if "VERIFY" not in label)
        )
        self.assertTrue(self.state_path.is_file())

    def test_03_update_requires_and_accepts_coherent_source_state(self) -> None:
        bundle, current, _target = self.update_bundle()
        self.state_path.write_bytes(planner.canonical_bytes(current) + b"\n")
        os.chmod(self.state_path, 0o600)
        journal = self.execute(bundle=bundle)
        self.assertEqual(journal["state"], "SUCCEEDED")
        self.assertIsNotNone(journal["sourceStateSha256"])

    def test_04_each_current_component_digest_divergence_precedes_adapter(self) -> None:
        for index, component in enumerate(COMPONENTS):
            with self.subTest(component=component):
                bundle, current, _target = self.update_bundle()
                current["components"][index]["immutableRef"] = (
                    current["components"][index]["immutableRef"][:-1] + "f"
                )
                self.state_path.write_bytes(planner.canonical_bytes(current) + b"\n")
                os.chmod(self.state_path, 0o600)
                adapter = FakeAdapter()
                self.assert_error(
                    "CURRENT_STATE_CONFLICT",
                    lambda: self.execute(adapter, bundle=bundle),
                )
                self.assertEqual(adapter.events, [])
                shutil.rmtree(bundle)

    def test_05_each_current_migration_set_divergence_precedes_adapter(self) -> None:
        for database_index in range(2):
            with self.subTest(database=DATABASES[database_index]):
                bundle, current, _target = self.update_bundle()
                current["databases"][database_index]["migrationSetSha256"] = (
                    "sha256:" + "f" * 64
                )
                self.state_path.write_bytes(planner.canonical_bytes(current) + b"\n")
                os.chmod(self.state_path, 0o600)
                adapter = FakeAdapter()
                self.assert_error(
                    "CURRENT_STATE_CONFLICT",
                    lambda: self.execute(adapter, bundle=bundle),
                )
                self.assertEqual(adapter.events, [])
                shutil.rmtree(bundle)

    def test_06_invalid_bundle_fails_before_journal_and_adapter(self) -> None:
        (self.bundle / "bundle.sha256").write_text("invalid\n")
        adapter = FakeAdapter()
        self.assert_error("INVALID_CONTRACT", lambda: self.execute(adapter))
        self.assertEqual(adapter.events, [])
        self.assertFalse(self.journal_path.exists())

    def test_07_invalid_operation_id_fails(self) -> None:
        self.assert_error(
            "INVALID_CONTRACT", lambda: self.execute(operation_id="../bad")
        )

    def test_08_insecure_directory_mode_fails(self) -> None:
        os.chmod(self.journals, 0o755)
        self.assert_error("UNSAFE_PATH", self.execute)

    def test_09_symlink_directory_fails(self) -> None:
        real = self.root / "real-journals"
        real.mkdir(mode=0o700)
        self.journals.rmdir()
        self.journals.symlink_to(real, target_is_directory=True)
        self.assert_error("UNSAFE_PATH", self.execute)

    def test_10_simultaneous_production_lock_fails(self) -> None:
        lock = self.journals / ".production.lock"
        descriptor = os.open(lock, os.O_RDWR | os.O_CREAT, 0o600)
        self.addCleanup(os.close, descriptor)
        fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
        self.assert_error("PRODUCTION_OPERATION_ACTIVE", self.execute)

    def test_11_corrupt_journal_blocks_before_adapter(self) -> None:
        self.journal_path.write_text("{}\n")
        os.chmod(self.journal_path, 0o600)
        adapter = FakeAdapter()
        self.assert_error("JOURNAL_CORRUPT", lambda: self.execute(adapter))
        self.assertEqual(adapter.events, [])

    def test_12_same_operation_and_bundle_is_terminally_idempotent(self) -> None:
        first_adapter = FakeAdapter()
        first = self.execute(first_adapter)
        second_adapter = FakeAdapter()
        second = self.execute(second_adapter)
        self.assertEqual(first, second)
        self.assertEqual(second_adapter.events, [])

    def test_13_same_operation_with_different_bundle_conflicts(self) -> None:
        self.execute()
        other = self.root / "other-bundle"
        planner.generate_bundle(
            target_path=TARGET_EXAMPLE,
            current_path=None,
            current_manifest_path=None,
            compose_path=COMPOSE,
            planned_at="2026-07-29T16:01:00Z",
            output_path=other,
        )
        self.assert_error(
            "OPERATION_CONFLICT", lambda: self.execute(bundle=other)
        )

    def test_14_succeeded_terminal_never_calls_adapter_again(self) -> None:
        self.execute()
        adapter = FakeAdapter(execute_errors={"PULL": RuntimeError("must not run")})
        self.assertEqual(self.execute(adapter)["state"], "SUCCEEDED")
        self.assertEqual(adapter.events, [])

    def test_15_failed_or_rolled_back_terminal_never_restarts(self) -> None:
        for action, expected in (("PULL", "FAILED"), ("MIGRATE", "ROLLED_BACK")):
            with self.subTest(action=action):
                if self.journal_path.exists():
                    self.journal_path.unlink()
                if self.state_path.exists():
                    self.state_path.unlink()
                adapter = FakeAdapter(
                    scripts={action: [failed()], "ROLLBACK": [succeeded("ROLLBACK")]}
                )
                terminal = self.execute(adapter)
                self.assertEqual(terminal["state"], expected)
                replay = FakeAdapter()
                self.assertEqual(self.execute(replay), terminal)
                self.assertEqual(replay.events, [])

    def test_16_successful_probe_skips_execute(self) -> None:
        adapter = FakeAdapter(
            scripts={action: [succeeded(action)] for action in ADAPTER_ACTIONS}
        )
        self.execute(adapter)
        self.assertFalse(any(kind == "execute" for kind, _ in adapter.events))

    def test_17_absent_probe_executes_once_and_requires_second_probe(self) -> None:
        adapter = FakeAdapter(scripts={"PULL": [absent(), succeeded("PULL")]})
        self.execute(adapter)
        self.assertEqual(adapter.events.count(("execute", "PULL")), 1)
        self.assertEqual(adapter.events.count(("probe", "PULL")), 2)

    def test_18_failed_probe_fails_closed(self) -> None:
        journal = self.execute(FakeAdapter(scripts={"PULL": [failed()]}))
        self.assertEqual((journal["state"], journal["errorCode"]), ("FAILED", "PULL_FAILED"))

    def test_19_unknown_probe_fails_closed(self) -> None:
        journal = self.execute(FakeAdapter(scripts={"PULL": [unknown()]}))
        self.assertEqual(journal["state"], "FAILED")

    def test_20_invalid_probe_result_is_sanitized(self) -> None:
        invalid = executor.ProbeResult(
            "BROKEN", "2026-07-29T17:00:00Z", "secret_payload"
        )
        journal = self.execute(FakeAdapter(scripts={"PULL": [invalid]}))
        self.assertEqual(journal["errorCode"], "INVALID_ADAPTER_RESULT")
        self.assertNotIn("secret_payload", json.dumps(journal))

    def test_21_execute_must_return_none(self) -> None:
        adapter = FakeAdapter(execute_results={"PULL": "unexpected secret"})
        journal = self.execute(adapter)
        self.assertEqual(journal["state"], "FAILED")
        self.assertNotIn("unexpected secret", json.dumps(journal))

    def test_22_pull_and_backup_failures_never_rollback(self) -> None:
        for action in ("PULL", "BACKUP"):
            with self.subTest(action=action):
                if self.journal_path.exists():
                    self.journal_path.unlink()
                adapter = FakeAdapter(scripts={action: [failed()]})
                self.assertEqual(self.execute(adapter)["state"], "FAILED")
                self.assertNotIn(("probe", "ROLLBACK"), adapter.events)

    def test_23_mutating_phase_failures_enter_rollback(self) -> None:
        for action in ("MIGRATE", "UPDATE", "VERIFY"):
            with self.subTest(action=action):
                if self.journal_path.exists():
                    self.journal_path.unlink()
                if self.state_path.exists():
                    self.state_path.unlink()
                adapter = FakeAdapter(
                    scripts={action: [failed()], "ROLLBACK": [succeeded("ROLLBACK")]}
                )
                self.assertEqual(self.execute(adapter)["state"], "ROLLED_BACK")
                self.assertIn(("probe", "ROLLBACK"), adapter.events)

    def test_24_proven_rollback_finishes_rolled_back(self) -> None:
        adapter = FakeAdapter(
            scripts={"MIGRATE": [failed()], "ROLLBACK": [succeeded("ROLLBACK")]}
        )
        journal = self.execute(adapter)
        self.assertEqual(journal["state"], "ROLLED_BACK")
        self.assertEqual(journal["steps"][-1]["status"], "SUCCEEDED")

    def test_25_uncertain_rollback_finishes_failed(self) -> None:
        adapter = FakeAdapter(
            scripts={"MIGRATE": [failed()], "ROLLBACK": [unknown()]}
        )
        journal = self.execute(adapter)
        self.assertEqual(journal["state"], "FAILED")
        self.assertEqual(journal["rollbackErrorCode"], "ROLLBACK_FAILED")

    def test_26_database_restore_required_is_monotonic(self) -> None:
        adapter = FakeAdapter(
            scripts={"MIGRATE": [failed()], "ROLLBACK": [succeeded("ROLLBACK")]}
        )
        journal = self.execute(adapter)
        self.assertIs(journal["databaseRestoreRequired"], True)

    def test_27_noop_steps_skip_only_pull_backup_migrate_update(self) -> None:
        bundle, current, _target = self.update_bundle()
        self.state_path.write_bytes(planner.canonical_bytes(current) + b"\n")
        os.chmod(self.state_path, 0o600)
        adapter = FakeAdapter()
        journal = self.execute(adapter, bundle=bundle)
        statuses = {item["name"]: item["status"] for item in journal["steps"]}
        for action in ("PULL", "BACKUP", "MIGRATE", "UPDATE"):
            self.assertEqual(statuses[action], "SKIPPED")

    def test_28_verify_is_never_skipped(self) -> None:
        bundle, current, _target = self.update_bundle()
        self.state_path.write_bytes(planner.canonical_bytes(current) + b"\n")
        os.chmod(self.state_path, 0o600)
        adapter = FakeAdapter()
        journal = self.execute(adapter, bundle=bundle)
        self.assertEqual(
            next(item for item in journal["steps"] if item["name"] == "VERIFY")[
                "status"
            ],
            "SUCCEEDED",
        )
        self.assertIn(("probe", "VERIFY"), adapter.events)

    def test_29_journal_is_persisted_before_every_execute(self) -> None:
        adapter = FakeAdapter(journal_path=self.journal_path)
        self.execute(adapter)
        observations = [
            exists
            for label, exists in adapter.state_presence
            if label.startswith("journal:")
        ]
        self.assertTrue(observations)
        self.assertTrue(all(observations))

    def test_30_completed_steps_are_not_executed_on_terminal_replay(self) -> None:
        self.execute()
        adapter = FakeAdapter()
        self.execute(adapter)
        self.assertEqual(adapter.events, [])

    def test_31_invalid_clock_fails_without_payload(self) -> None:
        self.assert_error(
            "INVALID_CLOCK", lambda: self.execute(clock=FakeClock(["not-a-time"]))
        )

    def test_32_regressive_clock_fails(self) -> None:
        clock = FakeClock(
            ["2026-07-29T17:00:01Z", "2026-07-29T17:00:00Z"]
        )
        self.assert_error("INVALID_CLOCK", lambda: self.execute(clock=clock))

    def test_33_confirmed_state_uses_injected_clock(self) -> None:
        clock = FakeClock(["2026-07-29T17:10:00Z"] * 100)
        adapter = FakeAdapter(
            scripts={
                action: [
                    executor.ProbeResult(
                        "ABSENT", "2026-07-29T17:10:00Z", None
                    ),
                    executor.ProbeResult(
                        "SUCCEEDED",
                        "2026-07-29T17:10:00Z",
                        f"evidence_{action.lower()}",
                    ),
                ]
                for action in ADAPTER_ACTIONS
            }
        )
        self.execute(adapter=adapter, clock=clock)
        installed = json.loads(self.state_path.read_text())
        self.assertEqual(installed["installedAt"], "2026-07-29T17:10:00Z")

    def test_34_first_installation_rejects_existing_state(self) -> None:
        self.state_path.write_text("{}\n")
        os.chmod(self.state_path, 0o600)
        self.assert_error("CURRENT_STATE_CONFLICT", self.execute)

    def test_35_concurrent_state_change_before_commit_is_detected(self) -> None:
        class MutatingAdapter(FakeAdapter):
            def execute(inner_self, context: Any) -> Any:
                result = super().execute(context)
                if context.action == "VERIFY":
                    self.state_path.write_text("{}\n")
                    os.chmod(self.state_path, 0o600)
                return result

        journal = self.execute(MutatingAdapter())
        self.assertIn(journal["state"], {"FAILED", "ROLLED_BACK"})
        self.assertEqual(journal["errorCode"], "CURRENT_STATE_CONFLICT")

    def test_36_journal_files_are_canonical_and_mode_0600(self) -> None:
        self.execute()
        raw = self.journal_path.read_bytes()
        self.assertEqual(raw, planner.canonical_bytes(json.loads(raw)) + b"\n")
        self.assertEqual(stat.S_IMODE(self.journal_path.stat().st_mode), 0o600)

    def test_37_confirmed_state_is_canonical_and_mode_0600(self) -> None:
        self.execute()
        raw = self.state_path.read_bytes()
        self.assertEqual(raw, planner.canonical_bytes(json.loads(raw)) + b"\n")
        self.assertEqual(stat.S_IMODE(self.state_path.stat().st_mode), 0o600)

    def test_38_schema_and_evidence_reject_extra_fields(self) -> None:
        invalid = executor.ProbeResult(
            "SUCCEEDED", "2026-07-29T17:00:00Z", None
        )
        journal = self.execute(FakeAdapter(scripts={"PULL": [invalid]}))
        self.assertEqual(journal["errorCode"], "INVALID_ADAPTER_RESULT")

    def test_39_public_error_is_one_line_and_sanitized(self) -> None:
        with self.assertRaises(executor.DeploymentExecutionError) as caught:
            self.execute(operation_id="../secret\ntraceback")
        rendered = str(caught.exception)
        self.assertEqual(rendered, caught.exception.code)
        self.assertNotIn("\n", rendered)
        self.assertNotIn("secret", rendered)
        self.assertNotIn("traceback", rendered)

    def test_40_no_external_command_or_network_capability_exists(self) -> None:
        source = (ROOT / "tools/deploy/deployment_executor.py").read_text()
        for forbidden in (
            "subprocess",
            "os.system",
            "docker",
            "psql",
            "requests",
            "httpx",
            "socket",
        ):
            self.assertNotIn(forbidden, source.lower())

    def test_41_no_system_clock_is_read(self) -> None:
        source = (ROOT / "tools/deploy/deployment_executor.py").read_text()
        for forbidden in ("datetime.now(", "datetime.utcnow(", "time.time("):
            self.assertNotIn(forbidden, source)

    def test_42_no_temporary_residue_remains_after_success(self) -> None:
        self.execute()
        residues = [
            item
            for item in self.root.rglob("*")
            if ".tmp-" in item.name
        ]
        self.assertEqual(residues, [])

    def test_43_other_nonterminal_journal_blocks_new_operation(self) -> None:
        self.leave_pull_running()
        adapter = FakeAdapter()
        self.assert_error(
            "PRODUCTION_OPERATION_ACTIVE",
            lambda: self.execute(
                adapter,
                operation_id="deployment_fedcba9876543210",
            ),
        )
        self.assertEqual(adapter.events, [])

    def test_44_nonterminal_journal_resumes_to_completion(self) -> None:
        raw = self.leave_pull_running()
        resumed = self.execute()
        self.assertEqual(resumed["state"], "SUCCEEDED")
        self.assertNotEqual(self.journal_path.read_bytes(), raw)
        self.assertTrue(self.state_path.is_file())

    def test_45_running_step_probes_before_deciding_to_execute(self) -> None:
        self.leave_pull_running()
        adapter = FakeAdapter(scripts={"PULL": [succeeded("PULL")]})
        journal = self.execute(adapter)
        self.assertEqual(journal["state"], "SUCCEEDED")
        self.assertEqual(adapter.events[0], ("probe", "PULL"))
        self.assertNotIn(("execute", "PULL"), adapter.events)
        pull = next(
            item for item in journal["steps"] if item["name"] == "PULL"
        )
        self.assertEqual(pull["attempts"], 1)

    def test_46_crash_before_installed_state_replace_resumes(self) -> None:
        original = executor._replace_atomic

        def crash_before(source: Path, destination: Path) -> None:
            if destination == self.state_path:
                raise SimulatedCrash()
            original(source, destination)

        with patch.object(executor, "_replace_atomic", side_effect=crash_before):
            with self.assertRaises(SimulatedCrash):
                self.execute()
        self.assertFalse(self.state_path.exists())
        interrupted = json.loads(self.journal_path.read_bytes())
        commit = next(
            item
            for item in interrupted["steps"]
            if item["name"] == "COMMIT_STATE"
        )
        self.assertEqual(commit["status"], "RUNNING")
        self.assertEqual(self.execute()["state"], "SUCCEEDED")
        self.assertTrue(self.state_path.is_file())

    def test_47_crash_after_installed_state_replace_is_reconciled(self) -> None:
        original = executor._replace_atomic

        def crash_after(source: Path, destination: Path) -> None:
            original(source, destination)
            if destination == self.state_path:
                raise SimulatedCrash()

        with patch.object(executor, "_replace_atomic", side_effect=crash_after):
            with self.assertRaises(SimulatedCrash):
                self.execute()
        before_resume = self.state_path.read_bytes()
        interrupted = json.loads(self.journal_path.read_bytes())
        self.assertIsNone(interrupted["confirmedStateSha256"])
        self.assertEqual(self.execute()["state"], "SUCCEEDED")
        self.assertEqual(self.state_path.read_bytes(), before_resume)

    def test_48_atomic_overwrite_hook_failures_preserve_previous_journal(
        self,
    ) -> None:
        previous = self.leave_pull_running()
        hooks = (
            "_write_bytes",
            "_fsync_file",
            "_verify_staged_json",
            "_replace_atomic",
        )
        for hook in hooks:
            with self.subTest(hook=hook):
                self.journal_path.write_bytes(previous)
                os.chmod(self.journal_path, 0o600)
                with patch.object(
                    executor, hook, side_effect=OSError("injected")
                ):
                    self.assert_error("JOURNAL_IO_FAILED", self.execute)
                self.assertEqual(self.journal_path.read_bytes(), previous)
                self.assertEqual(
                    [
                        item
                        for item in self.journals.iterdir()
                        if ".tmp-" in item.name
                    ],
                    [],
                )

    def test_49_crash_after_parent_fsync_is_reconciled_on_resume(self) -> None:
        original = executor._fsync_directory

        def crash_after_parent_fsync(path: Path) -> None:
            original(path)
            if path == self.state_parent:
                raise SimulatedCrash()

        with patch.object(
            executor,
            "_fsync_directory",
            side_effect=crash_after_parent_fsync,
        ):
            with self.assertRaises(SimulatedCrash):
                self.execute()
        before_resume = self.state_path.read_bytes()
        self.assertEqual(self.execute()["state"], "SUCCEEDED")
        self.assertEqual(self.state_path.read_bytes(), before_resume)
        self.assertEqual(
            [item for item in self.state_parent.iterdir() if ".tmp-" in item.name],
            [],
        )

    def test_50_queued_resume_with_conflicting_state_finishes_failed(
        self,
    ) -> None:
        with patch.object(
            executor, "_run_transaction", side_effect=SimulatedCrash()
        ):
            with self.assertRaises(SimulatedCrash):
                self.execute()
        queued = json.loads(self.journal_path.read_bytes())
        self.assertEqual(queued["state"], "QUEUED")
        self.assertTrue(
            all(step["status"] == "PENDING" for step in queued["steps"])
        )
        self.write_conflicting_state()
        adapter = FakeAdapter()
        resumed = self.execute(adapter)
        self.assertEqual(
            (resumed["state"], resumed["errorCode"]),
            ("FAILED", "CURRENT_STATE_CONFLICT"),
        )
        self.assertEqual(adapter.events, [])
        self.assertNotEqual(resumed["errorCode"], "JOURNAL_IO_FAILED")

    def test_51_pulling_without_started_step_conflict_finishes_failed(
        self,
    ) -> None:
        original = executor._transition

        def crash_after_pulling(
            journal: dict[str, Any],
            destination: str,
            clock: Any,
            journal_path: Path,
        ) -> None:
            original(journal, destination, clock, journal_path)
            if destination == "PULLING":
                raise SimulatedCrash()

        with patch.object(
            executor, "_transition", side_effect=crash_after_pulling
        ):
            with self.assertRaises(SimulatedCrash):
                self.execute()
        pulling = json.loads(self.journal_path.read_bytes())
        pull = next(
            step for step in pulling["steps"] if step["name"] == "PULL"
        )
        self.assertEqual((pulling["state"], pull["status"]), ("PULLING", "PENDING"))
        self.write_conflicting_state()
        adapter = FakeAdapter()
        resumed = self.execute(adapter)
        self.assertEqual(
            (resumed["state"], resumed["errorCode"]),
            ("FAILED", "CURRENT_STATE_CONFLICT"),
        )
        self.assertEqual(adapter.events, [])

    def test_52_each_impossible_temporal_relation_corrupts_journal(self) -> None:
        base = self.spread_success_timestamps()

        def pull(value: dict[str, Any]) -> dict[str, Any]:
            return next(
                step for step in value["steps"] if step["name"] == "PULL"
            )

        mutations = {
            "finished_before_started": lambda value: pull(value).__setitem__(
                "finishedAt", "2026-07-29T17:00:10Z"
            ),
            "evidence_before_started": lambda value: pull(value)[
                "evidence"
            ].__setitem__("observedAt", "2026-07-29T17:00:10Z"),
            "evidence_after_finished": lambda value: pull(value)[
                "evidence"
            ].__setitem__("observedAt", "2026-07-29T17:00:13Z"),
            "step_outside_transition_window": lambda value: pull(
                value
            ).__setitem__("startedAt", "2026-07-29T17:00:09Z"),
            "terminal_finished_differs_from_updated": lambda value: value.__setitem__(
                "finishedAt", "2026-07-29T17:01:19Z"
            ),
        }
        for name, mutate in mutations.items():
            with self.subTest(mutation=name):
                candidate = copy.deepcopy(base)
                mutate(candidate)
                self.journal_path.write_bytes(
                    planner.canonical_bytes(candidate) + b"\n"
                )
                os.chmod(self.journal_path, 0o600)
                adapter = FakeAdapter()
                self.assert_error("JOURNAL_CORRUPT", lambda: self.execute(adapter))
                self.assertEqual(adapter.events, [])


if __name__ == "__main__":
    unittest.main()
