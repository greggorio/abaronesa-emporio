from __future__ import annotations

from datetime import UTC, datetime
from typing import Any, cast

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session, sessionmaker

from emporio_release_control.api import create_app
from emporio_release_control.config import Settings
from emporio_release_control.errors import (
    PreDispatchFailure,
    RemoteHttpFailure,
    RemoteTransportFailure,
    RuntimeFailure,
)
from emporio_release_control.persistence import CandidateSnapshot, SyncState
from emporio_release_control.security import Principal
from emporio_release_control.service import PublisherService


class FakeGitHub:
    def __init__(self) -> None:
        self.dispatches: list[tuple[str, dict[str, str]]] = []

    def dispatch_publication(self, operation_id: str, request: dict[str, str]) -> None:
        self.dispatches.append((operation_id, request))


class FakeVerifier:
    def verify(self, token: str) -> Principal:
        values = {
            "reader": Principal("reader", frozenset({"release:read"})),
            "publisher": Principal("publisher", frozenset({"release:publish"})),
            "both": Principal(
                "both", frozenset({"release:read", "release:publish"})
            ),
        }
        if token not in values:
            raise RuntimeFailure("UNAUTHORIZED", 401, "Unauthorized")
        return values[token]


def seed(factory: sessionmaker[Session]) -> str:
    candidate = "candidate-" + "1" * 40 + "-2-1"
    with factory.begin() as session:
        session.add(
            CandidateSnapshot(
                candidate_id=candidate,
                source_commit="1" * 40,
                eligibility="READY",
                ci_status="PASSED",
                manifest_status="VALID",
                created_at=datetime(2026, 7, 29, tzinfo=UTC),
                manifest={"candidateId": candidate},
                artifact_id=1,
                artifact_digest="sha256:" + "a" * 64,
            )
        )
    return candidate


@pytest.fixture()
def client(
    factory: sessionmaker[Session], settings: Settings
) -> tuple[TestClient, PublisherService, str]:
    candidate = seed(factory)
    service = PublisherService(
        factory, cast(Any, FakeGitHub()), b"p" * 32, 365, lambda _id: None
    )
    app = create_app(settings, service, FakeVerifier())  # type: ignore[arg-type]
    return TestClient(app, raise_server_exceptions=False), service, candidate


def auth(token: str = "reader") -> dict[str, str]:  # noqa: S107
    return {"Authorization": f"Bearer {token}"}


def payload(candidate: str) -> dict[str, str]:
    return {
        "candidateId": candidate,
        "versionBump": "PATCH",
        "description": "Safe description",
        "changelog": "Safe changelog",
    }


def test_exact_routes_and_public_health(
    client: tuple[TestClient, PublisherService, str]
) -> None:
    http, _, _ = client
    paths = {route.path for route in cast(Any, http.app).routes}
    assert paths == {
        "/health/live",
        "/health/ready",
        "/api/release-control/v1/capabilities",
        "/api/release-publisher/v1/candidates",
        "/api/release-publisher/v1/releases",
        "/api/release-publisher/v1/operations/{operation_id}",
    }
    response = http.get("/health/live")
    assert response.status_code == 200 and response.json() == {"status": "ok"}
    assert response.headers["x-content-type-options"] == "nosniff"
    assert response.headers["cache-control"] == "no-store"
    assert response.headers["referrer-policy"] == "no-referrer"
    assert http.get("/health/ready").status_code == 503
    assert http.get("/api/deployment-control/v1/current").status_code == 404


def test_authentication_authorization_and_capabilities(
    client: tuple[TestClient, PublisherService, str]
) -> None:
    http, _, _ = client
    assert http.get("/api/release-control/v1/capabilities").status_code == 401
    assert http.get(
        "/api/release-control/v1/capabilities", headers=auth("bad")
    ).status_code == 401
    assert http.get(
        "/api/release-control/v1/capabilities", headers=auth("publisher")
    ).status_code == 403
    response = http.get(
        "/api/release-control/v1/capabilities", headers=auth()
    )
    assert response.status_code == 200
    assert response.json()["mode"] == "publisher"


def test_candidate_release_lists_and_tampered_cursor(
    client: tuple[TestClient, PublisherService, str]
) -> None:
    http, _, candidate = client
    response = http.get(
        "/api/release-publisher/v1/candidates?limit=1", headers=auth()
    )
    assert response.status_code == 200
    assert response.json()["items"][0]["candidateId"] == candidate
    assert http.get(
        "/api/release-publisher/v1/releases", headers=auth()
    ).json() == {"items": [], "nextCursor": None}
    invalid = http.get(
        "/api/release-publisher/v1/candidates?cursor=tampered", headers=auth()
    )
    assert invalid.status_code == 400


def test_post_validation_idempotency_and_polling_by_operation(
    client: tuple[TestClient, PublisherService, str]
) -> None:
    http, _, candidate = client
    headers = {
        **auth("publisher"),
        "Idempotency-Key": "key-123456789012",
        "Content-Type": "application/json",
    }
    first = http.post(
        "/api/release-publisher/v1/releases",
        headers=headers,
        json=payload(candidate),
    )
    assert first.status_code == 202
    assert first.headers["idempotency-replayed"] == "false"
    operation_id = first.json()["operationId"]
    replay = http.post(
        "/api/release-publisher/v1/releases",
        headers=headers,
        json=payload(candidate),
    )
    assert replay.status_code == 202
    assert replay.headers["idempotency-replayed"] == "true"
    assert replay.json()["operationId"] == operation_id
    assert http.get(
        f"/api/release-publisher/v1/operations/{operation_id}", headers=auth()
    ).json()["operationId"] == operation_id
    assert http.get(
        "/api/release-publisher/v1/operations/pub_invalid", headers=auth()
    ).status_code == 400


@pytest.mark.parametrize(
    ("headers", "body", "status"),
    [
        (auth("publisher"), "{}", 422),
        (
            {**auth("publisher"), "Content-Type": "text/plain",
             "Idempotency-Key": "key-123456789012"},
            "{}",
            422,
        ),
        (
            {**auth("publisher"), "Content-Type": "application/json",
             "Idempotency-Key": "short"},
            "{}",
            400,
        ),
    ],
)
def test_post_rejects_content_type_missing_fields_and_key(
    client: tuple[TestClient, PublisherService, str],
    headers: dict[str, str],
    body: str,
    status: int,
) -> None:
    http, _, _ = client
    response = http.post(
        "/api/release-publisher/v1/releases", headers=headers, content=body
    )
    assert response.status_code == status
    assert response.headers["content-type"].startswith("application/problem+json")
    assert set(response.json()) >= {"type", "title", "status", "code", "traceId"}


def test_extra_field_body_limit_and_cors(
    client: tuple[TestClient, PublisherService, str], settings: Settings
) -> None:
    http, _, candidate = client
    headers = {
        **auth("publisher"),
        "Idempotency-Key": "key-123456789012",
        "Content-Type": "application/json",
    }
    extra = payload(candidate)
    extra["repository"] = "evil/repo"
    assert http.post(
        "/api/release-publisher/v1/releases", headers=headers, json=extra
    ).status_code == 422
    assert http.post(
        "/api/release-publisher/v1/releases",
        headers=headers,
        content=b"x" * (settings.max_json_body_bytes + 1),
    ).status_code == 422
    preflight = http.options(
        "/api/release-publisher/v1/releases",
        headers={
            "Origin": settings.origin_list[0],
            "Access-Control-Request-Method": "POST",
            "Access-Control-Request-Headers": "authorization,content-type,idempotency-key",
        },
    )
    assert preflight.status_code == 200
    assert preflight.headers["access-control-allow-origin"] == settings.origin_list[0]
    denied = http.options(
        "/api/release-publisher/v1/releases",
        headers={
            "Origin": "http://evil.invalid:8084",
            "Access-Control-Request-Method": "POST",
        },
    )
    assert denied.status_code == 400


def test_readiness_after_both_green_syncs(
    client: tuple[TestClient, PublisherService, str],
    factory: sessionmaker[Session],
) -> None:
    http, _, _ = client
    with factory.begin() as session:
        for domain in ("candidates", "releases"):
            session.add(
                SyncState(
                    domain=domain,
                    last_success_at=datetime.now(UTC),
                    drift=False,
                )
            )
    assert http.get("/health/ready").json() == {"status": "ok"}


@pytest.mark.parametrize(
    "failure",
    [
        RuntimeFailure("GITHUB_RESPONSE_INVALID"),
        RemoteHttpFailure(503),
        RuntimeError("sensitive internal exception"),
    ],
)
def test_post_internal_failures_are_normalized_problem_details(
    client: tuple[TestClient, PublisherService, str],
    failure: Exception,
) -> None:
    http, service, candidate = client

    def fail(_candidate: str) -> None:
        raise failure

    service.revalidate_candidate = fail
    response = http.post(
        "/api/release-publisher/v1/releases",
        headers={
            **auth("publisher"),
            "Idempotency-Key": "internal-key-1234",
            "Content-Type": "application/json",
        },
        json=payload(candidate),
    )
    assert response.status_code == 500
    assert response.headers["content-type"].startswith("application/problem+json")
    assert response.json()["code"] == "INTERNAL_ERROR"
    assert set(response.json()) == {"type", "title", "status", "code", "traceId"}
    body = response.text
    assert "GITHUB" not in body
    assert "503" not in body
    assert "sensitive internal exception" not in body


@pytest.mark.parametrize(
    "path",
    [
        "/api/release-publisher/v1/candidates?limit=0",
        "/api/release-publisher/v1/releases?limit=invalid",
    ],
)
def test_get_query_validation_is_bad_request(
    client: tuple[TestClient, PublisherService, str],
    path: str,
) -> None:
    response = client[0].get(path, headers=auth())
    assert response.status_code == 400
    assert response.json()["code"] == "BAD_REQUEST"


def test_problem_details_public_code_enum_is_exact() -> None:
    from emporio_release_control.schemas import ProblemDetails

    code_schema = ProblemDetails.model_json_schema()["properties"]["code"]
    assert set(code_schema["enum"]) == {
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


@pytest.mark.parametrize(
    ("failure", "state"),
    [
        (PreDispatchFailure(), "FAILED"),
        (RemoteTransportFailure(uncertain=True), "REQUESTED"),
        (RemoteHttpFailure(403), "FAILED"),
        (None, "REQUESTED"),
    ],
)
def test_post_remains_accepted_after_persisted_dispatch_result(
    factory: sessionmaker[Session],
    settings: Settings,
    failure: Exception | None,
    state: str,
) -> None:
    candidate = seed(factory)
    github = FakeGitHub()

    def dispatch_publication(operation_id: str, request: dict[str, str]) -> None:
        github.dispatches.append((operation_id, request))
        if failure is not None:
            raise failure

    github.dispatch_publication = dispatch_publication  # type: ignore[method-assign]
    service = PublisherService(
        factory, cast(Any, github), b"p" * 32, 365, lambda _id: None
    )
    http = TestClient(
        create_app(settings, service, FakeVerifier()),  # type: ignore[arg-type]
        raise_server_exceptions=False,
    )
    response = http.post(
        "/api/release-publisher/v1/releases",
        headers={
            **auth("publisher"),
            "Idempotency-Key": "dispatch-key-1234",
            "Content-Type": "application/json",
        },
        json=payload(candidate),
    )
    assert response.status_code == 202
    assert response.json()["state"] == state
    assert len(github.dispatches) == 1
