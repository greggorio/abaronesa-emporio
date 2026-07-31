import shutil
import tempfile
import unittest
from pathlib import Path

from tools.docker import validate_node_images as contract


FILES = [
    "frontend/Dockerfile",
    "frontend/.dockerignore",
    "frontend/entrypoint.sh",
    "frontend/nginx.conf",
    "frontend/package.json",
    "website_front/Dockerfile",
    "website_front/.dockerignore",
    "website_front/entrypoint.sh",
    "website_front/nginx.conf",
    "website_front/package.json",
    "whatsapp_service/Dockerfile",
    "whatsapp_service/.dockerignore",
    "whatsapp_service/index.js",
    "whatsapp_service/app.js",
    "whatsapp_service/package.json",
]


class NodeImagesContractTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        for relative in FILES:
            target = self.root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(contract.ROOT / relative, target)
        source = contract.ROOT / "website_front/src"
        shutil.copytree(source, self.root / "website_front/src")

    def tearDown(self):
        self.temp.cleanup()

    def errors(self):
        return contract.validate(self.root)

    def mutate(self, relative, old, new):
        path = self.root / relative
        value = path.read_text()
        self.assertIn(old, value)
        path.write_text(value.replace(old, new, 1))

    def assert_mutant(self, relative, old, new, expected):
        self.mutate(relative, old, new)
        self.assertTrue(any(item.startswith(expected) for item in self.errors()))

    def test_01_real_contract_is_valid(self):
        self.assertEqual([], contract.validate())

    def test_02_missing_file_fails(self):
        (self.root / "frontend/Dockerfile").unlink()
        self.assertIn("MISSING_FILE:frontend/Dockerfile", self.errors())

    def test_03_digest_is_required(self):
        self.assert_mutant("frontend/Dockerfile", "@sha256:", "@sha255:", "BASE_DIGEST_REQUIRED")

    def test_04_node18_is_rejected(self):
        self.assert_mutant("frontend/Dockerfile", "node:24.13.0", "node:18.20.0", "NODE24_BASE_REQUIRED")

    def test_05_floating_node_alias_is_rejected(self):
        self.assert_mutant("website_front/Dockerfile", "node:24.13.0-alpine3.23", "node:24-alpine", "NODE24_BASE_REQUIRED")

    def test_06_npm_install_is_rejected(self):
        self.assert_mutant("frontend/Dockerfile", "npm ci", "npm install", "NPM_CI_REQUIRED")

    def test_07_global_quasar_is_rejected(self):
        self.assert_mutant("frontend/Dockerfile", "RUN npm run build", "RUN npm install -g @quasar/cli && quasar build", "GLOBAL_CLI_FORBIDDEN")

    def test_08_node_in_nginx_runtime_is_rejected(self):
        self.assert_mutant("frontend/Dockerfile", "EXPOSE 80", "RUN node --version\nEXPOSE 80", "NODE_IN_SPA_RUNTIME")

    def test_09_startup_package_install_is_rejected(self):
        self.assert_mutant("frontend/entrypoint.sh", "set -eu", "set -eu\napk add curl", "STARTUP_INSTALL_FORBIDDEN")

    def test_10_external_health_is_rejected(self):
        self.assert_mutant("frontend/Dockerfile", "127.0.0.1", "backend", "HEALTHCHECK_INVALID")

    def test_11_legacy_website_env_is_rejected(self):
        self.assert_mutant("website_front/src/config/api.ts", "websiteApiUrl", "villaApiUrl", "WEBSITE_LEGACY_NAMES_FORBIDDEN")

    def test_12_wrong_internal_target_is_rejected(self):
        self.assert_mutant("website_front/entrypoint.sh", "website_back:8085", "backend:8085", "WEBSITE_INTERNAL_TARGET_INVALID")

    def test_13_coupled_liveness_is_rejected(self):
        self.assert_mutant("whatsapp_service/app.js", "res.status(200).json({ status: 'UP' });", "res.status(state.connected ? 200 : 503).json({ status: 'UP' });", "WHATSAPP_LIVENESS_COUPLED")

    def test_14_root_whatsapp_is_rejected(self):
        self.assert_mutant("whatsapp_service/Dockerfile", "USER 10001:10001", "USER root", "WHATSAPP_NONROOT_REQUIRED")

    def test_15_wrong_session_path_is_rejected(self):
        self.assert_mutant("whatsapp_service/Dockerfile", "SESSION_DIR=/data/session", "SESSION_DIR=/app/session", "WHATSAPP_SESSION_CONTRACT_INVALID")

    def test_16_secret_arg_is_rejected(self):
        self.assert_mutant("whatsapp_service/Dockerfile", "ARG VCS_REF=unknown", "ARG PASSWORD=unknown", "ARG_CONTRACT_INVALID")

    def test_17_latest_is_rejected(self):
        self.assert_mutant("whatsapp_service/Dockerfile", "node:24.13.0-alpine3.23", "node:latest", "LATEST_FORBIDDEN")

    def test_18_docker_socket_is_rejected(self):
        self.assert_mutant("whatsapp_service/Dockerfile", "EXPOSE 3001", "RUN echo /var/run/docker.sock\nEXPOSE 3001", "FORBIDDEN_RUNTIME_CONTENT")

    def test_19_dockerignore_is_fail_closed(self):
        self.assert_mutant("website_front/.dockerignore", ".wwebjs_auth", ".auth", "DOCKERIGNORE_INCOMPLETE")

    def test_20_whatsapp_health_path_is_exact(self):
        self.assert_mutant("whatsapp_service/Dockerfile", "/health/live", "/status", "WHATSAPP_PORT_HEALTH_INVALID")

    def test_21_extra_arg_is_rejected(self):
        self.assert_mutant("frontend/Dockerfile", "ARG VCS_REF=unknown", "ARG VCS_REF=unknown\nARG API_URL", "ARG_CONTRACT_INVALID")


if __name__ == "__main__":
    unittest.main()
