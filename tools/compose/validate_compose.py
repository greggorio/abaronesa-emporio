#!/usr/bin/env python3
import json,os,re,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
COMPOSE=ROOT/"ops/compose/compose.prod.yml"
SERVICES={"postgresql","backend","website_back","frontend","website_front","whatsapp_service","gateway"}
REPOS={"backend":"abaronesa-emporio-backend","website_back":"abaronesa-emporio-website-backend",
"frontend":"abaronesa-emporio-frontend","website_front":"abaronesa-emporio-website-frontend",
"whatsapp_service":"abaronesa-emporio-whatsapp-service","gateway":"abaronesa-emporio-gateway"}
NETWORKS={"postgresql":{"emporio-db"},"backend":{"emporio-app","emporio-db"},
"website_back":{"emporio-app","emporio-db"},"frontend":{"emporio-app"},
"website_front":{"emporio-app"},"whatsapp_service":{"emporio-app"},"gateway":{"emporio-app"}}
MOUNTS={"postgresql":{("postgres-data","/var/lib/postgresql/data")},
"backend":{("backend-uploads","/app/uploads")},"website_back":{("website-uploads","/app/uploads")},
"whatsapp_service":{("whatsapp-session","/data/session")}}
HEALTH={"postgresql":"pg_isready","backend":"http://127.0.0.1:8080/actuator/health",
"website_back":"http://127.0.0.1:8085/actuator/health","frontend":"http://127.0.0.1/healthz",
"website_front":"http://127.0.0.1/healthz","whatsapp_service":"http://127.0.0.1:3001/health/live",
"gateway":"http://127.0.0.1:8080/healthz"}
DEPENDS={"postgresql":set(),"backend":{"postgresql"},"website_back":{"postgresql"},"frontend":set(),
"website_front":set(),"whatsapp_service":set(),
"gateway":{"backend","website_back","frontend","website_front","whatsapp_service"}}
ENV_KEYS={
"postgresql":{"TZ","POSTGRES_DB","POSTGRES_USER","POSTGRES_PASSWORD","POSTGRES_ADMIN_USER","ERP_DB_NAME","ERP_DB_USER","ERP_DB_PASSWORD","WEBSITE_DB_NAME","WEBSITE_DB_USER","WEBSITE_DB_PASSWORD"},
"frontend":{"TZ","VITE_BASE_API_URL","RELEASE_CONTROL_MODE"},"website_front":{"TZ","VITE_ERP_API_URL","VITE_WEBSITE_API_URL"},
"whatsapp_service":{"TZ","NODE_ENV","PORT","SESSION_DIR","BASE_COUNTRY_CODE"},"gateway":set(),
"backend":{"TZ","SPRING_PROFILES_ACTIVE","SPRING_FLYWAY_ENABLED","SPRING_DATASOURCE_URL","SPRING_DATASOURCE_USERNAME","SPRING_DATASOURCE_PASSWORD",
"INTEGRATION_SYSTEM_TOKEN_SECRET","JAVA_TOOL_OPTIONS","WHATSAPP_SERVICE_URL","APP_CORS_ALLOWED_ORIGINS","ERP_BASE_URL",
"ECOMMERCE_BASE_URL","ANDROID_BASE_URL","WEBSITE_BASE_URL","ESPRESSO_API_BASE_URL","APP_FRONTEND_URL",
"SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_REDIRECT_URI","MAIL_ENABLED","MAIL_HOST","MAIL_PORT","MAIL_USERNAME",
"MAIL_PASSWORD","MANAGEMENT_HEALTH_MAIL_ENABLED","UBER_CLIENT_ID","UBER_CLIENT_SECRET","UBER_CUSTOMER_ID",
"UBER_ACCESS_TOKEN","UBER_SCOPE","UBER_PICKUP_NAME","UBER_PICKUP_PHONE","UBER_PICKUP_ADDRESS","UBER_PICKUP_NOTES",
"UBER_PICKUP_READY_PATH","ESPRESSO_SYNC_BASE_URL","ESPRESSO_SYNC_API_KEY","WEBSITE_SYNC_API_KEY","NFE_SCHEMA_PATH","NFE_XML_PATH",
"STORE_UPLOAD_DIR","STORE_UPLOAD_CATEGORIA_DIR","STORE_UPLOAD_SUBCATEGORIA_DIR","STORE_UPLOAD_CERTIFICADO_DIR",
"STORE_UPLOAD_PRODUTO_DIR","STORE_UPLOAD_SIGNAGE_AI_DIR","STORE_UPLOAD_SIGNAGE_DIR","GOOGLE_CLIENT_ID",
"GOOGLE_CLIENT_SECRET","ROOT_BOOTSTRAP_ENABLED","ROOT_BOOTSTRAP_NAME","ROOT_BOOTSTRAP_EMAIL","ROOT_BOOTSTRAP_PASSWORD",
"RELEASE_CONTROL_DEPLOYER_IDENTITY_ENABLED","RELEASE_CONTROL_DEPLOYER_IDENTITY_ISSUER",
"RELEASE_CONTROL_DEPLOYER_IDENTITY_PRIVATE_KEY_PATH","RELEASE_CONTROL_DEPLOYER_IDENTITY_KEY_ID"},
"website_back":{"TZ","SPRING_PROFILES_ACTIVE","FLYWAY_ENABLED","SPRING_DATASOURCE_URL","SPRING_DATASOURCE_USERNAME",
"SPRING_DATASOURCE_PASSWORD","INTEGRATION_SYSTEM_TOKEN_SECRET","JAVA_TOOL_OPTIONS","ERP_API_URL",
"APP_CORS_ALLOWED_ORIGINS","APP_WEBSOCKET_ALLOWED_ORIGINS","APP_FIREBASE_ENABLED","FIREBASE_CREDENTIALS_PATH",
"STORE_UPLOAD_GALERIA_DIR","STORE_UPLOAD_THEME_ASSETS_DIR","STORE_UPLOAD_ANDROID_ASSETS_DIR",
"STORE_UPLOAD_ANDROID_PRIVATE_DIR","WEBSITE_ERP_SYNC_KEY","UBER_CLIENT_ID","UBER_CLIENT_SECRET","UBER_CUSTOMER_ID",
"UBER_ACCESS_TOKEN","UBER_SCOPE","UBER_PICKUP_NAME","UBER_PICKUP_PHONE","UBER_PICKUP_ADDRESS","UBER_PICKUP_NOTES",
"UBER_PICKUP_READY_PATH"}}

def fixture_env():
    h="a"*64; env=os.environ.copy()
    iv={"backend":"BACKEND_IMAGE","website_back":"WEBSITE_BACK_IMAGE","frontend":"FRONTEND_IMAGE",
      "website_front":"WEBSITE_FRONT_IMAGE","whatsapp_service":"WHATSAPP_IMAGE","gateway":"GATEWAY_IMAGE"}
    env.update({iv[k]:f"ghcr.io/greggorio/{v}@sha256:{h}" for k,v in REPOS.items()})
    env.update(POSTGRES_IMAGE=f"postgres:16.10-alpine3.22@sha256:{h}",POSTGRES_ADMIN_USER="admin_s10",
      POSTGRES_ADMIN_PASSWORD="opaque",ERP_DB_NAME="erp_s10",ERP_DB_USER="erp_s10_user",ERP_DB_PASSWORD="opaque",
      WEBSITE_DB_NAME="web_s10",WEBSITE_DB_USER="web_s10_user",WEBSITE_DB_PASSWORD="opaque",
      INTEGRATION_SYSTEM_TOKEN_SECRET="opaque",ERP_WEBSITE_SYNC_KEY="opaque-sync",
      GOOGLE_CLIENT_ID="opaque",GOOGLE_CLIENT_SECRET="opaque",
      RELEASE_CONTROL_DEPLOYER_IDENTITY_ENABLED="true",
      RELEASE_CONTROL_DEPLOYER_IDENTITY_ISSUER="https://erp-emporio.abaronesa.net.br",
      RELEASE_CONTROL_DEPLOYER_IDENTITY_PRIVATE_KEY_FILE="/tmp/s45-private-key.pem",
      RELEASE_CONTROL_DEPLOYER_IDENTITY_KEY_ID="s45-production-key-01",RELEASE_CONTROL_MODE="deployer")
    return env
def resolved(env=None):
    p=subprocess.run(["docker","compose","-f",str(COMPOSE),"config","--format","json"],cwd=ROOT,
      env=env or fixture_env(),text=True,capture_output=True)
    if p.returncode: raise ValueError("docker compose config failed: "+p.stderr.strip())
    return json.loads(p.stdout)
def _mounts(service):
    return {(x.get("source"),x.get("target")) for x in service.get("volumes",[]) if x.get("type")=="volume"}
def validate(data=None,db_script=None,override=None,compose_text=None):
    d=data or resolved(); s=d.get("services",{}); errors=[]
    db_script=db_script if db_script is not None else (ROOT/"ops/db/init-databases.sh").read_text()
    override=override if override is not None else (ROOT/"ops/compose/testing/compose.s10.yml").read_text()
    if set(s)!=SERVICES: errors.append("service list")
    for name in SERVICES & set(s):
        v=s[name]
        if any(k in v for k in ("build","container_name","privileged","network_mode","devices")): errors.append(f"{name} forbidden")
        if v.get("restart")!="unless-stopped" or not v.get("init") or v.get("stop_grace_period")!="30s": errors.append(f"{name} lifecycle")
        if "no-new-privileges:true" not in v.get("security_opt",[]): errors.append(f"{name} security")
        log=v.get("logging",{}); opts=log.get("options",{})
        if log.get("driver")!="json-file" or opts.get("max-size")!="10m" or opts.get("max-file")!="3": errors.append(f"{name} logs")
        if not v.get("mem_limit") or not v.get("cpus") or not v.get("pids_limit"): errors.append(f"{name} resources")
        ports=v.get("ports",[])
        if name!="gateway" and ports: errors.append(f"{name} port")
        if set(v.get("networks",{}))!=NETWORKS[name]: errors.append(f"{name} networks")
        test=" ".join(map(str,v.get("healthcheck",{}).get("test",[])))
        if HEALTH[name] not in test: errors.append(f"{name} health")
        deps=v.get("depends_on",{})
        if set(deps)!=DEPENDS[name] or any(x.get("condition")!="service_healthy" for x in deps.values()): errors.append(f"{name} depends")
        if name in MOUNTS and not MOUNTS[name].issubset(_mounts(v)): errors.append(f"{name} mounts")
        if name not in MOUNTS and _mounts(v): errors.append(f"{name} unexpected volume")
        if any("docker.sock" in str(x) for x in v.get("volumes",[])): errors.append(f"{name} docker socket")
    gp=s.get("gateway",{}).get("ports",[{}])[0]
    if gp.get("host_ip")!="127.0.0.1" or gp.get("target")!=8080 or gp.get("published")!="8120": errors.append("gateway loopback")
    gateway=s.get("gateway",{})
    if not gateway.get("read_only") or not any(
        str(entry).split(":", 1)[0] == "/tmp"
        for entry in gateway.get("tmpfs", [])
    ):
        errors.append("gateway filesystem")
    for name,repo in REPOS.items():
        if name in s and not re.fullmatch(rf"ghcr\.io/greggorio/{repo}@sha256:[0-9a-f]{{64}}",s[name]["image"]): errors.append(f"{name} image")
    if "postgresql" in s and not re.fullmatch(r"postgres:16\.\d+-alpine3\.\d+@sha256:[0-9a-f]{64}",s["postgresql"]["image"]): errors.append("postgres image")
    compose_text=compose_text if compose_text is not None else COMPOSE.read_text()
    secret_name="release-control-deployer-identity-private-key"
    secret=d.get("secrets",{}).get(secret_name,{})
    backend_secrets=s.get("backend",{}).get("secrets",[])
    if set(d.get("secrets",{}))!={secret_name} or not secret.get("file") or secret.get("external"):
        errors.append("deployer secret definition")
    if len(backend_secrets)!=1 or backend_secrets[0].get("source")!=secret_name or backend_secrets[0].get("target")!=secret_name or str(backend_secrets[0].get("uid"))!="10001" or str(backend_secrets[0].get("gid"))!="10001" or backend_secrets[0].get("mode") not in (256,"0400"):
        errors.append("backend deployer secret")
    if any(name!="backend" and service.get("secrets") for name,service in s.items()):
        errors.append("deployer secret exposure")
    if "RELEASE_CONTROL_MODE: \"${RELEASE_CONTROL_MODE:-disabled}\"" not in compose_text or "-----BEGIN" in compose_text:
        errors.append("deployer safe defaults")
    nets=d.get("networks",{})
    if set(nets)!={"emporio-app","emporio-db"} or not nets.get("emporio-db",{}).get("internal") or nets.get("emporio-app",{}).get("internal"): errors.append("network definitions")
    vols=d.get("volumes",{})
    expected_names={"postgres-data":"emporio-postgres-data","backend-uploads":"emporio-backend-uploads","website-uploads":"emporio-website-uploads","whatsapp-session":"emporio-whatsapp-session"}
    if {k:v.get("name") for k,v in vols.items()}!=expected_names: errors.append("volume definitions")
    for name,keys in ENV_KEYS.items():
        if name in s and set(s[name].get("environment",{}))!=keys: errors.append(f"{name} environment allowlist")
    be=s.get("backend",{}).get("environment",{}); wb=s.get("website_back",{}).get("environment",{})
    required_be={"SPRING_PROFILES_ACTIVE","SPRING_FLYWAY_ENABLED","SPRING_DATASOURCE_URL","INTEGRATION_SYSTEM_TOKEN_SECRET","JAVA_TOOL_OPTIONS","WHATSAPP_SERVICE_URL","APP_CORS_ALLOWED_ORIGINS","NFE_SCHEMA_PATH","NFE_XML_PATH","MAIL_ENABLED","MANAGEMENT_HEALTH_MAIL_ENABLED","ESPRESSO_SYNC_API_KEY","WEBSITE_SYNC_API_KEY","RELEASE_CONTROL_DEPLOYER_IDENTITY_ENABLED","RELEASE_CONTROL_DEPLOYER_IDENTITY_ISSUER","RELEASE_CONTROL_DEPLOYER_IDENTITY_PRIVATE_KEY_PATH","RELEASE_CONTROL_DEPLOYER_IDENTITY_KEY_ID"}
    required_wb={"SPRING_PROFILES_ACTIVE","FLYWAY_ENABLED","SPRING_DATASOURCE_URL","INTEGRATION_SYSTEM_TOKEN_SECRET","JAVA_TOOL_OPTIONS","ERP_API_URL","APP_CORS_ALLOWED_ORIGINS","APP_WEBSOCKET_ALLOWED_ORIGINS","APP_FIREBASE_ENABLED","FIREBASE_CREDENTIALS_PATH","WEBSITE_ERP_SYNC_KEY"}
    if not required_be.issubset(be) or "JAVA_OPTS" in be or be.get("SPRING_FLYWAY_ENABLED")!="false" or be.get("WHATSAPP_SERVICE_URL")!="http://whatsapp_service:3001" or be.get("MANAGEMENT_HEALTH_MAIL_ENABLED")!=be.get("MAIL_ENABLED"): errors.append("backend environment")
    if be.get("RELEASE_CONTROL_DEPLOYER_IDENTITY_ENABLED")!="true" or be.get("RELEASE_CONTROL_DEPLOYER_IDENTITY_ISSUER")!="https://erp-emporio.abaronesa.net.br" or be.get("RELEASE_CONTROL_DEPLOYER_IDENTITY_PRIVATE_KEY_PATH")!="/run/secrets/release-control-deployer-identity-private-key" or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{15,63}",be.get("RELEASE_CONTROL_DEPLOYER_IDENTITY_KEY_ID", "")) or secret.get("file")=="/dev/null":
        errors.append("deployer identity wiring")
    if s.get("frontend",{}).get("environment",{}).get("RELEASE_CONTROL_MODE")!="deployer":
        errors.append("frontend deployer mode")
    if not required_wb.issubset(wb) or wb.get("FLYWAY_ENABLED")!="false" or wb.get("ERP_API_URL")!="http://backend:8080": errors.append("website environment")
    sync_aliases=(be.get("ESPRESSO_SYNC_API_KEY"),be.get("WEBSITE_SYNC_API_KEY"),wb.get("WEBSITE_ERP_SYNC_KEY"))
    if any(value is None for value in sync_aliases) or len(set(sync_aliases))!=1:
        errors.append("sync aliases")
    if s.get("whatsapp_service",{}).get("environment",{}).get("SESSION_DIR")!="/data/session": errors.append("whatsapp runtime")
    if "WHATSAPP_INITIALIZATION_DISABLED" in s.get("whatsapp_service",{}).get("environment",{}): errors.append("production whatsapp disabled")
    if not all(x in db_script for x in ("ON_ERROR_STOP=1","^[a-z_][a-z0-9_]{0,62}$","rolcanlogin","pg_get_userbyid(datdba)","Owner preexistente incompativel","Role preexistente incompativel","PASSWORD %L")): errors.append("database initializer")
    legacy=["deploy/docker-compose.yml","deploy/infra/docker-compose.yml","deploy/nginx/conf.d/emporio.conf",
      "deploy/nginx/emporio-erp.conf.template","deploy/nginx/emporio-website.conf.template","deploy/nginx/nginx.conf",
      "ops/compose/docker-compose.emporio-website.yml",
      "ops/scripts/init-multiple-dbs.sh","ops/deploy/deploy-tenant.sh"]
    if any((ROOT/x).exists() for x in legacy): errors.append("legacy artifact")
    if not all(x in override for x in ("backend:s10","website-back:s10","WHATSAPP_INITIALIZATION_DISABLED","https://erp.s10.invalid","https://website.s10.invalid")) or "build:" in override: errors.append("local override")
    if errors: raise ValueError("; ".join(errors))
if __name__=="__main__":
    try: validate()
    except Exception as e: print(f"INVALID: {e}",file=sys.stderr); raise SystemExit(1)
    print("Compose contract valid")
