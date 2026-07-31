"""Exact FastAPI surface for the immutable deployer mode."""

from __future__ import annotations

import re
import uuid
from collections.abc import Awaitable, Callable
from typing import Any, cast

from fastapi import Depends, FastAPI, Header, Query, Request, Response
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHttpException
from starlette.middleware.base import BaseHTTPMiddleware

from .config import Settings
from .deployer_schemas import (
    CurrentInstallationResponse,
    DeployerProblemCode,
    DeployerProblemDetails,
    DeploymentOperationResponse,
    DeploymentPlan,
    DeploymentRequest,
    GlobalReleasePage,
    RollbackOperationResponse,
    RollbackRequest,
)
from .deployer_service import DeployerService
from .errors import RuntimeFailure, normalize_public_failure
from .security import JwtVerifier, Principal, RateLimiter

IDEMPOTENCY_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:-]{15,127}$")
ROLLBACK_IDEMPOTENCY_RE = re.compile(
    r"^deployer-rollback-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
)
OPERATION_RE = re.compile(r"^(?:dep|rbk)_[0-9a-f]{32}$")
SEMVER_RE = re.compile(r"^v(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)$")


class DeployerSecurityMiddleware(BaseHTTPMiddleware):
    def __init__(self, app: Any, max_json_body_bytes: int) -> None:
        super().__init__(app)
        self.max_json_body_bytes = max_json_body_bytes

    async def dispatch(
        self,
        request: Request,
        call_next: Callable[[Request], Awaitable[Response]],
    ) -> Response:
        request.state.trace_id = uuid.uuid4().hex
        response = await call_next(request)
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Cache-Control"] = "no-store"
        response.headers["Referrer-Policy"] = "no-referrer"
        return response


def _problem(request: Request, failure: RuntimeFailure) -> JSONResponse:
    active = getattr(failure, "active_operation_id", None)
    if failure.code in {
        "PRODUCTION_OPERATION_ACTIVE",
        "RELEASE_NOT_ELIGIBLE",
        "CURRENT_INSTALLATION_UNRECONCILED",
    } and failure.status == 409:
        failure = RuntimeFailure(failure.code, 409, "Conflict")
    else:
        failure = normalize_public_failure(failure)
    trace_id = getattr(request.state, "trace_id", uuid.uuid4().hex)
    problem = DeployerProblemDetails(
        type=f"https://errors.invalid/{failure.code.lower()}",
        title=failure.title,
        status=failure.status,
        code=cast(DeployerProblemCode, failure.code),
        trace_id=trace_id,
    )
    content = problem.model_dump(by_alias=True, exclude_none=True)
    if failure.code == "PRODUCTION_OPERATION_ACTIVE" and isinstance(active, str):
        content["activeOperationId"] = active
    return JSONResponse(
        status_code=failure.status,
        content=content,
        media_type="application/problem+json",
    )


def create_deployer_app(
    settings: Settings,
    service: DeployerService,
    verifier: JwtVerifier,
    *,
    limiter: RateLimiter | None = None,
    private_key_valid: bool = True,
) -> FastAPI:
    app = FastAPI(
        title="Emporio Deployment Control API",
        version="1.1.0",
        docs_url=None,
        redoc_url=None,
        openapi_url=None,
    )
    app.state.service = service
    app.state.settings = settings
    rate_limiter = limiter or RateLimiter()
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.origin_list,
        allow_credentials=False,
        allow_methods=["GET", "POST"],
        allow_headers=["Authorization", "Content-Type", "Idempotency-Key"],
    )
    app.add_middleware(
        DeployerSecurityMiddleware,
        max_json_body_bytes=settings.max_json_body_bytes,
    )

    @app.exception_handler(RuntimeFailure)
    async def runtime_failure(request: Request, exc: RuntimeFailure) -> JSONResponse:
        return _problem(request, exc)

    @app.exception_handler(Exception)
    async def unexpected_failure(request: Request, _exc: Exception) -> JSONResponse:
        return _problem(request, RuntimeFailure("INTERNAL_ERROR"))

    @app.exception_handler(RequestValidationError)
    async def validation_failure(request: Request, _exc: RequestValidationError) -> JSONResponse:
        failure = (
            RuntimeFailure("UNPROCESSABLE", 422, "Unprocessable")
            if request.method == "POST"
            else RuntimeFailure("BAD_REQUEST", 400, "Bad request")
        )
        return _problem(request, failure)

    @app.exception_handler(StarletteHttpException)
    async def http_failure(request: Request, exc: StarletteHttpException) -> JSONResponse:
        code = "NOT_FOUND" if exc.status_code == 404 else "BAD_REQUEST"
        return _problem(
            request,
            RuntimeFailure(code, 404 if exc.status_code == 404 else 400, "Request failed"),
        )

    def principal(
        authorization: str | None = Header(default=None, alias="Authorization"),
    ) -> Principal:
        if not authorization or not authorization.startswith("Bearer "):
            raise RuntimeFailure("UNAUTHORIZED", 401, "Unauthorized")
        token = authorization.removeprefix("Bearer ")
        if not token or " " in token:
            raise RuntimeFailure("UNAUTHORIZED", 401, "Unauthorized")
        return verifier.verify(token)

    def reader(authenticated: Principal = Depends(principal)) -> Principal:
        authenticated.require("deployment:read")
        rate_limiter.check(authenticated.sub, "read", settings.read_rate_per_minute)
        return authenticated

    def executor(authenticated: Principal = Depends(principal)) -> Principal:
        authenticated.require("deployment:execute")
        rate_limiter.check(authenticated.sub, "deploy", settings.deploy_rate_per_minute)
        return authenticated

    def rollback_actor(authenticated: Principal = Depends(principal)) -> Principal:
        authenticated.require("deployment:rollback")
        rate_limiter.check(
            authenticated.sub, "rollback", settings.rollback_rate_per_minute
        )
        return authenticated

    async def parse_body(request: Request, model: type[Any]) -> Any:
        content_type = request.headers.get("content-type", "").split(";", 1)[0].lower()
        if content_type != "application/json":
            raise RuntimeFailure("UNPROCESSABLE", 422, "Unprocessable")
        raw = await request.body()
        if len(raw) > settings.max_json_body_bytes:
            raise RuntimeFailure("UNPROCESSABLE", 422, "Unprocessable")
        try:
            return model.model_validate_json(raw)
        except ValueError as exc:
            raise RuntimeFailure("UNPROCESSABLE", 422, "Unprocessable") from exc

    @app.get("/health/live")
    def live() -> dict[str, str]:
        return {"status": "ok"}

    @app.get("/health/ready")
    def ready() -> JSONResponse:
        available = service.ready(private_key_valid)
        return JSONResponse(
            status_code=200 if available else 503,
            content={"status": "ok" if available else "unavailable"},
        )

    @app.get("/api/release-control/v1/capabilities")
    def capabilities(_principal: Principal = Depends(reader)) -> dict[str, Any]:
        return {
            "apiVersion": "v1",
            "capabilities": [
                "deployment:read",
                "deployment:execute",
                "deployment:rollback",
            ],
            "mode": "deployer",
        }

    @app.get(
        "/api/deployment-control/v1/current",
        response_model=CurrentInstallationResponse,
        response_model_by_alias=True,
    )
    def current(
        _principal: Principal = Depends(reader),
    ) -> CurrentInstallationResponse:
        return service.get_current()

    @app.get(
        "/api/deployment-control/v1/releases",
        response_model=GlobalReleasePage,
        response_model_by_alias=True,
    )
    def releases(
        limit: int = Query(default=25, ge=1, le=100),
        cursor: str | None = Query(default=None, min_length=1, max_length=256),
        _principal: Principal = Depends(reader),
    ) -> GlobalReleasePage:
        return service.list_releases(limit, cursor)

    @app.get(
        "/api/deployment-control/v1/releases/{release_id}/plan",
        response_model=DeploymentPlan,
        response_model_by_alias=True,
    )
    def plan(
        release_id: str,
        _principal: Principal = Depends(reader),
    ) -> DeploymentPlan:
        if SEMVER_RE.fullmatch(release_id) is None:
            raise RuntimeFailure("BAD_REQUEST", 400, "Bad request")
        return service.get_plan(release_id)

    @app.post(
        "/api/deployment-control/v1/deployments",
        status_code=202,
        response_model=DeploymentOperationResponse,
        response_model_by_alias=True,
    )
    async def deploy(
        request: Request,
        response: Response,
        idempotency_key: str = Header(alias="Idempotency-Key"),
        authenticated: Principal = Depends(executor),
    ) -> DeploymentOperationResponse:
        if IDEMPOTENCY_RE.fullmatch(idempotency_key) is None:
            raise RuntimeFailure("BAD_REQUEST", 400, "Bad request")
        body = await parse_body(request, DeploymentRequest)
        operation, replay = service.create_deployment(
            authenticated, idempotency_key, body, request.state.trace_id
        )
        response.headers["Idempotency-Replayed"] = str(replay).lower()
        return operation

    @app.get(
        "/api/deployment-control/v1/deployments/{deployment_id}",
        response_model=DeploymentOperationResponse,
        response_model_by_alias=True,
    )
    def operation(
        deployment_id: str,
        _principal: Principal = Depends(reader),
    ) -> DeploymentOperationResponse:
        if OPERATION_RE.fullmatch(deployment_id) is None:
            raise RuntimeFailure("BAD_REQUEST", 400, "Bad request")
        return service.get_operation(deployment_id)

    @app.post(
        "/api/deployment-control/v1/rollbacks",
        status_code=202,
        response_model=RollbackOperationResponse,
        response_model_by_alias=True,
    )
    async def rollback(
        request: Request,
        response: Response,
        idempotency_key: str = Header(alias="Idempotency-Key"),
        authenticated: Principal = Depends(rollback_actor),
    ) -> RollbackOperationResponse:
        if ROLLBACK_IDEMPOTENCY_RE.fullmatch(idempotency_key) is None:
            raise RuntimeFailure("BAD_REQUEST", 400, "Bad request")
        body = await parse_body(request, RollbackRequest)
        operation, replay = service.create_rollback(
            authenticated, idempotency_key, body, request.state.trace_id
        )
        response.headers["Idempotency-Replayed"] = str(replay).lower()
        return operation

    @app.get(
        "/api/deployment-control/v1/rollbacks/{operation_id}",
        response_model=RollbackOperationResponse,
        response_model_by_alias=True,
    )
    def rollback_operation(
        operation_id: str,
        _principal: Principal = Depends(reader),
    ) -> RollbackOperationResponse:
        if not operation_id.startswith("rbk_") or OPERATION_RE.fullmatch(operation_id) is None:
            raise RuntimeFailure("BAD_REQUEST", 400, "Bad request")
        return service.get_rollback_operation(operation_id)

    return app
