"""Fail-closed publisher settings."""

from __future__ import annotations

from pathlib import Path
from typing import Literal, Self
from urllib.parse import urlsplit

from pydantic import Field, SecretStr, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

from .constants import GITHUB_API


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="RELEASE_CONTROL_", extra="forbid", case_sensitive=False
    )

    profile: Literal["runtime", "development", "test"] = "runtime"
    mode: Literal["publisher", "deployer"]
    db_host: str = Field(min_length=1)
    db_port: int = Field(ge=1, le=65535)
    db_name: str = Field(min_length=1)
    db_user: str = Field(min_length=1)
    db_password: SecretStr
    db_sslmode: Literal["require", "disable"]
    jwt_issuer: str
    jwt_audience: Literal["emporio-release-control", "emporio-release-control-deployer"]
    jwt_jwks_url: str
    cors_origins: str
    github_app_id: int = Field(gt=0)
    github_installation_id: int = Field(gt=0)
    github_private_key_path: Path
    hash_pepper: SecretStr
    github_api_base: str = GITHUB_API

    reconcile_interval_seconds: int = Field(default=15, gt=0, le=60)
    dispatch_discovery_timeout_seconds: int = Field(default=600, gt=0, le=3600)
    http_connect_timeout_seconds: int = Field(default=3, gt=0, le=30)
    http_read_timeout_seconds: int = Field(default=10, gt=0, le=60)
    github_max_pages: int = Field(default=10, gt=0, le=20)
    idempotency_retention_days: int = Field(default=365, gt=0, le=3650)
    read_rate_per_minute: int = Field(default=120, gt=0, le=600)
    publish_rate_per_minute: int = Field(default=5, gt=0, le=60)
    deploy_rate_per_minute: int = Field(default=5, ge=1, le=30)
    rollback_rate_per_minute: int = Field(default=2, ge=1, le=10)
    max_json_body_bytes: int = Field(default=16384, gt=0, le=65536)

    @field_validator("jwt_issuer", "jwt_jwks_url")
    @classmethod
    def normalize_identity_url(cls, value: str) -> str:
        if not value:
            raise ValueError("identity URL required")
        return value.rstrip("/")

    @field_validator("db_host", "db_name", "db_user")
    @classmethod
    def reject_control_characters(cls, value: str) -> str:
        if any(ord(char) < 33 or ord(char) == 127 for char in value):
            raise ValueError("invalid database property")
        return value

    @model_validator(mode="after")
    def validate_profile_and_secrets(self) -> Self:
        if len(self.db_password.get_secret_value().encode()) < 1:
            raise ValueError("database password required")
        if len(self.hash_pepper.get_secret_value().encode()) < 32:
            raise ValueError("hash pepper must contain at least 32 bytes")
        if self.mode == "publisher" and self.jwt_audience != "emporio-release-control":
            raise ValueError("publisher mode requires publisher audience")
        if self.mode == "deployer" and self.jwt_audience != "emporio-release-control-deployer":
            raise ValueError("deployer mode requires deployer audience")
        if self.profile == "runtime":
            if self.db_sslmode != "require" or self.github_api_base != GITHUB_API:
                raise ValueError("runtime transport configuration invalid")
            self._require_url(self.jwt_issuer, "https", loopback=False)
            self._require_url(self.jwt_jwks_url, "https", loopback=False)
        elif self.profile == "development":
            if (
                self.db_sslmode != "disable"
                or self.db_host not in {"127.0.0.1", "localhost"}
                or self.github_api_base != GITHUB_API
            ):
                raise ValueError("development profile requires local database")
            self._validate_local_identity_urls()
        else:
            parsed = urlsplit(self.github_api_base)
            if (
                self.db_sslmode != "disable"
                or self.db_host not in {"127.0.0.1", "localhost"}
                or parsed.scheme != "http"
                or parsed.hostname not in {"127.0.0.1", "localhost"}
            ):
                raise ValueError("test profile requires loopback transports")
            self._validate_local_identity_urls()
        self._validate_origins()
        return self

    def _validate_local_identity_urls(self) -> None:
        self._require_url(self.jwt_issuer, "http", loopback=True)
        self._require_url(self.jwt_jwks_url, "http", loopback=True)
        if self.jwt_jwks_url != self.jwt_issuer + "/jwks":
            raise ValueError("JWKS URL must be issuer plus /jwks")

    @staticmethod
    def _require_url(value: str, scheme: str, *, loopback: bool) -> None:
        parsed = urlsplit(value)
        if (
            parsed.scheme != scheme
            or not parsed.netloc
            or parsed.hostname is None
            or parsed.username
            or parsed.password
            or parsed.query
            or parsed.fragment
            or (loopback and parsed.hostname not in {"127.0.0.1", "localhost"})
        ):
            raise ValueError("invalid identity URL")

    def _validate_origins(self) -> None:
        origins = self.origin_list
        if not origins:
            raise ValueError("CORS allowlist required")
        for origin in origins:
            parsed = urlsplit(origin)
            expected_scheme = "https" if self.profile == "runtime" else "http"
            if (
                origin == "*"
                or parsed.scheme != expected_scheme
                or not parsed.netloc
                or parsed.hostname is None
                or parsed.username
                or parsed.password
                or parsed.path not in {"", "/"}
                or parsed.query
                or parsed.fragment
                or (
                    self.profile in {"development", "test"}
                    and parsed.hostname not in {"127.0.0.1", "localhost"}
                )
                or (self.profile == "development" and parsed.port is None)
            ):
                raise ValueError("invalid CORS origin")

    @property
    def origin_list(self) -> list[str]:
        return [item.strip().rstrip("/") for item in self.cors_origins.split(",") if item.strip()]

    @property
    def database_url(self) -> str:
        from sqlalchemy.engine import URL

        return URL.create(
            "postgresql+psycopg",
            username=self.db_user,
            password=self.db_password.get_secret_value(),
            host=self.db_host,
            port=self.db_port,
            database=self.db_name,
            query={"sslmode": self.db_sslmode},
        ).render_as_string(hide_password=False)

    def read_github_private_key(self) -> bytes:
        data = self.github_private_key_path.read_bytes()
        if not data.startswith(b"-----BEGIN") or len(data) > 32 * 1024:
            raise ValueError("invalid GitHub App private key file")
        return data
