#!/usr/bin/env python3
"""Fail-closed structural validation for the S16 identity bridge."""

from __future__ import annotations

import ast
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
IDENTITY = (
    ROOT
    / "backend/src/main/java/com/baronesa/emporio/releasecontrol/identity"
)
SECURITY = (
    ROOT
    / "backend/src/main/java/com/baronesa/emporio/config/SecurityConfig.java"
)
CONFIG = ROOT / "release_control/src/emporio_release_control/config.py"
DOCUMENT = (
    ROOT
    / "docs/infrastructure/deployment/release-control/IDENTIDADE_PUBLISHER.md"
)

REQUIRED = {
    "backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/"
    "ReleaseControlIdentityConfiguration.java",
    "backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/"
    "ReleaseControlIdentityController.java",
    "backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/"
    "ReleaseControlIdentityKeyMaterial.java",
    "backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/"
    "ReleaseControlIdentityService.java",
    "backend/src/test/java/com/baronesa/emporio/releasecontrol/identity/"
    "ReleaseControlIdentityContractTest.java",
    "backend/src/test/java/com/baronesa/emporio/releasecontrol/identity/"
    "ReleaseControlIdentityHttpSecurityTest.java",
    "release_control/src/emporio_release_control/config.py",
    "release_control/tests/test_config_security.py",
    "tools/releases/validate_publisher_identity_bridge.py",
    "tools/releases/tests/test_publisher_identity_bridge_contract.py",
    "docs/infrastructure/deployment/release-control/IDENTIDADE_PUBLISHER.md",
}


class ValidationError(ValueError):
    pass


def require(condition: bool, code: str) -> None:
    if not condition:
        raise ValidationError(code)


def validate(root: Path = ROOT) -> None:
    require(all((root / item).is_file() for item in REQUIRED), "required-file")
    identity_dir = root / IDENTITY.relative_to(ROOT)
    identity_files = sorted(identity_dir.glob("*.java"))
    identity = "\n".join(path.read_text(encoding="utf-8") for path in identity_files)
    controller = (identity_dir / "ReleaseControlIdentityController.java").read_text()
    service = (identity_dir / "ReleaseControlIdentityService.java").read_text()
    configuration = (
        identity_dir / "ReleaseControlIdentityConfiguration.java"
    ).read_text()
    security = (root / SECURITY.relative_to(ROOT)).read_text()

    require(
        controller.count('@GetMapping("/jwks")') == 1
        and controller.count('@PostMapping("/token")') == 1
        and controller.count('@RequestMapping("/api/release-control/identity")') == 1
        and identity.count("@GetMapping(") == 1
        and identity.count("@PostMapping(") == 1,
        "exact-routes",
    )
    public_matcher = (
        'HttpMethod.GET,\n'
        '                                "/api/release-control/identity/jwks"'
    )
    system_matcher = (
        'HttpMethod.POST,\n'
        '                                "/api/release-control/identity/token"'
    )
    # The authority must be bound to this exact matcher. A global substring test
    # survives when another matcher — the S23 deployer token — still carries
    # SYSTEM, so require the role immediately after the publisher token matcher.
    system_authority = system_matcher + '\n                        ).hasRole("SYSTEM")'
    require(
        public_matcher in security
        and system_matcher in security
        and security.index(public_matcher) < security.index("anyRequest()")
        and security.index(system_matcher) < security.index("anyRequest()")
        and system_authority in security,
        "security-matchers",
    )
    require(
        '@PreAuthorize("hasRole(\'SYSTEM\')")' in controller,
        "method-authorization",
    )
    require(
        'AUDIENCE = "emporio-release-control"' in service
        and 'SCOPE = "release:read release:publish"' in service
        and "TTL_SECONDS = 300" in service
        and "Jwts.SIG.RS256" in service
        and ".signWith(keyMaterial.privateKey(), Jwts.SIG.RS256)" in service,
        "fixed-token-authority",
    )
    require(
        identity.count(
            '@ConditionalOnProperty(\n'
            '        name = "app.release-control.identity.enabled",\n'
            '        havingValue = "true"'
        )
        == 3,
        "opt-in-components",
    )
    for properties in (
        "backend/src/main/resources/application.properties",
        "backend/src/main/resources/application-dev.properties",
        "backend/src/main/resources/application-prod.properties",
        "backend/src/test/resources/application-test.properties",
    ):
        text = (root / properties).read_text()
        require(
            "app.release-control.identity.enabled="
            "${RELEASE_CONTROL_IDENTITY_ENABLED:false}" in text,
            "opt-in-default",
        )
    require(
        'PEM_BEGIN = "-----BEGIN PRIVATE KEY-----"' in configuration
        and "new PKCS8EncodedKeySpec(encoded)" in configuration
        and "RSAPrivateCrtKey" in configuration
        and "MIN_RSA_BITS = 3072" in configuration
        and "Files.isSymbolicLink(path)" in configuration
        and "MAX_PRIVATE_KEY_BYTES = 16 * 1024" in configuration,
        "key-contract",
    )
    require(
        '.issuer(keyMaterial.issuer())' in service
        and ".add(AUDIENCE)" in service
        and '.subject("erp-user:" + userId)' in service
        and '.claim("scope", SCOPE)' in service
        and ".notBefore(Date.from(issuedAt))" in service
        and ".expiration(Date.from(issuedAt.plusSeconds(TTL_SECONDS)))" in service
        and ".id(UUID.randomUUID().toString())" in service,
        "claim-contract",
    )
    require(
        'new TokenResponse(\n'
        '                service.issue(principal.getId()),\n'
        '                "Bearer",\n'
        '                ReleaseControlIdentityService.TTL_SECONDS,\n'
        '                ReleaseControlIdentityService.SCOPE' in controller
        and "request.getQueryString() != null" in controller
        and "request.getInputStream().readNBytes(1).length != 0" in controller
        and "principal.getId() < 1" in controller,
        "exchange-contract",
    )
    require(
        "new JwkKey(\n"
        '                "RSA",\n'
        '                "sig",\n'
        '                "RS256"' in controller
        and "CacheControl.noStore()" in controller
        and "privateKey" not in controller.partition("public record JwkKey(")[2],
        "jwks-contract",
    )
    require(
        all(
            marker not in identity
            for marker in (
                "Logger",
                "log.",
                "System.out",
                "HS256",
                "HS512",
                "deployment:",
                "refresh",
            )
        ),
        "identity-isolation",
    )

    config_path = root / CONFIG.relative_to(ROOT)
    config = config_path.read_text()
    tree = ast.parse(config)
    settings = next(
        node
        for node in tree.body
        if isinstance(node, ast.ClassDef) and node.name == "Settings"
    )
    profile = next(
        node
        for node in settings.body
        if isinstance(node, ast.AnnAssign)
        and isinstance(node.target, ast.Name)
        and node.target.id == "profile"
    )
    require(
        isinstance(profile.annotation, ast.Subscript)
        and isinstance(profile.annotation.slice, ast.Tuple)
        and {item.value for item in profile.annotation.slice.elts if isinstance(item, ast.Constant)}
        == {"runtime", "development", "test"},
        "publisher-profiles",
    )
    require(
        'self.profile == "development"' in config
        and 'self.db_host not in {"127.0.0.1", "localhost"}' in config
        and "self._validate_local_identity_urls()" in config
        and 'self.jwt_jwks_url != self.jwt_issuer + "/jwks"' in config
        and 'self.profile == "runtime"' in config
        and 'self.db_sslmode != "require"' in config
        and 'self.github_api_base != GITHUB_API' in config
        and 'expected_scheme = "https" if self.profile == "runtime" else "http"'
        in config,
        "publisher-profile-contract",
    )

    pom = ET.parse(root / "backend/pom.xml").getroot()
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    jjwt = [
        element.findtext("m:artifactId", namespaces=namespace)
        for element in pom.findall("m:dependencies/m:dependency", namespace)
        if element.findtext("m:groupId", namespaces=namespace) == "io.jsonwebtoken"
    ]
    require(
        jjwt == ["jjwt-api", "jjwt-impl", "jjwt-jackson"]
        and pom.findtext("m:properties/m:jjwt.version", namespaces=namespace)
        == "0.12.6",
        "maven-dependencies",
    )

    document = (root / DOCUMENT.relative_to(ROOT)).read_text()
    require(
        all(
            marker in document
            for marker in (
                "HS512",
                "RS256",
                "ROLE_SYSTEM",
                "release:read release:publish",
                "300",
                "runtime",
                "development",
                "test",
                "openssl genpkey -algorithm RSA",
                "rsa_keygen_bits:3072",
                "chmod 600",
                "fora do repositório",
                "UI não foi implementada",
            )
        ),
        "canonical-documentation",
    )


def main() -> int:
    try:
        validate()
    except (OSError, ValueError, ET.ParseError, SyntaxError) as exc:
        code = str(exc) if isinstance(exc, ValidationError) else "validation-error"
        print(f"publisher-identity-bridge:invalid:{code}", file=sys.stderr)
        return 2
    print("publisher-identity-bridge:valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
