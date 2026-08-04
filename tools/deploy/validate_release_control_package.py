"""Fail-closed validator for the isolated S28 release-control package."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

REQUIRED = (
    ".dockerignore",
    "release_control/Dockerfile",
    "release_control/.dockerignore",
    "release_control/README.md",
    "release_control/uv.lock",
    "ops/compose/release-control.yml",
    "ops/env/release-control.env.example",
    "ops/systemd/emporio-release-control.service.example",
    "docs/infrastructure/deployment/release-control/OPERACAO_RELEASE_CONTROL.md",
)

# CVE-2026-69247 (HIGH, fixed in 50.0.0): the pin that shipped before S44
# caught it. Never let it silently reappear from an unrelated lock refresh.
KNOWN_VULNERABLE_PINS = ("cryptography-49.0.0",)

REQUIRED_ENV = {
    "RELEASE_CONTROL_PROFILE",
    "RELEASE_CONTROL_MODE",
    "RELEASE_CONTROL_IMAGE",
    "RELEASE_CONTROL_POSTGRES_IMAGE",
    "RELEASE_CONTROL_LOOPBACK_PORT",
    "RELEASE_CONTROL_DB_HOST",
    "RELEASE_CONTROL_DB_PORT",
    "RELEASE_CONTROL_DB_NAME",
    "RELEASE_CONTROL_DB_USER",
    "RELEASE_CONTROL_DB_PASSWORD",
    "RELEASE_CONTROL_DB_SSLMODE",
    "RELEASE_CONTROL_JWT_ISSUER",
    "RELEASE_CONTROL_JWT_AUDIENCE",
    "RELEASE_CONTROL_JWT_JWKS_URL",
    "RELEASE_CONTROL_CORS_ORIGINS",
    "RELEASE_CONTROL_GITHUB_APP_ID",
    "RELEASE_CONTROL_GITHUB_INSTALLATION_ID",
    "RELEASE_CONTROL_GITHUB_APP_PRIVATE_KEY_PATH",
    "RELEASE_CONTROL_GITHUB_PRIVATE_KEY_PATH",
    "RELEASE_CONTROL_HASH_PEPPER",
    "RELEASE_CONTROL_GITHUB_API_BASE",
}

CANONICAL_ROOT = "/opt/sistemas/emporio-control"
LEGACY_ROOT = "/opt/emporio-release-control"

PLACEHOLDER_MARKERS = (
    "<",
    ">",
    "__SET_",
    "replace-with",
    "from-secret-manager",
)
SECRET_ASSIGNMENT = re.compile(
    r"(?im)^\s*(?:RELEASE_CONTROL_)?(?:DB_PASSWORD|HASH_PEPPER)\s*[:=]\s*([^#\n]+)"
)


def _read(root: Path, relative: str, errors: list[str]) -> str:
    path = root / relative
    if not path.is_file():
        errors.append(f"missing:{relative}")
        return ""
    try:
        return path.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as exc:
        errors.append(f"unreadable:{relative}:{exc}")
        return ""


def _require(text: str, marker: str, code: str, errors: list[str]) -> None:
    if marker not in text:
        errors.append(code)


def _service_block(compose: str, service: str) -> str:
    match = re.search(
        rf"(?ms)^  {re.escape(service)}:\n(.*?)(?=^  [A-Za-z0-9_-]+:|^secrets:|^volumes:|^networks:|\Z)",
        compose,
    )
    return match.group(1) if match else ""


def _validate_dockerfile(text: str, errors: list[str]) -> None:
    # Both stages must pin the same base by digest so a publication is
    # reproducible; the readable tag may stay in front of the digest.
    for stage, code in (("builder", "docker-base-builder"), ("runtime", "docker-base-runtime")):
        if not re.search(
            rf"^FROM python:3\.13-alpine3\.23@sha256:[0-9a-f]{{64}} AS {stage}$",
            text,
            re.MULTILINE,
        ):
            errors.append(code)
    digests = re.findall(r"^FROM \S+@(sha256:[0-9a-f]{64})", text, re.MULTILINE)
    if len(set(digests)) != 1:
        errors.append("docker-base-digest-mismatch")
    for marker, code in (
        ("ARG UV_VERSION=", "docker-uv-pinned"),
        ("WORKDIR /build", "docker-build-workdir"),
        ("WORKDIR /app", "docker-runtime-workdir"),
        ("COPY release_control/pyproject.toml release_control/uv.lock ./", "docker-lock-input"),
        ("RUN uv sync --locked --no-dev --no-install-project", "docker-prod-deps-bootstrap"),
        ("RUN uv sync --locked --no-dev", "docker-prod-deps"),
        ("COPY release_control/src ./src", "docker-src"),
        ("COPY release_control/migrations ./migrations", "docker-migrations"),
        ("COPY release_control/alembic.ini ./", "docker-alembic"),
        ("ops/releases/candidate-manifest.schema.json", "docker-candidate-schema"),
        ("ops/releases/global-release.schema.json", "docker-release-schema"),
        ("ops/releases/release-publication-outcome.schema.json", "docker-publication-outcome-schema"),
        ("ops/deploy/schemas/deployment-workflow-outcome.schema.json", "docker-deployment-outcome-schema"),
        ("ops/deploy/schemas/rollback-workflow-outcome.schema.json", "docker-rollback-outcome-schema"),
        ("COPY --from=builder --chown=10001:10001 /build/ops ./ops", "docker-runtime-schemas"),
        # Alpine base: BusyBox adduser/addgroup instead of shadow utilities.
        ("addgroup --gid 10001 release-control", "docker-group"),
        ("adduser --uid 10001 --ingroup release-control", "docker-user"),
        ("USER 10001:10001", "docker-final-user"),
        ("HEALTHCHECK", "docker-healthcheck"),
        ("http://127.0.0.1:8080/health/live", "docker-health-live"),
        ("CMD [\"sh\", \"-c\",", "docker-entrypoint"),
    ):
        _require(text, marker, code, errors)

    migration_index = text.find("alembic upgrade head")
    uvicorn_index = text.find("uvicorn emporio_release_control.main:app")
    if migration_index < 0 or uvicorn_index < 0 or migration_index >= uvicorn_index:
        errors.append("docker-migration-before-uvicorn")
    if "USER root" in text or re.search(r"FROM\s+[^\n]+:latest", text):
        errors.append("docker-root-or-floating-image")
    if re.search(r"(?m)^\s*(?:ADD|COPY)\s+\.\s", text):
        errors.append("docker-broad-copy")
    if "--dev" in text.replace("--no-dev", ""):
        errors.append("docker-development-dependencies")


def _validate_dockerignore(text: str, errors: list[str]) -> None:
    for marker, code in (
        (".env", "context-env"),
        (".env.*", "context-env-variants"),
        ("*.pem", "context-pem"),
        ("*.key", "context-key"),
        (".venv/", "context-venv"),
        (".pytest_cache/", "context-pytest-cache"),
        (".ruff_cache/", "context-ruff-cache"),
        (".mypy_cache/", "context-mypy-cache"),
        ("*.py[cod]", "context-python-bytecode"),
        ("tests/", "context-tests"),
        ("node_modules/", "context-node-modules"),
        ("uploads/", "context-uploads"),
    ):
        _require(text, marker, code, errors)


def _validate_root_dockerignore(text: str, errors: list[str]) -> None:
    lines = [line.strip() for line in text.splitlines() if line.strip() and not line.startswith("#")]
    if not lines or lines[0] != "**":
        errors.append("root-context-deny-by-default")
    for marker, code in (
        ("!release_control/pyproject.toml", "root-context-pyproject"),
        ("!release_control/uv.lock", "root-context-lock"),
        ("!release_control/src/**", "root-context-src"),
        ("!release_control/migrations/**", "root-context-migrations"),
        ("!ops/releases/candidate-manifest.schema.json", "root-context-candidate-schema"),
        ("!ops/releases/global-release.schema.json", "root-context-release-schema"),
        ("!ops/releases/release-publication-outcome.schema.json", "root-context-publication-outcome-schema"),
        ("!ops/deploy/schemas/deployment-workflow-outcome.schema.json", "root-context-deployment-outcome-schema"),
        ("!ops/deploy/schemas/rollback-workflow-outcome.schema.json", "root-context-rollback-outcome-schema"),
    ):
        _require(text, marker, code, errors)


def _validate_compose(text: str, errors: list[str]) -> None:
    services = text.split("services:", 1)[1].split("\nsecrets:", 1)[0] if "services:" in text else ""
    service_names = set(re.findall(r"(?m)^  ([A-Za-z0-9_-]+):$", services))
    if service_names != {"release_control", "release_control_postgresql"}:
        errors.append(f"compose-services:{sorted(service_names)}")

    for marker, code in (
        ('"127.0.0.1:${RELEASE_CONTROL_LOOPBACK_PORT:-8180}:8080"', "compose-loopback-port"),
        ("release_control_postgresql_data:", "compose-db-volume"),
        ("name: emporio_release_control_postgresql_data", "compose-own-volume"),
        ("name: emporio_release_control_internal", "compose-own-network"),
        ("condition: service_healthy", "compose-db-order"),
        ("pg_isready", "compose-db-health"),
        ("http://127.0.0.1:8080/health/live", "compose-app-health"),
        ("read_only: true", "compose-read-only"),
        ("no-new-privileges:true", "compose-no-new-privileges"),
        ("cap_drop:", "compose-cap-drop"),
        ("target: github-app-private-key", "compose-secret-target"),
        ("file: ${RELEASE_CONTROL_GITHUB_APP_PRIVATE_KEY_PATH}", "compose-secret-file-backed"),
        ('uid: "10001"', "compose-secret-uid"),
        ('gid: "10001"', "compose-secret-gid"),
    ):
        _require(text, marker, code, errors)

    for forbidden, code in (
        ("build:", "compose-build"),
        ("include:", "compose-import"),
        ("extends:", "compose-extends"),
        ("compose.prod.yml", "compose-commercial-import"),
        ("docker-compose.emporio.yml", "compose-commercial-import"),
        ("network_mode: host", "compose-host-network"),
        ("privileged:", "compose-privileged"),
        ("cap_add:", "compose-cap-add"),
        ("/var/run/docker.sock", "compose-docker-socket"),
        ("0.0.0.0:", "compose-public-port"),
        ("external: true", "compose-external-secret"),
        ("docker secret", "compose-swarm-secret"),
        ("swarm", "compose-swarm"),
        ("postgres:16.6-alpine", "compose-floating-postgres-default"),
        (LEGACY_ROOT, "compose-legacy-path"),
    ):
        if forbidden.lower() in text.lower():
            errors.append(code)

    if text.count("pull_policy: never") != 2:
        errors.append("compose-pull-policy-never")

    app = _service_block(text, "release_control")
    if not app:
        errors.append("compose-app-service")
    else:
        if 'user: "10001:10001"' not in app:
            errors.append("compose-app-user")
        if "RELEASE_CONTROL_GITHUB_PRIVATE_KEY_PATH: /run/secrets/github-app-private-key" not in app:
            errors.append("compose-private-key-path")
        environment = re.search(
            r"(?ms)^    environment:\n(.*?)(?=^    [A-Za-z0-9_-]+:|\Z)",
            app,
        )
        app_env = re.findall(
            r"(?m)^      ([A-Za-z0-9_]+):$",
            environment.group(1) if environment else "",
        )
        if any(not key.startswith("RELEASE_CONTROL_") for key in app_env):
            errors.append("compose-app-env-scope")
        if "secrets:" not in app:
            errors.append("compose-app-secret-mount")

    if text.count('"127.0.0.1:${RELEASE_CONTROL_LOOPBACK_PORT:-8180}:8080"') != 1:
        errors.append("compose-single-host-binding")


def _parse_env(text: str) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def _validate_env(text: str, errors: list[str]) -> None:
    values = _parse_env(text)
    missing = sorted(REQUIRED_ENV - values.keys())
    errors.extend(f"env-missing:{key}" for key in missing)
    if values.get("RELEASE_CONTROL_PROFILE") != "runtime":
        errors.append("env-profile")
    if values.get("RELEASE_CONTROL_MODE") != "deployer":
        errors.append("env-mode")
    if values.get("RELEASE_CONTROL_JWT_AUDIENCE") != "emporio-release-control-deployer":
        errors.append("env-audience")
    if values.get("RELEASE_CONTROL_GITHUB_PRIVATE_KEY_PATH") != "/run/secrets/github-app-private-key":
        errors.append("env-private-key-path")
    pem_host_path = values.get("RELEASE_CONTROL_GITHUB_APP_PRIVATE_KEY_PATH", "")
    if not pem_host_path.startswith("/etc/emporio/"):
        errors.append("env-pem-host-path-scope")
    if values.get("RELEASE_CONTROL_LOOPBACK_PORT") != "8180":
        errors.append("env-loopback-port")
    # The internal PostgreSQL host is the only one ever allowed sslmode=disable;
    # the shipped example documents exactly that pairing.
    if values.get("RELEASE_CONTROL_DB_HOST") != "release_control_postgresql":
        errors.append("env-db-host")
    if values.get("RELEASE_CONTROL_DB_SSLMODE") != "disable":
        errors.append("env-db-tls")
    if "latest" in values.get("RELEASE_CONTROL_IMAGE", "").lower():
        errors.append("env-floating-image")
    for key, code in (
        ("RELEASE_CONTROL_IMAGE", "env-image-not-digest"),
        ("RELEASE_CONTROL_POSTGRES_IMAGE", "env-postgres-image-not-digest"),
    ):
        if "@sha256:" not in values.get(key, ""):
            errors.append(code)

    for match in SECRET_ASSIGNMENT.finditer(text):
        value = match.group(1).strip().strip('"\'')
        if not any(marker.lower() in value.lower() for marker in PLACEHOLDER_MARKERS):
            errors.append("env-literal-secret")


def _validate_systemd(text: str, errors: list[str]) -> None:
    for marker, code in (
        ("[Unit]", "systemd-unit"),
        ("After=network-online.target docker.service", "systemd-order"),
        ("User=emporio-release-control", "systemd-user"),
        ("Group=emporio-release-control", "systemd-group"),
        (f"WorkingDirectory={CANONICAL_ROOT}", "systemd-workdir"),
        ("EnvironmentFile=/etc/emporio/release-control.env", "systemd-env-file"),
        ("ExecStart=", "systemd-start"),
        ("--no-build --pull never --wait", "systemd-start-safe"),
        ("ExecStop=", "systemd-stop"),
        ("stop --timeout 30", "systemd-stop-safe"),
        ("TimeoutStopSec=", "systemd-stop-timeout"),
    ):
        _require(text, marker, code, errors)
    for forbidden, code in (
        ("--reload", "systemd-reload"),
        ("docker.sock", "systemd-socket"),
        ("User=root", "systemd-root-user"),
        ("User=deploy-emporio", "systemd-commercial-user"),
        (LEGACY_ROOT, "systemd-legacy-path"),
    ):
        if forbidden in text:
            errors.append(code)


def _validate_docs(readme: str, operations: str, errors: list[str]) -> None:
    for marker, code in (
        ("alembic upgrade head", "docs-migrations"),
        ("/health/live", "docs-health-live"),
        ("/health/ready", "docs-health-ready"),
        ("backup", "docs-backup"),
        ("restauração", "docs-restore"),
        ("stop --timeout 30", "docs-safe-stop"),
    ):
        _require(operations, marker, code, errors)
    _require(operations, "não afirma que o serviço foi", "docs-no-install-claim", errors)
    _require(operations, "não executa Docker build", "docs-no-execution-claim", errors)
    _require(readme, "não executa build", "readme-no-execution-claim", errors)
    _require(readme, "somente dependências de produção", "readme-prod-deps", errors)


def _validate_no_literal_secrets(contents: list[str], errors: list[str]) -> None:
    combined = "\n".join(contents)
    for marker, code in (
        ("-----BEGIN", "literal-private-key"),
        ("ghp_", "literal-github-token"),
        ("github_pat_", "literal-github-token"),
        (LEGACY_ROOT, "package-legacy-path"),
    ):
        if marker.lower() in combined.lower():
            errors.append(code)
    for pin in KNOWN_VULNERABLE_PINS:
        if pin in combined:
            errors.append(f"known-vulnerable-pin:{pin}")


def validate(root: Path = ROOT) -> list[str]:
    errors: list[str] = []
    texts = {relative: _read(root, relative, errors) for relative in REQUIRED}
    _validate_dockerfile(texts["release_control/Dockerfile"], errors)
    _validate_root_dockerignore(texts[".dockerignore"], errors)
    _validate_dockerignore(texts["release_control/.dockerignore"], errors)
    _validate_compose(texts["ops/compose/release-control.yml"], errors)
    _validate_env(texts["ops/env/release-control.env.example"], errors)
    _validate_systemd(texts["ops/systemd/emporio-release-control.service.example"], errors)
    _validate_docs(
        texts["release_control/README.md"],
        texts["docs/infrastructure/deployment/release-control/OPERACAO_RELEASE_CONTROL.md"],
        errors,
    )
    _validate_no_literal_secrets(list(texts.values()), errors)
    return sorted(set(errors))


def main() -> int:
    errors = validate()
    if errors:
        for error in errors:
            print(f"release-control-package:invalid:{error}", file=sys.stderr)
        return 3
    print("release-control-package:valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
