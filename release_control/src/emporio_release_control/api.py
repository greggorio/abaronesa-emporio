"""Exact FastAPI publisher surface."""

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
from .errors import RuntimeFailure, normalize_public_failure
from .schemas import (
    CandidatePage,
    ProblemDetails,
    PublicationOperationResponse,
    PublicProblemCode,
    PublishReleaseRequest,
    ReleasePage,
)
from .security import JwtVerifier, Principal, RateLimiter
from .service import PublisherService

IDEMPOTENCY_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:-]{15,127}$")
OPERATION_RE = re.compile(r"^pub_[0-9a-f]{32}$")


class SecurityMiddleware(BaseHTTPMiddleware):
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
    failure = normalize_public_failure(failure)
    trace_id = getattr(request.state, "trace_id", uuid.uuid4().hex)
    problem = ProblemDetails(
        type=f"https://errors.invalid/{failure.code.lower()}",
        title=failure.title,
        status=failure.status,
        code=cast(PublicProblemCode, failure.code),
        trace_id=trace_id,
    )
    return JSONResponse(
        status_code=failure.status,
        content=problem.model_dump(by_alias=True, exclude_none=True),
        media_type="application/problem+json",
    )


def create_app(
    settings: Settings,
    service: PublisherService,
    verifier: JwtVerifier,
    *,
    limiter: RateLimiter | None = None,
    private_key_valid: bool = True,
) -> FastAPI:
    app = FastAPI(
        title="Emporio Release Publisher API",
        version="1.0.0",
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
    app.add_middleware(SecurityMiddleware, max_json_body_bytes=settings.max_json_body_bytes)

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
        status = 404 if exc.status_code == 404 else 400
        return _problem(request, RuntimeFailure(code, status, "Request failed"))

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
        authenticated.require("release:read")
        rate_limiter.check(authenticated.sub, "read", settings.read_rate_per_minute)
        return authenticated

    def publisher(authenticated: Principal = Depends(principal)) -> Principal:
        authenticated.require("release:publish")
        rate_limiter.check(authenticated.sub, "publish", settings.publish_rate_per_minute)
        return authenticated

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
            "mode": "publisher",
            "apiVersion": "v1",
            "capabilities": ["release:read", "release:publish"],
        }

    @app.get(
        "/api/release-publisher/v1/candidates",
        response_model=CandidatePage,
        response_model_by_alias=True,
    )
    def candidates(
        limit: int = Query(default=25, ge=1, le=100),
        cursor: str | None = Query(default=None, min_length=1, max_length=256),
        eligibility: str | None = Query(default=None, pattern="^(NOT_ELIGIBLE|READY)$"),
        _principal: Principal = Depends(reader),
    ) -> CandidatePage:
        return service.list_candidates(limit, cursor, eligibility)

    @app.get(
        "/api/release-publisher/v1/releases",
        response_model=ReleasePage,
        response_model_by_alias=True,
    )
    def releases(
        limit: int = Query(default=25, ge=1, le=100),
        cursor: str | None = Query(default=None, min_length=1, max_length=256),
        _principal: Principal = Depends(reader),
    ) -> ReleasePage:
        return service.list_releases(limit, cursor)

    @app.post(
        "/api/release-publisher/v1/releases",
        status_code=202,
        response_model=PublicationOperationResponse,
        response_model_by_alias=True,
    )
    async def publish(
        request: Request,
        response: Response,
        idempotency_key: str = Header(alias="Idempotency-Key"),
        authenticated: Principal = Depends(publisher),
    ) -> PublicationOperationResponse:
        if IDEMPOTENCY_RE.fullmatch(idempotency_key) is None:
            raise RuntimeFailure("BAD_REQUEST", 400, "Bad request")
        content_type = request.headers.get("content-type", "").split(";", 1)[0].lower()
        if content_type != "application/json":
            raise RuntimeFailure("UNPROCESSABLE", 422, "Unprocessable")
        raw = await request.body()
        if len(raw) > settings.max_json_body_bytes:
            raise RuntimeFailure("UNPROCESSABLE", 422, "Unprocessable")
        try:
            body = PublishReleaseRequest.model_validate_json(raw)
        except ValueError as exc:
            raise RuntimeFailure("UNPROCESSABLE", 422, "Unprocessable") from exc
        operation, replay = service.create_publication(
            authenticated, idempotency_key, body, request.state.trace_id
        )
        response.headers["Idempotency-Replayed"] = str(replay).lower()
        return operation

    @app.get(
        "/api/release-publisher/v1/operations/{operation_id}",
        response_model=PublicationOperationResponse,
        response_model_by_alias=True,
    )
    def operation(
        operation_id: str,
        _principal: Principal = Depends(reader),
    ) -> PublicationOperationResponse:
        if OPERATION_RE.fullmatch(operation_id) is None:
            raise RuntimeFailure("BAD_REQUEST", 400, "Bad request")
        value = service.get_operation(operation_id)
        if value.operation_id != operation_id:
            raise RuntimeFailure("INTERNAL_ERROR")
        return value

    return app
