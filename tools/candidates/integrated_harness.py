#!/usr/bin/env python3
"""Exact seven-service harness and fail-closed directed cleanup."""
import argparse,json,os,re,subprocess,sys
from datetime import datetime,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/"tools/releases"));sys.path.insert(0,str(ROOT/"tools/candidates"))
import artifact_io,candidate_manifest,probe_candidate,validate_pending
SERVICES=("postgresql","backend","website_back","frontend","website_front","whatsapp_service","gateway")
def now():return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00","Z")
def execute(pending,compose,override,project,output,runner=subprocess.run,check_output=subprocess.check_output):
 pending_errors=candidate_manifest.validate_pending(pending)
 if pending_errors:raise ValueError("pending:"+",".join(pending_errors))
 if not re.fullmatch(r"candidate-[0-9]+-[0-9]+",project):raise ValueError("project")
 env=os.environ.copy();env["CANDIDATE_PROJECT"]=project
 command=["docker","compose","-p",project,"-f",str(compose),"-f",str(override)]
 cleanup_errors=[];service_receipt=[];probes=[]
 try:
  model=json.loads(check_output(command+["config","--format","json"],text=True,env=env))
  if set(model.get("services",{}))!=set(SERVICES) or len(model["services"])!=7:raise ValueError("seven canonical services")
  bound=[(name,p) for name,s in model["services"].items() for p in s.get("ports",[])]
  if len(bound)!=1 or bound[0][0]!="gateway" or bound[0][1].get("host_ip")!="127.0.0.1" or str(bound[0][1].get("published"))!=env["CANDIDATE_GATEWAY_PORT"] or bound[0][1].get("target")!=8080:raise ValueError("gateway bind")
  runner(command+["pull","--quiet","--policy","always"],check=True,env=env)
  runner(command+["run","--rm","-T","--entrypoint","/app/bin/migrate","backend","migrate"],check=True,env=env)
  runner(command+["up","-d","--no-build","--pull","never","--wait","--wait-timeout","600"],check=True,env=env)
  rows=json.loads(check_output(command+["ps","--format","json"],text=True,env=env))
  by_service={r["Service"]:r for r in rows}
  if set(by_service)!=set(SERVICES) or any(r.get("State")!="running" or r.get("Health")!="healthy" for r in rows):raise ValueError("service state")
  service_receipt=[{"id":s,"state":"running","health":"healthy"} for s in SERVICES]
  probes=probe_candidate.run()
  runner(command+["exec","-T","backend","curl","-fsS","http://whatsapp_service:3001/status"],check=True,stdout=subprocess.DEVNULL,env=env)
  probes.append({"id":"whatsapp_internal","status":"passed"})
 finally:
  def run_cleanup(name,args,absence=False):
   try:result=runner(args,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,env=env)
   except Exception:
    cleanup_errors.append(name+":error");return
   if (absence and result.returncode==0) or (not absence and result.returncode!=0):cleanup_errors.append(name)
  run_cleanup("down",command+["down","-v","--remove-orphans"])
  for component in pending["components"]:
   ref=component["immutableRef"]
   run_cleanup("remove:"+component["id"],["docker","image","rm",ref])
   run_cleanup("image-present:"+component["id"],["docker","image","inspect",ref],absence=True)
  counts={}
  for kind,args in {"containers":["docker","ps","-aq","--filter",f"label=com.docker.compose.project={project}"],"volumes":["docker","volume","ls","-q","--filter",f"label=com.docker.compose.project={project}"],"networks":["docker","network","ls","-q","--filter",f"label=com.docker.compose.project={project}"]}.items():
   try:counts[kind]=len(check_output(args,text=True).split())
   except Exception:counts[kind]=-1;cleanup_errors.append("count:"+kind)
  if any(counts.values()):cleanup_errors.append("project-residue")
  run_cleanup("logout",["docker","logout","ghcr.io"])
 if cleanup_errors:raise ValueError("cleanup:"+",".join(cleanup_errors))
 pending_data=artifact_io.canonical(pending)
 receipt={"schemaVersion":1,"status":"passed","repository":"greggorio/abaronesa-emporio","commitSha":pending["commitSha"],"workflowRunId":pending["workflow"]["runId"],"workflowAttempt":pending["workflow"]["attempt"],"pendingSha256":artifact_io.digest(pending_data),"checkedAt":now(),"services":service_receipt,"probes":probes,"cleanup":{"containers":0,"volumes":0,"networks":0,"imagesAbsent":6}}
 data=artifact_io.canonical(receipt);artifact_io.atomic_bundle(output,{"integration-result.json":data,"integration-result.json.sha256":artifact_io.sidecar(data)});return receipt
def execute_bundle(pending_dir,effective,selection,compose,override,project,output,runner=subprocess.run,check_output=subprocess.check_output,expected_run=None,expected_attempt=None):
 pending=validate_pending.load_bundle(pending_dir,effective,selection,expected_run,expected_attempt)
 return execute(pending,compose,override,project,output,runner,check_output)
def main():
 p=argparse.ArgumentParser();p.add_argument("--pending-dir",type=Path,required=True);p.add_argument("--effective",type=Path,required=True);p.add_argument("--selection",type=Path,required=True);p.add_argument("--run",required=True);p.add_argument("--attempt",type=int,required=True);p.add_argument("--compose",type=Path,required=True);p.add_argument("--override",type=Path,required=True);p.add_argument("--project",required=True);p.add_argument("--output",type=Path,required=True);a=p.parse_args()
 try:
  execute_bundle(a.pending_dir,a.effective,a.selection,a.compose,a.override,a.project,a.output,expected_run=a.run,expected_attempt=a.attempt);return 0
 except Exception as e:print("integration:invalid:"+str(e),file=sys.stderr);return 3
if __name__=="__main__":raise SystemExit(main())
