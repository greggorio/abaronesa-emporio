"""carry the commit subject alongside each candidate

Revision ID: 0004_candidate_commit_subject
Revises: 0003_commercial_rollback
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0004_candidate_commit_subject"
down_revision: str | None = "0003_commercial_rollback"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column(
        "rc_candidate_snapshot",
        sa.Column("commit_subject", sa.String(length=200), nullable=True),
    )


def downgrade() -> None:
    op.drop_column("rc_candidate_snapshot", "commit_subject")
