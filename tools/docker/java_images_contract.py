#!/usr/bin/env python3
"""Validador estatico fail-closed das duas imagens Java."""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BUILD_TAG = "maven:3.9.16-eclipse-temurin-21-alpine"
RUNTIME_TAG = "eclipse-temurin:21.0.11_10-jre-alpine-3.23"
BUILD_BASE = (
    f"{BUILD_TAG}@"
    "sha256:d88e5b38297858f65f97bc7e7964c760ab988fd18ace41589176f1468c49a489"
)
RUNTIME_BASE = (
    f"{RUNTIME_TAG}@"
    "sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c"
)
DIGEST = re.compile(r"sha256:[0-9a-f]{64}")
FRONTEND_BASE = (
    "docker.io/docker/dockerfile@"
    "sha256:b5f3b260a9678e1d83d2fce86eeddf79420b79147eaba2a25986f47133d73720"
)
FRONTEND_DIRECTIVE = f"# syntax={FRONTEND_BASE}"
SPRING_BOOT_BASELINE = "3.5.16"
SPRINGDOC_BASELINE = "2.8.17"
BOM_OWNED = re.compile(r"jackson|tomcat", re.I)
OKHTTP_BOM_GROUP = "com.squareup.okhttp3"
OKHTTP_BOM_ARTIFACT = "okhttp-bom"
OKHTTP_BOM_VERSION = "${okhttp.version}"
PROTECTIVE_PROPERTIES = {
    "backend": {
        "postgresql.version": "42.7.12",
        "thymeleaf.version": "3.1.5.RELEASE",
        "okhttp.version": "4.12.0",
    },
    "website_back": {
        "postgresql.version": "42.7.12",
        "netty.version": "4.1.136.Final",
    },
}
PROTECTIVE_MANAGED = {
    "backend": {
        "commons-beanutils": "1.11.0",
        "neethi": "3.2.2",
    },
    "website_back": {
        "protobuf-java": "3.25.5",
        "grpc-netty-shaded": "1.75.0",
    },
}
JAVA_DANFE_TOKENS = ("java-danfe",)
JASPERREPORTS_TOKENS = ("jasperreports",)
DANFE_RENDERER_REQUIRED = (
    ("org.springframework.boot", "spring-boot-starter-thymeleaf", None),
    ("org.xhtmlrenderer", "flying-saucer-pdf-openpdf", "9.1.22"),
    ("com.google.zxing", "core", "3.5.3"),
    ("com.google.zxing", "javase", "3.5.3"),
)
FLYWAY_REQUIRED = (
    "CoreErrorCode.RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED",
    "CoreErrorCode.RESOLVED_REPEATABLE_MIGRATION_NOT_APPLIED",
)
FLYWAY_OBSOLETE = re.compile(r"(?<!Core)\bErrorCode\.RESOLVED_")
REQUIRED_IGNORES = {
    ".git", ".github", ".env", ".env.*", "!.env.example", "target", "uploads",
    "*.pfx", "*.p12", "*.pem", "*.key", "*.hprof", "hs_err_pid*",
    "replay_pid*", ".idea", ".vscode", ".classpath", ".project", ".settings",
}


@dataclass(frozen=True)
class ContractFiles:
    backend_dockerfile: Path
    website_dockerfile: Path
    backend_ignore: Path
    website_ignore: Path
    website_pom: Path
    website_base_properties: Path
    website_prod_properties: Path
    website_security: Path
    documentation: Path
    backend_pom: Path
    backend_migration: Path
    website_migration: Path


def default_files(root: Path = ROOT) -> ContractFiles:
    return ContractFiles(
        root / "backend/Dockerfile",
        root / "website_back/Dockerfile",
        root / "backend/.dockerignore",
        root / "website_back/.dockerignore",
        root / "website_back/pom.xml",
        root / "website_back/src/main/resources/application.properties",
        root / "website_back/src/main/resources/application-prod.properties",
        root / "website_back/src/main/java/com/baronesa/website/config/SecurityConfig.java",
        root / "docs/infrastructure/deployment/images/JAVA_IMAGES.md",
        root / "backend/pom.xml",
        root / "backend/src/main/java/com/baronesa/emporio/migration/ProductionMigrationMain.java",
        root / "website_back/src/main/java/com/baronesa/website/migration/ProductionMigrationMain.java",
    )


def _read(path: Path, errors: list[str]) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except (OSError, UnicodeError):
        errors.append(f"FILE_MISSING:{path}")
        return ""


def _runtime(dockerfile: str) -> str:
    parts = re.split(r"(?m)^FROM\s+", dockerfile)
    return "FROM " + parts[2] if len(parts) == 3 else ""


def _validate_pom(name: str, text: str, errors: list[str]) -> None:
    parent = re.search(r"(?s)<parent>(.*?)</parent>", text)
    parent_text = parent.group(1) if parent else ""
    parent_version = re.search(r"<version>\s*([^<\s]+)\s*</version>", parent_text)
    if (
        "<artifactId>spring-boot-starter-parent</artifactId>" not in parent_text
        or parent_version is None
        or parent_version.group(1) != SPRING_BOOT_BASELINE
    ):
        errors.append(f"SPRING_BOOT_BASELINE_INVALID:{name}")

    springdoc = re.search(r"<springdoc\.version>\s*([^<\s]+)\s*</springdoc\.version>", text)
    if springdoc is None or springdoc.group(1) != SPRINGDOC_BASELINE:
        errors.append(f"SPRINGDOC_BASELINE_INVALID:{name}")

    properties = re.search(r"(?s)<properties>(.*?)</properties>", text)
    declared = re.findall(r"(?m)^\s*<([A-Za-z][\w.\-]*)>", properties.group(1) if properties else "")
    if any(BOM_OWNED.search(item) for item in declared):
        errors.append(f"SPRING_BOM_OVERRIDE_FORBIDDEN:{name}")

    for prop, version in PROTECTIVE_PROPERTIES[name].items():
        if f"<{prop}>{version}</{prop}>" not in text:
            errors.append(f"PROTECTIVE_OVERRIDE_MISSING:{name}:{prop}")
    for artifact, version in PROTECTIVE_MANAGED[name].items():
        block = re.search(
            r"(?s)<artifactId>%s</artifactId>\s*<version>\s*([^<\s]+)\s*</version>" % re.escape(artifact),
            text,
        )
        if block is None or block.group(1) != version:
            errors.append(f"PROTECTIVE_OVERRIDE_MISSING:{name}:{artifact}")

    if name == "backend" and not _imports_okhttp_bom(text):
        errors.append(f"OKHTTP_BOM_IMPORT_REQUIRED:{name}")

    if name == "backend":
        _validate_danfe_chain(name, text, declared, errors)


def _validate_danfe_chain(
    name: str,
    text: str,
    declared: list[str],
    errors: list[str],
) -> None:
    blocks = re.findall(r"(?s)<dependency>(.*?)</dependency>", text)
    lowered = [block.lower() for block in blocks]
    properties = [item.lower() for item in declared]

    if any(token in item for item in properties for token in JAVA_DANFE_TOKENS) or any(
        token in block for block in lowered for token in JAVA_DANFE_TOKENS
    ):
        errors.append(f"UNUSED_JAVA_DANFE_FORBIDDEN:{name}")

    if any(token in item for item in properties for token in JASPERREPORTS_TOKENS) or any(
        token in block for block in lowered for token in JASPERREPORTS_TOKENS
    ):
        errors.append(f"JASPERREPORTS_FORBIDDEN:{name}")

    for group, artifact, version in DANFE_RENDERER_REQUIRED:
        if not _declares_renderer_part(blocks, group, artifact, version):
            errors.append(f"DANFE_RENDERER_REQUIRED:{name}")
            return


def _declares_renderer_part(
    blocks: list[str],
    group: str,
    artifact: str,
    version: str | None,
) -> bool:
    for block in blocks:
        if f"<groupId>{group}</groupId>" not in block:
            continue
        if f"<artifactId>{artifact}</artifactId>" not in block:
            continue
        if version is None:
            return True
        return f"<version>{version}</version>" in block
    return False


def _imports_okhttp_bom(text: str) -> bool:
    for block in re.findall(r"(?s)<dependency>(.*?)</dependency>", text):
        if f"<artifactId>{OKHTTP_BOM_ARTIFACT}</artifactId>" not in block:
            continue
        return (
            f"<groupId>{OKHTTP_BOM_GROUP}</groupId>" in block
            and f"<version>{OKHTTP_BOM_VERSION}</version>" in block
            and "<type>pom</type>" in block
            and "<scope>import</scope>" in block
        )
    return False


def _validate_migration(name: str, text: str, errors: list[str]) -> None:
    missing = any(marker not in text for marker in FLYWAY_REQUIRED)
    if missing or FLYWAY_OBSOLETE.search(text):
        errors.append(f"FLYWAY_ERROR_CODE_INVALID:{name}")


def _validate_dockerfile(name: str, text: str, errors: list[str]) -> None:
    if text.splitlines()[:1] != [FRONTEND_DIRECTIVE]:
        errors.append(f"DOCKERFILE_FRONTEND_INVALID:{name}")
    from_lines = re.findall(r"(?m)^FROM\s+(\S+)\s+AS\s+(\w+)\s*$", text)
    if len(from_lines) != 2 or [stage for _, stage in from_lines] != ["build", "runtime"]:
        errors.append(f"STAGES_INVALID:{name}")
        return
    build_image, runtime_image = from_lines[0][0], from_lines[1][0]
    if build_image != BUILD_BASE or runtime_image != RUNTIME_BASE:
        errors.append(f"BASE_TAG_INVALID:{name}")
    if not DIGEST.fullmatch(build_image.split("@")[-1]) or not DIGEST.fullmatch(runtime_image.split("@")[-1]):
        errors.append(f"BASE_DIGEST_INVALID:{name}")
    if "latest" in text.lower():
        errors.append(f"LATEST_FORBIDDEN:{name}")
    runtime = _runtime(text)
    if re.search(r"\b(?:mvn|maven|javac|jdk)\b", runtime, re.I):
        errors.append(f"RUNTIME_BUILD_TOOL:{name}")
    if "USER 10001:10001" not in runtime:
        errors.append(f"USER_INVALID:{name}")
    if 'ENTRYPOINT ["java", "-jar", "/app/app.jar"]' not in runtime:
        errors.append(f"ENTRYPOINT_INVALID:{name}")
    if 'ENV JAVA_TOOL_OPTIONS=""' not in runtime:
        errors.append(f"JAVA_TOOL_OPTIONS_INVALID:{name}")
    if "SPRING_PROFILES_ACTIVE=prod" not in runtime:
        errors.append(f"SPRING_PROFILE_INVALID:{name}")
    if "STOPSIGNAL SIGTERM" not in runtime:
        errors.append(f"STOPSIGNAL_INVALID:{name}")
    if re.search(r"(?m)^VOLUME\b", text):
        errors.append(f"VOLUME_FORBIDDEN:{name}")
    if "docker.sock" in text:
        errors.append(f"DOCKER_SOCKET_FORBIDDEN:{name}")
    if "/home/gregorio" in text:
        errors.append(f"PERSONAL_PATH_FORBIDDEN:{name}")
    args = set(re.findall(r"(?m)^ARG\s+([A-Za-z_][A-Za-z0-9_]*)", text))
    if args != {"VCS_REF", "IMAGE_VERSION"}:
        errors.append(f"ARGS_INVALID:{name}")
    for line in re.findall(r"(?mi)^(?:ARG|ENV|LABEL)\s+.*$", text):
        if re.search(r"password|secret|token|api[_-]?key|credential", line, re.I):
            errors.append(f"SENSITIVE_METADATA:{name}")

    expected_port = "8080" if name == "backend" else "8085"
    expected_health = f"http://127.0.0.1:{expected_port}/actuator/health"
    if f"EXPOSE {expected_port}" not in runtime:
        errors.append(f"PORT_INVALID:{name}")
    health = re.search(r"(?ms)^HEALTHCHECK\s+(.+?)\nENTRYPOINT", runtime)
    health_text = health.group(1) if health else ""
    if expected_health not in health_text or "curl --fail --silent --show-error" not in health_text:
        errors.append(f"HEALTH_INVALID:{name}")
    for option in ("--interval=", "--timeout=", "--start-period=", "--retries="):
        if option not in health_text:
            errors.append(f"HEALTH_OPTIONS_INVALID:{name}")
            break
    for package in ("curl", "ca-certificates", "tzdata"):
        if package not in runtime:
            errors.append(f"RUNTIME_PACKAGE_MISSING:{name}:{package}")
    if "apk add --no-cache" not in runtime:
        errors.append(f"PACKAGE_CACHE_INVALID:{name}")

    if name == "backend":
        for package in ("ffmpeg", "fontconfig", "ttf-dejavu"):
            if package not in runtime:
                errors.append(f"BACKEND_PACKAGE_MISSING:{package}")
        if "COPY --chown=0:0 nfe/schemas/ /app/nfe/schemas/" not in runtime:
            errors.append("BACKEND_SCHEMA_COPY_INVALID")
        if "chmod -R a-w /app/nfe/schemas" not in runtime:
            errors.append("BACKEND_SCHEMA_PERMISSION_INVALID")
        for path in ("/app/uploads", "/app/nfe/xmls"):
            if path not in runtime:
                errors.append(f"BACKEND_WRITABLE_PATH_MISSING:{path}")
    else:
        for path in (
            "/app/uploads/galeria", "/app/uploads/theme-assets",
            "/app/uploads/android-assets", "/app/uploads/android-private",
        ):
            if path not in runtime:
                errors.append(f"WEBSITE_WRITABLE_PATH_MISSING:{path}")
        if "chown -R 10001:10001 /app/uploads" not in runtime:
            errors.append("WEBSITE_OWNERSHIP_INVALID")


def validate(files: ContractFiles | None = None) -> list[str]:
    if files is None:
        files = default_files()
    if not isinstance(files, ContractFiles):
        return ["INPUT_INVALID"]
    errors: list[str] = []
    backend = _read(files.backend_dockerfile, errors)
    website = _read(files.website_dockerfile, errors)
    backend_ignore = _read(files.backend_ignore, errors)
    website_ignore = _read(files.website_ignore, errors)
    pom = _read(files.website_pom, errors)
    _read(files.website_base_properties, errors)
    prod = _read(files.website_prod_properties, errors)
    security = _read(files.website_security, errors)
    _read(files.documentation, errors)
    backend_pom = _read(files.backend_pom, errors)
    backend_migration = _read(files.backend_migration, errors)
    website_migration = _read(files.website_migration, errors)
    if errors:
        return errors

    _validate_dockerfile("backend", backend, errors)
    _validate_dockerfile("website_back", website, errors)
    _validate_pom("backend", backend_pom, errors)
    _validate_pom("website_back", pom, errors)
    _validate_migration("backend", backend_migration, errors)
    _validate_migration("website_back", website_migration, errors)
    for name, content in (("backend", backend_ignore), ("website_back", website_ignore)):
        entries = {line.strip() for line in content.splitlines() if line.strip() and not line.startswith("#")}
        if not REQUIRED_IGNORES <= entries:
            errors.append(f"DOCKERIGNORE_INCOMPLETE:{name}")
        if entries & {"pom.xml", "src", "src/**"}:
            errors.append(f"DOCKERIGNORE_ESSENTIAL:{name}")
        if name == "backend" and entries & {"nfe", "nfe/**", "nfe/schemas", "nfe/schemas/**"}:
            errors.append("DOCKERIGNORE_SCHEMA_FORBIDDEN")

    if "<artifactId>spring-boot-starter-actuator</artifactId>" not in pom:
        errors.append("WEBSITE_ACTUATOR_MISSING")
    expected_prod = {
        "management.endpoints.web.exposure.include=health",
        "management.endpoint.health.show-details=never",
        "management.endpoint.health.show-components=never",
    }
    if not expected_prod <= set(prod.splitlines()):
        errors.append("WEBSITE_HEALTH_PROPERTIES_INVALID")
    if '.requestMatchers("/actuator/health").permitAll()' not in security:
        errors.append("WEBSITE_HEALTH_SECURITY_INVALID")
    if '.requestMatchers("/actuator/**").permitAll()' in security:
        errors.append("WEBSITE_ACTUATOR_SECURITY_TOO_BROAD")
    return errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["validate"])
    try:
        parser.parse_args(argv)
        errors = validate()
    except (OSError, TypeError, ValueError):
        errors = ["INPUT_INVALID"]
    if errors:
        for error in errors:
            print(f"java-images-contract:invalid:{error}", file=sys.stderr)
        return 2
    print("java-images-contract:valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
