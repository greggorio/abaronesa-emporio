from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from validate_host_nginx import CONFIG, validate


class HostNginxTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.valid = CONFIG.read_text(encoding="utf-8")

    def assert_mutant(self, old: str, new: str) -> None:
        self.assertIn(old, self.valid)
        with self.assertRaises(ValueError):
            validate(self.valid.replace(old, new, 1))

    def test_valid(self):
        validate(self.valid)

    def test_independent_mutants(self):
        mutations = (
            ("server_name erp-emporio.abaronesa.net.br;", "server_name wrong.invalid;"),
            ("server 127.0.0.1:8120;", "server 0.0.0.0:8120;"),
            ("proxy_pass http://127.0.0.1:8180;", "proxy_pass http://emporio_commercial_gateway;"),
            ("location ^~ /api/release-control/v1/ { return 404; }", "location ^~ /api/release-control/v1/ { proxy_pass http://127.0.0.1:8180; }"),
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


if __name__ == "__main__":
    unittest.main()
