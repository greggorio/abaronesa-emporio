"""Causal tests for the S28 release-control package validator."""

from __future__ import annotations

import shutil
import tempfile
import unittest
from pathlib import Path

from tools.deploy.validate_release_control_package import REQUIRED, validate


ROOT = Path(__file__).resolve().parents[3]


class ReleaseControlPackageContractTest(unittest.TestCase):
    def package_copy(self) -> tuple[tempfile.TemporaryDirectory[str], Path]:
        temporary = tempfile.TemporaryDirectory()
        root = Path(temporary.name)
        for relative in REQUIRED:
            destination = root / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / relative, destination)
        return temporary, root

    def assert_mutant_rejected(self, relative: str, mutate) -> None:
        temporary, root = self.package_copy()
        self.addCleanup(temporary.cleanup)
        path = root / relative
        path.write_text(mutate(path.read_text(encoding="utf-8")), encoding="utf-8")
        self.assertTrue(validate(root), msg=f"mutant accepted: {relative}")

    def test_baseline_is_valid(self) -> None:
        self.assertEqual(validate(ROOT), [])

    def test_mutant_01_root_user_is_rejected(self) -> None:
        self.assert_mutant_rejected("release_control/Dockerfile", lambda text: text.replace("USER 10001:10001", "USER root"))

    def test_mutant_02_secret_context_is_rejected(self) -> None:
        self.assert_mutant_rejected("release_control/.dockerignore", lambda text: text.replace(".env.*\n", ""))

    def test_mutant_03_development_dependencies_are_rejected(self) -> None:
        self.assert_mutant_rejected("release_control/Dockerfile", lambda text: text.replace("--no-dev", "--dev", 1))

    def test_mutant_04_migration_after_server_is_rejected(self) -> None:
        def swap(text: str) -> str:
            migration = 'alembic upgrade head && exec uvicorn'
            return text.replace(migration, 'uvicorn emporio_release_control.main:app && exec alembic upgrade head')

        self.assert_mutant_rejected("release_control/Dockerfile", swap)

    def test_mutant_05_public_healthcheck_is_rejected(self) -> None:
        self.assert_mutant_rejected("ops/compose/release-control.yml", lambda text: text.replace("127.0.0.1:8080/health/live", "0.0.0.0:8080/health/live"))

    def test_mutant_06_commercial_compose_import_is_rejected(self) -> None:
        self.assert_mutant_rejected("ops/compose/release-control.yml", lambda text: text + "\ninclude: ../compose.prod.yml\n")

    def test_mutant_07_public_host_port_is_rejected(self) -> None:
        self.assert_mutant_rejected("ops/compose/release-control.yml", lambda text: text.replace('"127.0.0.1:${RELEASE_CONTROL_LOOPBACK_PORT:-8180}:8080"', '"0.0.0.0:${RELEASE_CONTROL_LOOPBACK_PORT:-8180}:8080"'))

    def test_mutant_08_shared_volume_is_rejected(self) -> None:
        self.assert_mutant_rejected("ops/compose/release-control.yml", lambda text: text.replace("emporio_release_control_postgresql_data", "emporio_postgresql_data"))

    def test_mutant_09_privileged_container_is_rejected(self) -> None:
        self.assert_mutant_rejected("ops/compose/release-control.yml", lambda text: text.replace("    read_only: true", "    privileged: true\n    read_only: true"))

    def test_mutant_10_literal_secret_is_rejected(self) -> None:
        self.assert_mutant_rejected("ops/env/release-control.env.example", lambda text: text.replace("<database-password-from-secret-manager>", "real-database-password"))

    def test_mutant_11_systemd_environment_contract_is_rejected(self) -> None:
        self.assert_mutant_rejected("ops/systemd/emporio-release-control.service.example", lambda text: text.replace("EnvironmentFile=/etc/emporio/release-control.env\n", ""))

    def test_mutant_12_installation_claim_is_rejected(self) -> None:
        self.assert_mutant_rejected("docs/infrastructure/deployment/release-control/OPERACAO_RELEASE_CONTROL.md", lambda text: text.replace("não afirma que o serviço foi", "afirma que o serviço foi"))


if __name__ == "__main__":
    unittest.main()
