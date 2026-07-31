from __future__ import annotations

import ipaddress
import os
import socket
import sys
from collections.abc import Iterator
from pathlib import Path
from urllib.parse import urlsplit

import pytest
from alembic import command
from alembic.config import Config
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from sqlalchemy import Engine, text
from sqlalchemy.orm import Session, sessionmaker
from testcontainers.postgres import PostgresContainer

ROOT = Path(__file__).resolve().parents[1]
SOURCE = str(ROOT / "src")
if SOURCE not in sys.path:
    sys.path.insert(0, SOURCE)

from emporio_release_control.config import Settings  # noqa: E402
from emporio_release_control.persistence import (  # noqa: E402
    build_engine,
    build_session_factory,
)


@pytest.fixture(autouse=True)
def block_non_loopback_sockets(monkeypatch: pytest.MonkeyPatch) -> Iterator[None]:
    original = socket.socket.connect

    def guarded(sock: socket.socket, address: object) -> object:
        if sock.family in {socket.AF_INET, socket.AF_INET6}:
            if not isinstance(address, tuple) or not address:
                raise OSError("network destination rejected")
            host = str(address[0])
            try:
                allowed = ipaddress.ip_address(host).is_loopback
            except ValueError:
                allowed = host == "localhost"
            if not allowed:
                raise OSError("non-loopback network rejected")
        return original(sock, address)  # type: ignore[arg-type]

    monkeypatch.setattr(socket.socket, "connect", guarded)
    yield


@pytest.fixture(scope="session")
def rsa_material(tmp_path_factory: pytest.TempPathFactory) -> tuple[Path, object, object]:
    private = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    public = private.public_key()
    path = tmp_path_factory.mktemp("keys") / "github-app.pem"
    path.write_bytes(
        private.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption(),
        )
    )
    return path, private, public


def settings_env(url: str, key_path: Path) -> dict[str, str]:
    parsed = urlsplit(url)
    return {
        "RELEASE_CONTROL_PROFILE": "test",
        "RELEASE_CONTROL_MODE": "publisher",
        "RELEASE_CONTROL_DB_HOST": parsed.hostname or "127.0.0.1",
        "RELEASE_CONTROL_DB_PORT": str(parsed.port or 5432),
        "RELEASE_CONTROL_DB_NAME": parsed.path.removeprefix("/"),
        "RELEASE_CONTROL_DB_USER": parsed.username or "test",
        "RELEASE_CONTROL_DB_PASSWORD": parsed.password or "test",
        "RELEASE_CONTROL_DB_SSLMODE": "disable",
        "RELEASE_CONTROL_JWT_ISSUER": "http://127.0.0.1:8080/api/release-control/identity",
        "RELEASE_CONTROL_JWT_AUDIENCE": "emporio-release-control",
        "RELEASE_CONTROL_JWT_JWKS_URL": (
            "http://127.0.0.1:8080/api/release-control/identity/jwks"
        ),
        "RELEASE_CONTROL_CORS_ORIGINS": "http://127.0.0.1:8084",
        "RELEASE_CONTROL_GITHUB_APP_ID": "100",
        "RELEASE_CONTROL_GITHUB_INSTALLATION_ID": "200",
        "RELEASE_CONTROL_GITHUB_PRIVATE_KEY_PATH": str(key_path),
        "RELEASE_CONTROL_HASH_PEPPER": "p" * 32,
        "RELEASE_CONTROL_GITHUB_API_BASE": "http://127.0.0.1:9",
    }


@pytest.fixture(scope="session")
def postgres_url(rsa_material: tuple[Path, object, object]) -> Iterator[str]:
    key_path, _, _ = rsa_material
    with PostgresContainer("postgres:16-alpine", driver="psycopg") as postgres:
        url = postgres.get_connection_url()
        os.environ.update(settings_env(url, key_path))
        config = Config(str(ROOT / "alembic.ini"))
        config.set_main_option("script_location", str(ROOT / "migrations"))
        command.upgrade(config, "head")
        command.upgrade(config, "head")
        yield url


@pytest.fixture(scope="session")
def settings(postgres_url: str, rsa_material: tuple[Path, object, object]) -> Settings:
    key_path, _, _ = rsa_material
    return Settings.model_validate({
        key.removeprefix("RELEASE_CONTROL_").lower(): value
        for key, value in settings_env(postgres_url, key_path).items()
    })


@pytest.fixture(scope="session")
def engine(settings: Settings) -> Iterator[Engine]:
    value = build_engine(settings.database_url)
    yield value
    value.dispose()


@pytest.fixture()
def factory(engine: Engine) -> Iterator[sessionmaker[Session]]:
    tables = (
        "rc_audit_event",
        "rc_rollback_backup",
        "rc_deployment_idempotency_key",
        "rc_current_installation",
        "rc_deployment_operation",
        "rc_idempotency_key",
        "rc_publication_operation",
        "rc_candidate_snapshot",
        "rc_release_snapshot",
        "rc_sync_state",
    )
    with engine.begin() as connection:
        connection.execute(text("TRUNCATE " + ",".join(tables) + " RESTART IDENTITY CASCADE"))
    yield build_session_factory(engine)
