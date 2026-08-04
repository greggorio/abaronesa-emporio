from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from validate_host_nginx import CONFIG, GATEWAY_CONFIG, validate


class HostNginxTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.valid = CONFIG.read_text(encoding="utf-8")
        cls.gateway = GATEWAY_CONFIG.read_text(encoding="utf-8")

    def assert_mutant(self, old: str, new: str) -> None:
        self.assertIn(old, self.valid)
        with self.assertRaises(ValueError):
            validate(self.valid.replace(old, new, 1), self.gateway)

    def test_valid(self):
        validate(self.valid, self.gateway)

    def test_independent_mutants(self):
        mutations = (
            ("server_name erp-emporio.abaronesa.net.br;", "server_name wrong.invalid;"),
            ("server 127.0.0.1:8120;", "server 0.0.0.0:8120;"),
            ("location ^~ /api/deployment-control/v1/ {", "location ^~ /api/deployment-control/ {"),
            ("location ^~ /api/release-control/v1/ { return 404; }", "location ^~ /api/release-control/v1/ { proxy_pass http://127.0.0.1:8180; }"),
            ("location ^~ /api/deployment-control/v1/ { return 404; }", "location ^~ /api/deployment-control/v1/ { proxy_pass http://127.0.0.1:8180; }"),
            ("location ^~ /api/release-control/identity/deployer/", "location ^~ /api/release-control/v1/identity/deployer/"),
            ("include /etc/letsencrypt/options-ssl-nginx.conf;", "ssl_protocols TLSv1;"),
            ("client_max_body_size 10m;", "client_max_body_size 100m;"),
            ("proxy_connect_timeout 5s;", "proxy_connect_timeout 0;"),
            ("Upgrade $http_upgrade", "Upgrade off"),
            ("X-Frame-Options", "X-Removed-Frame"),
        )
        for old, new in mutations:
            with self.subTest(old=old):
                self.assert_mutant(old, new)

    def test_operations_upstream_must_be_exact_loopback(self):
        marker = "location ^~ /api/deployment-control/v1/ {"
        start = self.valid.index(marker)
        prefix = self.valid[:start]
        block = self.valid[start:]
        mutant = prefix + block.replace(
            "proxy_pass http://127.0.0.1:8180;",
            "proxy_pass http://127.0.0.1:8181;",
            1,
        )
        with self.assertRaises(ValueError):
            validate(mutant, self.gateway)

    def test_identity_must_not_reach_control_plane(self):
        marker = "location ^~ /api/release-control/identity/deployer/ {"
        start = self.valid.index(marker)
        prefix = self.valid[:start]
        block = self.valid[start:]
        mutant = prefix + block.replace(
            "proxy_pass http://emporio_commercial_gateway;",
            "proxy_pass http://127.0.0.1:8180;",
            1,
        )
        with self.assertRaises(ValueError):
            validate(mutant, self.gateway)

    def test_gateway_defensive_block_is_required_for_both_hosts(self):
        mutant = self.gateway.replace(
            "  location ^~ /api/deployment-control/ { return 404; }\n",
            "",
            1,
        )
        with self.assertRaises(ValueError):
            validate(self.valid, mutant)


if __name__ == "__main__":
    unittest.main()
