from __future__ import annotations

import copy
import hashlib
import json
import os
import shutil
import stat
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path
from typing import Any, Callable
from unittest.mock import patch

from tools.deploy import deployment_executor as core
from tools.deploy import deployment_plan as planner
from tools.deploy import production_adapter as adapter


ROOT = Path(__file__).resolve().parents[3]
TARGET = ROOT / "ops/releases/examples/global-release.example.json"
COMPOSE = ROOT / "ops/compose/compose.prod.yml"
DOCKER = Path("/usr/bin/docker")
CURL = Path("/usr/bin/curl")
OPERATION = "deployment_0123456789abcdef"


class FakeClock:
    def __init__(self, value: str = "2026-07-31T12:00:00Z"):
        self.value = value

    def now(self) -> str:
        return self.value


class FakeRunner:
    def __init__(
        self,
        handler: Callable[[tuple[str, ...], int, Path | None], Any] | None = None,
    ):
        self.handler = handler
        self.calls: list[tuple[tuple[str, ...], int, Path | None]] = []

    def run(
        self,
        argv: tuple[str, ...],
        *,
        timeout_seconds: int,
        stdout_file: Path | None = None,
    ) -> adapter.ProcessResult:
        self.calls.append((argv, timeout_seconds, stdout_file))
        if self.handler is None:
            return adapter.ProcessResult(0, b"")
        result = self.handler(argv, timeout_seconds, stdout_file)
        if isinstance(result, Exception):
            raise result
        return result


def process(code: int = 0, stdout: bytes = b"") -> adapter.ProcessResult:
    return adapter.ProcessResult(code, stdout)


class ProductionAdapterTest(unittest.TestCase):
    def setUp(self) -> None:
        self.root = Path(
            tempfile.mkdtemp(prefix=".s20-adapter-", dir=ROOT)
        )
        self.addCleanup(shutil.rmtree, self.root, True)
        os.chmod(self.root, 0o700)
        for relative in ("shared", "shared/backups", "shared/deploy", "shared/deploy/journals", "releases"):
            (self.root / relative).mkdir(mode=0o700)
        self.env = self.root / "shared/.env"
        self.env.write_bytes(b"FICTITIOUS_ONLY=true\n")
        os.chmod(self.env, 0o600)
        self.bundle = self.root / "releases/v0.0.1"
        planner.generate_bundle(
            target_path=TARGET,
            current_path=None,
            current_manifest_path=None,
            compose_path=COMPOSE,
            planned_at="2026-07-31T12:00:00Z",
            output_path=self.bundle,
        )
        self.plan = planner.validate_bundle(self.bundle)
        self.manifest = json.loads((self.bundle / "manifest.json").read_text())
        self.refs = {
            item["id"]: item["immutableRef"]
            for item in self.manifest["components"]
        }

    def make_adapter(
        self,
        runner: FakeRunner | None = None,
        *,
        plan: dict[str, Any] | None = None,
        bundle: Path | None = None,
        port: int = 8120,
    ) -> adapter.ProductionDeploymentAdapter:
        return adapter.ProductionDeploymentAdapter(
            root=self.root,
            bundle=bundle or self.bundle,
            plan=plan or self.plan,
            runner=runner or FakeRunner(),
            clock=FakeClock(),
            docker_binary=DOCKER,
            curl_binary=CURL,
            gateway_loopback_port=port,
        )

    def context(
        self,
        action: str,
        *,
        plan: dict[str, Any] | None = None,
        bundle: Path | None = None,
        restore: bool = False,
    ) -> core.ActionContext:
        selected = plan or self.plan
        changed = tuple(
            item["id"] for item in selected["databases"] if item["changed"]
        )
        services: tuple[str, ...] = ()
        databases: tuple[str, ...] = ()
        if action == "PULL":
            services = tuple(selected["servicesToPull"])
        elif action == "UPDATE":
            services = tuple(selected["servicesToUpdate"])
        elif action in {"VERIFY", "ROLLBACK"}:
            services = tuple(adapter.COMPONENTS)
        if action in {"BACKUP", "MIGRATE", "ROLLBACK"}:
            databases = changed
        elif action == "VERIFY":
            databases = tuple(adapter.DATABASES)
        return core.ActionContext(
            OPERATION,
            action,
            bundle or self.bundle,
            selected["sourceRelease"],
            selected["targetRelease"],
            services,
            databases,
            restore,
        )

    def update_bundle(self) -> tuple[Path, dict[str, Any]]:
        current_manifest = planner.load_target(TARGET)
        current = planner.build_next_state(
            current_manifest, "2026-07-31T12:01:00Z"
        )
        current["reconciled"] = True
        current["installedAt"] = "2026-07-31T12:02:00Z"
        target = copy.deepcopy(current_manifest)
        target["release"] = "v0.0.2"
        target["previousRelease"] = "v0.0.1"
        target["publishedAt"] = "2026-07-31T12:03:00Z"
        current_path = self.root / "current.json"
        current_manifest_path = self.root / "current-manifest.json"
        target_path = self.root / "target.json"
        current_path.write_bytes(planner.canonical_bytes(current) + b"\n")
        current_manifest_path.write_bytes(
            planner.global_release.canonical(current_manifest)
        )
        target_path.write_bytes(planner.global_release.canonical(target))
        output = self.root / "releases/v0.0.2"
        planner.generate_bundle(
            target_path=target_path,
            current_path=current_path,
            current_manifest_path=current_manifest_path,
            compose_path=COMPOSE,
            planned_at="2026-07-31T12:04:00Z",
            output_path=output,
        )
        return output, planner.validate_bundle(output)

    def image_handler(
        self, states: dict[str, str]
    ) -> Callable[[tuple[str, ...], int, Path | None], adapter.ProcessResult]:
        def handle(
            argv: tuple[str, ...], _timeout: int, _output: Path | None
        ) -> adapter.ProcessResult:
            if argv[-3:] == ("config", "--format", "json"):
                image = "postgres:16@sha256:" + "a" * 64
                return process(
                    0,
                    json.dumps(
                        {"services": {"postgresql": {"image": image}}}
                    ).encode(),
                )
            if argv[1:3] == ("image", "inspect"):
                reference = argv[-1]
                state = states.get(reference, "ABSENT")
                if state == "ABSENT":
                    return process(1)
                if state == "ERROR":
                    return process(2)
                digest = reference.rsplit("@", 1)[-1]
                repository = reference.rsplit("@", 1)[0].split(":", 1)[0]
                observed = digest if state == "PRESENT" else "sha256:" + "f" * 64
                return process(0, json.dumps([repository + "@" + observed]).encode())
            return process()

        return handle

    def container_handler(
        self,
        refs: dict[str, str],
        *,
        absent: set[str] | None = None,
        unhealthy: set[str] | None = None,
        extra: bool = False,
        curls_ok: bool = True,
    ) -> Callable[[tuple[str, ...], int, Path | None], adapter.ProcessResult]:
        absent = absent or set()
        unhealthy = unhealthy or set()

        def handle(
            argv: tuple[str, ...], _timeout: int, _output: Path | None
        ) -> adapter.ProcessResult:
            if argv[0] == os.fspath(CURL):
                return process(0 if curls_ok else 22)
            if "ps" in argv and "--format" in argv:
                services = [
                    {"Service": name} for name in adapter.ALL_SERVICES
                ]
                if extra:
                    services.append({"Service": "unexpected"})
                return process(0, b"\n".join(json.dumps(x).encode() for x in services))
            if "ps" in argv and "-q" in argv:
                service = argv[-1]
                return process(0, b"" if service in absent else f"cid_{service}\n".encode())
            if argv[1:3] == ("container", "inspect"):
                service = argv[-1].removeprefix("cid_")
                image = refs.get(service, "postgres:16@sha256:" + "a" * 64)
                value = {
                    "Config": {"Image": image},
                    "State": {
                        "Status": "running",
                        "Health": {
                            "Status": "unhealthy" if service in unhealthy else "healthy"
                        },
                    },
                }
                return process(0, json.dumps(value).encode())
            return process()

        return handle

    def test_01_compose_argv_is_exact_and_release_env_is_second(self) -> None:
        runner = FakeRunner()
        self.make_adapter(runner).validate_compose()
        argv = runner.calls[0][0]
        self.assertEqual(argv[:4], (str(DOCKER), "compose", "--project-name", "abaronesa-emporio"))
        self.assertEqual(
            [argv[index + 1] for index, value in enumerate(argv) if value == "--env-file"],
            [str(self.env), str(self.bundle / "release.env")],
        )

    def test_02_runner_has_no_shell_and_bounds_output(self) -> None:
        recorded: dict[str, Any] = {}
        real_popen = subprocess.Popen

        def spy_popen(argv: Any, **kwargs: Any) -> Any:
            recorded.update(kwargs)
            return real_popen(argv, **kwargs)

        argv = (
            sys.executable,
            "-c",
            "import sys\nsys.stdout.write('x' * 70000)\nsys.stdout.flush()\n",
        )
        with patch.object(adapter.subprocess, "Popen", side_effect=spy_popen):
            with self.assertRaisesRegex(adapter.ProductionAdapterError, "OUTPUT_LIMIT_EXCEEDED"):
                adapter.SubprocessRunner()._run_captured(argv, 30)
        self.assertIs(recorded["shell"], False)
        self.assertIs(recorded["stdin"], subprocess.DEVNULL)
        self.assertIs(recorded["stderr"], subprocess.DEVNULL)
        self.assertTrue(recorded["close_fds"])

    def test_03_timeout_and_nonzero_are_sanitized(self) -> None:
        argv = (sys.executable, "-c", "import time\ntime.sleep(5)\n")
        started = time.monotonic()
        with self.assertRaisesRegex(adapter.ProductionAdapterError, "COMMAND_TIMEOUT"):
            adapter.SubprocessRunner()._run_captured(argv, 1)
        self.assertLess(time.monotonic() - started, 4)
        runner = FakeRunner(lambda *_: process(2))
        with self.assertRaisesRegex(adapter.ProductionAdapterError, "PULL_COMMAND_FAILED"):
            self.make_adapter(runner).execute(self.context("PULL"))

    def test_04_secret_never_enters_argv_error_or_evidence(self) -> None:
        secret = "FICTITIOUS_SUPER_SECRET"
        self.env.write_text(f"SECRET={secret}\n", encoding="utf-8")
        failing = FakeRunner(lambda *_: RuntimeError(secret))
        with self.assertRaises(adapter.ProductionAdapterError) as caught:
            self.make_adapter(failing).execute(self.context("PULL"))
        states = {ref: "PRESENT" for ref in self.refs.values()}
        states["postgres:16@sha256:" + "a" * 64] = "PRESENT"
        runner = FakeRunner(self.image_handler(states))
        result = self.make_adapter(runner).probe(self.context("PULL"))
        rendered = (
            json.dumps([call[0] for call in failing.calls + runner.calls])
            + str(caught.exception)
            + str(result)
        )
        self.assertNotIn(secret, rendered)

    def test_05_unsafe_root_and_symlink_fail_before_runner(self) -> None:
        runner = FakeRunner()
        with self.assertRaises(adapter.ProductionAdapterError):
            adapter.ProductionDeploymentAdapter(
                root=Path("relative"), bundle=self.bundle, plan=self.plan,
                runner=runner, clock=FakeClock(), docker_binary=DOCKER, curl_binary=CURL,
            )
        real = self.root / "real"; real.mkdir(mode=0o700)
        link = self.root / "link"; link.symlink_to(real, target_is_directory=True)
        with self.assertRaises(adapter.ProductionAdapterError):
            adapter.ProductionDeploymentAdapter(
                root=link, bundle=self.bundle, plan=self.plan,
                runner=runner, clock=FakeClock(), docker_binary=DOCKER, curl_binary=CURL,
            )
        with self.assertRaises(adapter.ProductionAdapterError):
            adapter.ProductionDeploymentAdapter(
                root=self.root / "escape" / "..",
                bundle=self.bundle,
                plan=self.plan,
                runner=runner,
                clock=FakeClock(),
                docker_binary=DOCKER,
                curl_binary=CURL,
            )
        os.chmod(self.root, 0o770)
        try:
            with self.assertRaises(adapter.ProductionAdapterError):
                self.make_adapter(runner)
        finally:
            os.chmod(self.root, 0o700)
        self.assertEqual(runner.calls, [])

    def test_06_env_must_be_mode_0600(self) -> None:
        os.chmod(self.env, 0o640)
        with self.assertRaises(adapter.ProductionAdapterError):
            self.make_adapter()

    def test_07_bundle_plan_divergence_precedes_runner(self) -> None:
        mutated = copy.deepcopy(self.plan)
        mutated["targetRelease"] = "v9.9.9"
        runner = FakeRunner()
        with self.assertRaisesRegex(adapter.ProductionAdapterError, "INVALID_BUNDLE"):
            self.make_adapter(runner, plan=mutated)
        self.assertEqual(runner.calls, [])

    def test_08_invalid_historical_source_fails_closed(self) -> None:
        bundle, plan = self.update_bundle()
        from tools.deploy import deployment_cli

        current = json.loads((self.bundle / "installed-state.next.json").read_text())
        current["reconciled"] = True
        current["installedAt"] = "2026-07-31T12:05:00Z"
        state_path = self.root / "shared/deploy/installed-state.json"
        state_path.write_bytes(planner.canonical_bytes(current) + b"\n")
        os.chmod(state_path, 0o600)
        (self.root / "current").symlink_to("releases/v0.0.2")
        runner = FakeRunner()
        with self.assertRaisesRegex(
            deployment_cli.DeploymentCliError, "UNSAFE_LINK_STATE"
        ):
            deployment_cli.execute(
                operation_id=OPERATION,
                release=plan["targetRelease"],
                deploy_root=self.root,
                runner=runner,
                clock=FakeClock(),
                docker_binary=DOCKER,
                curl_binary=CURL,
            )
        self.assertEqual(runner.calls, [])
        (self.root / "current").unlink()
        (self.bundle / "bundle.sha256").write_bytes(b"invalid\n")
        instance = self.make_adapter(bundle=bundle, plan=plan)
        with self.assertRaisesRegex(adapter.ProductionAdapterError, "SOURCE_BUNDLE_INVALID"):
            instance.execute(self.context("ROLLBACK", bundle=bundle, plan=plan))

    def test_09_fully_absent_pull_executes_once(self) -> None:
        runner = FakeRunner(self.image_handler({}))
        instance = self.make_adapter(runner)
        context = self.context("PULL")
        self.assertEqual(instance.probe(context).status, "ABSENT")
        instance.execute(context)
        self.assertEqual(sum("pull" in call[0] for call in runner.calls), 1)

    def test_10_partial_pull_is_unknown_without_pull(self) -> None:
        runner = FakeRunner(self.image_handler({self.refs["backend"]: "PRESENT"}))
        result = self.make_adapter(runner).probe(self.context("PULL"))
        self.assertEqual(result.status, "UNKNOWN")
        self.assertFalse(any("pull" in call[0] for call in runner.calls))

    def test_11_first_install_pull_includes_postgresql_first(self) -> None:
        states = {ref: "PRESENT" for ref in self.refs.values()}
        states["postgres:16@sha256:" + "a" * 64] = "PRESENT"
        runner = FakeRunner(self.image_handler(states))
        instance = self.make_adapter(runner)
        self.assertEqual(instance.probe(self.context("PULL")).status, "SUCCEEDED")
        instance.execute(self.context("PULL"))
        tail = runner.calls[-1][0]
        self.assertLess(tail.index("postgresql"), tail.index("backend"))

    def test_12_backup_starts_postgresql_before_dump(self) -> None:
        def handle(argv: tuple[str, ...], _timeout: int, output: Path | None) -> adapter.ProcessResult:
            if output:
                output.write_bytes(b"PGDMP"); os.chmod(output, 0o600)
            return process()
        runner = FakeRunner(handle)
        self.make_adapter(runner).execute(self.context("BACKUP"))
        self.assertIn("up", runner.calls[0][0])
        self.assertIn("pg_dump", runner.calls[1][0][-1])

    def test_13_dumps_use_stdout_files_not_memory(self) -> None:
        def handle(argv: tuple[str, ...], _timeout: int, output: Path | None) -> adapter.ProcessResult:
            if "pg_dump" in argv[-1]:
                self.assertIsNotNone(output)
                output.write_bytes(b"PGDMP"); os.chmod(output, 0o600)
            return process()
        runner = FakeRunner(handle)
        self.make_adapter(runner).execute(self.context("BACKUP"))

    def test_14_interrupted_backup_staging_resumes(self) -> None:
        def dump(
            _argv: tuple[str, ...], _timeout: int, output: Path | None
        ) -> adapter.ProcessResult:
            if output is not None:
                output.write_bytes(b"PGDMP")
                os.chmod(output, 0o600)
            return process()

        runner = FakeRunner(dump)
        instance = self.make_adapter(runner)
        with patch.object(adapter.os, "replace", side_effect=OSError("crash")):
            with self.assertRaises(adapter.ProductionAdapterError):
                instance.execute(self.context("BACKUP"))
        # Retry is allowed to resume its own safe staging.
        runner.handler = dump
        instance.execute(self.context("BACKUP"))
        self.assertTrue((self.root / f"shared/backups/{OPERATION}").is_dir())

    def test_15_adulterated_final_backup_is_failed_and_not_overwritten(self) -> None:
        final = self.root / f"shared/backups/{OPERATION}"
        final.mkdir(mode=0o700)
        dump = final / "erp.dump"; dump.write_bytes(b"x"); os.chmod(dump, 0o600)
        instance = self.make_adapter()
        self.assertEqual(instance.probe(self.context("BACKUP")).status, "FAILED")
        with self.assertRaisesRegex(adapter.ProductionAdapterError, "BACKUP_CONFLICT"):
            instance.execute(self.context("BACKUP"))

    def test_16_empty_dump_fails(self) -> None:
        def handle(_argv: tuple[str, ...], _timeout: int, output: Path | None) -> adapter.ProcessResult:
            if output:
                output.write_bytes(b""); os.chmod(output, 0o600)
            return process()
        with self.assertRaisesRegex(adapter.ProductionAdapterError, "BACKUP_DUMP_FAILED"):
            self.make_adapter(FakeRunner(handle)).execute(self.context("BACKUP"))

    def test_17_backup_manifest_is_canonical_mode_and_hashed(self) -> None:
        def handle(_argv: tuple[str, ...], _timeout: int, output: Path | None) -> adapter.ProcessResult:
            if output:
                output.write_bytes(b"PGDMP-" + output.name.encode()); os.chmod(output, 0o600)
            return process()
        self.make_adapter(FakeRunner(handle)).execute(self.context("BACKUP"))
        final = self.root / f"shared/backups/{OPERATION}"
        raw = (final / "backup-manifest.json").read_bytes()
        manifest = json.loads(raw)
        self.assertEqual(raw, adapter._canonical(manifest))
        for item in manifest["databases"]:
            dump = (final / item["file"]).read_bytes()
            self.assertEqual(item["sha256"], "sha256:" + hashlib.sha256(dump).hexdigest())
            self.assertEqual(stat.S_IMODE((final / item["file"]).stat().st_mode), 0o600)

    def test_18_migrations_execute_in_erp_website_order(self) -> None:
        runner = FakeRunner(lambda *_: process(0, b"MIGRATIONS_APPLIED\n"))
        self.make_adapter(runner).execute(self.context("MIGRATE"))
        services = [call[0][-2] for call in runner.calls]
        self.assertEqual(services, ["backend", "website_back"])

    def test_19_pending_migrates_but_invalid_fails(self) -> None:
        pending = FakeRunner(lambda *_: process(10, b"MIGRATIONS_PENDING\n"))
        self.assertEqual(self.make_adapter(pending).probe(self.context("MIGRATE")).status, "ABSENT")
        invalid = FakeRunner(lambda *_: process(20, b"MIGRATIONS_FAILED\n"))
        self.assertEqual(self.make_adapter(invalid).probe(self.context("MIGRATE")).status, "FAILED")

    def test_20_migration_entrypoints_do_not_start_spring(self) -> None:
        for path in (
            ROOT / "backend/src/main/java/com/baronesa/emporio/migration/ProductionMigrationMain.java",
            ROOT / "website_back/src/main/java/com/baronesa/website/migration/ProductionMigrationMain.java",
        ):
            text = path.read_text()
            self.assertNotIn("SpringApplication.run", text)
            self.assertNotIn("SpringApplication(", text)

    def test_21_normal_runtime_disables_flyway(self) -> None:
        text = (ROOT / "ops/compose/compose.prod.yml").read_text()
        self.assertIn('SPRING_FLYWAY_ENABLED: "false"', text)
        self.assertIn('FLYWAY_ENABLED: "false"', text)

    def test_22_update_uses_only_planned_services_not_postgresql(self) -> None:
        runner = FakeRunner()
        self.make_adapter(runner).execute(self.context("UPDATE"))
        argv = runner.calls[-1][0]
        self.assertNotIn("postgresql", argv)
        self.assertIn("--remove-orphans", argv)
        self.assertEqual(argv[-6:], tuple(adapter.COMPONENTS))

    def test_23_mixed_update_probe_is_unknown(self) -> None:
        refs = dict(self.refs)
        refs["backend"] = "ghcr.io/greggorio/abaronesa-emporio-backend@sha256:" + "f" * 64
        runner = FakeRunner(self.container_handler(refs))
        result = self.make_adapter(runner).probe(self.context("UPDATE"))
        self.assertEqual(result.status, "UNKNOWN")
        self.assertTrue(
            all(
                "--all" in call[0]
                for call in runner.calls
                if "ps" in call[0] and "-q" in call[0]
            )
        )

    def test_24_verify_requires_seven_services_and_four_smokes(self) -> None:
        runner = FakeRunner(self.container_handler(self.refs))
        result = self.make_adapter(runner).probe(self.context("VERIFY"))
        self.assertEqual(result.status, "SUCCEEDED")
        self.assertEqual(sum(call[0][0] == str(CURL) for call in runner.calls), 4)
        self.assertEqual(sum(call[0][1:3] == ("container", "inspect") for call in runner.calls), 7)

    def test_25_host_and_port_are_allowlisted(self) -> None:
        with self.assertRaisesRegex(adapter.ProductionAdapterError, "INVALID_GATEWAY_PORT"):
            self.make_adapter(port=80)
        runner = FakeRunner(self.container_handler(self.refs))
        instance = self.make_adapter(runner)
        with patch.object(adapter, "SMOKES", (("evil.invalid", "/"),)):
            self.assertEqual(instance.probe(self.context("VERIFY")).status, "ABSENT")
        self.assertFalse(any("evil.invalid" in " ".join(call[0]) for call in runner.calls))

    def test_26_update_rollback_uses_historical_bundle(self) -> None:
        bundle, plan = self.update_bundle()
        source_manifest = json.loads((self.bundle / "manifest.json").read_text())
        refs = {item["id"]: item["immutableRef"] for item in source_manifest["components"]}
        runner = FakeRunner(self.container_handler(refs))
        instance = self.make_adapter(runner, bundle=bundle, plan=plan)
        instance.execute(self.context("ROLLBACK", bundle=bundle, plan=plan))
        up = runner.calls[-1][0]
        self.assertIn(str(self.bundle / "compose.prod.yml"), up)
        self.assertNotIn("--remove-orphans", up)

    def test_27_first_rollback_removes_only_six_services(self) -> None:
        runner = FakeRunner()
        self.make_adapter(runner).execute(self.context("ROLLBACK"))
        argv = runner.calls[-1][0]
        self.assertEqual(argv[-9:], ("rm", "-f", "-s", *adapter.COMPONENTS))
        self.assertNotIn("postgresql", argv)

    def test_28_rollback_never_runs_migration_or_restore(self) -> None:
        runner = FakeRunner()
        self.make_adapter(runner).execute(self.context("ROLLBACK"))
        rendered = " ".join(runner.calls[-1][0]).lower()
        self.assertNotIn("migrate", rendered)
        self.assertNotIn("restore", rendered)

    def test_29_database_restore_required_is_preserved_in_evidence(self) -> None:
        runner = FakeRunner(self.container_handler(self.refs, absent=set(adapter.COMPONENTS)))
        instance = self.make_adapter(runner)
        context = self.context("ROLLBACK", restore=True)
        # Six absent containers prove first-install rollback without mutating the flag.
        result = instance.probe(context)
        self.assertTrue(context.database_restore_required)
        self.assertEqual(result.status, "SUCCEEDED")

    def test_30_cli_links_change_only_after_succeeded(self) -> None:
        from tools.deploy import deployment_cli

        bundle, plan = self.update_bundle()
        current = json.loads((self.bundle / "installed-state.next.json").read_text())
        current["reconciled"] = True
        current["installedAt"] = "2026-07-31T12:05:00Z"
        state_path = self.root / "shared/deploy/installed-state.json"
        state_path.write_bytes(planner.canonical_bytes(current) + b"\n")
        os.chmod(state_path, 0o600)
        (self.root / "current").symlink_to("releases/v0.0.1")
        target = json.loads((bundle / "installed-state.next.json").read_text())
        target["reconciled"] = True
        target["installedAt"] = "2026-07-31T12:06:00Z"

        def completed(**_kwargs: Any) -> dict[str, Any]:
            self.assertEqual(os.readlink(self.root / "current"), "releases/v0.0.1")
            self.assertFalse((self.root / "previous").exists())
            data = planner.canonical_bytes(target) + b"\n"
            state_path.write_bytes(data)
            os.chmod(state_path, 0o600)
            return {
                "state": "SUCCEEDED",
                "confirmedStateSha256": "sha256:" + hashlib.sha256(data).hexdigest(),
            }

        with patch.object(
            deployment_cli.production_adapter.ProductionDeploymentAdapter,
            "validate_compose",
        ), patch.object(
            deployment_cli.deployment_executor,
            "execute_deployment",
            side_effect=completed,
        ):
            journal, exit_code = deployment_cli.execute(
                operation_id=OPERATION,
                release=plan["targetRelease"],
                deploy_root=self.root,
                runner=FakeRunner(),
                clock=FakeClock(),
                docker_binary=DOCKER,
                curl_binary=CURL,
            )
        self.assertEqual(journal["state"], "SUCCEEDED")
        self.assertEqual(exit_code, 0)
        self.assertEqual(os.readlink(self.root / "previous"), "releases/v0.0.1")
        self.assertEqual(os.readlink(self.root / "current"), "releases/v0.0.2")

    def test_31_link_reconciliation_uses_atomic_replace_and_fsync(self) -> None:
        from tools.deploy import deployment_cli

        bundle, plan = self.update_bundle()
        (self.root / "current").symlink_to("releases/v0.0.1")
        state = json.loads((bundle / "installed-state.next.json").read_text())
        state["reconciled"] = True
        state["installedAt"] = "2026-07-31T12:06:00Z"
        state_path = self.root / "shared/deploy/installed-state.json"
        state_bytes = planner.canonical_bytes(state) + b"\n"
        state_path.write_bytes(state_bytes)
        os.chmod(state_path, 0o600)
        journal = {
            "state": "SUCCEEDED",
            "confirmedStateSha256": "sha256:" + hashlib.sha256(state_bytes).hexdigest(),
        }
        original = deployment_cli._replace_link
        calls = 0

        def crash_between(root: Path, name: str, release: str) -> None:
            nonlocal calls
            calls += 1
            if calls == 2:
                raise deployment_cli.DeploymentCliError(
                    "LINK_RECONCILIATION_FAILED"
                )
            original(root, name, release)

        with patch.object(
            deployment_cli, "_replace_link", side_effect=crash_between
        ):
            with self.assertRaisesRegex(
                deployment_cli.DeploymentCliError,
                "LINK_RECONCILIATION_FAILED",
            ):
                deployment_cli._reconcile_links(
                    self.root, plan, journal, state_path
                )
        self.assertEqual(os.readlink(self.root / "previous"), "releases/v0.0.1")
        self.assertEqual(os.readlink(self.root / "current"), "releases/v0.0.1")
        deployment_cli._reconcile_links(self.root, plan, journal, state_path)
        self.assertEqual(os.readlink(self.root / "current"), "releases/v0.0.2")

    def test_32_failed_or_rolled_back_never_promotes_target(self) -> None:
        from tools.deploy import deployment_cli

        bundle, plan = self.update_bundle()
        (self.root / "current").symlink_to("releases/v0.0.1")
        state_path = self.root / "shared/deploy/installed-state.json"
        for terminal in ("FAILED", "ROLLED_BACK"):
            with self.subTest(terminal=terminal):
                deployment_cli._reconcile_links(
                    self.root,
                    plan,
                    {"state": terminal},
                    state_path,
                )
                self.assertEqual(
                    os.readlink(self.root / "current"), "releases/v0.0.1"
                )
                self.assertFalse((self.root / "previous").exists())

    def test_33_terminal_replay_is_governed_by_core_not_adapter(self) -> None:
        from tools.deploy import deployment_cli

        target = json.loads((self.bundle / "installed-state.next.json").read_text())
        target["reconciled"] = True
        target["installedAt"] = "2026-07-31T12:06:00Z"
        state_path = self.root / "shared/deploy/installed-state.json"
        state_bytes = planner.canonical_bytes(target) + b"\n"
        state_path.write_bytes(state_bytes)
        os.chmod(state_path, 0o600)
        (self.root / "current").symlink_to("releases/v0.0.1")
        journal_path = (
            self.root / f"shared/deploy/journals/{OPERATION}.json"
        )
        journal_path.write_bytes(b'{"state":"SUCCEEDED"}\n')
        os.chmod(journal_path, 0o600)
        terminal = {
            "state": "SUCCEEDED",
            "confirmedStateSha256": "sha256:" + hashlib.sha256(state_bytes).hexdigest(),
        }
        runner = FakeRunner()
        with patch.object(
            deployment_cli.production_adapter.ProductionDeploymentAdapter,
            "validate_compose",
        ), patch.object(
            deployment_cli.deployment_executor,
            "execute_deployment",
            return_value=terminal,
        ) as execute_core:
            for _ in range(2):
                deployment_cli.execute(
                    operation_id=OPERATION,
                    release="v0.0.1",
                    deploy_root=self.root,
                    runner=runner,
                    clock=FakeClock(),
                    docker_binary=DOCKER,
                    curl_binary=CURL,
                )
        self.assertEqual(execute_core.call_count, 2)
        self.assertEqual(runner.calls, [])

    def test_34_cli_output_is_canonical_and_exit_codes_are_fixed(self) -> None:
        from tools.deploy import deployment_cli
        journal = {
            "databaseRestoreRequired": False,
            "errorCode": None,
            "operationId": OPERATION,
            "state": "SUCCEEDED",
        }
        rendered = deployment_cli._output(journal)
        self.assertEqual(rendered, json.dumps(journal, sort_keys=True, separators=(",", ":")))
        self.assertEqual(deployment_cli.TERMINAL_EXIT, {"SUCCEEDED": 0, "ROLLED_BACK": 20, "FAILED": 21})

    def test_35_public_errors_never_render_payload_or_newline(self) -> None:
        error = adapter.ProductionAdapterError("COMMAND_FAILED")
        self.assertEqual(str(error), error.code)
        self.assertNotIn("\n", str(error))
        self.assertNotIn("secret", str(error))
        from tools.deploy import deployment_cli

        payload = "/private/path stdout=FICTITIOUS_SECRET\nTraceback"
        with patch.object(
            deployment_cli, "execute", side_effect=RuntimeError(payload)
        ), patch("builtins.print") as rendered:
            exit_code = deployment_cli.main(
                ["deploy", "--operation-id", OPERATION, "--release", "v0.0.1"]
            )
        self.assertEqual(exit_code, 6)
        public = repr(rendered.call_args_list)
        for forbidden in ("/private/path", "stdout=", "FICTITIOUS_SECRET", "Traceback"):
            self.assertNotIn(forbidden, public)
        self.assertIn("OPERATIONAL_FAILURE", public)

    def test_36_forbidden_commands_and_shell_are_absent(self) -> None:
        source = Path(adapter.__file__).read_text().lower()
        for forbidden in ("shell=true", '"down"', "'down'", "--build", ":latest", '"sudo"', "'sudo'", "prune"):
            self.assertNotIn(forbidden, source)

    def test_37_cli_calls_s19_without_copying_machine(self) -> None:
        from tools.deploy import deployment_cli
        source = Path(deployment_cli.__file__).read_text()
        self.assertIn("execute_deployment", source)
        for state in ("PULLING", "BACKING_UP", "MIGRATING", "UPDATING", "VERIFYING"):
            self.assertNotIn(state, source)

    def test_38_s18_s19_and_adapter_contracts_remain_valid(self) -> None:
        from tools.deploy import validate_deployment_executor
        from tools.deploy import validate_deployment_plan
        from tools.deploy import validate_production_adapter
        validate_deployment_plan.validate(ROOT)
        validate_deployment_executor.validate_executor_source(ROOT)
        validate_production_adapter.validate(ROOT)

    # -- Causal proofs required by the review contract, Section 1.1.5 -----

    def _tampered_source_bundle(
        self, mutate: Callable[[dict[str, Any]], dict[str, Any]]
    ) -> None:
        """Rebuild `self.bundle` (release v0.0.1) as a self-consistent bundle
        whose manifest was mutated, without touching v0.0.2's plan. This
        reproduces the B02 attack: a historical bundle that is internally
        valid but no longer matches what the newer plan recorded as the
        confirmed current state."""
        target = planner.load_target(TARGET)
        mutated = mutate(copy.deepcopy(target))
        target_path = self.root / "tampered-target.json"
        target_path.write_bytes(planner.global_release.canonical(mutated))
        staging = Path(tempfile.mkdtemp(prefix=".s20-alt-", dir=self.root)) / "bundle"
        planner.generate_bundle(
            target_path=target_path,
            current_path=None,
            current_manifest_path=None,
            compose_path=COMPOSE,
            planned_at="2026-07-31T12:00:00Z",
            output_path=staging,
        )
        shutil.rmtree(self.bundle)
        shutil.move(str(staging), str(self.bundle))

    def test_39_source_bundle_component_digest_mismatch_fails_before_runner(
        self,
    ) -> None:
        bundle, plan = self.update_bundle()

        def mutate(value: dict[str, Any]) -> dict[str, Any]:
            for component in value["components"]:
                if component["id"] == "backend":
                    digest = "sha256:" + "9" * 64
                    component["digest"] = digest
                    component["immutableRef"] = (
                        component["imageRepository"] + "@" + digest
                    )
                    component["provenance"]["verifiedSubject"] = component[
                        "immutableRef"
                    ]
            return value

        self._tampered_source_bundle(mutate)
        runner = FakeRunner()
        instance = self.make_adapter(runner, bundle=bundle, plan=plan)
        with self.assertRaisesRegex(
            adapter.ProductionAdapterError, "SOURCE_BUNDLE_INVALID"
        ):
            instance.execute(self.context("ROLLBACK", bundle=bundle, plan=plan))
        self.assertEqual(runner.calls, [])

    def test_40_source_bundle_migration_set_sha_mismatch_fails_before_runner(
        self,
    ) -> None:
        bundle, plan = self.update_bundle()

        def mutate(value: dict[str, Any]) -> dict[str, Any]:
            for database in value["databases"]:
                if database["id"] == "erp":
                    migrations = copy.deepcopy(database["migrations"])
                    version = "99999999999999"
                    migrations.append(
                        {
                            "version": version,
                            "path": (
                                database["location"]
                                + f"/V{version}__s20_causal_proof.sql"
                            ),
                            "sha256": "sha256:" + "7" * 64,
                        }
                    )
                    database["migrations"] = migrations
                    database["migrationSetSha256"] = planner._migration_set_digest(
                        migrations
                    )
                    database["latestVersion"] = migrations[-1]["version"]
            return value

        self._tampered_source_bundle(mutate)
        runner = FakeRunner()
        instance = self.make_adapter(runner, bundle=bundle, plan=plan)
        with self.assertRaisesRegex(
            adapter.ProductionAdapterError, "SOURCE_BUNDLE_INVALID"
        ):
            instance.execute(self.context("ROLLBACK", bundle=bundle, plan=plan))
        self.assertEqual(runner.calls, [])

    def test_41_valid_source_bundle_still_allows_probe_and_rollback(self) -> None:
        bundle, plan = self.update_bundle()
        source_manifest = json.loads((self.bundle / "manifest.json").read_text())
        refs = {
            item["id"]: item["immutableRef"]
            for item in source_manifest["components"]
        }
        runner = FakeRunner(self.container_handler(refs))
        instance = self.make_adapter(runner, bundle=bundle, plan=plan)
        result = instance.probe(self.context("ROLLBACK", bundle=bundle, plan=plan))
        self.assertEqual(result.status, "SUCCEEDED")
        instance.execute(self.context("ROLLBACK", bundle=bundle, plan=plan))

    def test_42_link_hash_mismatch_blocks_promotion_and_preserves_links(
        self,
    ) -> None:
        from tools.deploy import deployment_cli

        bundle, plan = self.update_bundle()
        (self.root / "current").symlink_to("releases/v0.0.1")
        state = json.loads((bundle / "installed-state.next.json").read_text())
        state["reconciled"] = True
        state["installedAt"] = "2026-07-31T12:06:00Z"
        state_path = self.root / "shared/deploy/installed-state.json"
        state_path.write_bytes(planner.canonical_bytes(state) + b"\n")
        os.chmod(state_path, 0o600)
        journal = {
            "state": "SUCCEEDED",
            "confirmedStateSha256": "sha256:" + "b" * 64,
        }
        with self.assertRaisesRegex(
            deployment_cli.DeploymentCliError, "CURRENT_STATE_CONFLICT"
        ):
            deployment_cli._reconcile_links(self.root, plan, journal, state_path)
        self.assertFalse((self.root / "previous").exists())
        self.assertEqual(os.readlink(self.root / "current"), "releases/v0.0.1")

    def test_43_state_mutation_after_first_read_is_detected(self) -> None:
        from tools.deploy import deployment_cli

        bundle, plan = self.update_bundle()
        (self.root / "current").symlink_to("releases/v0.0.1")
        state = json.loads((bundle / "installed-state.next.json").read_text())
        state["reconciled"] = True
        state["installedAt"] = "2026-07-31T12:06:00Z"
        state_path = self.root / "shared/deploy/installed-state.json"
        original_bytes = planner.canonical_bytes(state) + b"\n"
        state_path.write_bytes(original_bytes)
        os.chmod(state_path, 0o600)
        confirmed_sha = "sha256:" + hashlib.sha256(original_bytes).hexdigest()
        journal = {"state": "SUCCEEDED", "confirmedStateSha256": confirmed_sha}
        original_replace = deployment_cli._replace_link

        def tamper_after_swaps(root: Path, name: str, release: str) -> None:
            original_replace(root, name, release)
            if name == "current":
                tampered = json.loads(original_bytes)
                tampered["installedAt"] = "2026-07-31T13:00:00Z"
                state_path.write_bytes(
                    planner.canonical_bytes(tampered) + b"\n"
                )
                os.chmod(state_path, 0o600)

        with patch.object(
            deployment_cli, "_replace_link", side_effect=tamper_after_swaps
        ):
            with self.assertRaisesRegex(
                deployment_cli.DeploymentCliError, "CURRENT_STATE_CONFLICT"
            ):
                deployment_cli._reconcile_links(
                    self.root, plan, journal, state_path
                )

    def test_44_dump_streaming_never_calls_read_bytes(self) -> None:
        original_read_bytes = Path.read_bytes

        def guarded(path_self: Path) -> bytes:
            if path_self.name.endswith(".dump"):
                raise AssertionError(
                    "dump content must never be materialized in memory"
                )
            return original_read_bytes(path_self)

        def handle(
            _argv: tuple[str, ...], _timeout: int, output: Path | None
        ) -> adapter.ProcessResult:
            if output:
                output.write_bytes(b"PGDMP" + b"Q" * 4096)
                os.chmod(output, 0o600)
            return process()

        with patch.object(Path, "read_bytes", guarded):
            self.make_adapter(FakeRunner(handle)).execute(self.context("BACKUP"))

    def test_45_large_sparse_dump_is_streamed_without_full_allocation(
        self,
    ) -> None:
        final = self.root / f"shared/backups/{OPERATION}"
        final.mkdir(mode=0o700)
        size = 8 * 1024 * 1024 + 7
        dump_path = final / "erp.dump"
        with open(dump_path, "wb") as handle:
            handle.truncate(size)
        os.chmod(dump_path, 0o600)
        measured_size, measured_digest = adapter._dump_metadata(dump_path)
        self.assertEqual(measured_size, size)
        self.assertEqual(
            measured_digest, "sha256:" + hashlib.sha256(b"\x00" * size).hexdigest()
        )

    def test_46_dump_size_change_during_hash_fails_closed(self) -> None:
        final = self.root / f"shared/backups/{OPERATION}"
        final.mkdir(mode=0o700)
        dump_path = final / "erp.dump"
        dump_path.write_bytes(b"PGDMP" * 100)
        os.chmod(dump_path, 0o600)
        real_lstat = Path.lstat
        calls = {"count": 0}

        def flaky_lstat(path_self: Path) -> os.stat_result:
            result = real_lstat(path_self)
            if path_self == dump_path:
                calls["count"] += 1
                if calls["count"] == 2:
                    return os.stat_result(
                        (
                            result.st_mode,
                            result.st_ino,
                            result.st_dev,
                            result.st_nlink,
                            result.st_uid,
                            result.st_gid,
                            result.st_size + 1,
                            result.st_atime,
                            result.st_mtime,
                            result.st_ctime,
                        )
                    )
            return result

        with patch.object(Path, "lstat", flaky_lstat):
            with self.assertRaisesRegex(
                adapter.ProductionAdapterError, "BACKUP_INVALID"
            ):
                adapter._dump_metadata(dump_path)
        self.assertEqual(calls["count"], 2)

    def test_47_stdout_overflow_terminates_and_reaps_promptly(self) -> None:
        argv = (
            sys.executable,
            "-c",
            "import sys\nsys.stdout.write('A' * 70000)\nsys.stdout.flush()\n"
            "import time\ntime.sleep(10)\n",
        )
        runner = adapter.SubprocessRunner()
        started = time.monotonic()
        with self.assertRaisesRegex(
            adapter.ProductionAdapterError, "OUTPUT_LIMIT_EXCEEDED"
        ):
            runner._run_captured(argv, 30)
        self.assertLess(time.monotonic() - started, 5)

    def test_48_timeout_terminates_and_reaps_promptly(self) -> None:
        argv = (sys.executable, "-c", "import time\ntime.sleep(10)\n")
        runner = adapter.SubprocessRunner()
        started = time.monotonic()
        with self.assertRaisesRegex(
            adapter.ProductionAdapterError, "COMMAND_TIMEOUT"
        ):
            runner._run_captured(argv, 1)
        self.assertLess(time.monotonic() - started, 5)

    def test_49_repo_digests_pertinence_succeeds_with_extra_digest(self) -> None:
        ref = self.refs["backend"]
        other = (
            "ghcr.io/greggorio/abaronesa-emporio-backend@sha256:" + "e" * 64
        )
        runner = FakeRunner(lambda *_: process(0, json.dumps([other, ref]).encode()))
        instance = self.make_adapter(runner)
        self.assertEqual(instance._image_probe(ref), "SUCCEEDED")

    def test_50_repo_digests_without_expected_or_malformed_is_unknown(
        self,
    ) -> None:
        ref = self.refs["backend"]
        other = (
            "ghcr.io/greggorio/abaronesa-emporio-backend@sha256:" + "e" * 64
        )
        without_expected = self.make_adapter(
            FakeRunner(lambda *_: process(0, json.dumps([other]).encode()))
        )
        self.assertEqual(without_expected._image_probe(ref), "UNKNOWN")
        malformed = self.make_adapter(
            FakeRunner(lambda *_: process(0, json.dumps(["not-a-ref"]).encode()))
        )
        self.assertEqual(malformed._image_probe(ref), "UNKNOWN")
        empty = self.make_adapter(
            FakeRunner(lambda *_: process(0, json.dumps([]).encode()))
        )
        self.assertEqual(empty._image_probe(ref), "UNKNOWN")

    def test_51_cli_binary_resolution_ignores_ambient_path(self) -> None:
        from tools.deploy import deployment_cli

        source = Path(deployment_cli.__file__).read_text(encoding="utf-8")
        self.assertIn("resolve_binary", source)
        self.assertNotIn("shutil.which", source)
        fake_dir = self.root / "evil-path"
        fake_dir.mkdir(mode=0o755)
        fake_docker = fake_dir / "docker"
        fake_docker.write_bytes(b"#!/bin/sh\necho evil\n")
        os.chmod(fake_docker, 0o755)
        with patch.dict(os.environ, {"PATH": str(fake_dir)}):
            try:
                resolved = deployment_cli._binary("docker")
            except deployment_cli.DeploymentCliError as exc:
                self.assertEqual(exc.code, "DEPENDENCY_UNAVAILABLE")
            else:
                self.assertNotEqual(resolved, fake_docker)
                self.assertFalse(str(resolved).startswith(str(fake_dir)))

    # -- Six terminal causal proofs required by review Section 13.5 -----

    def _terminal_link_fixture(
        self, state: dict[str, Any]
    ) -> tuple[dict[str, Any], Path, dict[str, Any], bytes, bytes]:
        bundle, plan = self.update_bundle()
        del bundle
        current = self.root / "current"
        previous = self.root / "previous"
        current.symlink_to("releases/v0.0.1")
        previous.symlink_to("releases/v0.0.1")
        current_before = os.readlink(current).encode("utf-8")
        previous_before = os.readlink(previous).encode("utf-8")
        state_path = self.root / "shared/deploy/installed-state.json"
        state_bytes = planner.canonical_bytes(state) + b"\n"
        state_path.write_bytes(state_bytes)
        os.chmod(state_path, 0o600)
        journal = {
            "state": "SUCCEEDED",
            "confirmedStateSha256": (
                "sha256:" + hashlib.sha256(state_bytes).hexdigest()
            ),
        }
        return plan, state_path, journal, current_before, previous_before

    def test_52_incomplete_confirmed_state_blocks_links_with_matching_hash(
        self,
    ) -> None:
        from tools.deploy import deployment_cli

        state = {"reconciled": True, "release": "v0.0.2"}
        plan, state_path, journal, current_before, previous_before = (
            self._terminal_link_fixture(state)
        )
        with self.assertRaises(deployment_cli.DeploymentCliError) as caught:
            deployment_cli._reconcile_links(
                self.root, plan, journal, state_path
            )
        self.assertEqual(caught.exception.code, "CURRENT_STATE_CONFLICT")
        self.assertEqual(caught.exception.exit_code, 3)
        self.assertEqual(
            os.readlink(self.root / "current").encode("utf-8"),
            current_before,
        )
        self.assertEqual(
            os.readlink(self.root / "previous").encode("utf-8"),
            previous_before,
        )

    def test_53_invalid_confirmed_state_semantics_blocks_links(
        self,
    ) -> None:
        from tools.deploy import deployment_cli

        bundle, _ = self.update_bundle()
        state = json.loads(
            (bundle / "installed-state.next.json").read_text(encoding="utf-8")
        )
        state["reconciled"] = True
        state["installedAt"] = "2020-01-01T00:00:00Z"
        shutil.rmtree(bundle)
        plan, state_path, journal, current_before, previous_before = (
            self._terminal_link_fixture(state)
        )
        with self.assertRaises(deployment_cli.DeploymentCliError) as caught:
            deployment_cli._reconcile_links(
                self.root, plan, journal, state_path
            )
        self.assertEqual(caught.exception.code, "CURRENT_STATE_CONFLICT")
        self.assertEqual(caught.exception.exit_code, 3)
        self.assertEqual(
            os.readlink(self.root / "current").encode("utf-8"),
            current_before,
        )
        self.assertEqual(
            os.readlink(self.root / "previous").encode("utf-8"),
            previous_before,
        )

    def test_54_fully_valid_confirmed_state_reconciles_links(self) -> None:
        from tools.deploy import deployment_cli

        bundle, plan = self.update_bundle()
        state = json.loads(
            (bundle / "installed-state.next.json").read_text(encoding="utf-8")
        )
        state["reconciled"] = True
        state["installedAt"] = "2026-07-31T12:06:00Z"
        current = self.root / "current"
        previous = self.root / "previous"
        current.symlink_to("releases/v0.0.1")
        previous.symlink_to("releases/v0.0.1")
        state_path = self.root / "shared/deploy/installed-state.json"
        state_bytes = planner.canonical_bytes(state) + b"\n"
        state_path.write_bytes(state_bytes)
        os.chmod(state_path, 0o600)
        journal = {
            "state": "SUCCEEDED",
            "confirmedStateSha256": (
                "sha256:" + hashlib.sha256(state_bytes).hexdigest()
            ),
        }
        deployment_cli._reconcile_links(
            self.root, plan, journal, state_path
        )
        self.assertEqual(os.readlink(current), "releases/v0.0.2")
        self.assertEqual(os.readlink(previous), "releases/v0.0.1")

    def test_55_expected_repo_digest_plus_malformed_entry_is_unknown(
        self,
    ) -> None:
        ref = self.refs["backend"]
        runner = FakeRunner(
            lambda *_: process(0, json.dumps([ref, "not-a-ref"]).encode())
        )
        self.assertEqual(self.make_adapter(runner)._image_probe(ref), "UNKNOWN")

    def test_56_two_valid_repo_digests_with_expected_is_succeeded(
        self,
    ) -> None:
        ref = self.refs["backend"]
        other = (
            "ghcr.io/greggorio/abaronesa-emporio-backend@sha256:" + "e" * 64
        )
        runner = FakeRunner(
            lambda *_: process(0, json.dumps([ref, other]).encode())
        )
        self.assertEqual(
            self.make_adapter(runner)._image_probe(ref), "SUCCEEDED"
        )

    def test_57_valid_repo_digests_without_expected_are_unknown(self) -> None:
        ref = self.refs["backend"]
        others = [
            "ghcr.io/greggorio/abaronesa-emporio-backend@sha256:" + "d" * 64,
            "ghcr.io/greggorio/abaronesa-emporio-backend@sha256:" + "e" * 64,
        ]
        runner = FakeRunner(
            lambda *_: process(0, json.dumps(others).encode())
        )
        self.assertEqual(self.make_adapter(runner)._image_probe(ref), "UNKNOWN")


if __name__ == "__main__":
    unittest.main()
