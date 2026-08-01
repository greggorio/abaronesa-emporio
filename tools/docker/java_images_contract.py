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


def _validate_dockerfile(name: str, text: str, errors: list[str]) -> None:
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
    if errors:
        return errors

    _validate_dockerfile("backend", backend, errors)
    _validate_dockerfile("website_back", website, errors)
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
