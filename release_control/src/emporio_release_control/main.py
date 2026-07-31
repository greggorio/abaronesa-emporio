"""Mode-isolated ASGI bootstrap. Invalid configuration fails startup."""

from __future__ import annotations

from typing import Any

import httpx
from fastapi import FastAPI

from .config import Settings
from .constants import DEPLOYER_MODE, PUBLISHER_MODE
from .github import GitHubClient
from .persistence import build_engine, build_session_factory, migration_is_current
from .security import JwtVerifier
from .sync import Synchronizer


def create_runtime(runtime_settings: Settings | None = None) -> FastAPI:
    """Build exactly one immutable runtime graph selected by configuration."""
    settings = runtime_settings or Settings()
    private_key = settings.read_github_private_key()
    engine = build_engine(settings.database_url)
    if not migration_is_current(engine):
        engine.dispose()
        raise RuntimeError("database migration is not current")
    factory = build_session_factory(engine)
    github = GitHubClient(
        app_id=settings.github_app_id,
        installation_id=settings.github_installation_id,
        private_key=private_key,
        api_base=settings.github_api_base,
        connect_timeout=settings.http_connect_timeout_seconds,
        read_timeout=settings.http_read_timeout_seconds,
        max_pages=settings.github_max_pages,
    )
    jwks_http = httpx.Client(
        timeout=httpx.Timeout(
            settings.http_read_timeout_seconds,
            connect=settings.http_connect_timeout_seconds,
        ),
        follow_redirects=False,
    )
    verifier = JwtVerifier(
        settings.jwt_issuer,
        settings.jwt_audience,
        settings.jwt_jwks_url,
        jwks_http,
    )
    synchronizer = Synchronizer(factory, github)

    loop: Any
    if settings.mode == PUBLISHER_MODE:
        from .api import create_app
        from .reconciliation import ReconcileLoop, Reconciler
        from .service import PublisherService

        service = PublisherService(
            factory,
            github,
            settings.hash_pepper.get_secret_value().encode(),
            settings.idempotency_retention_days,
            revalidate_candidate=synchronizer.revalidate_candidate,
        )
        app = create_app(settings, service, verifier)
        reconciler = Reconciler(
            factory,
            github,
            service,
            synchronizer,
            settings.dispatch_discovery_timeout_seconds,
        )
        loop = ReconcileLoop(reconciler, settings.reconcile_interval_seconds)
    elif settings.mode == DEPLOYER_MODE:
        from .deployer_api import create_deployer_app
        from .deployer_reconciliation import DeployerReconcileLoop, DeployerReconciler
        from .deployer_service import DeployerService

        deployer_service = DeployerService(
            factory,
            github,
            settings.hash_pepper.get_secret_value().encode(),
            settings.idempotency_retention_days,
            revalidate_release=synchronizer.revalidate_release,
        )
        app = create_deployer_app(settings, deployer_service, verifier)
        deployer_reconciler = DeployerReconciler(
            factory,
            github,
            deployer_service,
            synchronizer,
            settings.dispatch_discovery_timeout_seconds,
        )
        loop = DeployerReconcileLoop(
            deployer_reconciler, settings.reconcile_interval_seconds
        )
    else:  # Defensive: Settings already rejects this branch.
        engine.dispose()
        jwks_http.close()
        github.client.close()
        raise RuntimeError("unsupported release-control mode")

    app.state.runtime_mode = settings.mode
    app.state.reconciler = loop.reconciler
    app.router.add_event_handler("startup", loop.start)

    def shutdown() -> None:
        loop.stop()
        jwks_http.close()
        github.client.close()
        engine.dispose()

    app.router.add_event_handler("shutdown", shutdown)
    return app


app = create_runtime()
