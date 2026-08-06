from __future__ import annotations

import importlib.util
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
MODULE_PATH = ROOT / "tools/releases/validate_publisher_identity_bridge.py"
SPEC = importlib.util.spec_from_file_location(
    "validate_publisher_identity_bridge", MODULE_PATH
)
validator = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = validator
SPEC.loader.exec_module(validator)


class PublisherIdentityBridgeContractTest(unittest.TestCase):
    def mutant(self) -> Path:
        target = Path(tempfile.mkdtemp(prefix="s16-identity-"))
        self.addCleanup(shutil.rmtree, target, True)
        for directory in ("backend", "release_control", "docs/infrastructure", "tools"):
            source = ROOT / directory
            if source.exists():
                shutil.copytree(
                    source,
                    target / directory,
                    ignore=shutil.ignore_patterns(
                        "target",
                        ".venv",
                        "__pycache__",
                        ".pytest_cache",
                        ".mypy_cache",
                        ".ruff_cache",
                    ),
                )
        return target

    def replace(self, root: Path, relative: str, old: str, new: str) -> None:
        path = root / relative
        text = path.read_text()
        self.assertIn(old, text)
        path.write_text(text.replace(old, new, 1))

    def test_real_bridge_is_valid(self) -> None:
        validator.validate(ROOT)

    def test_exact_route_mutant_fails(self) -> None:
        root = self.mutant()
        self.replace(
            root,
            "backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/"
            "ReleaseControlIdentityController.java",
            '@PostMapping("/token")',
            '@PostMapping("/token-v2")',
        )
        with self.assertRaisesRegex(ValueError, "exact-routes"):
            validator.validate(root)

    def test_security_matcher_mutant_fails(self) -> None:
        root = self.mutant()
        self.replace(
            root,
            "backend/src/main/java/com/baronesa/emporio/config/SecurityConfig.java",
            ').hasRole("SYSTEM")',
            ").authenticated()",
        )
        with self.assertRaisesRegex(ValueError, "security-matchers"):
            validator.validate(root)

    def test_method_authorization_mutant_fails(self) -> None:
        root = self.mutant()
        self.replace(
            root,
            "backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/"
            "ReleaseControlIdentityController.java",
            "@PreAuthorize(\"hasRole('SYSTEM')\")",
            "@PreAuthorize(\"isAuthenticated()\")",
        )
        with self.assertRaisesRegex(ValueError, "method-authorization"):
            validator.validate(root)

    def test_fixed_authority_mutants_fail(self) -> None:
        mutations = (
            ('AUDIENCE = "emporio-release-control"', 'AUDIENCE = "other"'),
            ('SCOPE = "release:read release:publish"', 'SCOPE = "release:read"'),
            ("TTL_SECONDS = 300", "TTL_SECONDS = 301"),
            ("Jwts.SIG.RS256", "Jwts.SIG.RS512"),
        )
        for old, new in mutations:
            with self.subTest(old=old):
                root = self.mutant()
                self.replace(
                    root,
                    "backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/"
                    "ReleaseControlIdentityService.java",
                    old,
                    new,
                )
                with self.assertRaisesRegex(ValueError, "fixed-token-authority"):
                    validator.validate(root)

    def test_opt_in_mutant_fails(self) -> None:
        root = self.mutant()
        self.replace(
            root,
            "backend/src/main/resources/application.properties",
            "${RELEASE_CONTROL_IDENTITY_ENABLED:false}",
            "${RELEASE_CONTROL_IDENTITY_ENABLED:true}",
        )
        with self.assertRaisesRegex(ValueError, "opt-in-default"):
            validator.validate(root)

    def test_dev_opt_in_mutant_fails(self) -> None:
        # Desligar a ponte no perfil dev volta a exigir variavel de ambiente a
        # cada inicializacao, que foi exatamente o que motivou a excecao.
        root = self.mutant()
        self.replace(
            root,
            "backend/src/main/resources/application-dev.properties",
            "${RELEASE_CONTROL_IDENTITY_ENABLED:true}",
            "${RELEASE_CONTROL_IDENTITY_ENABLED:false}",
        )
        with self.assertRaisesRegex(ValueError, "dev-opt-in-default"):
            validator.validate(root)

    def test_key_generation_outside_dev_fails(self) -> None:
        root = self.mutant()
        path = root / "backend/src/main/resources/application-prod.properties"
        path.write_text(
            path.read_text()
            + "app.release-control.identity.generate-key-if-absent=true\n"
        )
        with self.assertRaisesRegex(ValueError, "key-generation-dev-only"):
            validator.validate(root)

    def test_key_generation_contract_mutants_fail(self) -> None:
        for old, new in (
            (
                '@Value("${app.release-control.identity.generate-key-if-absent:false}")',
                '@Value("${app.release-control.identity.generate-key-if-absent:true}")',
            ),
            ("if (Files.exists(path)) {", "if (false) {"),
        ):
            with self.subTest(old=old):
                root = self.mutant()
                self.replace(
                    root,
                    "backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/"
                    "ReleaseControlIdentityConfiguration.java",
                    old,
                    new,
                )
                with self.assertRaisesRegex(ValueError, "key-generation-contract"):
                    validator.validate(root)

    def test_key_contract_mutants_fail(self) -> None:
        for old, new in (
            ("MIN_RSA_BITS = 3072", "MIN_RSA_BITS = 2048"),
            ("new PKCS8EncodedKeySpec(encoded)", "null"),
            ("Files.isSymbolicLink(path)", "false"),
        ):
            with self.subTest(old=old):
                root = self.mutant()
                self.replace(
                    root,
                    "backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/"
                    "ReleaseControlIdentityConfiguration.java",
                    old,
                    new,
                )
                with self.assertRaisesRegex(ValueError, "key-contract"):
                    validator.validate(root)

    def test_claim_contract_mutant_fails(self) -> None:
        root = self.mutant()
        self.replace(
            root,
            "backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/"
            "ReleaseControlIdentityService.java",
            ".notBefore(Date.from(issuedAt))",
            ".notBefore(Date.from(issuedAt.minusSeconds(1)))",
        )
        with self.assertRaisesRegex(ValueError, "claim-contract"):
            validator.validate(root)

    def test_exchange_contract_mutant_fails(self) -> None:
        root = self.mutant()
        self.replace(
            root,
            "backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/"
            "ReleaseControlIdentityController.java",
            "request.getQueryString() != null",
            "false",
        )
        with self.assertRaisesRegex(ValueError, "exchange-contract"):
            validator.validate(root)

    def test_jwks_contract_mutant_fails(self) -> None:
        root = self.mutant()
        self.replace(
            root,
            "backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/"
            "ReleaseControlIdentityController.java",
            '"RS256"',
            '"RS512"',
        )
        with self.assertRaisesRegex(ValueError, "jwks-contract"):
            validator.validate(root)

    def test_profile_contract_mutants_fail(self) -> None:
        for old, new in (
            ('Literal["runtime", "development", "test"]', 'Literal["runtime", "test"]'),
            (
                'self.jwt_jwks_url != self.jwt_issuer + "/jwks"',
                "False",
            ),
            ('self.db_sslmode != "require"', "False"),
        ):
            with self.subTest(old=old):
                root = self.mutant()
                self.replace(
                    root,
                    "release_control/src/emporio_release_control/config.py",
                    old,
                    new,
                )
                with self.assertRaisesRegex(
                    ValueError, "publisher-profiles|publisher-profile-contract"
                ):
                    validator.validate(root)

    def test_maven_dependency_mutant_fails(self) -> None:
        root = self.mutant()
        self.replace(root, "backend/pom.xml", "<jjwt.version>0.12.6", "<jjwt.version>0.12.5")
        with self.assertRaisesRegex(ValueError, "maven-dependencies"):
            validator.validate(root)

    def test_documentation_mutant_fails(self) -> None:
        root = self.mutant()
        self.replace(
            root,
            "docs/infrastructure/deployment/release-control/IDENTIDADE_PUBLISHER.md",
            "UI não foi implementada",
            "UI pronta",
        )
        with self.assertRaisesRegex(ValueError, "canonical-documentation"):
            validator.validate(root)


if __name__ == "__main__":
    unittest.main()
