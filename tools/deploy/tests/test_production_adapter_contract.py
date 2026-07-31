from __future__ import annotations

import copy
import importlib.util
import json
import os
import shutil
import stat
import sys
import tempfile
import unittest
from pathlib import Path

import jsonschema
import yaml


ROOT = Path(__file__).resolve().parents[3]
MODULE_PATH = ROOT / "tools/deploy/validate_production_adapter.py"
SPEC = importlib.util.spec_from_file_location(
    "validate_production_adapter", MODULE_PATH
)
validator = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = validator
SPEC.loader.exec_module(validator)

SCHEMA_PATH = ROOT / "ops/deploy/schemas/backup-manifest.schema.json"
EXAMPLE_PATH = ROOT / "ops/deploy/examples/backup-manifest.example.json"


class ProductionAdapterContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
        self.example = json.loads(EXAMPLE_PATH.read_text(encoding="utf-8"))

    def mutant(self) -> Path:
        target = Path(tempfile.mkdtemp(prefix="s20-contract-", dir="/tmp"))
        self.addCleanup(shutil.rmtree, target, True)
        paths = (
            "ops/deploy/schemas/backup-manifest.schema.json",
            "ops/deploy/examples/backup-manifest.example.json",
            "tools/deploy/production_adapter.py",
            "tools/deploy/deployment_cli.py",
            "ops/deploy/deploy-release.sh",
            "ops/deploy/README.md",
            "ops/compose/compose.prod.yml",
            "backend/src/main/java/com/baronesa/emporio/migration/ProductionMigrationMain.java",
            "website_back/src/main/java/com/baronesa/website/migration/ProductionMigrationMain.java",
            "backend/src/main/docker/migrate",
            "website_back/src/main/docker/migrate",
            "backend/Dockerfile",
            "website_back/Dockerfile",
        )
        for path in paths:
            source = ROOT / path
            destination = target / path
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, destination)
        return target

    def assert_invalid(self, root: Path, code: str) -> None:
        with self.assertRaisesRegex(ValueError, code):
            validator.validate(root)

    def test_backup_schema_and_example_are_valid_and_closed(self) -> None:
        validator.validate_backup_contract(self.schema, self.example)

    def test_real_versioned_contract_is_valid(self) -> None:
        validator.validate(ROOT)

    def test_missing_required_file_fails(self) -> None:
        root = self.mutant()
        (root / "ops/deploy/schemas/backup-manifest.schema.json").unlink()
        self.assert_invalid(root, "required-file")

    def test_open_schema_object_mutant_fails(self) -> None:
        schema = copy.deepcopy(self.schema)
        schema["$defs"]["database"]["additionalProperties"] = True
        with self.assertRaisesRegex(ValueError, "schema-open-object"):
            validator.validate_backup_contract(schema, self.example)

    def test_manifest_and_database_extra_fields_fail_schema(self) -> None:
        for location in ("manifest", "database"):
            with self.subTest(location=location):
                value = copy.deepcopy(self.example)
                if location == "manifest":
                    value["unexpected"] = True
                else:
                    value["databases"][0]["unexpected"] = True
                errors = list(
                    jsonschema.Draft202012Validator(self.schema).iter_errors(value)
                )
                self.assertTrue(errors)

    def test_database_order_file_size_hash_and_completion_mutants_fail(self) -> None:
        mutations = (
            lambda value: value["databases"].reverse(),
            lambda value: value["databases"][0].update(file="website.dump"),
            lambda value: value["databases"][0].update(size=0),
            lambda value: value["databases"][0].update(sha256="sha256:wrong"),
            lambda value: value.update(complete=False),
        )
        for index, mutation in enumerate(mutations):
            with self.subTest(index=index):
                value = copy.deepcopy(self.example)
                mutation(value)
                with self.assertRaisesRegex(ValueError, "backup-example-schema"):
                    validator.validate_backup_contract(self.schema, value)

    def test_single_changed_database_variants_are_valid(self) -> None:
        for index in (0, 1):
            with self.subTest(database=self.example["databases"][index]["id"]):
                value = copy.deepcopy(self.example)
                value["databases"] = [value["databases"][index]]
                validator.validate_backup_contract(self.schema, value)

    def test_compose_requires_both_flyway_flags_false(self) -> None:
        root = self.mutant()
        path = root / "ops/compose/compose.prod.yml"
        value = yaml.safe_load(path.read_text(encoding="utf-8"))
        mutations = (
            ("backend", "SPRING_FLYWAY_ENABLED", "compose-backend-flyway"),
            ("website_back", "FLYWAY_ENABLED", "compose-website-flyway"),
        )
        for service, key, code in mutations:
            with self.subTest(service=service):
                mutant = copy.deepcopy(value)
                mutant["services"][service]["environment"][key] = "true"
                path.write_text(
                    yaml.safe_dump(mutant, sort_keys=False), encoding="utf-8"
                )
                with self.assertRaisesRegex(ValueError, code):
                    validator.validate_compose(root)

    def test_forbidden_operational_command_mutants_fail(self) -> None:
        for marker in (
            '"down"',
            '"--build"',
            '"latest"',
            '"sudo"',
            '"docker system prune"',
            '"docker image prune"',
            "shell=True",
        ):
            with self.subTest(marker=marker):
                root = self.mutant()
                path = root / "tools/deploy/production_adapter.py"
                path.write_text(
                    path.read_text(encoding="utf-8")
                    + f"\n# FORBIDDEN_MUTANT {marker}\n",
                    encoding="utf-8",
                )
                self.assert_invalid(root, "adapter-forbidden")

    def test_runner_security_gate_mutants_fail(self) -> None:
        for token in (
            "shell=False",
            "subprocess.DEVNULL",
            "close_fds=True",
            "timeout=",
            "65536",
        ):
            with self.subTest(token=token):
                root = self.mutant()
                path = root / "tools/deploy/production_adapter.py"
                path.write_text(
                    path.read_text(encoding="utf-8").replace(token, "REMOVED_GATE"),
                    encoding="utf-8",
                )
                with self.assertRaisesRegex(
                    ValueError,
                    f"(adapter-required:{token}|python-syntax:production_adapter.py)",
                ):
                    validator.validate(root)

    def test_compose_base_backup_migration_and_atomicity_gate_mutants_fail(self) -> None:
        for token in (
            "--project-name",
            "release.env",
            "backup-manifest.json",
            "/app/bin/migrate",
            "os.replace",
            "os.fsync",
        ):
            with self.subTest(token=token):
                root = self.mutant()
                path = root / "tools/deploy/production_adapter.py"
                path.write_text(
                    path.read_text(encoding="utf-8").replace(token, "REMOVED_GATE"),
                    encoding="utf-8",
                )
                self.assert_invalid(root, f"adapter-required:{token}")

    def test_cli_must_call_s18_and_s19_without_copying_machine(self) -> None:
        root = self.mutant()
        path = root / "tools/deploy/deployment_cli.py"
        source = path.read_text(encoding="utf-8")
        path.write_text(
            source.replace("execute_deployment", "removed_core_call"),
            encoding="utf-8",
        )
        self.assert_invalid(root, "cli-required:execute_deployment")

        root = self.mutant()
        path = root / "tools/deploy/deployment_cli.py"
        path.write_text(
            path.read_text(encoding="utf-8")
            + "\nCOPIED_STATES = ('QUEUED','PULLING','BACKING_UP','MIGRATING',"
            + "'UPDATING','VERIFYING','ROLLING_BACK')\n",
            encoding="utf-8",
        )
        self.assert_invalid(root, "cli-copied-state-machine")

    def test_wrapper_rejects_unsafe_mutants(self) -> None:
        for marker in ("eval ", "source ", "bash -c", "sudo "):
            with self.subTest(marker=marker):
                root = self.mutant()
                path = root / "ops/deploy/deploy-release.sh"
                path.write_text(path.read_text(encoding="utf-8") + marker + "\n")
                self.assert_invalid(root, "wrapper-forbidden")

    def test_java_migration_cannot_boot_application_or_relax_flyway(self) -> None:
        for marker in (
            "SpringApplication.run",
            ".repair(",
            ".baseline(",
            ".clean(",
        ):
            with self.subTest(marker=marker):
                root = self.mutant()
                path = (
                    root
                    / "backend/src/main/java/com/baronesa/emporio/migration/ProductionMigrationMain.java"
                )
                path.write_text(path.read_text(encoding="utf-8") + marker + "\n")
                self.assert_invalid(root, "java-migration-forbidden")

    def test_migration_entrypoint_and_dockerfile_gates_fail_when_removed(self) -> None:
        root = self.mutant()
        path = root / "backend/src/main/docker/migrate"
        path.write_text(
            path.read_text(encoding="utf-8").replace("PropertiesLauncher", "REMOVED")
        )
        self.assert_invalid(root, "migrate-entrypoint")

        root = self.mutant()
        path = root / "backend/Dockerfile"
        path.write_text(
            path.read_text(encoding="utf-8").replace(
                "chmod 0555 /app/bin/migrate", "chmod 0777 /app/bin/migrate"
            )
        )
        self.assert_invalid(root, "dockerfile-migrate-mode")

    def test_documentation_restore_boundary_mutant_fails(self) -> None:
        root = self.mutant()
        path = root / "ops/deploy/README.md"
        path.write_text(
            path.read_text(encoding="utf-8").replace(
                "não restaura", "restaura automaticamente"
            ),
            encoding="utf-8",
        )
        self.assert_invalid(root, "documentation:não restaura")


if __name__ == "__main__":
    unittest.main()
