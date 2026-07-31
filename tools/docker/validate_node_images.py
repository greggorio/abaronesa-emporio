#!/usr/bin/env python3
"""Fail-closed static contract validator for the S09 Node images."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COMPONENTS = ("frontend", "website_front", "whatsapp_service")
NODE_BASE = (
    "node:24.13.0-alpine3.23@"
    "sha256:cd6fb7efa6490f039f3471a189214d5f548c11df1ff9e5b181aa49e22c14383e"
)
NGINX_BASE = (
    "nginx:1.29.5-alpine3.23@"
    "sha256:1eff5a5f3fcf8431a0abb7eddf5471fec24e5e1905a2581aeacdb07a4479b92b"
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


def read(root: Path, relative: str, errors: list[str]) -> str:
    path = root / relative
    if not path.is_file():
        errors.append(f"MISSING_FILE:{relative}")
        return ""
    return path.read_text(encoding="utf-8")


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
        if "npm ci" not in dockerfile or re.search(r"\bnpm install\b", dockerfile):
            errors.append(f"NPM_CI_REQUIRED:{name}")
        if "npm install -g" in dockerfile or "quasar build" in dockerfile:
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
