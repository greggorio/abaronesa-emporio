from __future__ import annotations

import importlib.util
import shutil
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
MODULE_PATH = ROOT / "tools/releases/validate_publisher_ui.py"
SPEC = importlib.util.spec_from_file_location("validate_publisher_ui", MODULE_PATH)
validator = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = validator
SPEC.loader.exec_module(validator)


class PublisherUiContractTest(unittest.TestCase):
    def mutant(self) -> Path:
        target = Path(tempfile.mkdtemp(prefix="s17-publisher-ui-"))
        self.addCleanup(shutil.rmtree, target, True)
        for relative in validator.REQUIRED_FILES:
            destination = target / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / relative, destination)
        router_root = ROOT / validator.ROUTER_ROOT
        for source in router_root.rglob("*"):
            if source.is_file() and source.suffix in {".js", ".ts"}:
                relative = source.relative_to(ROOT)
                destination = target / relative
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source, destination)
        return target

    def mutate(self, root: Path, relative: str, old: str, new: str) -> None:
        path = root / relative
        text = path.read_text(encoding="utf-8")
        self.assertIn(old, text, f"mutation anchor absent: {relative}: {old}")
        path.write_text(text.replace(old, new, 1), encoding="utf-8")

    def mutate_all(self, root: Path, relative: str, old: str, new: str) -> None:
        path = root / relative
        text = path.read_text(encoding="utf-8")
        self.assertIn(old, text, f"mutation anchor absent: {relative}: {old}")
        path.write_text(text.replace(old, new), encoding="utf-8")

    def assert_invalid(self, root: Path, code: str) -> None:
        with self.assertRaisesRegex(ValueError, code):
            validator.validate(root)

    def test_01_real_ui_contract_is_valid(self) -> None:
        validator.validate(ROOT)

    def test_02_local_activation_mode_mutant_fails(self) -> None:
        root = self.mutant()
        self.mutate(
            root,
            "frontend/.env.example",
            "VITE_RELEASE_CONTROL_MODE=publisher",
            "VITE_RELEASE_CONTROL_MODE=deployer",
        )
        self.assert_invalid(root, "activation-mode")

    def test_02b_missing_versioned_example_fails(self) -> None:
        """S30 correction-01 B: the CI checkout must carry the example file."""
        root = self.mutant()
        (root / validator.ENV_EXAMPLE).unlink()
        self.assert_invalid(root, "required-file")

    def test_02c_absent_local_env_is_valid(self) -> None:
        """frontend/.env is unversioned by security policy; CI must not need it."""
        root = self.mutant()
        self.assertFalse((root / validator.ENV_FILE).exists())
        validator.validate(root)

    def test_02d_present_local_env_is_still_validated(self) -> None:
        for marker, code in (
            ("VITE_RELEASE_CONTROL_MODE=deployer", "activation-mode"),
            ("VITE_RELEASE_PUBLISHER_URL=http://127.0.0.1:9999", "activation-url"),
        ):
            with self.subTest(marker=marker):
                root = self.mutant()
                target = root / validator.ENV_FILE
                text = (ROOT / validator.ENV_EXAMPLE).read_text(encoding="utf-8")
                key = marker.split("=", 1)[0]
                lines = [
                    marker if line.startswith(key + "=") else line
                    for line in text.splitlines()
                ]
                target.write_text("\n".join(lines) + "\n", encoding="utf-8")
                self.assert_invalid(root, code)

    def test_02e_example_url_must_stay_loopback(self) -> None:
        root = self.mutant()
        self.mutate(
            root,
            "frontend/.env.example",
            "VITE_RELEASE_PUBLISHER_URL=http://127.0.0.1:8090",
            "VITE_RELEASE_PUBLISHER_URL=https://publisher.example.com",
        )
        self.assert_invalid(root, "activation-url")

    def test_03_production_activation_mutant_fails(self) -> None:
        root = self.mutant()
        self.mutate(
            root,
            "frontend/src/config/releasePublisher.js",
            "env.PROD === true",
            "env.PROD === false",
        )
        self.assert_invalid(root, "activation-contract")

    def test_04_panel_card_and_component_mutant_fails(self) -> None:
        root = self.mutant()
        self.mutate_all(
            root,
            "frontend/src/components/configuracoes/PainelControle.vue",
            "release-publisher",
            "release-deployer",
        )
        self.assert_invalid(root, "panel-marker")

    def test_05_new_router_mutant_fails(self) -> None:
        root = self.mutant()
        router = root / "frontend/src/router/s17-mutant.js"
        router.write_text(
            'export const mutant = "/release-publisher";\n',
            encoding="utf-8",
        )
        self.assert_invalid(root, "publisher-router")

    def test_06_exchange_endpoint_mutant_fails(self) -> None:
        root = self.mutant()
        self.mutate(
            root,
            "frontend/src/services/releasePublisherClient.js",
            "/api/release-control/identity/token",
            "/api/release-control/identity/custom",
        )
        self.assert_invalid(root, "exchange-endpoint")

    def test_07_token_callback_and_storage_mutants_fail(self) -> None:
        root = self.mutant()
        self.mutate_all(
            root,
            "frontend/src/services/releasePublisherClient.js",
            "getErpToken",
            "staticErpToken",
        )
        self.assert_invalid(root, "exchange-token-callback")
        root = self.mutant()
        path = root / "frontend/src/services/releasePublisherClient.js"
        path.write_text(
            path.read_text(encoding="utf-8")
            + '\nlocalStorage.setItem("publisher-token", accessToken);\n',
            encoding="utf-8",
        )
        self.assert_invalid(root, "client-local-storage")

    def test_08_capabilities_and_ready_candidate_mutants_fail(self) -> None:
        root = self.mutant()
        self.mutate(
            root,
            "frontend/src/services/releasePublisherClient.js",
            "/api/release-control/v1/capabilities",
            "/api/release-control/v1/missing",
        )
        self.assert_invalid(root, "publisher-endpoints")
        root = self.mutant()
        self.mutate_all(
            root,
            "frontend/src/services/releasePublisherClient.js",
            '"READY"',
            '"NOT_ELIGIBLE"',
        )
        self.assert_invalid(root, "ready-filter")

    def test_09_release_history_page_limit_mutant_fails(self) -> None:
        root = self.mutant()
        client = root / "frontend/src/services/releasePublisherClient.js"
        text = client.read_text(encoding="utf-8")
        replaced = re_sub_page_limit(text)
        self.assertNotEqual(text, replaced, "release page limit anchor absent")
        client.write_text(replaced, encoding="utf-8")
        self.assert_invalid(root, "release-page-limit")

    def test_10_forbidden_component_selection_mutant_fails(self) -> None:
        root = self.mutant()
        path = root / "frontend/src/services/releasePublisherClient.js"
        path.write_text(
            path.read_text(encoding="utf-8")
            + '\nexport const forbiddenMutant = {"components": ["backend"]};\n',
            encoding="utf-8",
        )
        self.assert_invalid(root, "forbidden-request-authority")

    def test_11_attempt_key_schema_and_storage_mutants_fail(self) -> None:
        root = self.mutant()
        self.mutate(
            root,
            "frontend/src/services/releasePublisherAttempt.js",
            "emporio.releasePublisher.pending.v1",
            "emporio.releasePublisher.pending.v2",
        )
        self.assert_invalid(root, "attempt-contract")
        root = self.mutant()
        self.mutate(
            root,
            "frontend/src/services/releasePublisherAttempt.js",
            "globalThis.sessionStorage",
            "globalThis.localStorage",
        )
        self.assert_invalid(root, "attempt-contract|attempt-local-storage")

    def test_12_post_retry_boundary_mutant_fails(self) -> None:
        root = self.mutant()
        path = root / "frontend/src/services/releasePublisherClient.js"
        text = path.read_text(encoding="utf-8")
        anchor = "response.status === 401 && retry401"
        self.assertIn(anchor, text, "401 retry anchor absent")
        path.write_text(
            text.replace(anchor, "response.status === 500 && retry401", 1),
            encoding="utf-8",
        )
        self.assert_invalid(root, "single-401-retry")

    def test_13_poll_interval_timeout_and_cleanup_mutants_fail(self) -> None:
        root = self.mutant()
        component = root / (
            "frontend/src/components/configuracoes/ReleasePublisherConfig.vue"
        )
        text = component.read_text(encoding="utf-8")
        replaced = text.replace("3000", "1000", 1).replace("3_000", "1_000", 1)
        self.assertNotEqual(text, replaced, "poll interval anchor absent")
        component.write_text(replaced, encoding="utf-8")
        self.assert_invalid(root, "poll-interval")
        root = self.mutant()
        self.mutate_all(
            root,
            "frontend/src/components/configuracoes/ReleasePublisherConfig.vue",
            "onBeforeUnmount",
            "onBeforeMount",
        )
        self.assert_invalid(root, "poll-cleanup")

    def test_14_internal_error_rendering_mutant_fails(self) -> None:
        root = self.mutant()
        path = root / (
            "frontend/src/components/configuracoes/ReleasePublisherConfig.vue"
        )
        text = path.read_text(encoding="utf-8")
        path.write_text(
            text.replace(
                "<template>",
                "<template><div>{{ operation.errorCode }}</div>",
                1,
            ),
            encoding="utf-8",
        )
        self.assert_invalid(root, "rendered-internal-error-code")

    def test_15_documentation_mutants_fail(self) -> None:
        root = self.mutant()
        (root / validator.UI_DOC).unlink()
        self.assert_invalid(root, "required-file")
        root = self.mutant()
        for relative in (
            validator.RELEASE_CONTROL_README,
            validator.IDENTITY_DOC,
            validator.RUNTIME_DOC,
            validator.ONBOARDING_DOC,
            validator.DEVELOPMENT_README,
        ):
            path = root / relative
            path.write_text(
                path.read_text(encoding="utf-8").replace("UI_PUBLISHER.md", ""),
                encoding="utf-8",
            )
        self.assert_invalid(root, "ui-doc-link")

    def test_16_causal_test_surface_mutant_fails(self) -> None:
        root = self.mutant()
        self.mutate(
            root,
            "frontend/src/services/releasePublisherClient.spec.js",
            "Idempotency-Key",
            "Mutation-Key",
        )
        self.assert_invalid(root, "tests-client")


def re_sub_page_limit(text: str) -> str:
    import re

    return re.sub(
        r"((?:MAX_RELEASE_PAGES|RELEASE_PAGE_LIMIT)\s*=\s*)10\b",
        r"\g<1>11",
        text,
        count=1,
    )


if __name__ == "__main__":
    unittest.main()
