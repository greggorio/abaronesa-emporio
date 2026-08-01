from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[3]


def load(name):
    path = ROOT / f"tools/ci/{name}.py"
    spec = importlib.util.spec_from_file_location(f"ci_{name}", path)
    module = importlib.util.module_from_spec(spec)
    assert spec and spec.loader
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


ci = load("validate_ci")
migrations = load("migrations_contract")
secrets = load("secret_scan")
changes = load("resolve_changes")


class CIContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = ci.WORKFLOW.read_text()

    def test_01_real_workflow_is_valid(self):
        self.assertEqual([], ci.validate(self.workflow))

    def test_02_independent_workflow_mutants(self):
        mutants = (
            ("extra trigger", "  push:\n    branches: [main]", "  push:\n    branches: [main]\n  workflow_dispatch:"),
            ("write permission", "contents: read", "contents: write"),
            ("action tag", "actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd", "actions/checkout@v6"),
            ("self hosted", "runs-on: ubuntu-24.04", "runs-on: self-hosted"),
            ("shallow checkout", "fetch-depth: 0", "fetch-depth: 1"),
            ("missing job", "  whatsapp:\n", "  whatsapp_removed:\n"),
            ("missing command", "node --check app.js", "node app.js"),
            ("continue on error", "timeout-minutes: 10", "timeout-minutes: 10\n    continue-on-error: true"),
            ("registry login", "python3 tools/ci/validate_ci.py", "docker login ghcr.io\n          python3 tools/ci/validate_ci.py"),
            ("build push", "push: false", "push: true"),
            ("missing scan", "aquasecurity/trivy-action@", "aquasecurity/removed-action@"),
            ("wrong platform", "platforms: linux/amd64", "platforms: linux/arm64"),
            ("component omitted", "          - component: gateway", "          - component: gateway_removed"),
            ("dockerfile drift", "dockerfile: backend/Dockerfile", "dockerfile: Dockerfile"),
        )
        for label, old, new in mutants:
            with self.subTest(label=label):
                self.assertTrue(ci.validate(self.workflow.replace(old, new, 1)))

    def test_02b_backend_database_service_mutants(self):
        """S30 correction-01 D: the backend integration suite needs PostgreSQL."""
        self.assertEqual([], ci.validate(self.workflow))
        mutants = (
            (
                "service removed",
                "    services:\n      postgres:\n        image: postgres:16.6-alpine\n",
                "    steps_placeholder:\n",
            ),
            ("image drift", "image: postgres:16.6-alpine", "image: postgres:15-alpine"),
            ("floating image", "image: postgres:16.6-alpine", "image: postgres:latest"),
            ("database drift", "POSTGRES_DB: testdb", "POSTGRES_DB: otherdb"),
            ("user drift", "POSTGRES_USER: test", "POSTGRES_USER: postgres"),
            ("password drift", "POSTGRES_PASSWORD: test", "POSTGRES_PASSWORD: other"),
            ("port removed", '          - "5432:5432"\n', ""),
            ("port drift", '- "5432:5432"', '- "5433:5432"'),
            (
                "healthcheck removed",
                '          --health-cmd "pg_isready -U test -d testdb"\n',
                "",
            ),
            (
                "healthcheck target drift",
                'pg_isready -U test -d testdb',
                'pg_isready -U postgres -d postgres',
            ),
            ("interval removed", "          --health-interval 10s\n", ""),
            ("timeout removed", "          --health-timeout 5s\n", ""),
            ("retries removed", "          --health-retries 10\n", ""),
        )
        for label, old, new in mutants:
            with self.subTest(label=label):
                mutated = self.workflow.replace(old, new, 1)
                self.assertNotEqual(self.workflow, mutated, f"anchor absent: {label}")
                self.assertTrue(ci.validate(mutated), f"mutant survived: {label}")

    def test_02d_release_control_contract_requires_subcommand(self):
        """S30 correction-03 G: the bare call exits 2, so the contract is the exact form."""
        self.assertEqual([], ci.validate(self.workflow))
        self.assertIn("python3 tools/releases/release_control_contract.py validate", ci.REQUIRED_COMMANDS)
        bare = "python3 tools/releases/release_control_contract.py"
        self.assertIn("          " + bare + " validate\n", self.workflow)
        mutated = self.workflow.replace(bare + " validate", bare, 1)
        self.assertNotEqual(self.workflow, mutated)
        errors = ci.validate(mutated)
        self.assertTrue(errors)
        self.assertIn("release control contract without subcommand", errors)
        # The check must be positional, not a substring that accepts both forms.
        self.assertNotIn(bare + "\n", self.workflow)

    def test_02c_backend_database_env_is_synthetic(self):
        """The fixture credentials must stay synthetic and never become a secret."""
        self.assertEqual(
            {"POSTGRES_DB": "testdb", "POSTGRES_USER": "test", "POSTGRES_PASSWORD": "test"},
            ci.POSTGRES_ENV,
        )
        self.assertEqual(
            [],
            secrets.findings_for("ci.yml", self.workflow.encode("utf-8")),
        )

    def test_03_migration_positive_and_mutants(self):
        with tempfile.TemporaryDirectory() as raw:
            first = Path(raw) / "first"; second = Path(raw) / "second"
            first.mkdir(); second.mkdir()
            (first / "V1__init.sql").write_text("select 1;")
            (second / "V2__next.sql").write_text("select 2;")
            self.assertEqual([], migrations.validate((first, second)))
            (first / "invalid.sql").write_text("select 3;")
            self.assertTrue(migrations.validate((first, second)))
            (first / "V01__duplicate.sql").write_text("select 4;")
            self.assertTrue(any("DUPLICATE" in item for item in migrations.validate((first, second))))

    def test_04_secret_detector_positive_and_mutants(self):
        clean = (
            b"PASSWORD=replace-with-secret\n"
            b"HASH_PEPPER=__SET_IN_PROTECTED_ENV_FILE__\n"
        )
        self.assertEqual([], secrets.findings_for("fixture.env", clean))
        for value in (
            b"-----BEGIN " + b"PRIVATE KEY-----\n",
            b"GITHUB_TOKEN=ghp_abcdefghijklmnopqrstuvwxyz123456\\n",
            b"GOOGLE_API_KEY=AIzaabcdefghijklmnopqrstuvwxyz123456789\\n",
            b"UBER_CLIENT_SECRET=FictitiousMutantValue987654321\\n",
        ):
            with self.subTest(value=value[:12]):
                findings = secrets.findings_for("fixture.env", value)
                self.assertTrue(findings)
                self.assertNotIn(value.decode().strip(), "\\n".join(findings))

    def test_05_yaml_assignment_does_not_cross_newline(self):
        content = b"secrets:\n  external_key: FictitiousYamlValue987654321\n"
        self.assertEqual([], secrets.findings_for("compose.yaml", content))

    def test_06_only_explicit_workspace_placeholders_are_clean(self):
        for marker in (b"replace-with", b"__SET_", b"from-secret-manager"):
            with self.subTest(marker=marker):
                findings = secrets.findings_for(
                    "settings.env", b"PASSWORD=" + marker + b"-value\n"
                )
                self.assertEqual([], findings)

    def test_07_non_placeholder_assignment_remains_detected(self):
        value = b"FictitiousMutantValue987654321"
        findings = secrets.findings_for("settings.env", b"PASSWORD=" + value + b"\n")
        self.assertTrue(any(item.startswith("SENSITIVE_ASSIGNMENT:") for item in findings))
        self.assertNotIn(value.decode(), "\n".join(findings))

    def test_08_private_key_allowlist_requires_exact_rule_path_fingerprint(self):
        expected = {
            "PRIVATE_KEY:backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/ReleaseControlIdentityConfiguration.java:645a3fd1fbded7d1",
            "PRIVATE_KEY:backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/deployer/DeployerReleaseControlIdentityConfiguration.java:45b857fbbf8f5cff",
            "PRIVATE_KEY:backend/src/test/java/com/baronesa/emporio/releasecontrol/identity/ReleaseControlIdentityContractTest.java:adf9d660c6ecea59",
            "PRIVATE_KEY:backend/src/test/java/com/baronesa/emporio/releasecontrol/identity/ReleaseControlIdentityContractTest.java:ece92808270e9fba",
            "PRIVATE_KEY:docs/infrastructure/deployment/implementation/slices/S16-ponte-identidade-rs256-jwks-perfil-local-publisher.task.md:697fe882c1911841",
            "PRIVATE_KEY:tools/releases/validate_publisher_identity_bridge.py:d5c0e5f19884c2e6",
        }
        allowlisted = {
            item for item in secrets.allowlisted_findings()
            if item.startswith("PRIVATE_KEY:")
        }
        self.assertEqual(expected, allowlisted)
        for item in expected:
            rule, path, digest = item.split(":", 2)
            self.assertNotIn(f"{rule}:{path}.mutated:{digest}", allowlisted)
            self.assertNotIn(f"{rule}:{path}:{digest}0", allowlisted)

    def test_09_secret_after_two_megabytes_is_detected(self):
        content = (
            b"x" * 2_000_001
            + b"\nGITHUB_TOKEN=ghp_abcdefghijklmnopqrstuvwxyz123456\n"
        )
        self.assertTrue(secrets.findings_for("large.fixture", content))

    def test_10_secret_in_nul_content_is_detected(self):
        content = (
            b"binary\x00prefix\n"
            b"GOOGLE_API_KEY=AIzaabcdefghijklmnopqrstuvwxyz123456789\n"
        )
        self.assertTrue(secrets.findings_for("nul.fixture", content))

    def test_11_unsupported_file_is_not_counted_as_scanned(self):
        findings, scanned, unsupported = secrets.scan_paths(["missing.fixture"])
        self.assertEqual([], findings)
        self.assertEqual(0, scanned)
        self.assertEqual(1, len(unsupported))

    def test_12_diff_base_failure_selects_all(self):
        event = {"before": "1" * 40}
        with mock.patch.object(changes, "changed_paths", return_value=None):
            result = changes.resolve_event(event, "2" * 40)
        self.assertEqual(changes.catalog.CANONICAL, result["buildComponents"])
        self.assertIn("DIFF_BASE_UNAVAILABLE_FAIL_CLOSED", result["warnings"])

    def test_13_diff_preserves_hidden_and_newline_paths(self):
        event = {"before": "1" * 40}
        paths = [".github/workflows/ci.yml", "docs/name\nwith-newline.md"]
        with mock.patch.object(changes, "changed_paths", return_value=paths):
            result = changes.resolve_event(event, "2" * 40)
        self.assertEqual(sorted(paths), result["changedPaths"])
        self.assertEqual(changes.catalog.CANONICAL, result["validationComponents"])


if __name__ == "__main__":
    unittest.main()
