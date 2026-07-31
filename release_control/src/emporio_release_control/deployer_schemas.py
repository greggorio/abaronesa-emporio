"""Strict public API models for the mutually exclusive deployer runtime."""

from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator

RELEASE_PATTERN = r"^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$"
DEPLOYMENT_ID_PATTERN = r"^dep_[0-9a-f]{32}$"
DIGEST_PATTERN = r"^sha256:[0-9a-f]{64}$"
COMPONENTS = (
    "backend",
    "website_back",
    "frontend",
    "website_front",
    "whatsapp_service",
    "gateway",
)


class StrictDeployerModel(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)


DeployerProblemCode = Literal[
    "BAD_REQUEST",
    "UNAUTHORIZED",
    "FORBIDDEN",
    "NOT_FOUND",
    "CURRENT_INSTALLATION_UNRECONCILED",
    "IDEMPOTENCY_CONFLICT",
    "PRODUCTION_OPERATION_ACTIVE",
    "RELEASE_NOT_ELIGIBLE",
    "UNPROCESSABLE",
    "RATE_LIMITED",
    "INTERNAL_ERROR",
    "SERVICE_UNAVAILABLE",
]


class DeployerProblemDetails(StrictDeployerModel):
    type: str
    title: str
    status: int
    code: DeployerProblemCode
    detail: str | None = None
    trace_id: str = Field(alias="traceId")


class DeploymentRequest(StrictDeployerModel):
    release: str = Field(pattern=RELEASE_PATTERN, max_length=64)

    def canonical_dict(self) -> dict[str, str]:
        return {"release": self.release}


class RollbackRequest(StrictDeployerModel):
    release: str = Field(pattern=RELEASE_PATTERN, max_length=64)
    reason: str = Field(min_length=10, max_length=1000)

    def canonical_dict(self) -> dict[str, str]:
        return {"release": self.release, "reason": self.reason}


class DeploymentOperationResponse(StrictDeployerModel):
    operation_id: str = Field(alias="operationId", pattern=r"^(dep|rbk)_[0-9a-f]{32}$")
    operation_type: Literal["deployment", "rollback"] = Field(alias="operationType")
    state: Literal[
        "QUEUED",
        "PRECHECKING",
        "RESTORING",
        "SWITCHING",
        "VERIFYING",
        "SUCCEEDED",
        "ROLLING_BACK",
        "ROLLED_BACK",
        "FAILED",
        "UNCERTAIN",
    ]
    target_release: str = Field(alias="targetRelease", pattern=RELEASE_PATTERN)
    workflow_run_url: str | None = Field(default=None, alias="workflowRunUrl")
    created_at: datetime = Field(alias="createdAt")
    updated_at: datetime = Field(alias="updatedAt")
    error_code: str | None = Field(default=None, alias="errorCode", max_length=100)


class RollbackOperationResponse(StrictDeployerModel):
    operation_id: str = Field(alias="operationId", pattern=r"^rbk_[0-9a-f]{32}$")
    operation_type: Literal["rollback"] = Field(alias="operationType")
    state: Literal[
        "QUEUED",
        "PRECHECKING",
        "RESTORING",
        "SWITCHING",
        "VERIFYING",
        "SUCCEEDED",
        "ROLLING_BACK",
        "ROLLED_BACK",
        "FAILED",
        "UNCERTAIN",
    ]
    source_release: str = Field(alias="sourceRelease", pattern=RELEASE_PATTERN)
    target_release: str = Field(alias="targetRelease", pattern=RELEASE_PATTERN)
    database_restore_required: bool = Field(alias="databaseRestoreRequired")
    workflow_run_url: str | None = Field(default=None, alias="workflowRunUrl")
    created_at: datetime = Field(alias="createdAt")
    updated_at: datetime = Field(alias="updatedAt")
    error_code: str | None = Field(default=None, alias="errorCode", max_length=100)


class CurrentInstallationResponse(StrictDeployerModel):
    release: str = Field(pattern=RELEASE_PATTERN)
    source_commit: str = Field(alias="sourceCommit", pattern=r"^[0-9a-f]{40}$")
    installed_at: datetime = Field(alias="installedAt")
    reconciled: Literal[True]


class DeployableReleaseSummary(StrictDeployerModel):
    release: str = Field(pattern=RELEASE_PATTERN)
    source_commit: str = Field(alias="sourceCommit", pattern=r"^[0-9a-f]{40}$")
    published_at: datetime = Field(alias="publishedAt")
    eligible: bool


class DeployableReleasePage(StrictDeployerModel):
    items: list[DeployableReleaseSummary] = Field(max_length=100)
    next_cursor: str | None = Field(default=None, alias="nextCursor", max_length=256)


class ComponentPlanItem(StrictDeployerModel):
    component: Literal[
        "backend",
        "website_back",
        "frontend",
        "website_front",
        "whatsapp_service",
        "gateway",
    ]
    action: Literal["KEEP", "UPDATE"]
    current_digest: str | None = Field(
        default=None, alias="currentDigest", pattern=DIGEST_PATTERN
    )
    target_digest: str = Field(alias="targetDigest", pattern=DIGEST_PATTERN)


class DeploymentPlanResponse(StrictDeployerModel):
    source_release: str | None = Field(default=None, alias="sourceRelease")
    target_release: str = Field(alias="targetRelease", pattern=RELEASE_PATTERN)
    components: list[ComponentPlanItem] = Field(min_length=6, max_length=6)
    migration_required: bool = Field(alias="migrationRequired")
    backup_required: bool = Field(alias="backupRequired")

    @model_validator(mode="after")
    def validate_contract(self) -> DeploymentPlanResponse:
        if self.source_release is not None:
            # Validate an optional value without weakening the field's nullability.
            DeploymentRequest(release=self.source_release)
        if tuple(item.component for item in self.components) != COMPONENTS:
            raise ValueError("components must use canonical order")
        if self.backup_required != self.migration_required:
            raise ValueError("backupRequired must equal migrationRequired")
        return self


class DeployerCapabilitiesResponse(StrictDeployerModel):
    api_version: Literal["v1"] = Field(alias="apiVersion")
    capabilities: tuple[
        Literal["deployment:read", "deployment:execute", "deployment:rollback"], ...
    ]
    mode: Literal["deployer"]

    @model_validator(mode="after")
    def validate_capability_order(self) -> DeployerCapabilitiesResponse:
        if self.capabilities != (
            "deployment:read",
            "deployment:execute",
            "deployment:rollback",
        ):
            raise ValueError("capabilities must match the deployer version")
        return self


# Public names mirror the deployer OpenAPI vocabulary used by the router/service.
GlobalReleaseSummary = DeployableReleaseSummary
GlobalReleasePage = DeployableReleasePage
DeploymentPlan = DeploymentPlanResponse
