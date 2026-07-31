#!/usr/bin/env python3
from pathlib import Path
import re, sys

ROOT = Path(__file__).resolve().parents[2]

def _server(route, host):
    marker=f"server_name {host};"
    if marker not in route: return ""
    tail=route.split(marker,1)[1]
    return tail.split("\nserver {",1)[0]

def validate(dockerfile=None, nginx=None, route=None, common=None, websocket=None):
    base=ROOT/"ops/gateway"
    dockerfile=dockerfile if dockerfile is not None else (base/"Dockerfile").read_text()
    nginx=nginx if nginx is not None else (base/"nginx.conf").read_text()
    route=route if route is not None else (base/"conf.d/emporio.conf").read_text()
    common=common if common is not None else (base/"proxy-common.conf").read_text()
    websocket=websocket if websocket is not None else (base/"proxy-websocket.conf").read_text()
    erp=_server(route,"erp-emporio.abaronesa.net.br")
    web=_server(route,"emporio.abaronesa.net.br")
    errors=[]
    def require(ok,name):
        if not ok: errors.append(name)
    require(bool(re.search(r"^FROM nginxinc/nginx-unprivileged:[^\s]+@sha256:[0-9a-f]{64}$",dockerfile,re.M)),"base pinned")
    require(bool(re.search(r"^USER (?!0)\d+:\d+$",dockerfile,re.M)),"numeric non-root")
    require("EXPOSE 8080" in dockerfile and not re.search(r"EXPOSE\s+(80|443)(?:\s|$)",dockerfile,re.M),"expose")
    require("listen 8080 default_server" in route and "return 444" in route,"closed default")
    require(not re.search(r"listen\s+(80|443)(?:\s|;)",route),"forbidden listener")
    require(all(x in route for x in ("backend:8080","website_back:8085","frontend:80","website_front:80")),"upstreams")
    require("whatsapp_service" not in route,"direct whatsapp")
    for chunk,name,backend,frontend,limit in (
        (erp,"erp","erp_backend","erp_frontend","10m"),
        (web,"website","website_backend","website_frontend","2m")):
        require(bool(chunk),f"{name} host")
        require(f"client_max_body_size {limit};" in chunk,f"{name} body limit")
        require("location = /ws" in chunk and "location ^~ /ws/" in chunk,f"{name} ws exact/subpath")
        require(chunk.count(f"proxy_pass http://{backend};") >= 4,f"{name} backend routes")
        require(f"proxy_pass http://{frontend};" in chunk,f"{name} frontend")
        require("location ^~ /api/deployment-control/" in chunk and "return 404" in chunk,f"{name} control")
        require("location ^~ /api/" in chunk and "location ^~ /media/" in chunk,f"{name} api/media")
        require(all(h in chunk for h in ("X-Content-Type-Options","X-Frame-Options","Referrer-Policy")),f"{name} security headers")
        require("/actuator/" not in chunk,f"{name} actuator")
    require("location ^~ /oauth2/" in erp and "location ^~ /login/oauth2/" in erp,"erp oauth")
    require("/oauth2/" not in web and "/login/oauth2/" not in web,"website oauth")
    require(all(x in common for x in ("proxy_connect_timeout 5s","proxy_send_timeout 60s","proxy_read_timeout 60s")),"finite timeouts")
    require(all(x in common for x in ("X-Real-IP","X-Forwarded-For","X-Forwarded-Proto $forwarded_proto","X-Forwarded-Host")),"forward headers")
    require(all(x in websocket for x in ("Upgrade $http_upgrade","Connection $connection_upgrade","proxy_read_timeout 65s","proxy_buffering off")),"websocket controls")
    require("map $http_x_forwarded_proto $forwarded_proto" in nginx and "'' $scheme" in nginx,"proto fallback")
    require("pid /tmp/nginx.pid" in nginx and "read_only: true" not in dockerfile,"writable temp")
    if errors: raise ValueError("; ".join(errors))

if __name__=="__main__":
    try: validate()
    except Exception as error: print(f"INVALID: {error}",file=sys.stderr); raise SystemExit(1)
    print("Gateway contract valid")
