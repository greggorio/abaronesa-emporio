"""deployer runtime persistence

Revision ID: 0002_deployer_runtime
Revises: 0001_publisher_runtime
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "0002_deployer_runtime"
down_revision: str | None = "0001_publisher_runtime"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "rc_deployment_operation",
        sa.Column("operation_id", sa.String(36), primary_key=True),
        sa.Column("operation_type", sa.String(20), nullable=False),
        sa.Column("mode", sa.String(20), nullable=False),
        sa.Column("state", sa.String(20), nullable=False),
        sa.Column("actor_sub", sa.String(255), nullable=False),
        sa.Column("scopes", postgresql.JSONB(), nullable=False),
        sa.Column("target_release", sa.String(64), nullable=False),
        sa.Column("source_release", sa.String(64)),
        sa.Column("rollback_reason", sa.String(1000)),
        sa.Column("request_json", postgresql.JSONB(), nullable=False),
        sa.Column("request_hash", sa.CHAR(64), nullable=False),
        sa.Column("idempotency_hash", sa.CHAR(64), nullable=False),
        sa.Column("workflow_run_id", sa.BigInteger()),
        sa.Column("workflow_attempt", sa.Integer()),
        sa.Column("workflow_run_url", sa.String(512)),
        sa.Column("control_sha", sa.CHAR(40)),
        sa.Column("dispatch_state", sa.String(20), nullable=False),
        sa.Column("remote_state", sa.String(30)),
        sa.Column("transport_status", sa.String(20)),
        sa.Column("database_restore_required", sa.Boolean()),
        sa.Column("outcome_sha256", sa.String(71)),
        sa.Column("error_code", sa.String(100)),
        sa.Column("error_message", sa.String(300)),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("finished_at", sa.DateTime(timezone=True)),
        sa.Column("active_slot", sa.Integer()),
        sa.Column("version", sa.Integer(), nullable=False),
        sa.CheckConstraint(
            "(operation_type = 'deployment' AND operation_id ~ '^dep_[0-9a-f]{32}$') "
            "OR (operation_type = 'rollback' AND operation_id ~ '^rbk_[0-9a-f]{32}$')",
            name="ck_rc_deployment_operation_id_type",
        ),
        sa.CheckConstraint("mode = 'deployer'", name="ck_rc_deployment_mode"),
        sa.CheckConstraint(
            "state IN ('QUEUED','SUCCEEDED','ROLLED_BACK','FAILED')",
            name="ck_rc_deployment_state",
        ),
        sa.CheckConstraint(
            "dispatch_state IN ('NOT_SENT','SENT','UNCERTAIN','CONFIRMED')",
            name="ck_rc_deployment_dispatch_state",
        ),
        sa.CheckConstraint(
            "transport_status IS NULL OR transport_status IN ('CONFIRMED','INDETERMINATE')",
            name="ck_rc_deployment_transport_status",
        ),
        sa.CheckConstraint(
            "active_slot IS NULL OR active_slot = 1", name="ck_rc_deployment_active_slot"
        ),
        sa.CheckConstraint(
            "workflow_run_id IS NULL OR workflow_run_id > 0",
            name="ck_rc_deployment_workflow_run_id",
        ),
        sa.CheckConstraint(
            "workflow_attempt IS NULL OR workflow_attempt > 0",
            name="ck_rc_deployment_workflow_attempt",
        ),
        sa.CheckConstraint(
            "((dispatch_state = 'CONFIRMED' "
            "AND workflow_run_id IS NOT NULL AND workflow_attempt IS NOT NULL "
            "AND workflow_run_url IS NOT NULL AND control_sha IS NOT NULL) OR "
            "(dispatch_state IN ('NOT_SENT','SENT','UNCERTAIN') "
            "AND workflow_run_id IS NULL AND workflow_attempt IS NULL "
            "AND workflow_run_url IS NULL AND control_sha IS NULL))",
            name="ck_rc_deployment_workflow_binding",
        ),
        sa.CheckConstraint(
            "workflow_run_url IS NULL OR workflow_run_url ~ "
            "'^https://github\\.com/greggorio/abaronesa-emporio/actions/runs/[1-9][0-9]*$'",
            name="ck_rc_deployment_workflow_run_url",
        ),
        sa.CheckConstraint(
            "target_release ~ '^v(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$'",
            name="ck_rc_deployment_target_release",
        ),
        sa.CheckConstraint(
            "source_release IS NULL OR source_release ~ "
            "'^v(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$'",
            name="ck_rc_deployment_source_release",
        ),
        sa.CheckConstraint(
            "request_hash ~ '^[0-9a-f]{64}$' AND idempotency_hash ~ '^[0-9a-f]{64}$'",
            name="ck_rc_deployment_hashes",
        ),
        sa.CheckConstraint(
            "control_sha IS NULL OR control_sha ~ '^[0-9a-f]{40}$'",
            name="ck_rc_deployment_control_sha",
        ),
        sa.CheckConstraint(
            "outcome_sha256 IS NULL OR outcome_sha256 ~ '^sha256:[0-9a-f]{64}$'",
            name="ck_rc_deployment_outcome_digest",
        ),
        sa.CheckConstraint(
            "jsonb_typeof(scopes) = 'array' AND jsonb_typeof(request_json) = 'object'",
            name="ck_rc_deployment_json_shapes",
        ),
        sa.CheckConstraint(
            "(state = 'QUEUED' AND COALESCE(active_slot = 1, false) AND finished_at IS NULL) OR "
            "(state IN ('SUCCEEDED','ROLLED_BACK','FAILED') AND active_slot IS NULL "
            "AND finished_at IS NOT NULL)",
            name="ck_rc_deployment_lifecycle",
        ),
        sa.CheckConstraint("version > 0", name="ck_rc_deployment_version"),
    )
    op.create_index(
        "ix_rc_deployment_operation_state", "rc_deployment_operation", ["state"]
    )
    op.create_index(
        "ix_rc_deployment_operation_target_release",
        "rc_deployment_operation",
        ["target_release"],
    )
    op.create_index(
        "uq_rc_deployment_active_slot",
        "rc_deployment_operation",
        ["active_slot"],
        unique=True,
        postgresql_where=sa.text("active_slot = 1"),
    )

    op.create_table(
        "rc_deployment_idempotency_key",
        sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=True),
        sa.Column("mode", sa.String(20), nullable=False),
        sa.Column("route", sa.String(160), nullable=False),
        sa.Column("actor_sub", sa.String(255), nullable=False),
        sa.Column("key_hmac", sa.CHAR(64), nullable=False),
        sa.Column("request_hash", sa.CHAR(64), nullable=False),
        sa.Column(
            "operation_id",
            sa.String(36),
            sa.ForeignKey("rc_deployment_operation.operation_id", ondelete="RESTRICT"),
            nullable=False,
        ),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.UniqueConstraint(
            "mode",
            "route",
            "actor_sub",
            "key_hmac",
            name="uq_rc_deployment_idempotency_scope",
        ),
        sa.CheckConstraint("mode = 'deployer'", name="ck_rc_deployment_idempotency_mode"),
        sa.CheckConstraint(
            "key_hmac ~ '^[0-9a-f]{64}$' AND request_hash ~ '^[0-9a-f]{64}$'",
            name="ck_rc_deployment_idempotency_hashes",
        ),
    )

    op.create_table(
        "rc_current_installation",
        sa.Column("singleton_id", sa.Integer(), primary_key=True),
        sa.Column("release", sa.String(64)),
        sa.Column("source_commit", sa.CHAR(40)),
        sa.Column("previous_release", sa.String(64)),
        sa.Column("installed_at", sa.DateTime(timezone=True)),
        sa.Column("reconciled", sa.Boolean(), nullable=False),
        sa.Column("uncertainty_code", sa.String(100)),
        sa.Column("last_operation_id", sa.String(36)),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("version", sa.Integer(), nullable=False),
        sa.CheckConstraint("singleton_id = 1", name="ck_rc_current_installation_singleton"),
        sa.CheckConstraint(
            "NOT reconciled OR (release IS NOT NULL AND source_commit IS NOT NULL "
            "AND installed_at IS NOT NULL AND last_operation_id IS NOT NULL "
            "AND uncertainty_code IS NULL)",
            name="ck_rc_current_installation_reconciled",
        ),
        sa.CheckConstraint(
            "release IS NULL OR release ~ '^v(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$'",
            name="ck_rc_current_installation_release",
        ),
        sa.CheckConstraint(
            "previous_release IS NULL OR previous_release ~ "
            "'^v(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$'",
            name="ck_rc_current_installation_previous_release",
        ),
        sa.CheckConstraint(
            "source_commit IS NULL OR source_commit ~ '^[0-9a-f]{40}$'",
            name="ck_rc_current_installation_source_commit",
        ),
        sa.CheckConstraint("version > 0", name="ck_rc_current_installation_version"),
    )


def downgrade() -> None:
    op.drop_table("rc_current_installation")
    op.drop_table("rc_deployment_idempotency_key")
    op.drop_table("rc_deployment_operation")
