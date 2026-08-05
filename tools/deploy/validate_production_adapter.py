#!/usr/bin/env python3
"""Fail-closed structural validator for the S20 production adapter contract."""

from __future__ import annotations

import ast
import json
import re
import stat
import sys
from pathlib import Path
from typing import Any

import jsonschema
import yaml


ROOT = Path(__file__).resolve().parents[2]
SCHEMA = ROOT / "ops/deploy/schemas/backup-manifest.schema.json"
EXAMPLE = ROOT / "ops/deploy/examples/backup-manifest.example.json"
ADAPTER = ROOT / "tools/deploy/production_adapter.py"
CLI = ROOT / "tools/deploy/deployment_cli.py"
WRAPPER = ROOT / "ops/deploy/deploy-release.sh"
OPS_DOCUMENTATION = ROOT / "ops/deploy/README.md"
COMPOSE = ROOT / "ops/compose/compose.prod.yml"
JAVA_SOURCES = (
    ROOT
    / "backend/src/main/java/com/baronesa/emporio/migration/ProductionMigrationMain.java",
    ROOT
    / "website_back/src/main/java/com/baronesa/website/migration/ProductionMigrationMain.java",
)
MIGRATE_ENTRYPOINTS = (
    ROOT / "backend/src/main/docker/migrate",
    ROOT / "website_back/src/main/docker/migrate",
)
DOCKERFILES = (ROOT / "backend/Dockerfile", ROOT / "website_back/Dockerfile")

DATABASES = ("erp", "website")
DATABASE_FILES = {"erp": "erp.dump", "website": "website.dump"}
SERVICES = (
    "backend",
    "website_back",
    "frontend",
    "website_front",
    "whatsapp_service",
    "gateway",
)
HOSTS = (
    "erp-emporio.abaronesa.net.br",
    "emporio.abaronesa.net.br",
)


class ValidationError(ValueError):
    """Stable local validation error."""


def require(condition: bool, code: str) -> None:
    if not condition:
        raise ValidationError(code)


def read_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(value, dict), f"json-object:{path.name}")
    return value


def require_closed_objects(value: Any, location: str = "$") -> None:
    if isinstance(value, dict):
        if value.get("type") == "object":
            require(
                value.get("additionalProperties") is False,
                f"schema-open-object:{location}",
            )
        for key, child in value.items():
            require_closed_objects(child, f"{location}.{key}")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            require_closed_objects(child, f"{location}[{index}]")


def validate_backup_contract(
    schema: dict[str, Any], example: dict[str, Any]
) -> None:
    require(
        schema.get("$schema") == "https://json-schema.org/draft/2020-12/schema",
        "backup-schema-draft",
    )
    require_closed_objects(schema)
    try:
        jsonschema.Draft202012Validator.check_schema(schema)
    except jsonschema.SchemaError as exc:
        raise ValidationError("backup-schema-invalid") from exc
    errors = list(
        jsonschema.Draft202012Validator(
            schema, format_checker=jsonschema.FormatChecker()
        ).iter_errors(example)
    )
    require(not errors, "backup-example-schema")
    require(example.get("kind") == "deployment-backup", "backup-kind")
    require(example.get("complete") is True, "backup-complete")
    databases = example.get("databases")
    require(isinstance(databases, list) and databases, "backup-databases")
    ids = [item.get("id") for item in databases if isinstance(item, dict)]
    require(
        ids in (["erp"], ["website"], ["erp", "website"]),
        "backup-database-order",
    )
    for item in databases:
        database_id = item["id"]
        require(
            item.get("file") == DATABASE_FILES[database_id],
            f"backup-database-file:{database_id}",
        )
        require(
            isinstance(item.get("size"), int) and item["size"] > 0,
            f"backup-database-size:{database_id}",
        )


def _source(path: Path) -> tuple[str, ast.Module]:
    source = path.read_text(encoding="utf-8")
    try:
        return source, ast.parse(source)
    except SyntaxError as exc:
        raise ValidationError(f"python-syntax:{path.name}") from exc


def validate_adapter_source(root: Path) -> None:
    source, tree = _source(root / ADAPTER.relative_to(ROOT))
    definitions = {
        node.name
        for node in tree.body
        if isinstance(node, (ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef))
    }
    require(
        {"ProcessResult", "ProcessRunner", "ProductionDeploymentAdapter"}
        <= definitions,
        "adapter-public-surface",
    )
    lowered = source.casefold()
    for forbidden in (
        "shell=true",
        "os.system",
        "os.popen",
        "docker compose down",
        '"down"',
        "'down'",
        "--build",
        ":latest",
        '"latest"',
        "'latest'",
        "docker system prune",
        "docker image prune",
        '"sudo"',
        "'sudo'",
        "eval(",
    ):
        require(forbidden not in lowered, f"adapter-forbidden:{forbidden}")
    for token in (
        "subprocess.run",
        "shell=False",
        "subprocess.DEVNULL",
        "close_fds=True",
        "timeout=",
        "65536",
        "PATH",
        "LANG",
        "LC_ALL",
        "pwd.getpwuid",
        "os.geteuid",
        "DOCKER_CONFIG",
        '".docker"',
        '"config.json"',
        "DOCKER_CONFIG_INVALID",
        "--project-name",
        "abaronesa-emporio",
        "--env-file",
        "release.env",
        "compose.prod.yml",
        "--no-build",
        "--no-deps",
        "--entrypoint",
        "/app/bin/migrate",
        "MIGRATIONS_APPLIED",
        "MIGRATIONS_PENDING",
        "backup-manifest.json",
        "os.replace",
        "os.fsync",
        "0o600",
        "127.0.0.1",
        "gateway_loopback_port",
        "8120",
        "database_restore_required",
    ):
        require(token in source, f"adapter-required:{token}")
    for action in ("PULL", "BACKUP", "MIGRATE", "UPDATE", "VERIFY", "ROLLBACK"):
        require(action in source, f"adapter-action:{action}")
    for service in SERVICES:
        require(service in source, f"adapter-service:{service}")
    for database in DATABASES:
        require(database in source, f"adapter-database:{database}")
    for host in HOSTS:
        require(host in source, f"adapter-host:{host}")
    for prefix in ("pull:", "backup:", "migrate:", "update:", "verify:", "rollback:"):
        require(prefix in source, f"adapter-evidence:{prefix}")


def validate_cli_source(root: Path) -> None:
    source, tree = _source(root / CLI.relative_to(ROOT))
    del tree
    lowered = source.casefold()
    for forbidden in (
        "shell=true",
        "os.system",
        "os.popen",
        "eval(",
        "traceback",
    ):
        require(forbidden not in lowered, f"cli-forbidden:{forbidden}")
    for token in (
        "deployment_executor",
        "execute_deployment",
        "deployment_plan",
        "validate_bundle",
        "ProductionDeploymentAdapter",
        "EMPORIO_DEPLOY_ROOT",
        "/opt/sistemas/emporio",
        "--operation-id",
        "--release",
        "installed-state.json",
        "journals",
        "current",
        "previous",
        "json.dumps",
        "sort_keys=True",
    ):
        require(token in source, f"cli-required:{token}")
    for code in ("20", "21", "6"):
        require(code in source, f"cli-exit-code:{code}")
    require(
        not all(state in source for state in (
            "QUEUED",
            "PULLING",
            "BACKING_UP",
            "MIGRATING",
            "UPDATING",
            "VERIFYING",
            "ROLLING_BACK",
        )),
        "cli-copied-state-machine",
    )


def validate_wrapper(root: Path) -> None:
    path = root / WRAPPER.relative_to(ROOT)
    source = path.read_text(encoding="utf-8")
    require(source.startswith("#!/usr/bin/env bash\n"), "wrapper-shebang")
    for token in (
        "set -euo pipefail",
        "umask 077",
        "deployment_cli.py",
        'exec python3',
        '"$@"',
    ):
        require(token in source, f"wrapper-required:{token}")
    for forbidden in ("eval ", "source ", "bash -c", "sudo "):
        require(forbidden not in source, f"wrapper-forbidden:{forbidden.strip()}")
    require(stat.S_IMODE(path.stat().st_mode) == 0o755, "wrapper-mode")


def validate_java_migrations(root: Path) -> None:
    for path in JAVA_SOURCES:
        source = (root / path.relative_to(ROOT)).read_text(encoding="utf-8")
        label = path.parts[-6]
        for token in (
            "ProductionMigrationMain",
            '"probe"',
            '"migrate"',
            "SPRING_DATASOURCE_URL",
            "SPRING_DATASOURCE_USERNAME",
            "SPRING_DATASOURCE_PASSWORD",
            "classpath:db/migration",
            "baselineOnMigrate(true)",
            "validateOnMigrate(true)",
            "cleanDisabled(true)",
            "MIGRATIONS_APPLIED",
            "MIGRATIONS_PENDING",
            "MIGRATIONS_FAILED",
        ):
            require(token in source, f"java-migration:{label}:{token}")
        for forbidden in ("SpringApplication", ".repair(", ".baseline(", ".clean("):
            require(forbidden not in source, f"java-migration-forbidden:{label}")
    for path in MIGRATE_ENTRYPOINTS:
        source = (root / path.relative_to(ROOT)).read_text(encoding="utf-8")
        for token in (
            "set -eu",
            "exec java",
            "ProductionMigrationMain",
            "/app/app.jar",
            "PropertiesLauncher",
            '"$@"',
        ):
            require(token in source, f"migrate-entrypoint:{path.parent.parent.name}")
        require(
            stat.S_IMODE((root / path.relative_to(ROOT)).stat().st_mode) == 0o755,
            f"migrate-entrypoint-mode:{path.parent.parent.name}",
        )
    for dockerfile in DOCKERFILES:
        source = (root / dockerfile.relative_to(ROOT)).read_text(encoding="utf-8")
        require("/app/bin/migrate" in source, f"dockerfile-migrate:{dockerfile.parent.name}")
        require("chown=0:0" in source, f"dockerfile-migrate-owner:{dockerfile.parent.name}")
        require("chmod 0555 /app/bin/migrate" in source, f"dockerfile-migrate-mode:{dockerfile.parent.name}")


def validate_compose(root: Path) -> None:
    value = yaml.safe_load((root / COMPOSE.relative_to(ROOT)).read_text(encoding="utf-8"))
    try:
        backend = value["services"]["backend"]["environment"]
        website = value["services"]["website_back"]["environment"]
    except (KeyError, TypeError) as exc:
        raise ValidationError("compose-shape") from exc
    require(
        backend.get("SPRING_FLYWAY_ENABLED") == "false",
        "compose-backend-flyway",
    )
    require(
        website.get("FLYWAY_ENABLED") == "false",
        "compose-website-flyway",
    )


def validate_documentation(root: Path) -> None:
    source = (root / OPS_DOCUMENTATION.relative_to(ROOT)).read_text(
        encoding="utf-8"
    )
    lowered = source.casefold()
    for phrase in (
        "/opt/sistemas/emporio",
        "operation-id",
        "primeira instalação",
        "atualização",
        "backup",
        "não restaura",
        "migrations exclusivas",
        "probe",
        "previous",
        "current",
        "exit codes",
        "sanitiz",
        "pré-requisitos",
        "teste exclusivamente local",
        "databaseRestoreRequired",
    ):
        require(phrase.casefold() in lowered, f"documentation:{phrase}")


def validate(root: Path = ROOT) -> None:
    required = (
        SCHEMA,
        EXAMPLE,
        ADAPTER,
        CLI,
        WRAPPER,
        OPS_DOCUMENTATION,
        COMPOSE,
        *JAVA_SOURCES,
        *MIGRATE_ENTRYPOINTS,
        *DOCKERFILES,
    )
    require(
        all((root / path.relative_to(ROOT)).is_file() for path in required),
        "required-file",
    )
    validate_backup_contract(
        read_json(root / SCHEMA.relative_to(ROOT)),
        read_json(root / EXAMPLE.relative_to(ROOT)),
    )
    validate_adapter_source(root)
    validate_cli_source(root)
    validate_wrapper(root)
    validate_java_migrations(root)
    validate_compose(root)
    validate_documentation(root)


def main() -> int:
    try:
        validate()
    except (
        OSError,
        UnicodeError,
        ValueError,
        json.JSONDecodeError,
        yaml.YAMLError,
        jsonschema.SchemaError,
    ) as exc:
        code = str(exc) if isinstance(exc, ValidationError) else "validation-error"
        print(f"production-adapter-contract:invalid:{code}", file=sys.stderr)
        return 3
    print("production-adapter-contract:valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
