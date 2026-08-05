from __future__ import annotations

import contextlib
import copy
import importlib.util
import io
import json
import os
import shutil
import stat
import subprocess
import tempfile
import time
import unittest
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[3]
MODULE_PATH = ROOT / "tools/deploy/deployment_plan.py"
SPEC = importlib.util.spec_from_file_location("deployment_plan", MODULE_PATH)
planner = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(planner)

TARGET_PATH = ROOT / "ops/releases/examples/global-release.example.json"
CURRENT_PATH = ROOT / "ops/deploy/examples/installed-state.example.json"
COMPOSE_PATH = ROOT / "ops/compose/compose.prod.yml"
PLANNED_AT = "2026-07-29T17:00:00Z"


class DeploymentPlanTest(unittest.TestCase):
    def setUp(self) -> None:
        self.target = json.loads(TARGET_PATH.read_text(encoding="utf-8"))
        self.current = json.loads(CURRENT_PATH.read_text(encoding="utf-8"))

    def update_target(self) -> dict:
        target = copy.deepcopy(self.target)
        target["release"] = "v0.0.2"
        target["previousRelease"] = self.current["release"]
        target["publishedAt"] = "2026-07-29T16:10:00Z"
        return target

    def change_component(self, target: dict, index: int) -> None:
        item = target["components"][index]
        digest = "sha256:" + f"{index + 10:x}" * 64
        digest = digest[:71]
        immutable = f"{item['imageRepository']}@{digest}"
        item["digest"] = digest
        item["immutableRef"] = immutable

    def set_migrations(self, database: dict, migrations: list[dict]) -> None:
        database["migrations"] = copy.deepcopy(migrations)
        database["migrationSetSha256"] = planner._migration_set_digest(migrations)
        if "latestVersion" in database:
            database["latestVersion"] = migrations[-1]["version"]

    def assert_code(self, code: str, callable_, *args, **kwargs) -> None:
        with self.assertRaises(planner.DeploymentPlanError) as caught:
            callable_(*args, **kwargs)
        self.assertEqual(code, caught.exception.code)

    def write_json(self, directory: Path, name: str, value: dict) -> Path:
        path = directory / name
        path.write_bytes(planner._json_file_bytes(value))
        return path

    def generate_first_bundle(self, parent: Path, name: str = "bundle") -> Path:
        output = parent / name
        planner.generate_bundle(
            target_path=TARGET_PATH,
            current_path=None,
            current_manifest_path=None,
            compose_path=COMPOSE_PATH,
            planned_at="2026-07-29T16:00:00Z",
            output_path=output,
        )
        return output

    def rewrite_checksums(self, bundle: Path) -> None:
        payloads = {
            name: (bundle / name).read_bytes() for name in planner.BUNDLE_PAYLOADS
        }
        (bundle / "bundle.sha256").write_bytes(planner._checksum_file(payloads))
        os.chmod(bundle / "bundle.sha256", 0o600)

    def assert_no_staging(self, parent: Path, output: Path) -> None:
        self.assertFalse(output.exists())
        self.assertEqual([], list(parent.glob(f".{output.name}.stage-*")))

    def test_first_installation_generates_six_updates_in_canonical_order(self) -> None:
        plan = planner.build_plan(self.target, None, "2026-07-29T16:00:00Z")
        self.assertEqual(list(planner.COMPONENTS), [x["component"] for x in plan["components"]])
        self.assertEqual(["UPDATE"] * 6, [x["action"] for x in plan["components"]])
        self.assertEqual([None] * 6, [x["currentDigest"] for x in plan["components"]])
        self.assertEqual(list(planner.COMPONENTS), plan["servicesToPull"])
        self.assertEqual(plan["servicesToPull"], plan["servicesToUpdate"])

    def test_equal_update_generates_six_keeps(self) -> None:
        plan = planner.build_plan(self.update_target(), self.current, PLANNED_AT)
        self.assertEqual(["KEEP"] * 6, [x["action"] for x in plan["components"]])
        self.assertEqual([], plan["servicesToPull"])
        self.assertEqual([], plan["servicesToUpdate"])

    def test_each_isolated_component_change_updates_only_that_component(self) -> None:
        for index, component in enumerate(planner.COMPONENTS):
            with self.subTest(component=component):
                target = self.update_target()
                self.change_component(target, index)
                plan = planner.build_plan(target, self.current, PLANNED_AT)
                self.assertEqual(
                    ["UPDATE" if i == index else "KEEP" for i in range(6)],
                    [x["action"] for x in plan["components"]],
                )
                self.assertEqual([component], plan["servicesToPull"])

    def test_service_and_image_variable_mapping_is_exact(self) -> None:
        plan = planner.build_plan(self.target, None, "2026-07-29T16:00:00Z")
        self.assertEqual(
            [
                (component, planner.SERVICES[component], planner.IMAGE_VARIABLES[component])
                for component in planner.COMPONENTS
            ],
            [
                (item["component"], item["service"], item["imageVariable"])
                for item in plan["components"]
            ],
        )

    def test_release_env_is_exact_complete_and_deterministic(self) -> None:
        expected = [f"RELEASE_ID={self.target['release']}"]
        by_id = {item["id"]: item for item in self.target["components"]}
        expected.extend(
            f"{planner.IMAGE_VARIABLES[item]}={by_id[item]['immutableRef']}"
            for item in planner.COMPONENTS
        )
        expected_bytes = ("\n".join(expected) + "\n").encode()
        self.assertEqual(expected_bytes, planner.build_release_env(self.target))
        self.assertEqual(expected_bytes, planner.build_release_env(copy.deepcopy(self.target)))
        self.assertEqual(7, len(expected_bytes.splitlines()))

    def test_current_migration_prefix_produces_only_suffix(self) -> None:
        target = self.update_target()
        current = copy.deepcopy(self.current)
        target_db = target["databases"][0]
        suffix = target_db["migrations"][-2:]
        self.set_migrations(current["databases"][0], target_db["migrations"][:-2])
        plan = planner.build_plan(target, current, PLANNED_AT)
        self.assertEqual(suffix, plan["databases"][0]["pendingMigrations"])
        self.assertTrue(plan["migrationRequired"])
        self.assertTrue(plan["backupRequired"])

    def test_identical_migrations_do_not_require_backup(self) -> None:
        plan = planner.build_plan(self.update_target(), self.current, PLANNED_AT)
        self.assertEqual([False, False], [x["changed"] for x in plan["databases"]])
        self.assertFalse(plan["migrationRequired"])
        self.assertFalse(plan["backupRequired"])

    def test_two_changed_databases_have_independent_deltas(self) -> None:
        target = self.update_target()
        current = copy.deepcopy(self.current)
        expected = []
        for index, database in enumerate(target["databases"]):
            count = index + 1
            expected.append(database["migrations"][-count:])
            self.set_migrations(current["databases"][index], database["migrations"][:-count])
        plan = planner.build_plan(target, current, PLANNED_AT)
        self.assertEqual(expected, [x["pendingMigrations"] for x in plan["databases"]])
        self.assertEqual([True, True], [x["changed"] for x in plan["databases"]])

    def test_non_forward_migration_mutations_fail_causally(self) -> None:
        cases = {}
        removed = self.update_target()
        self.set_migrations(removed["databases"][0], removed["databases"][0]["migrations"][:-1])
        cases["removal"] = removed
        altered = self.update_target()
        migrations = copy.deepcopy(altered["databases"][0]["migrations"])
        migrations[0]["sha256"] = "sha256:" + "f" * 64
        self.set_migrations(altered["databases"][0], migrations)
        cases["applied-hash"] = altered
        for name, target in cases.items():
            with self.subTest(name=name):
                self.assert_code(
                    "NON_FORWARD_MIGRATION",
                    planner.build_plan,
                    target,
                    self.current,
                    PLANNED_AT,
                )
        reordered = copy.deepcopy(self.current["databases"][0]["migrations"])
        reordered[0], reordered[1] = reordered[1], reordered[0]
        self.assert_code(
            "NON_FORWARD_MIGRATION",
            planner._pending_migrations,
            self.current["databases"][0]["migrations"],
            reordered,
        )

    def test_release_chain_jump_equal_and_downgrade_fail(self) -> None:
        cases = {
            "jump": ("v0.0.2", "v0.0.0"),
            "equal": ("v0.0.1", "v0.0.1"),
            "downgrade": ("v0.0.0", "v0.0.1"),
        }
        for name, (release, previous) in cases.items():
            with self.subTest(name=name):
                target = self.update_target()
                target["release"] = release
                target["previousRelease"] = previous
                self.assert_code(
                    "RELEASE_CHAIN_MISMATCH",
                    planner._validate_chain,
                    target,
                    self.current,
                )

    def test_first_installation_accepts_historical_predecessor_without_installing_it(
        self,
    ) -> None:
        first = copy.deepcopy(self.target)
        first["previousRelease"] = "v0.0.0"
        plan = planner.build_plan(first, None, "2026-07-29T16:00:00Z")
        self.assertTrue(plan["firstInstallation"])
        self.assertIsNone(plan["sourceRelease"])
        self.assertEqual(
            ["UPDATE"] * 6, [item["action"] for item in plan["components"]]
        )
        self.assertEqual(
            [None] * 6, [item["currentDigest"] for item in plan["components"]]
        )
        self.assertEqual(
            [database["migrations"] for database in first["databases"]],
            [database["pendingMigrations"] for database in plan["databases"]],
        )
        self.assertEqual(
            [True, True], [item["changed"] for item in plan["databases"]]
        )
        self.assertTrue(plan["migrationRequired"])
        self.assertTrue(plan["backupRequired"])

    def test_update_still_requires_the_installed_release_as_predecessor(self) -> None:
        update = self.update_target()
        update["previousRelease"] = "v0.0.0"
        self.assert_code(
            "RELEASE_CHAIN_MISMATCH",
            planner.build_plan,
            update,
            self.current,
            PLANNED_AT,
        )

    def test_first_installation_bundle_preserves_null_operational_source(self) -> None:
        with tempfile.TemporaryDirectory(prefix="s46-first-lineage-", dir="/tmp") as raw:
            parent = Path(raw)
            target = copy.deepcopy(self.target)
            target["previousRelease"] = "v0.0.0"
            target_path = self.write_json(parent, "target.json", target)
            bundle = parent / "bundle"
            plan = planner.generate_bundle(
                target_path=target_path,
                current_path=None,
                current_manifest_path=None,
                compose_path=COMPOSE_PATH,
                planned_at="2026-07-29T16:00:00Z",
                output_path=bundle,
            )
            self.assertTrue(plan["firstInstallation"])
            self.assertIsNone(plan["sourceRelease"])
            validated = planner.validate_bundle(bundle)
            self.assertTrue(validated["firstInstallation"])
            self.assertIsNone(validated["sourceRelease"])

            plan_path = bundle / "deployment-plan.json"
            invented = json.loads(plan_path.read_text(encoding="utf-8"))
            invented["sourceRelease"] = target["previousRelease"]
            plan_path.write_bytes(planner._json_file_bytes(invented))
            self.rewrite_checksums(bundle)
            self.assert_code("INVALID_CONTRACT", planner.validate_bundle, bundle)

    def test_current_and_historical_manifest_must_be_paired(self) -> None:
        with tempfile.TemporaryDirectory(prefix="s18-pair-", dir="/tmp") as raw:
            parent = Path(raw)
            self.assert_code(
                "CURRENT_STATE_MISMATCH",
                planner.generate_bundle,
                target_path=TARGET_PATH,
                current_path=CURRENT_PATH,
                current_manifest_path=None,
                compose_path=COMPOSE_PATH,
                planned_at=PLANNED_AT,
                output_path=parent / "missing-manifest",
            )
            self.assert_code(
                "CURRENT_STATE_MISMATCH",
                planner.generate_bundle,
                target_path=TARGET_PATH,
                current_path=None,
                current_manifest_path=TARGET_PATH,
                compose_path=COMPOSE_PATH,
                planned_at=PLANNED_AT,
                output_path=parent / "missing-current",
            )

    def test_historical_manifest_hash_mismatch_fails(self) -> None:
        current = copy.deepcopy(self.current)
        current["manifestSha256"] = "sha256:" + "f" * 64
        self.assert_code(
            "CURRENT_STATE_MISMATCH",
            planner._validate_current_pair,
            current,
            self.target,
        )

    def test_repository_digest_and_immutable_mutants_fail(self) -> None:
        mutants = []
        repository = copy.deepcopy(self.target)
        repository["components"][0]["imageRepository"] += "-wrong"
        mutants.append(repository)
        digest = copy.deepcopy(self.target)
        digest["components"][0]["digest"] = "sha256:" + "f" * 64
        mutants.append(digest)
        immutable = copy.deepcopy(self.target)
        immutable["components"][0]["immutableRef"] = (
            immutable["components"][0]["imageRepository"] + "@sha256:" + "f" * 64
        )
        mutants.append(immutable)
        swapped = copy.deepcopy(self.target)
        swapped["components"][0]["immutableRef"] = (
            swapped["components"][1]["immutableRef"]
        )
        mutants.append(swapped)
        for index, mutant in enumerate(mutants):
            with self.subTest(index=index):
                self.assert_code("INVALID_CONTRACT", planner._validate_target_contract, mutant)

    def test_component_id_duplicate_missing_extra_and_order_mutants_fail(self) -> None:
        mutants = []
        duplicate = copy.deepcopy(self.target)
        duplicate["components"][1]["id"] = duplicate["components"][0]["id"]
        mutants.append(duplicate)
        missing = copy.deepcopy(self.target)
        missing["components"].pop()
        mutants.append(missing)
        extra = copy.deepcopy(self.target)
        extra["components"].append(copy.deepcopy(extra["components"][-1]))
        extra["components"][-1]["id"] = "extra"
        mutants.append(extra)
        reordered = copy.deepcopy(self.target)
        reordered["components"][0], reordered["components"][1] = (
            reordered["components"][1],
            reordered["components"][0],
        )
        mutants.append(reordered)
        for index, mutant in enumerate(mutants):
            with self.subTest(index=index):
                self.assert_code("INVALID_CONTRACT", planner._validate_target_contract, mutant)

    def test_compose_wrong_path_symlink_and_size_limit_fail(self) -> None:
        with tempfile.TemporaryDirectory(prefix="s18-compose-", dir="/tmp") as raw:
            parent = Path(raw)
            wrong = parent / "compose.yml"
            wrong.write_bytes(COMPOSE_PATH.read_bytes())
            self.assert_code("INVALID_CONTRACT", planner._load_compose, wrong)
            link = parent / "compose-link.yml"
            link.symlink_to(COMPOSE_PATH)
            self.assert_code("INVALID_CONTRACT", planner._load_compose, link)
        with mock.patch.object(planner, "MAX_COMPOSE_BYTES", COMPOSE_PATH.stat().st_size - 1):
            self.assert_code("INVALID_CONTRACT", planner._load_compose, COMPOSE_PATH)

    def test_preexisting_outputs_are_never_altered(self) -> None:
        with tempfile.TemporaryDirectory(prefix="s18-conflict-", dir="/tmp") as raw:
            parent = Path(raw)
            for name, initial in (("empty", b""), ("populated", b"preserve")):
                with self.subTest(name=name):
                    output = parent / name
                    output.mkdir()
                    marker = output / "marker"
                    marker.write_bytes(initial)
                    self.assert_code(
                        "BUNDLE_CONFLICT",
                        planner.generate_bundle,
                        target_path=TARGET_PATH,
                        current_path=None,
                        current_manifest_path=None,
                        compose_path=COMPOSE_PATH,
                        planned_at="2026-07-29T16:00:00Z",
                        output_path=output,
                    )
                    self.assertEqual(initial, marker.read_bytes())

    def test_injected_write_failures_leave_no_staging(self) -> None:
        with tempfile.TemporaryDirectory(prefix="s18-write-", dir="/tmp") as raw:
            parent = Path(raw)
            real_write = planner._write_file
            for failure_call in range(1, 7):
                with self.subTest(failure_call=failure_call):
                    output = parent / f"bundle-{failure_call}"
                    calls = 0

                    def failing_write(path, data):
                        nonlocal calls
                        calls += 1
                        if calls == failure_call:
                            raise OSError("injected")
                        return real_write(path, data)

                    with mock.patch.object(planner, "_write_file", side_effect=failing_write):
                        self.assert_code(
                            "ATOMICITY_FAILED",
                            planner.generate_bundle,
                            target_path=TARGET_PATH,
                            current_path=None,
                            current_manifest_path=None,
                            compose_path=COMPOSE_PATH,
                            planned_at="2026-07-29T16:00:00Z",
                            output_path=output,
                        )
                    self.assert_no_staging(parent, output)

    def test_injected_fsync_verify_and_rename_failures_leave_no_staging(self) -> None:
        patches = (
            ("fsync", "_fsync_directory", OSError("fsync")),
            ("verify", "_verify_staging", planner.DeploymentPlanError("INVALID_CONTRACT")),
            ("rename", "replace", OSError("rename")),
        )
        with tempfile.TemporaryDirectory(prefix="s18-atomic-", dir="/tmp") as raw:
            parent = Path(raw)
            for name, attribute, failure in patches:
                with self.subTest(name=name):
                    output = parent / name
                    owner = planner.os if name == "rename" else planner
                    with mock.patch.object(owner, attribute, side_effect=failure):
                        expected = "INVALID_CONTRACT" if name == "verify" else "ATOMICITY_FAILED"
                        self.assert_code(
                            expected,
                            planner.generate_bundle,
                            target_path=TARGET_PATH,
                            current_path=None,
                            current_manifest_path=None,
                            compose_path=COMPOSE_PATH,
                            planned_at="2026-07-29T16:00:00Z",
                            output_path=output,
                        )
                    self.assert_no_staging(parent, output)

    def test_each_fsync_failure_is_causal_and_never_leaves_staging(self) -> None:
        with tempfile.TemporaryDirectory(prefix="s18-fsync-", dir="/tmp") as raw:
            parent = Path(raw)
            real_fsync = planner.os.fsync
            for failure_call in range(1, 9):
                with self.subTest(failure_call=failure_call):
                    output = parent / f"bundle-{failure_call}"
                    calls = 0

                    def failing_fsync(descriptor):
                        nonlocal calls
                        calls += 1
                        if calls == failure_call:
                            raise OSError("injected")
                        return real_fsync(descriptor)

                    with mock.patch.object(
                        planner.os, "fsync", side_effect=failing_fsync
                    ):
                        self.assert_code(
                            "ATOMICITY_FAILED",
                            planner.generate_bundle,
                            target_path=TARGET_PATH,
                            current_path=None,
                            current_manifest_path=None,
                            compose_path=COMPOSE_PATH,
                            planned_at="2026-07-29T16:00:00Z",
                            output_path=output,
                        )
                    self.assertEqual(
                        [], list(parent.glob(f".{output.name}.stage-*"))
                    )
                    if failure_call < 8:
                        self.assertFalse(output.exists())
                    else:
                        self.assertTrue(output.is_dir())
                        self.assertEqual(
                            planner.BUNDLE_FILES,
                            {item.name for item in output.iterdir()},
                        )
                        planner.validate_bundle(output)

    def test_generated_bundle_has_exact_files_modes_and_valid_hashes(self) -> None:
        with tempfile.TemporaryDirectory(prefix="s18-bundle-", dir="/tmp") as raw:
            bundle = self.generate_first_bundle(Path(raw))
            self.assertEqual(planner.BUNDLE_FILES, {x.name for x in bundle.iterdir()})
            self.assertEqual(0o700, stat.S_IMODE(bundle.stat().st_mode))
            self.assertEqual(
                {0o600}, {stat.S_IMODE(x.stat().st_mode) for x in bundle.iterdir()}
            )
            self.assertEqual("v0.0.1", planner.validate_bundle(bundle)["targetRelease"])

    def test_modified_payload_and_extra_file_fail_bundle_validation(self) -> None:
        with tempfile.TemporaryDirectory(prefix="s18-tamper-", dir="/tmp") as raw:
            parent = Path(raw)
            modified = self.generate_first_bundle(parent, "modified")
            with (modified / "release.env").open("ab") as stream:
                stream.write(b"#")
            self.assert_code("INVALID_CONTRACT", planner.validate_bundle, modified)
            extra = self.generate_first_bundle(parent, "extra")
            (extra / "unexpected").write_bytes(b"x")
            os.chmod(extra / "unexpected", 0o600)
            self.assert_code("INVALID_CONTRACT", planner.validate_bundle, extra)

    def test_internal_symlink_fails_bundle_validation(self) -> None:
        with tempfile.TemporaryDirectory(prefix="s18-symlink-", dir="/tmp") as raw:
            bundle = self.generate_first_bundle(Path(raw))
            path = bundle / "release.env"
            path.unlink()
            path.symlink_to(COMPOSE_PATH)
            self.assert_code("INVALID_CONTRACT", planner.validate_bundle, bundle)

    def test_noncanonical_json_fails_even_with_updated_checksum(self) -> None:
        with tempfile.TemporaryDirectory(prefix="s18-json-", dir="/tmp") as raw:
            bundle = self.generate_first_bundle(Path(raw))
            path = bundle / "deployment-plan.json"
            value = json.loads(path.read_text(encoding="utf-8"))
            path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
            os.chmod(path, 0o600)
            self.rewrite_checksums(bundle)
            self.assert_code("INVALID_CONTRACT", planner.validate_bundle, bundle)

    def test_invalid_timestamps_fail_and_system_clock_is_not_read(self) -> None:
        invalid = (
            "2026-07-29T17:00:00.000Z",
            "2026-07-29T17:00:00+00:00",
            "2026-07-29 17:00:00Z",
            "2026-02-30T17:00:00Z",
        )
        for value in invalid:
            with self.subTest(value=value):
                self.assert_code("INVALID_CONTRACT", planner.build_plan, self.target, None, value)
        with mock.patch.object(time, "time", side_effect=AssertionError("clock read")) as clock:
            planner.build_plan(self.target, None, "2026-07-29T16:00:00Z")
            clock.assert_not_called()

    def test_no_external_command_surface_exists_or_is_called(self) -> None:
        source = MODULE_PATH.read_text(encoding="utf-8")
        for forbidden in ("subprocess", "os.system(", "os.popen(", "exec(", "spawn"):
            self.assertNotIn(forbidden, source)
        with mock.patch.object(
            subprocess, "run", side_effect=AssertionError("external command")
        ) as command:
            planner.build_plan(self.target, None, "2026-07-29T16:00:00Z")
            command.assert_not_called()

    def test_cli_errors_are_single_line_sanitized_and_without_traceback(self) -> None:
        sensitive = "fixture-sensitive-marker"
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            exit_code = planner.main(
                [
                    "generate",
                    "--target",
                    sensitive,
                    "--compose",
                    str(COMPOSE_PATH),
                    "--planned-at",
                    "invalid",
                    "--output",
                    "/tmp/s18-never-created",
                ]
            )
        value = stderr.getvalue()
        self.assertEqual(3, exit_code)
        self.assertEqual(1, len(value.splitlines()))
        self.assertNotIn(sensitive, value)
        self.assertNotIn("Traceback", value)

    def test_next_state_is_unreconciled_target_projection(self) -> None:
        value = planner.build_next_state(self.target, "2026-07-29T16:00:00Z")
        self.assertFalse(value["reconciled"])
        self.assertIsNone(value["installedAt"])
        self.assertEqual(planner.manifest_digest(self.target), value["manifestSha256"])
        self.assertEqual(
            [{"id": x["id"], "immutableRef": x["immutableRef"]} for x in self.target["components"]],
            value["components"],
        )


if __name__ == "__main__":
    unittest.main()
