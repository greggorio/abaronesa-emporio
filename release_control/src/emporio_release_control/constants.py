"""Immutable identities for the mutually exclusive runtime modes."""

PUBLISHER_MODE = "publisher"
DEPLOYER_MODE = "deployer"
REPOSITORY = "greggorio/abaronesa-emporio"
OWNER = "greggorio"
REPO = "abaronesa-emporio"
REF = "main"
PUBLISHER_WORKFLOW = "publish-release.yml"
DEPLOYER_WORKFLOW = "deploy-production.yml"
ROLLBACK_WORKFLOW = "rollback-production.yml"
GITHUB_API = "https://api.github.com"
USER_AGENT = "emporio-release-control/0.1"
PUBLISHER_ADVISORY_LOCK_ID = 7_331_504_215
DEPLOYER_ADVISORY_LOCK_ID = 7_331_504_216

PUBLICATION_STATES = frozenset(
    {"REQUESTED", "VALIDATING", "PUBLISHING", "PUBLISHED", "FAILED"}
)
TERMINAL_STATES = frozenset({"PUBLISHED", "FAILED"})
TRANSITIONS = frozenset(
    {
        ("REQUESTED", "VALIDATING"),
        ("VALIDATING", "PUBLISHING"),
        ("PUBLISHING", "PUBLISHED"),
        ("REQUESTED", "FAILED"),
        ("VALIDATING", "FAILED"),
        ("PUBLISHING", "FAILED"),
    }
)

ROLLBACK_STATES = (
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
)
DEPLOYMENT_STATES = frozenset(
    {
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
    }
)
DEPLOYMENT_TERMINAL_STATES = frozenset(
    {"SUCCEEDED", "ROLLED_BACK", "FAILED", "UNCERTAIN"}
)
ROLLBACK_TERMINAL_STATES = frozenset({"SUCCEEDED", "ROLLED_BACK", "FAILED", "UNCERTAIN"})
ROLLBACK_TRANSITIONS = frozenset(
    {
        ("QUEUED", "PRECHECKING"),
        ("PRECHECKING", "RESTORING"),
        ("PRECHECKING", "SWITCHING"),
        ("PRECHECKING", "FAILED"),
        ("PRECHECKING", "UNCERTAIN"),
        ("RESTORING", "SWITCHING"),
        ("RESTORING", "FAILED"),
        ("RESTORING", "ROLLING_BACK"),
        ("RESTORING", "UNCERTAIN"),
        ("SWITCHING", "VERIFYING"),
        ("SWITCHING", "ROLLING_BACK"),
        ("SWITCHING", "UNCERTAIN"),
        ("VERIFYING", "SUCCEEDED"),
        ("VERIFYING", "ROLLING_BACK"),
        ("VERIFYING", "UNCERTAIN"),
        ("ROLLING_BACK", "ROLLED_BACK"),
        ("ROLLING_BACK", "UNCERTAIN"),
    }
)
