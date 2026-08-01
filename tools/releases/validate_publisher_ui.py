#!/usr/bin/env python3
"""Fail-closed structural validator for the S17 publisher development UI."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]

ENV_FILE = Path("frontend/.env")
ENV_EXAMPLE = Path("frontend/.env.example")
CONFIG_FILE = Path("frontend/src/config/releasePublisher.js")
CONFIG_TEST = Path("frontend/src/config/releasePublisher.spec.js")
CLIENT_FILE = Path("frontend/src/services/releasePublisherClient.js")
CLIENT_TEST = Path("frontend/src/services/releasePublisherClient.spec.js")
ATTEMPT_FILE = Path("frontend/src/services/releasePublisherAttempt.js")
ATTEMPT_TEST = Path("frontend/src/services/releasePublisherAttempt.spec.js")
COMPONENT_FILE = Path(
    "frontend/src/components/configuracoes/ReleasePublisherConfig.vue"
)
COMPONENT_TEST = Path(
    "frontend/src/components/configuracoes/ReleasePublisherConfig.spec.js"
)
PANEL_FILE = Path("frontend/src/components/configuracoes/PainelControle.vue")
ROUTER_ROOT = Path("frontend/src/router")
UI_DOC = Path("docs/infrastructure/deployment/release-control/UI_PUBLISHER.md")
RELEASE_CONTROL_README = Path(
    "docs/infrastructure/deployment/release-control/README.md"
)
IDENTITY_DOC = Path(
    "docs/infrastructure/deployment/release-control/IDENTIDADE_PUBLISHER.md"
)
RUNTIME_DOC = Path(
    "docs/infrastructure/deployment/release-control/RUNTIME_PUBLISHER.md"
)
ONBOARDING_DOC = Path("docs/development/ONBOARDING_MINIMO.md")
DEVELOPMENT_README = Path("docs/development/README.md")

REQUIRED_FILES = {
    ENV_EXAMPLE,
    CONFIG_FILE,
    CONFIG_TEST,
    CLIENT_FILE,
    CLIENT_TEST,
    ATTEMPT_FILE,
    ATTEMPT_TEST,
    COMPONENT_FILE,
    COMPONENT_TEST,
    PANEL_FILE,
    UI_DOC,
    RELEASE_CONTROL_README,
    IDENTITY_DOC,
    RUNTIME_DOC,
    ONBOARDING_DOC,
    DEVELOPMENT_README,
}

PUBLISHER_PATHS = {
    "/api/release-control/v1/capabilities",
    "/api/release-publisher/v1/candidates",
    "/api/release-publisher/v1/releases",
    "/api/release-publisher/v1/operations/",
}
PUBLIC_CODES = {
    "BAD_REQUEST",
    "UNAUTHORIZED",
    "FORBIDDEN",
    "NOT_FOUND",
    "IDEMPOTENCY_CONFLICT",
    "VERSION_RESERVATION_CONFLICT",
    "UNPROCESSABLE",
    "RATE_LIMITED",
    "INTERNAL_ERROR",
    "SERVICE_UNAVAILABLE",
}
FORBIDDEN_REQUEST_PROPERTIES = {
    "component",
    "components",
    "dependency",
    "dependencies",
    "image",
    "images",
    "digest",
    "digests",
    "repository",
    "workflow",
}
NONTERMINAL_STATES = {"REQUESTED", "VALIDATING", "PUBLISHING"}
TERMINAL_STATES = {"PUBLISHED", "FAILED"}


class ValidationError(ValueError):
    """Stable structural validation error."""


def require(condition: bool, code: str) -> None:
    if not condition:
        raise ValidationError(code)


def _read(root: Path, relative: Path) -> str:
    return (root / relative).read_text(encoding="utf-8")


def _env_values(text: str) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if separator:
            values[key.strip()] = value.strip()
    return values


def _quoted_property_names(text: str) -> set[str]:
    return set(re.findall(r"""["']([A-Za-z][A-Za-z0-9_]*)["']\s*:""", text))


def _runtime_sources(root: Path) -> dict[Path, str]:
    return {
        CONFIG_FILE: _read(root, CONFIG_FILE),
        CLIENT_FILE: _read(root, CLIENT_FILE),
        ATTEMPT_FILE: _read(root, ATTEMPT_FILE),
        COMPONENT_FILE: _read(root, COMPONENT_FILE),
        PANEL_FILE: _read(root, PANEL_FILE),
    }


def _validate_activation(env: str, config: str) -> None:
    values = _env_values(env)
    require(
        values.get("VITE_RELEASE_CONTROL_MODE") == "publisher",
        "activation-mode",
    )
    require(
        values.get("VITE_RELEASE_PUBLISHER_URL") == "http://127.0.0.1:8090",
        "activation-url",
    )
    markers = (
        "resolveReleasePublisherConfig",
        "env.PROD === true",
        "env.DEV !== true",
        '["disabled", "publisher"]',
        "VITE_RELEASE_CONTROL_MODE",
        "VITE_RELEASE_PUBLISHER_URL",
        'parsed.protocol !== "http:"',
        "LOOPBACK_HOSTS",
        "hasExplicitPort",
        "parsed.username.length > 0",
        "parsed.password.length > 0",
        "parsed.pathname",
        "parsed.search.length > 0",
        "parsed.hash.length > 0",
        "parsed.origin",
    )
    require(all(marker in config for marker in markers), "activation-contract")
    require("RuntimeConfig" not in config, "activation-runtime-config")
    require(
        not re.search(
            r"""(?:const|let|var)\s+\w*(?:url|Url|URL)\w*\s*=\s*["']http://""",
            config,
        ),
        "activation-hardcoded-fallback",
    )


def _validate_panel(panel: str, router: str) -> None:
    for marker in (
        "Gerenciamento de Releases",
        "Publique uma release global a partir de um candidato validado",
        "new_releases",
        "release-publisher",
        "ReleasePublisherConfig",
        "configComponents",
        "configTitles",
        "isRootUser",
    ):
        require(marker in panel, f"panel-marker:{marker}")
    require(
        re.search(
            r'''v-if\s*=\s*"[^"]*(?:isRootUser[^"]*publisher|publisher[^"]*isRootUser)[^"]*"''',
            panel,
        )
        is not None
        or re.search(
            r"""v-if\s*=\s*'[^']*(?:isRootUser[^']*publisher|publisher[^']*isRootUser)[^']*'""",
            panel,
        )
        is not None,
        "panel-visibility",
    )
    require("release-publisher" not in router.lower(), "publisher-router")


def _validate_client(client: str) -> None:
    require("createReleasePublisherClient" in client, "client-factory")
    require("getErpToken" in client, "exchange-token-callback")
    require(
        "/api/release-control/identity/token" in client,
        "exchange-endpoint",
    )
    require(
        all(path in client for path in PUBLISHER_PATHS),
        "publisher-endpoints",
    )
    for marker in (
        "Authorization",
        "Bearer ",
        "Accept",
        "application/json",
        "Content-Type",
        "Idempotency-Key",
        "accessToken",
        "tokenType",
        "expiresIn",
        "scope",
    ):
        require(marker in client, f"client-header-or-exchange:{marker}")
    require(
        all(code in client for code in PUBLIC_CODES),
        "problem-code-map",
    )
    require(
        all(state in client for state in NONTERMINAL_STATES | TERMINAL_STATES),
        "operation-states",
    )
    require("READY" in client and "PASSED" in client and "VALID" in client, "ready-filter")
    require("URLSearchParams" in client, "opaque-cursor")
    require(
        re.search(r"(?:MAX_RELEASE_PAGES|RELEASE_PAGE_LIMIT)\s*=\s*10\b", client)
        is not None,
        "release-page-limit",
    )
    require(
        re.search(
            r"response\.status\s*={2,3}\s*401\s*&&\s*retry401\b",
            client,
        )
        is not None,
        "single-401-retry",
    )
    require(
        re.search(r"(?:retry|retried|allowRetry|canRetry)", client, re.IGNORECASE)
        is not None,
        "bounded-retry",
    )
    require(
        re.search(r"(?:exchangePromise|exchangeInFlight|tokenPromise)", client)
        is not None,
        "exchange-single-flight",
    )
    request_properties = _quoted_property_names(client)
    require(
        not request_properties.intersection(FORBIDDEN_REQUEST_PROPERTIES),
        "forbidden-request-authority",
    )
    require("localStorage" not in client, "client-local-storage")
    require(
        re.search(r"(?:setItem|removeItem)\s*\(", client) is None,
        "publisher-token-storage",
    )
    require("console." not in client, "client-console")


def _validate_attempt(attempt: str) -> None:
    markers = (
        'PENDING_ATTEMPT_KEY = "emporio.releasePublisher.pending.v1"',
        "MAX_ATTEMPT_BYTES = 16 * 1024",
        "schemaVersion",
        "idempotencyKey",
        "publisher-ui-",
        "operationId",
        "createdAt",
        "crypto.randomUUID",
        "sessionStorage",
        "TextEncoder",
        "setItem",
        "removeItem",
    )
    require(all(marker in attempt for marker in markers), "attempt-contract")
    require("localStorage" not in attempt, "attempt-local-storage")
    require(
        re.search(r"""["']token["']\s*:""", attempt, re.IGNORECASE) is None,
        "attempt-token-field",
    )


def _validate_component(component: str) -> None:
    template = component.partition("<script")[0]
    for marker in (
        "Candidato",
        "Tipo de atualização",
        "Descrição",
        "Changelog",
        "Próxima versão estimada",
        "Retomar envio",
        "Descartar tentativa",
        "A publicação falhou. Consulte a operação nos logs do serviço.",
    ):
        require(marker in component, f"component-marker:{marker}")
    require(
        re.search(
            r"(?:POLL_INTERVAL|POLL_INTERVAL_MS)\s*=\s*3_?000\b|default:\s*3_?000\b",
            component,
        )
        is not None,
        "poll-interval",
    )
    require(
        re.search(
            r"(?:POLL_TIMEOUT|POLL_TIMEOUT_MS)\s*=\s*(?:10\s*\*\s*60\s*\*\s*1000|600_?000)\b|default:\s*(?:10\s*\*\s*60\s*\*\s*1000|600_?000)\b",
            component,
        )
        is not None,
        "poll-timeout",
    )
    require("onBeforeUnmount" in component or "onUnmounted" in component, "poll-cleanup")
    require(
        all(state in component for state in NONTERMINAL_STATES | TERMINAL_STATES),
        "visual-operation-states",
    )
    require(
        re.search(r"\{\{[^}]*errorCode", template) is None
        and re.search(r"""v-(?:text|html)\s*=\s*["'][^"']*errorCode""", template)
        is None,
        "rendered-internal-error-code",
    )
    require(
        re.search(r"\{\{[^}]*\.detail", template) is None
        and re.search(r"""v-(?:text|html)\s*=\s*["'][^"']*\.detail""", template)
        is None,
        "rendered-remote-detail",
    )
    require("localStorage" not in component, "component-local-storage")
    require("console." not in component, "component-console")


def _validate_tests(root: Path) -> None:
    tests_by_group = {
        "tests-config": (
            _read(root, CONFIG_TEST),
            (
            "production",
            "deployer",
            "RuntimeConfig",
            "https://",
            ),
        ),
        "tests-client": (
            _read(root, CLIENT_TEST),
            (
            "401",
            "capabilities",
            "candidate",
            "10",
            "Idempotency-Key",
            "in-flight",
            "never retries POST",
            ),
        ),
        "tests-attempt": (
            _read(root, ATTEMPT_TEST),
            (
            "UUID",
            "16 KiB",
            "operationId",
            "sessionStorage",
            "localStorage",
            ),
        ),
        "tests-component": (
            _read(root, COMPONENT_TEST),
            (
            "cancelar confirmação",
            "persiste a tentativa antes",
            "ação explícita",
            "descart",
            "REQUESTED",
            "PUBLISHED",
            "timer",
            "errorCode",
            ),
        ),
    }
    for code, (tests, markers) in tests_by_group.items():
        require(all(marker.lower() in tests.lower() for marker in markers), code)


def _validate_docs(root: Path) -> None:
    ui = _read(root, UI_DOC)
    for marker in (
        "Painel de Controle",
        "Desenvolvimento",
        "Gerenciamento de Releases",
        "MAJOR",
        "MINOR",
        "PATCH",
        "estimada",
        "efetiva",
        "Retomar envio",
        "sessionStorage",
        "memória",
        "publisher",
        "deployer",
    ):
        require(marker.lower() in ui.lower(), f"ui-doc:{marker}")
    linked_docs = "\n".join(
        _read(root, path)
        for path in (
            RELEASE_CONTROL_README,
            IDENTITY_DOC,
            RUNTIME_DOC,
            ONBOARDING_DOC,
            DEVELOPMENT_README,
        )
    )
    require("UI_PUBLISHER.md" in linked_docs, "ui-doc-link")
    require(
        "VITE_RELEASE_CONTROL_MODE" in linked_docs
        and "VITE_RELEASE_PUBLISHER_URL" in linked_docs,
        "ui-doc-environment",
    )


def validate(root: Path = ROOT) -> None:
    """Validate the complete S17 UI contract without executing frontend code."""

    require(all((root / path).is_file() for path in REQUIRED_FILES), "required-file")
    sources = _runtime_sources(root)
    router = "\n".join(
        path.read_text(encoding="utf-8")
        for path in sorted((root / ROUTER_ROOT).rglob("*"))
        if path.is_file() and path.suffix in {".js", ".ts"}
    )
    # The versioned example is the CI contract; frontend/.env stays local and
    # unversioned, but is still validated whenever the developer has one.
    _validate_activation(_read(root, ENV_EXAMPLE), sources[CONFIG_FILE])
    if (root / ENV_FILE).is_file():
        _validate_activation(_read(root, ENV_FILE), sources[CONFIG_FILE])
    _validate_panel(sources[PANEL_FILE], router)
    _validate_client(sources[CLIENT_FILE])
    _validate_attempt(sources[ATTEMPT_FILE])
    _validate_component(sources[COMPONENT_FILE])
    _validate_tests(root)
    _validate_docs(root)


def main() -> int:
    try:
        validate()
    except (OSError, UnicodeError, ValueError) as exc:
        code = str(exc) if isinstance(exc, ValidationError) else "validation-error"
        print(f"publisher-ui:invalid:{code}", file=sys.stderr)
        return 2
    print("publisher-ui:valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
