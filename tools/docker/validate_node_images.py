#!/usr/bin/env python3
"""Fail-closed static contract validator for the S09 Node images."""

from __future__ import annotations

import json
import re
import shlex
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COMPONENTS = ("frontend", "website_front", "whatsapp_service")
NODE_BASE = (
    "node:24.18.1-alpine3.24@"
    "sha256:f70403e87646dc51b45295f4b8b70cdad0b63d2297c4c9899119b03f7af7a6b3"
)
NGINX_BASE = (
    "nginx:1.31.3-alpine3.24@"
    "sha256:4a73073bd557c65b759505da037898b61f1be6cbcc3c2c3aeac22d2a470c1752"
)
IGNORE_REQUIRED = (
    ".git",
    ".github",
    ".env",
    "node_modules",
    "dist",
    "coverage",
    "*.log",
    "*.hprof",
    ".wwebjs_auth",
    "*.pfx",
    "*.key",
)
GLOBAL_NPM_FLAGS = ("-g", "--global", "--location=global")
GLOBAL_NPM_SUBCOMMANDS = ("install", "i", "add")
WHATSAPP_PACKAGE_MANAGERS = ("npm", "npx", "corepack", "yarn", "yarnpkg")
WHATSAPP_RUNTIME_PURGE_PATHS = (
    "/usr/local/lib/node_modules/npm",
    "/usr/local/lib/node_modules/corepack",
    "/opt/yarn-v1.22.22",
    "/usr/local/bin/npm",
    "/usr/local/bin/npx",
    "/usr/local/bin/corepack",
    "/usr/local/bin/yarn",
    "/usr/local/bin/yarnpkg",
)


def read(root: Path, relative: str, errors: list[str]) -> str:
    path = root / relative
    if not path.is_file():
        errors.append(f"MISSING_FILE:{relative}")
        return ""
    return path.read_text(encoding="utf-8")


def dockerfile_instructions(dockerfile: str) -> list[tuple[str, str]]:
    """Return logical Dockerfile instructions with continuations collapsed."""
    instructions: list[tuple[str, str]] = []
    logical = ""
    for raw_line in dockerfile.splitlines():
        line = raw_line.strip()
        if not logical and (not line or line.startswith("#")):
            continue
        logical = f"{logical} {line}".strip()
        if line.endswith("\\"):
            logical = logical[:-1].rstrip()
            continue
        match = re.match(r"^([A-Za-z]+)\s+(.*)$", logical, re.DOTALL)
        if match:
            instructions.append((match.group(1).upper(), match.group(2).strip()))
        logical = ""
    return instructions


def docker_stage(dockerfile: str, alias: str) -> str:
    stage = re.search(
        rf"(?mi)^FROM\s+[^\n]+\s+AS\s+{re.escape(alias)}\s*(?:#.*)?$",
        dockerfile,
    )
    if not stage:
        return ""
    next_stage = re.search(r"(?mi)^FROM\s+", dockerfile[stage.end() :])
    end = stage.end() + next_stage.start() if next_stage else len(dockerfile)
    return dockerfile[stage.start() : end]


def shell_commands(body: str) -> list[list[str]]:
    """Tokenize direct shell commands separated by common shell operators."""
    commands: list[list[str]] = []
    for segment in re.split(r"\s*(?:&&|\|\||;|\|)\s*", body):
        try:
            tokens = shlex.split(segment, comments=True, posix=True)
        except ValueError:
            continue
        while tokens and tokens[0].startswith("--mount="):
            tokens.pop(0)
        while tokens and tokens[0] in {"!", "then", "do", "command", "exec"}:
            tokens.pop(0)
        if tokens and tokens[0] == "env":
            tokens.pop(0)
            while tokens and re.match(r"^[A-Za-z_][A-Za-z0-9_]*=", tokens[0]):
                tokens.pop(0)
        while tokens and re.match(r"^[A-Za-z_][A-Za-z0-9_]*=", tokens[0]):
            tokens.pop(0)
        if tokens:
            commands.append(tokens)
    return commands


def docker_commands(dockerfile: str) -> list[list[str]]:
    commands: list[list[str]] = []
    for instruction, body in dockerfile_instructions(dockerfile):
        if instruction in {"RUN", "CMD", "ENTRYPOINT", "HEALTHCHECK"}:
            command_body = body
            if instruction == "HEALTHCHECK" and body.upper().startswith("CMD "):
                command_body = body[4:].strip()
            if command_body.startswith("["):
                try:
                    command = json.loads(command_body)
                except json.JSONDecodeError:
                    command = []
                if isinstance(command, list) and all(
                    isinstance(token, str) for token in command
                ):
                    commands.append(command)
                    continue
            commands.extend(shell_commands(command_body))
    return commands


def command_name(tokens: list[str]) -> str:
    return Path(tokens[0]).name if tokens else ""


def is_global_npm_install(tokens: list[str]) -> bool:
    if command_name(tokens) != "npm":
        return False
    arguments = tokens[1:]
    return any(
        (index + 1 < len(arguments) and arguments[index + 1] in GLOBAL_NPM_SUBCOMMANDS)
        or (index > 0 and arguments[index - 1] in GLOBAL_NPM_SUBCOMMANDS)
        for index, value in enumerate(arguments)
        if value in GLOBAL_NPM_FLAGS
    )


def has_non_global_npm_install(dockerfile: str) -> bool:
    return any(
        command_name(tokens) == "npm"
        and "install" in tokens[1:]
        and not is_global_npm_install(tokens)
        for tokens in docker_commands(dockerfile)
    )


def whatsapp_runtime_is_clean(dockerfile: str) -> bool:
    runtime = docker_stage(dockerfile, "runtime")
    if not runtime:
        return False
    commands = docker_commands(runtime)
    package_manager_invocation = any(
        command_name(tokens) in WHATSAPP_PACKAGE_MANAGERS
        and not is_global_npm_install(tokens)
        for tokens in commands
    )
    removed_paths = {
        token
        for tokens in commands
        if command_name(tokens) == "rm"
        and any(
            option.startswith("-")
            and ("r" in option[1:].lower() or option == "--recursive")
            for option in tokens[1:]
        )
        for token in tokens[1:]
        if not token.startswith("-")
    }
    return (
        not package_manager_invocation
        and set(WHATSAPP_RUNTIME_PURGE_PATHS).issubset(removed_paths)
    )


def validate(root: Path = ROOT) -> list[str]:
    errors: list[str] = []
    dockerfiles = {
        name: read(root, f"{name}/Dockerfile", errors) for name in COMPONENTS
    }
    entries = {
        name: read(root, f"{name}/entrypoint.sh", errors)
        for name in ("frontend", "website_front")
    }
    entries["whatsapp_service"] = read(
        root, "whatsapp_service/index.js", errors
    ) + read(root, "whatsapp_service/app.js", errors)

    for name, dockerfile in dockerfiles.items():
        from_lines = re.findall(r"(?mi)^FROM\s+(\S+)", dockerfile)
        if not from_lines or any("@sha256:" not in item for item in from_lines):
            errors.append(f"BASE_DIGEST_REQUIRED:{name}")
        if not from_lines or any(":latest" in item for item in from_lines):
            errors.append(f"LATEST_FORBIDDEN:{name}")
        if NODE_BASE not in from_lines:
            errors.append(f"NODE24_BASE_REQUIRED:{name}")
        if "npm ci" not in dockerfile or has_non_global_npm_install(dockerfile):
            errors.append(f"NPM_CI_REQUIRED:{name}")
        if any(is_global_npm_install(tokens) for tokens in docker_commands(dockerfile)) or "quasar build" in dockerfile:
            errors.append(f"GLOBAL_CLI_FORBIDDEN:{name}")
        args = re.findall(r"(?mi)^ARG\s+([A-Za-z_][A-Za-z0-9_]*)", dockerfile)
        if sorted(args) != ["IMAGE_VERSION", "VCS_REF"]:
            errors.append(f"ARG_CONTRACT_INVALID:{name}")
        for label in (
            "org.opencontainers.image.source",
            "org.opencontainers.image.revision",
            "org.opencontainers.image.version",
        ):
            if label not in dockerfile:
                errors.append(f"OCI_LABEL_REQUIRED:{name}")
        if "STOPSIGNAL SIGTERM" not in dockerfile:
            errors.append(f"STOP_SIGNAL_REQUIRED:{name}")
        if "HEALTHCHECK --interval=" not in dockerfile or "127.0.0.1" not in dockerfile:
            errors.append(f"HEALTHCHECK_INVALID:{name}")
        if re.search(r"(?i)(docker\.sock|VOLUME\s|PASSWORD|SECRET|TOKEN|PRIVATE_KEY)", dockerfile):
            errors.append(f"FORBIDDEN_RUNTIME_CONTENT:{name}")
        ignore = read(root, f"{name}/.dockerignore", errors)
        for token in IGNORE_REQUIRED:
            if token not in ignore.splitlines():
                errors.append(f"DOCKERIGNORE_INCOMPLETE:{name}")
                break

    for name in ("frontend", "website_front"):
        dockerfile = dockerfiles[name]
        if NGINX_BASE not in re.findall(r"(?mi)^FROM\s+(\S+)", dockerfile):
            errors.append(f"NGINX_BASE_REQUIRED:{name}")
        runtime = dockerfile.split(f"FROM {NGINX_BASE}", 1)[-1]
        if re.search(r"(?i)\b(node|npm|npx)\b", runtime):
            errors.append(f"NODE_IN_SPA_RUNTIME:{name}")
        if "EXPOSE 80" not in runtime or "/healthz" not in runtime:
            errors.append(f"SPA_PORT_HEALTH_INVALID:{name}")
        if 'CMD ["nginx", "-g", "daemon off;"]' not in runtime:
            errors.append(f"SPA_COMMAND_INVALID:{name}")
        if re.search(r"(?i)\b(apk|apt-get|npm|yarn)\s+(add|install|ci)", entries[name]):
            errors.append(f"STARTUP_INSTALL_FORBIDDEN:{name}")
        if 'exec "$@"' not in entries[name]:
            errors.append(f"ENTRYPOINT_EXEC_REQUIRED:{name}")
        nginx = read(root, f"{name}/nginx.conf", errors)
        if "location = /healthz" not in nginx or "try_files $uri $uri/ /index.html" not in nginx:
            errors.append(f"NGINX_CONTRACT_INVALID:{name}")

    frontend_entry = entries["frontend"]
    if "VITE_BASE_API_URL" not in frontend_entry or "jq -cn" not in frontend_entry:
        errors.append("FRONTEND_RUNTIME_CONFIG_INVALID")

    website_active = entries["website_front"]
    for path in (root / "website_front/src").rglob("*"):
        if path.is_file() and path.suffix in {".ts", ".tsx", ".js", ".jsx"}:
            website_active += path.read_text(encoding="utf-8")
    if "VITE_WEBSITE_API_URL" not in website_active or "websiteApiUrl" not in website_active:
        errors.append("WEBSITE_CANONICAL_NAMES_REQUIRED")
    if "VITE_VILLA_API_URL" in website_active or "villaApiUrl" in website_active:
        errors.append("WEBSITE_LEGACY_NAMES_FORBIDDEN")
    if "http://website_back:8085" not in entries["website_front"]:
        errors.append("WEBSITE_INTERNAL_TARGET_INVALID")
    if "http://backend:8085" in website_active:
        errors.append("WEBSITE_INTERNAL_TARGET_INVALID")
    if "jq -cn" not in entries["website_front"] or "jq -Rr @html" not in entries["website_front"]:
        errors.append("WEBSITE_SERIALIZATION_INVALID")

    whatsapp_docker = dockerfiles["whatsapp_service"]
    whatsapp_app = entries["whatsapp_service"]
    if not whatsapp_runtime_is_clean(whatsapp_docker):
        errors.append("PACKAGE_MANAGER_IN_WHATSAPP_RUNTIME")
    if "USER 10001:10001" not in whatsapp_docker:
        errors.append("WHATSAPP_NONROOT_REQUIRED")
    if "SESSION_DIR=/data/session" not in whatsapp_docker or "chmod 0700 /data/session" not in whatsapp_docker:
        errors.append("WHATSAPP_SESSION_CONTRACT_INVALID")
    if "chromium" not in whatsapp_docker or "PUPPETEER_EXECUTABLE_PATH=/usr/bin/chromium-browser" not in whatsapp_docker:
        errors.append("WHATSAPP_CHROMIUM_REQUIRED")
    if "EXPOSE 3001" not in whatsapp_docker or "/health/live" not in whatsapp_docker:
        errors.append("WHATSAPP_PORT_HEALTH_INVALID")
    live_start = whatsapp_app.find("app.get('/health/live'")
    live_end = whatsapp_app.find("\n  app.", live_start + 1)
    live_handler = (
        whatsapp_app[live_start:live_end]
        if live_start >= 0 and live_end > live_start
        else ""
    )
    if not live_handler or not re.search(r"status\s*:\s*['\"]UP['\"]", live_handler):
        errors.append("WHATSAPP_LIVENESS_REQUIRED")
    elif re.search(r"connected|qr|client", live_handler, re.I):
        errors.append("WHATSAPP_LIVENESS_COUPLED")
    if "app.get('/status'" not in whatsapp_app or "hasQr" not in whatsapp_app:
        errors.append("WHATSAPP_STATUS_REQUIRED")
    if "await client.initialize()" in read(root, "whatsapp_service/index.js", errors):
        errors.append("WHATSAPP_BLOCKING_BOOTSTRAP")
    package = read(root, "whatsapp_service/package.json", errors)
    if '"test": "node --test"' not in package:
        errors.append("WHATSAPP_TEST_COMMAND_REQUIRED")

    for name in COMPONENTS:
        package = read(root, f"{name}/package.json", errors)
        if '"node": "^24.0.0"' not in package:
            errors.append(f"NODE_ENGINE_INVALID:{name}")

    return sorted(set(errors))


def main(argv: list[str]) -> int:
    if argv != ["validate"]:
        print("usage: validate_node_images.py validate", file=sys.stderr)
        return 2
    errors = validate()
    if errors:
        for error in errors:
            print(error)
        return 1
    print("node-images-contract:valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
