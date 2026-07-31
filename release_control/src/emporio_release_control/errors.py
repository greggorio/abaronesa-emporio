"""Stable sanitized runtime errors."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Final


@dataclass(slots=True)
class RuntimeFailure(Exception):
    code: str
    status: int = 500
    title: str = "Request failed"

    def __str__(self) -> str:
        return self.code


class RemoteHttpFailure(RuntimeFailure):
    def __init__(self, status: int) -> None:
        super().__init__("GITHUB_HTTP_ERROR", 502, "Remote request failed")
        self.remote_status = status


class RemoteTransportFailure(RuntimeFailure):
    def __init__(self, uncertain: bool = False) -> None:
        code = "WORKFLOW_DISPATCH_UNCERTAIN" if uncertain else "GITHUB_TRANSPORT_FAILED"
        super().__init__(code, 502, "Remote request failed")
        self.uncertain = uncertain


class PreDispatchFailure(RuntimeFailure):
    def __init__(self) -> None:
        super().__init__("WORKFLOW_DISPATCH_NOT_SENT")


PUBLIC_FAILURES: Final[dict[tuple[int, str], tuple[str, int, str]]] = {
    (400, "BAD_REQUEST"): ("BAD_REQUEST", 400, "Bad request"),
    (401, "UNAUTHORIZED"): ("UNAUTHORIZED", 401, "Unauthorized"),
    (403, "FORBIDDEN"): ("FORBIDDEN", 403, "Forbidden"),
    (404, "NOT_FOUND"): ("NOT_FOUND", 404, "Not found"),
    (409, "IDEMPOTENCY_CONFLICT"): ("IDEMPOTENCY_CONFLICT", 409, "Conflict"),
    (409, "VERSION_RESERVATION_CONFLICT"): (
        "VERSION_RESERVATION_CONFLICT",
        409,
        "Conflict",
    ),
    (422, "UNPROCESSABLE"): ("UNPROCESSABLE", 422, "Unprocessable"),
    (429, "RATE_LIMITED"): ("RATE_LIMITED", 429, "Too many requests"),
}


def normalize_public_failure(failure: RuntimeFailure) -> RuntimeFailure:
    public = PUBLIC_FAILURES.get((failure.status, failure.code))
    if public is None:
        return RuntimeFailure("INTERNAL_ERROR", 500, "Internal server error")
    code, status, title = public
    return RuntimeFailure(code, status, title)

