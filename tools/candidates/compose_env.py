#!/usr/bin/env python3
"""Emit sanitized ephemeral Compose variables to GITHUB_ENV without logging values."""
import argparse,os,secrets,socket,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/"tools/releases"));sys.path.insert(0,str(ROOT/"tools/candidates"))
import validate_pending
MAP={"backend":"CANDIDATE_BACKEND_IMAGE","website_back":"CANDIDATE_WEBSITE_BACK_IMAGE","frontend":"CANDIDATE_FRONTEND_IMAGE","website_front":"CANDIDATE_WEBSITE_FRONT_IMAGE","whatsapp_service":"CANDIDATE_WHATSAPP_IMAGE","gateway":"CANDIDATE_GATEWAY_IMAGE"}
BASE_ALIASES={value:value.removeprefix("CANDIDATE_") for value in MAP.values()}
SENSITIVE_KEYS=("CANDIDATE_ROOT_PASSWORD","POSTGRES_ADMIN_PASSWORD","ERP_DB_PASSWORD","WEBSITE_DB_PASSWORD","INTEGRATION_SYSTEM_TOKEN_SECRET","ERP_WEBSITE_SYNC_KEY","GOOGLE_CLIENT_SECRET")
def build_env(manifest,postgres_image,port):
 refs={x["id"]:x["immutableRef"] for x in manifest["components"]}
 env={MAP[k]:v for k,v in refs.items()}
 env.update({BASE_ALIASES[key]:value for key,value in env.items()})
 env.update(POSTGRES_IMAGE=postgres_image,POSTGRES_ADMIN_USER="candidate_admin",POSTGRES_ADMIN_PASSWORD=secrets.token_hex(24),ERP_DB_NAME="candidate_erp",ERP_DB_USER="candidate_erp_user",ERP_DB_PASSWORD=secrets.token_hex(24),WEBSITE_DB_NAME="candidate_web",WEBSITE_DB_USER="candidate_web_user",WEBSITE_DB_PASSWORD=secrets.token_hex(24),INTEGRATION_SYSTEM_TOKEN_SECRET=secrets.token_hex(64),ERP_WEBSITE_SYNC_KEY=secrets.token_hex(32),GOOGLE_CLIENT_ID="candidate.invalid",GOOGLE_CLIENT_SECRET=secrets.token_hex(24),CANDIDATE_ROOT_EMAIL="root@candidate.invalid",CANDIDATE_ROOT_PASSWORD=secrets.token_urlsafe(32),CANDIDATE_GATEWAY_PORT=str(port),GATEWAY_LOOPBACK_PORT=str(port))
 return env
def emit_masks(env):
 for key in SENSITIVE_KEYS:print(f"::add-mask::{env[key]}")
def main():
 p=argparse.ArgumentParser();p.add_argument("--pending-dir",type=Path,required=True);p.add_argument("--effective",type=Path,required=True);p.add_argument("--selection",type=Path,required=True);p.add_argument("--run",required=True);p.add_argument("--attempt",type=int,required=True);a=p.parse_args()
 m=validate_pending.load_bundle(a.pending_dir,a.effective,a.selection,a.run,a.attempt)
 with socket.socket() as s:s.bind(("127.0.0.1",0)); port=s.getsockname()[1]
 env=build_env(m,os.environ["CANDIDATE_POSTGRES_IMAGE"],port)
 emit_masks(env)
 with open(os.environ["GITHUB_ENV"],"a") as f:
  for key,value in env.items(): f.write(f"{key}={value}\n")
 print("candidate-compose-env:written")
if __name__=="__main__": main()
