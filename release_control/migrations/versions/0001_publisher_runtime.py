"""publisher runtime persistence

Revision ID: 0001_publisher_runtime
Revises:
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "0001_publisher_runtime"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "rc_publication_operation",
        sa.Column("operation_id", sa.String(36), primary_key=True),
        sa.Column("operation_type", sa.String(20), nullable=False),
        sa.Column("mode", sa.String(20), nullable=False),
        sa.Column("state", sa.String(20), nullable=False),
        sa.Column("actor_sub", sa.String(255), nullable=False),
        sa.Column("scopes", postgresql.JSONB(), nullable=False),
        sa.Column("candidate_id", sa.String(128), nullable=False),
        sa.Column("request_json", postgresql.JSONB(), nullable=False),
        sa.Column("request_hash", sa.String(64), nullable=False),
        sa.Column("idempotency_hash", sa.String(64), nullable=False),
        sa.Column("target_release", sa.String(64)),
        sa.Column("source_commit", sa.String(40)),
        sa.Column("workflow_run_id", sa.BigInteger()),
        sa.Column("workflow_attempt", sa.Integer()),
        sa.Column("workflow_run_url", sa.String(512)),
        sa.Column("remote_state", sa.String(30)),
        sa.Column("dispatch_state", sa.String(20), nullable=False),
        sa.Column("error_code", sa.String(100)),
        sa.Column("error_message", sa.String(300)),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("finished_at", sa.DateTime(timezone=True)),
        sa.Column("active_slot", sa.Integer()),
        sa.Column("version", sa.Integer(), nullable=False),
    )
    op.create_index("ix_rc_publication_operation_state", "rc_publication_operation", ["state"])
    op.create_index(
        "ix_rc_publication_operation_candidate_id", "rc_publication_operation", ["candidate_id"]
    )
    op.create_index(
        "uq_rc_publication_active_slot",
        "rc_publication_operation",
        ["active_slot"],
        unique=True,
        postgresql_where=sa.text("active_slot = 1"),
    )
    op.create_table(
        "rc_idempotency_key",
        sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=True),
        sa.Column("mode", sa.String(20), nullable=False),
        sa.Column("route", sa.String(160), nullable=False),
        sa.Column("actor_sub", sa.String(255), nullable=False),
        sa.Column("key_hmac", sa.String(64), nullable=False),
        sa.Column("request_hash", sa.String(64), nullable=False),
        sa.Column(
            "operation_id",
            sa.String(36),
            sa.ForeignKey("rc_publication_operation.operation_id", ondelete="RESTRICT"),
            nullable=False,
        ),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.UniqueConstraint(
            "mode", "route", "actor_sub", "key_hmac", name="uq_rc_idempotency_scope"
        ),
    )
    op.create_table(
        "rc_candidate_snapshot",
        sa.Column("candidate_id", sa.String(128), primary_key=True),
        sa.Column("source_commit", sa.String(40), nullable=False),
        sa.Column("eligibility", sa.String(20), nullable=False),
        sa.Column("ci_status", sa.String(20), nullable=False),
        sa.Column("manifest_status", sa.String(20), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("manifest", postgresql.JSONB(), nullable=False),
        sa.Column("artifact_id", sa.BigInteger(), nullable=False),
        sa.Column("artifact_digest", sa.String(71), nullable=False),
        sa.Column("synchronized_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index(
        "ix_rc_candidate_snapshot_eligibility", "rc_candidate_snapshot", ["eligibility"]
    )
    op.create_index(
        "ix_rc_candidate_snapshot_created_at", "rc_candidate_snapshot", ["created_at"]
    )
    op.create_table(
        "rc_release_snapshot",
        sa.Column("release", sa.String(64), primary_key=True),
        sa.Column("source_commit", sa.String(40), nullable=False),
        sa.Column("state", sa.String(20), nullable=False),
        sa.Column("published_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("candidate_id", sa.String(128), nullable=False, unique=True),
        sa.Column("manifest", postgresql.JSONB(), nullable=False),
        sa.Column("synchronized_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index(
        "ix_rc_release_snapshot_published_at", "rc_release_snapshot", ["published_at"]
    )
    op.create_table(
        "rc_audit_event",
        sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=True),
        sa.Column("trace_id", sa.String(128), nullable=False),
        sa.Column("actor_sub", sa.String(255)),
        sa.Column("action", sa.String(100), nullable=False),
        sa.Column("result", sa.String(30), nullable=False),
        sa.Column("operation_id", sa.String(36)),
        sa.Column("metadata_json", postgresql.JSONB(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_rc_audit_event_trace_id", "rc_audit_event", ["trace_id"])
    op.create_index("ix_rc_audit_event_operation_id", "rc_audit_event", ["operation_id"])
    op.create_table(
        "rc_sync_state",
        sa.Column("domain", sa.String(20), primary_key=True),
        sa.Column("last_success_at", sa.DateTime(timezone=True)),
        sa.Column("drift", sa.Boolean(), nullable=False),
        sa.Column("error_code", sa.String(100)),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.execute(
        """
        CREATE FUNCTION rc_audit_append_only() RETURNS trigger AS $$
        BEGIN
          RAISE EXCEPTION 'rc_audit_event is append-only';
        END;
        $$ LANGUAGE plpgsql
        """
    )
    op.execute(
        """
        CREATE TRIGGER trg_rc_audit_append_only
        BEFORE UPDATE OR DELETE ON rc_audit_event
        FOR EACH ROW EXECUTE FUNCTION rc_audit_append_only()
        """
    )


def downgrade() -> None:
    op.drop_table("rc_sync_state")
    op.execute("DROP FUNCTION rc_audit_append_only()")
    op.drop_table("rc_audit_event")
    op.drop_table("rc_release_snapshot")
    op.drop_table("rc_candidate_snapshot")
    op.drop_table("rc_idempotency_key")
    op.drop_table("rc_publication_operation")
