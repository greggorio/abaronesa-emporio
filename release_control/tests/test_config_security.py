from __future__ import annotations

import base64
from pathlib import Path

import httpx
import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa
from pydantic import ValidationError

from emporio_release_control.config import Settings
from emporio_release_control.errors import RuntimeFailure
from emporio_release_control.security import CursorCodec, JwtVerifier, RateLimiter


def kwargs(settings: Settings) -> dict[str, object]:
    return settings.model_dump()


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("db_port", 0),
        ("db_sslmode", "require"),
        ("hash_pepper", "short"),
        ("cors_origins", "*"),
        ("cors_origins", "http://console.invalid"),
        ("cors_origins", "https://user:pass@console.invalid"),
        ("cors_origins", "https://console.invalid/path"),
        ("reconcile_interval_seconds", 0),
        ("dispatch_discovery_timeout_seconds", 3601),
        ("http_connect_timeout_seconds", 31),
        ("http_read_timeout_seconds", 61),
        ("github_max_pages", 21),
        ("idempotency_retention_days", 3651),
        ("read_rate_per_minute", 601),
        ("publish_rate_per_minute", 61),
        ("max_json_body_bytes", 65537),
    ],
)
def test_settings_reject_each_invalid_value(
    settings: Settings, field: str, value: object
) -> None:
    values = kwargs(settings)
    values[field] = value
    with pytest.raises(ValidationError):
        Settings.model_validate(values)


def test_runtime_profile_fixes_github_and_ssl(settings: Settings) -> None:
    values = kwargs(settings)
    values.update(
        profile="runtime",
        db_sslmode="require",
        github_api_base="https://api.github.com",
        jwt_issuer="https://issuer.invalid",
        jwt_jwks_url="https://issuer.invalid/jwks",
        cors_origins="https://console.invalid",
    )
    runtime = Settings.model_validate(values)
    assert "sslmode=require" in runtime.database_url
    for field, value in (
        ("github_api_base", "https://evil.invalid"),
        ("db_sslmode", "disable"),
    ):
        mutant = dict(values)
        mutant[field] = value
        with pytest.raises(ValidationError):
            Settings.model_validate(mutant)


def test_settings_accept_exactly_publisher_or_deployer(settings: Settings) -> None:
    values = kwargs(settings)
    assert Settings.model_validate(values).mode == "publisher"
    values["mode"] = "deployer"
    values["jwt_audience"] = "emporio-release-control-deployer"
    assert Settings.model_validate(values).mode == "deployer"
    values["mode"] = "both"
    with pytest.raises(ValidationError):
        Settings.model_validate(values)


def test_mode_and_audience_validation_is_coupled(settings: Settings) -> None:
    values = kwargs(settings)
    # Publisher mode requires publisher audience
    values["mode"] = "publisher"
    values["jwt_audience"] = "emporio-release-control"
    assert Settings.model_validate(values).mode == "publisher"
    # Publisher mode rejects deployer audience
    values["jwt_audience"] = "emporio-release-control-deployer"
    with pytest.raises(ValidationError, match="publisher mode requires publisher audience"):
        Settings.model_validate(values)
    # Deployer mode requires deployer audience
    values["mode"] = "deployer"
    values["jwt_audience"] = "emporio-release-control-deployer"
    assert Settings.model_validate(values).mode == "deployer"
    # Deployer mode rejects publisher audience
    values["jwt_audience"] = "emporio-release-control"
    with pytest.raises(ValidationError, match="deployer mode requires deployer audience"):
        Settings.model_validate(values)


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("deploy_rate_per_minute", 0),
        ("deploy_rate_per_minute", 31),
        ("rollback_rate_per_minute", 0),
        ("rollback_rate_per_minute", 11),
    ],
)
def test_deployer_rate_limits_are_closed(
    settings: Settings, field: str, value: int
) -> None:
    values = kwargs(settings)
    values[field] = value
    with pytest.raises(ValidationError):
        Settings.model_validate(values)


def test_development_profile_is_strictly_loopback(settings: Settings) -> None:
    values = kwargs(settings)
    values.update(
        profile="development",
        db_host="127.0.0.1",
        db_sslmode="disable",
        github_api_base="https://api.github.com",
        jwt_issuer="http://127.0.0.1:8080/api/release-control/identity",
        jwt_jwks_url=(
            "http://127.0.0.1:8080/api/release-control/identity/jwks"
        ),
        cors_origins="http://localhost:8084",
    )
    development = Settings.model_validate(values)
    assert development.profile == "development"
    mutants = (
        ("db_host", "database.invalid"),
        ("db_sslmode", "require"),
        ("github_api_base", "http://127.0.0.1:9000"),
        ("jwt_issuer", "http://issuer.invalid/identity"),
        ("jwt_issuer", "https://127.0.0.1:8080/identity"),
        ("jwt_jwks_url", "http://127.0.0.1:8080/other"),
        ("cors_origins", "http://localhost"),
        ("cors_origins", "http://localhost:8084/path"),
        ("cors_origins", "http://user@localhost:8084"),
    )
    for field, value in mutants:
        mutant = dict(values)
        mutant[field] = value
        with pytest.raises(ValidationError):
            Settings.model_validate(mutant)


def test_test_profile_rejects_every_non_loopback_transport(
    settings: Settings,
) -> None:
    values = kwargs(settings)
    Settings.model_validate(values)
    for field, value in (
        ("db_host", "database.invalid"),
        ("github_api_base", "http://github.invalid"),
        ("jwt_issuer", "http://issuer.invalid/identity"),
        ("jwt_jwks_url", "http://127.0.0.1:8080/other"),
        ("cors_origins", "http://console.invalid:8084"),
    ):
        mutant = dict(values)
        mutant[field] = value
        with pytest.raises(ValidationError):
            Settings.model_validate(mutant)


def test_runtime_rejects_http_identity_and_wrong_audience(
    settings: Settings,
) -> None:
    values = kwargs(settings)
    values.update(
        profile="runtime",
        db_sslmode="require",
        github_api_base="https://api.github.com",
        jwt_issuer="https://issuer.invalid",
        jwt_jwks_url="https://issuer.invalid/jwks",
        cors_origins="https://console.invalid",
    )
    for field, value in (
        ("jwt_issuer", "http://127.0.0.1:8080/identity"),
        ("jwt_jwks_url", "http://127.0.0.1:8080/identity/jwks"),
        ("cors_origins", "http://localhost:8084"),
        ("jwt_audience", "other"),
    ):
        mutant = dict(values)
        mutant[field] = value
        with pytest.raises(ValidationError):
            Settings.model_validate(mutant)


def test_private_key_is_read_from_file_only(
    settings: Settings, tmp_path: Path
) -> None:
    assert settings.read_github_private_key().startswith(b"-----BEGIN")
    invalid = tmp_path / "invalid.pem"
    invalid.write_text("not-a-key")
    values = kwargs(settings)
    values["github_private_key_path"] = invalid
    with pytest.raises(ValueError):
        Settings.model_validate(values).read_github_private_key()


def jwk(public: rsa.RSAPublicKey, kid: str = "test-key") -> dict[str, str]:
    numbers = public.public_numbers()
    def encode(value: int) -> str:
        return base64.urlsafe_b64encode(
            value.to_bytes((value.bit_length() + 7) // 8, "big")
        ).rstrip(b"=").decode()
    return {"kty": "RSA", "kid": kid, "use": "sig", "alg": "RS256",
            "n": encode(numbers.n), "e": encode(numbers.e)}


def test_jwt_valid_and_scope_exclusively_from_scope_claim(
    rsa_material: tuple[Path, rsa.RSAPrivateKey, rsa.RSAPublicKey]
) -> None:
    _, private, public = rsa_material
    transport = httpx.MockTransport(
        lambda _request: httpx.Response(200, json={"keys": [jwk(public)]})
    )
    verifier = JwtVerifier(
        "https://issuer.invalid",
        "emporio-release-control",
        "https://issuer.invalid/jwks",
        httpx.Client(transport=transport),
    )
    token = jwt.encode(
        {
            "iss": "https://issuer.invalid",
            "aud": "emporio-release-control",
            "sub": "actor",
            "scope": "release:read release:publish",
            "roles": ["admin"],
            "exp": 4_102_444_800,
        },
        private,
        algorithm="RS256",
        headers={"kid": "test-key"},
    )
    principal = verifier.verify(token)
    assert principal.sub == "actor"
    assert principal.scopes == {"release:read", "release:publish"}
    principal.require("release:read")
    with pytest.raises(RuntimeFailure, match="FORBIDDEN"):
        principal.require("deployment:execute")


@pytest.mark.parametrize(
    "mutation",
    [
        "issuer",
        "audience",
        "algorithm",
        "hs512",
        "subject",
        "scope",
        "expired",
        "unknown-kid",
    ],
)
def test_jwt_rejects_identity_algorithm_and_claim_mutants(
    rsa_material: tuple[Path, rsa.RSAPrivateKey, rsa.RSAPublicKey], mutation: str
) -> None:
    _, private, public = rsa_material
    signing_key: str | rsa.RSAPrivateKey = private
    verifier = JwtVerifier(
        "https://issuer.invalid",
        "emporio-release-control",
        "https://issuer.invalid/jwks",
        httpx.Client(
            transport=httpx.MockTransport(
                lambda _request: httpx.Response(200, json={"keys": [jwk(public)]})
            )
        ),
    )
    claims: dict[str, object] = {
        "iss": "https://issuer.invalid",
        "aud": "emporio-release-control",
        "sub": "actor",
        "scope": "release:read",
        "exp": 4_102_444_800,
    }
    headers = {"kid": "test-key"}
    algorithm = "RS256"
    if mutation == "issuer":
        claims["iss"] = "https://other.invalid"
    elif mutation == "audience":
        claims["aud"] = "other"
    elif mutation == "algorithm":
        algorithm = "HS256"
        signing_key = "not-a-real-secret"
    elif mutation == "hs512":
        algorithm = "HS512"
        signing_key = "not-a-real-erp-secret-" * 4
    elif mutation == "subject":
        claims.pop("sub")
    elif mutation == "scope":
        claims["scope"] = ["release:read"]
    elif mutation == "expired":
        claims["exp"] = 1
    else:
        headers["kid"] = "unknown"
    token = jwt.encode(claims, signing_key, algorithm=algorithm, headers=headers)
    with pytest.raises(RuntimeFailure, match="UNAUTHORIZED"):
        verifier.verify(token)


def test_cursor_is_canonical_signed_and_tamper_evident() -> None:
    codec = CursorCodec(b"x" * 32)
    value = codec.encode({"release": "v1.2.3"})
    assert "=" not in value
    assert codec.decode(value) == {"release": "v1.2.3"}
    for mutant in (value[:-1] + "A", "%%%", base64.urlsafe_b64encode(b"short").decode()):
        with pytest.raises(RuntimeFailure, match="BAD_REQUEST"):
            codec.decode(mutant)


def test_rate_limit_is_per_actor_and_window() -> None:
    now = [100.0]
    limiter = RateLimiter(lambda: now[0])
    limiter.check("a", "read", 2)
    limiter.check("a", "read", 2)
    limiter.check("b", "read", 2)
    with pytest.raises(RuntimeFailure, match="RATE_LIMITED"):
        limiter.check("a", "read", 2)
    now[0] += 61
    limiter.check("a", "read", 2)
