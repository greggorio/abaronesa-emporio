"""Public API models matching the publisher OpenAPI."""

from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)


class PublishReleaseRequest(StrictModel):
    candidate_id: str = Field(alias="candidateId", min_length=12, max_length=128)
    version_bump: Literal["MAJOR", "MINOR", "PATCH"] = Field(alias="versionBump")
    description: str = Field(min_length=1, max_length=500)
    changelog: str = Field(min_length=1, max_length=10000)

    def canonical_dict(self) -> dict[str, str]:
        return {
            "candidateId": self.candidate_id,
            "versionBump": self.version_bump,
            "description": self.description,
            "changelog": self.changelog,
        }


class PublicationOperationResponse(StrictModel):
    operation_id: str = Field(alias="operationId")
    state: Literal["REQUESTED", "VALIDATING", "PUBLISHING", "PUBLISHED", "FAILED"]
    candidate_id: str = Field(alias="candidateId")
    release: str | None = None
    workflow_run_url: str | None = Field(default=None, alias="workflowRunUrl")
    created_at: datetime = Field(alias="createdAt")
    updated_at: datetime = Field(alias="updatedAt")
    error_code: str | None = Field(default=None, alias="errorCode")


class CandidateSummary(StrictModel):
    candidate_id: str = Field(alias="candidateId")
    source_commit: str = Field(alias="sourceCommit")
    eligibility: Literal["NOT_ELIGIBLE", "READY"]
    ci_status: Literal["PENDING", "PASSED", "FAILED"] = Field(alias="ciStatus")
    manifest_status: Literal["PENDING", "VALID", "INVALID"] = Field(alias="manifestStatus")
    created_at: datetime = Field(alias="createdAt")
    # Sempre presente na resposta, nulo quando a mensagem do commit nao pode ser
    # obtida: assim o cliente tem um formato unico para validar.
    commit_subject: str | None = Field(alias="commitSubject", default=None)


class ReleaseSummary(StrictModel):
    release: str
    source_commit: str = Field(alias="sourceCommit")
    state: Literal["PUBLISHED"]
    published_at: datetime = Field(alias="publishedAt")


class CandidatePage(StrictModel):
    items: list[CandidateSummary]
    next_cursor: str | None = Field(default=None, alias="nextCursor")


class ReleasePage(StrictModel):
    items: list[ReleaseSummary]
    next_cursor: str | None = Field(default=None, alias="nextCursor")


PublicProblemCode = Literal[
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
]


class ProblemDetails(StrictModel):
    type: str
    title: str
    status: int
    code: PublicProblemCode
    detail: str | None = None
    trace_id: str = Field(alias="traceId")
