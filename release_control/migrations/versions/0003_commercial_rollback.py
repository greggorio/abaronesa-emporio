"""activate the commercial rollback executor contract

Revision ID: 0003_commercial_rollback
Revises: 0002_deployer_runtime
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "0003_commercial_rollback"
down_revision: str | None = "0002_deployer_runtime"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

ROLLBACK_STATES = "'QUEUED','PRECHECKING','RESTORING','SWITCHING','VERIFYING','SUCCEEDED','ROLLING_BACK','ROLLED_BACK','FAILED','UNCERTAIN'"


def upgrade() -> None:
    op.add_column(
        "rc_deployment_operation",
        sa.Column("source_state_sha256", sa.String(71), nullable=True),
    )
    op.add_column("rc_deployment_operation", sa.Column("backup_id", sa.String(128)))
    op.add_column(
        "rc_deployment_operation",
        sa.Column("journal_json", postgresql.JSONB(), nullable=False, server_default="{}"),
    )
    op.add_column(
        "rc_deployment_operation",
        sa.Column("evidence_json", postgresql.JSONB(), nullable=False, server_default="{}"),
    )
    op.alter_column("rc_deployment_operation", "journal_json", server_default=None)
    op.alter_column("rc_deployment_operation", "evidence_json", server_default=None)
    op.add_column("rc_current_installation", sa.Column("state_sha256", sa.String(71)))

    op.create_table(
        "rc_rollback_backup",
        sa.Column("backup_id", sa.String(128), primary_key=True),
        sa.Column("source_release", sa.String(64), nullable=False),
        sa.Column("source_state_sha256", sa.String(71), nullable=False),
        sa.Column("databases", postgresql.JSONB(), nullable=False),
        sa.Column("artifact_sha256", sa.String(71), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("verified", sa.Boolean(), nullable=False),
        sa.Column("evidence_json", postgresql.JSONB(), nullable=False),
        sa.CheckConstraint("jsonb_typeof(databases) = 'array'", name="ck_rc_rollback_backup_databases_json"),
        sa.CheckConstraint("databases = '[\"erp\", \"website\"]'::jsonb", name="ck_rc_rollback_backup_databases"),
        sa.CheckConstraint("source_state_sha256 ~ '^sha256:[0-9a-f]{64}$'", name="ck_rc_rollback_backup_source_hash"),
        sa.CheckConstraint("artifact_sha256 ~ '^sha256:[0-9a-f]{64}$'", name="ck_rc_rollback_backup_artifact_hash"),
        sa.CheckConstraint("expires_at > created_at", name="ck_rc_rollback_backup_expiry"),
        sa.CheckConstraint("jsonb_typeof(evidence_json) = 'object'", name="ck_rc_rollback_backup_evidence_json"),
    )
    op.create_index("ix_rc_rollback_backup_source_release", "rc_rollback_backup", ["source_release"])
    op.create_index("ix_rc_rollback_backup_expires_at", "rc_rollback_backup", ["expires_at"])

    op.drop_constraint("ck_rc_deployment_state", "rc_deployment_operation", type_="check")
    op.create_check_constraint(
        "ck_rc_deployment_state",
        "rc_deployment_operation",
        f"state IN ({ROLLBACK_STATES})",
    )
    op.drop_constraint("ck_rc_deployment_lifecycle", "rc_deployment_operation", type_="check")
    op.create_check_constraint(
        "ck_rc_deployment_lifecycle",
        "rc_deployment_operation",
        "((state IN ('QUEUED','PRECHECKING','RESTORING','SWITCHING','VERIFYING','ROLLING_BACK') AND COALESCE(active_slot = 1, false) AND finished_at IS NULL) OR (state IN ('SUCCEEDED','ROLLED_BACK','FAILED','UNCERTAIN') AND active_slot IS NULL AND finished_at IS NOT NULL))",
    )


def downgrade() -> None:
    op.drop_constraint("ck_rc_deployment_lifecycle", "rc_deployment_operation", type_="check")
    op.drop_constraint("ck_rc_deployment_state", "rc_deployment_operation", type_="check")
    op.create_check_constraint(
        "ck_rc_deployment_state",
        "rc_deployment_operation",
        "state IN ('QUEUED','SUCCEEDED','ROLLED_BACK','FAILED')",
    )
    op.create_check_constraint(
        "ck_rc_deployment_lifecycle",
        "rc_deployment_operation",
        "((state = 'QUEUED' AND COALESCE(active_slot = 1, false) AND finished_at IS NULL) OR (state IN ('SUCCEEDED','ROLLED_BACK','FAILED') AND active_slot IS NULL AND finished_at IS NOT NULL))",
    )
    op.drop_index("ix_rc_rollback_backup_expires_at", table_name="rc_rollback_backup")
    op.drop_index("ix_rc_rollback_backup_source_release", table_name="rc_rollback_backup")
    op.drop_table("rc_rollback_backup")
    op.drop_column("rc_current_installation", "state_sha256")
    op.drop_column("rc_deployment_operation", "evidence_json")
    op.drop_column("rc_deployment_operation", "journal_json")
    op.drop_column("rc_deployment_operation", "backup_id")
    op.drop_column("rc_deployment_operation", "source_state_sha256")
