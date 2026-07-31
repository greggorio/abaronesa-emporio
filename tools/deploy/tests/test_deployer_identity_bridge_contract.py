"""Testes causais da ponte de identidade do deployer (S23).

Suíte mutante que prova falha causal de cada verificação do validador
validate_deployer_identity_bridge.py, seguindo o padrão já aceito em
test_deployer_runtime_contract.py (S22).
"""

from __future__ import annotations

import base64
import importlib.util
import shutil
import sys
import tempfile
import types
import unittest
from pathlib import Path

import httpx
import jwt
from cryptography.hazmat.primitives.asymmetric import rsa

ROOT = Path(__file__).resolve().parents[3]
MODULE_PATH = ROOT / "tools/deploy/validate_deployer_identity_bridge.py"
SPEC = importlib.util.spec_from_file_location("validate_deployer_identity_bridge", MODULE_PATH)
validator = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = validator
SPEC.loader.exec_module(validator)


def load_jwt_verifier() -> tuple[type, type]:
    """Carrega JwtVerifier e RuntimeFailure sem depender de PYTHONPATH externo."""
    package_name = "_s23_release_control"
    package = types.ModuleType(package_name)
    package.__path__ = [str(ROOT / "release_control/src/emporio_release_control")]
    sys.modules[package_name] = package

    errors_spec = importlib.util.spec_from_file_location(
        f"{package_name}.errors",
        ROOT / "release_control/src/emporio_release_control/errors.py",
    )
    assert errors_spec and errors_spec.loader
    errors = importlib.util.module_from_spec(errors_spec)
    sys.modules[errors_spec.name] = errors
    errors_spec.loader.exec_module(errors)

    security_spec = importlib.util.spec_from_file_location(
        f"{package_name}.security",
        ROOT / "release_control/src/emporio_release_control/security.py",
    )
    assert security_spec and security_spec.loader
    security = importlib.util.module_from_spec(security_spec)
    sys.modules[security_spec.name] = security
    security_spec.loader.exec_module(security)
    return security.JwtVerifier, errors.RuntimeFailure


JwtVerifier, RuntimeFailure = load_jwt_verifier()


class DeployerIdentityBridgeContractTest(unittest.TestCase):
    def mutant(self) -> Path:
        """Cria cópia temporária da árvore relevante."""
        target = Path(tempfile.mkdtemp(prefix="s23-identity-"))
        self.addCleanup(shutil.rmtree, target, True)
        for directory in ("backend", "release_control", "docs"):
            source = ROOT / directory
            if source.exists():
                shutil.copytree(source, target / directory)
        return target

    def replace(self, root: Path, relative: str, old: str, new: str) -> None:
        """Substitui texto em arquivo dentro da mutante."""
        path = root / relative
        text = path.read_text(encoding="utf-8")
        self.assertIn(old, text, f"padrão '{old}' não encontrado em {relative}")
        path.write_text(text.replace(old, new, 1), encoding="utf-8")

    def assert_mutant_invalid(self, root: Path) -> None:
        """Verifica que validador falha com exit code 1."""
        with self.assertRaises(SystemExit) as ctx:
            validator.main(root)
        self.assertEqual(ctx.exception.code, 1,
                        f"Esperava exit code 1, obteve {ctx.exception.code}")

    def test_real_identity_bridge_is_valid(self) -> None:
        """Validador aceita implementação real."""
        result = validator.main(ROOT)
        self.assertEqual(result, 0,
                        f"Implementação real deve ser válida, mas retornou {result}")

    def test_missing_deployer_controller_fails(self) -> None:
        """Falta controlador = falha."""
        root = self.mutant()
        (
            root
            / "backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/deployer/"
            "DeployerReleaseControlIdentityController.java"
        ).unlink()
        self.assert_mutant_invalid(root)

    def test_deployer_controller_missing_base_path_fails(self) -> None:
        """Rota sem path base correto = falha."""
        root = self.mutant()
        self.replace(
            root,
            "backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/deployer/"
            "DeployerReleaseControlIdentityController.java",
            "@RequestMapping(\"/api/release-control/identity/deployer\")",
            "@RequestMapping(\"/api/release-control/identity/wrong\")",
        )
        self.assert_mutant_invalid(root)

    def test_deployer_controller_missing_jwks_route_fails(self) -> None:
        """Falta rota JWKS = falha."""
        root = self.mutant()
        self.replace(
            root,
            "backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/deployer/"
            "DeployerReleaseControlIdentityController.java",
            "@GetMapping(\"/jwks\")",
            "@GetMapping(\"/missing-jwks\")",
        )
        self.assert_mutant_invalid(root)

    def test_deployer_controller_missing_token_route_fails(self) -> None:
        """Falta rota token = falha."""
        root = self.mutant()
        self.replace(
            root,
            "backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/deployer/"
            "DeployerReleaseControlIdentityController.java",
            "@PostMapping(\"/token\")",
            "@PostMapping(\"/missing-token\")",
        )
        self.assert_mutant_invalid(root)

    def test_deployer_token_missing_preauthorize_fails(self) -> None:
        """Token sem @PreAuthorize = falha."""
        root = self.mutant()
        self.replace(
            root,
            "backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/deployer/"
            "DeployerReleaseControlIdentityController.java",
            "@PreAuthorize(\"hasRole('SYSTEM')\")",
            "@PreAuthorize(\"hasAnyRole('ADMIN')\")",
        )
        self.assert_mutant_invalid(root)

    def test_deployer_service_wrong_audience_fails(self) -> None:
        """Audience incorreta no service = falha."""
        root = self.mutant()
        self.replace(
            root,
            "backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/deployer/"
            "DeployerReleaseControlIdentityService.java",
            'AUDIENCE = "emporio-release-control-deployer"',
            'AUDIENCE = "emporio-release-control"',
        )
        self.assert_mutant_invalid(root)

    def test_deployer_service_wrong_scope_fails(self) -> None:
        """Scope incorreto no service = falha."""
        root = self.mutant()
        self.replace(
            root,
            "backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/deployer/"
            "DeployerReleaseControlIdentityService.java",
            'SCOPE = "deployment:read deployment:execute"',
            'SCOPE = "release:read release:publish"',
        )
        self.assert_mutant_invalid(root)

    def test_security_config_missing_deployer_jwks_matcher_fails(self) -> None:
        """SecurityConfig sem matcher JWKS deployer = falha."""
        root = self.mutant()
        self.replace(
            root,
            "backend/src/main/java/com/baronesa/emporio/config/SecurityConfig.java",
            '"/api/release-control/identity/deployer/jwks"',
            '"/api/release-control/identity/deployer/wrong"',
        )
        self.assert_mutant_invalid(root)

    def test_security_config_missing_deployer_token_matcher_fails(self) -> None:
        """SecurityConfig sem matcher token deployer = falha."""
        root = self.mutant()
        self.replace(
            root,
            "backend/src/main/java/com/baronesa/emporio/config/SecurityConfig.java",
            '"/api/release-control/identity/deployer/token"',
            '"/api/release-control/identity/deployer/wrong"',
        )
        self.assert_mutant_invalid(root)

    def test_release_control_config_missing_deployer_audience_fails(self) -> None:
        """config.py sem audience deployer no Literal = falha."""
        root = self.mutant()
        self.replace(
            root,
            "release_control/src/emporio_release_control/config.py",
            'jwt_audience: Literal["emporio-release-control", "emporio-release-control-deployer"]',
            'jwt_audience: Literal["emporio-release-control"]',
        )
        self.assert_mutant_invalid(root)

    def test_missing_identidade_deployer_docs_fails(self) -> None:
        """Falta IDENTIDADE_DEPLOYER.md = falha."""
        root = self.mutant()
        (root / "docs/infrastructure/deployment/release-control/IDENTIDADE_DEPLOYER.md").unlink()
        self.assert_mutant_invalid(root)

    def test_new_arbitrary_maven_dependency_fails(self) -> None:
        """Dependência nova não relacionada a JWT também é rejeitada."""
        root = self.mutant()
        self.replace(
            root,
            "backend/pom.xml",
            "    <dependencies>\n",
            "    <dependencies>\n"
            "        <dependency>\n"
            "            <groupId>org.tinylog</groupId>\n"
            "            <artifactId>tinylog-impl</artifactId>\n"
            "        </dependency>\n",
        )
        self.assert_mutant_invalid(root)

    def test_publisher_and_deployer_tokens_fail_cross_audience(self) -> None:
        """JwtVerifier rejeita tokens válidos emitidos para a outra audiência."""
        private = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        public = private.public_key()
        kid = "s23-cross-audience"

        def encode(value: int) -> str:
            raw = value.to_bytes((value.bit_length() + 7) // 8, "big")
            return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()

        numbers = public.public_numbers()
        jwks = {
            "keys": [
                {
                    "kty": "RSA",
                    "kid": kid,
                    "use": "sig",
                    "alg": "RS256",
                    "n": encode(numbers.n),
                    "e": encode(numbers.e),
                }
            ]
        }
        client = httpx.Client(
            transport=httpx.MockTransport(lambda _request: httpx.Response(200, json=jwks))
        )
        publisher = JwtVerifier(
            "https://issuer.invalid",
            "emporio-release-control",
            "https://issuer.invalid/jwks",
            client,
        )
        deployer = JwtVerifier(
            "https://issuer.invalid",
            "emporio-release-control-deployer",
            "https://issuer.invalid/jwks",
            client,
        )

        def token(audience: str) -> str:
            return jwt.encode(
                {
                    "iss": "https://issuer.invalid",
                    "aud": audience,
                    "sub": "erp-user:23",
                    "scope": (
                        "release:read release:publish"
                        if audience == "emporio-release-control"
                        else "deployment:read deployment:execute"
                    ),
                    "exp": 4_102_444_800,
                },
                private,
                algorithm="RS256",
                headers={"kid": kid},
            )

        publisher_token = token("emporio-release-control")
        deployer_token = token("emporio-release-control-deployer")
        try:
            self.assertEqual(publisher.verify(publisher_token).sub, "erp-user:23")
            self.assertEqual(deployer.verify(deployer_token).sub, "erp-user:23")
            with self.assertRaises(RuntimeFailure):
                deployer.verify(publisher_token)
            with self.assertRaises(RuntimeFailure):
                publisher.verify(deployer_token)
        finally:
            client.close()


if __name__ == "__main__":
    unittest.main()
