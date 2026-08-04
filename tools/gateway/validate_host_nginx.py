#!/usr/bin/env python3
"""Fail-closed contract for the production host Nginx configuration."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONFIG = ROOT / "ops/nginx/emporio.production.conf"
GATEWAY_CONFIG = ROOT / "ops/gateway/conf.d/emporio.conf"
ERP = "erp-emporio.abaronesa.net.br"
WEBSITE = "emporio.abaronesa.net.br"
CAPABILITIES_PATH = "/api/release-control/v1/"
OPERATIONS_PATH = "/api/deployment-control/v1/"


def _servers(text: str, host: str) -> list[str]:
    return [
        block
        for block in re.findall(r"server\s*\{.*?\n\}", text, re.S)
        if re.search(rf"server_name\s+{re.escape(host)};", block)
    ]


def validate(text: str | None = None, gateway_text: str | None = None) -> None:
    text = CONFIG.read_text(encoding="utf-8") if text is None else text
    gateway_text = (
        GATEWAY_CONFIG.read_text(encoding="utf-8")
        if gateway_text is None
        else gateway_text
    )
    errors: list[str] = []
    erp = _servers(text, ERP)
    website = _servers(text, WEBSITE)

    def require(condition: bool, label: str) -> None:
        if not condition:
            errors.append(label)

    require(len(erp) == 2 and len(website) == 2, "exact host blocks")
    require(text.count("server 127.0.0.1:8120;") == 1, "single loopback gateway")
    require("0.0.0.0:8120" not in text and "server 127.0.0.1:8180;" not in text, "upstream exposure")
    require("listen 80 default_server" not in text and "listen 443 default_server" not in text, "default tenant")
    require(text.count("location ^~ /.well-known/acme-challenge/") == 2, "acme only on http")
    require(text.count("return 301 https://$host$request_uri;") == 2, "https redirects")
    require(text.count("include /etc/letsencrypt/options-ssl-nginx.conf;") == 2, "tls options")
    require("TLSv1;" not in text and "TLSv1.1" not in text, "tls policy")
    require(text.count("X-Content-Type-Options") == 2 and text.count("X-Frame-Options") == 2 and text.count("Referrer-Policy") == 2, "security headers")
    require("client_max_body_size 10m;" in text and "client_max_body_size 2m;" in text, "body limits")
    require(text.count("proxy_connect_timeout 5s;") == 5 and text.count("proxy_send_timeout 60s;") == 5, "finite proxy timeouts")
    require(text.count("Upgrade $http_upgrade") == 2 and text.count('Connection "upgrade"') == 2, "websocket")

    erp_tls = next((x for x in erp if "listen 443 ssl http2;" in x), "")
    web_tls = next((x for x in website if "listen 443 ssl http2;" in x), "")
    erp_http = next((x for x in erp if "listen 80;" in x), "")
    web_http = next((x for x in website if "listen 80;" in x), "")
    for block, label in ((erp_http, "erp http"), (web_http, "website http")):
        require("/.well-known/acme-challenge/" in block and "https://$host$request_uri" in block, label)
    for path, label in (
        (CAPABILITIES_PATH, "erp capabilities namespace"),
        (OPERATIONS_PATH, "erp operations namespace"),
    ):
        require(
            f"location ^~ {path}" in erp_tls
            and re.search(
                rf"location \^~ {re.escape(path)}\s*\{{.*?proxy_pass http://127\.0\.0\.1:8180;",
                erp_tls,
                re.S,
            )
            is not None,
            label,
        )
    require("location ^~ /api/release-control/identity/deployer/" in erp_tls and "proxy_pass http://emporio_commercial_gateway;" in erp_tls, "identity same origin")
    require("/api/release-control/v1/identity/deployer/" not in erp_tls, "identity route collision")
    require(
        "proxy_pass http://127.0.0.1:8180;" not in web_tls
        and f"location ^~ {CAPABILITIES_PATH} {{ return 404; }}" in web_tls
        and f"location ^~ {OPERATIONS_PATH} {{ return 404; }}" in web_tls,
        "website control closed",
    )
    require(text.count("proxy_pass http://127.0.0.1:8180;") == 2, "exclusive control proxy")
    require(
        gateway_text.count("location ^~ /api/deployment-control/ { return 404; }")
        == 2,
        "gateway defensive deployment block",
    )
    require(all(header in erp_tls for header in ("Host $host", "X-Real-IP $remote_addr", "X-Forwarded-For $proxy_add_x_forwarded_for", "X-Forwarded-Proto https", "X-Forwarded-Host $host")), "closed proxy headers")
    require(text.count("ssl_certificate /etc/letsencrypt/live/") == 2 and text.count("ssl_certificate_key /etc/letsencrypt/live/") == 2, "certificate paths")
    if errors:
        raise ValueError("; ".join(errors))


if __name__ == "__main__":
    try:
        validate()
    except Exception as error:
        print(f"INVALID: {error}", file=sys.stderr)
        raise SystemExit(1)
    print("Host Nginx contract valid")
