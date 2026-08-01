#!/usr/bin/env python3
"""Strict CI candidate plan v2."""
import argparse,hashlib,json,os,re,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]; sys.path.insert(0,str(ROOT/"tools/releases"))
import catalog
REPOSITORY="greggorio/abaronesa-emporio"; ZERO="0"*40
ROOT_BASE_WARNING="DIFF_BASE_UNAVAILABLE_FAIL_CLOSED"
KEYS={"schemaVersion","repository","commitSha","baseCommitSha","ref","workflowRunId","workflowAttempt","catalogSha256","resolution"}
SHA=re.compile(r"[0-9a-f]{40}")
def catalog_digest(): return "sha256:"+hashlib.sha256(catalog.DEFAULT_CATALOG.read_bytes()).hexdigest()
def validate(value):
 errors=[]
 if not isinstance(value,dict) or set(value)!=KEYS:return ["PLAN_SHAPE"]
 if value["schemaVersion"]!=2 or value["repository"]!=REPOSITORY:errors.append("PLAN_IDENTITY")
 if not SHA.fullmatch(str(value["commitSha"])) or not SHA.fullmatch(str(value["baseCommitSha"])):errors.append("PLAN_SHA")
 if value["ref"]!="refs/heads/main" or not re.fullmatch(r"[1-9][0-9]*",str(value["workflowRunId"])) or not isinstance(value["workflowAttempt"],int) or value["workflowAttempt"]<1:errors.append("PLAN_RUN")
 if value["catalogSha256"]!=catalog_digest():errors.append("PLAN_CATALOG")
 try:
  r=value["resolution"]
  if r not in accepted_resolutions(value,r):errors.append("PLAN_RESOLUTION")
 except Exception:errors.append("PLAN_RESOLUTION")
 return errors
def accepted_resolutions(value,resolution):
 """Resolutions a plan may carry.

 The canonical form is always catalog.resolve. On the root commit there is no
 diff base, so resolve_changes.py fails closed to the complete BOM and adds
 DIFF_BASE_UNAVAILABLE_FAIL_CLOSED. That single extra warning is accepted only
 when baseCommitSha is exactly forty zeros and the classification is
 first_release. Every other field, and every other warning, must still match
 catalog.resolve exactly.
 """
 first_release=resolution.get("classification")=="first_release"
 canonical=catalog.resolve(catalog.load_yaml(),resolution["changedPaths"],first_release)
 accepted=[canonical]
 if first_release and value.get("baseCommitSha")==ZERO:
  relaxed=dict(canonical);relaxed["warnings"]=sorted(set(canonical["warnings"]+[ROOT_BASE_WARNING]));accepted.append(relaxed)
 return accepted
def generate(resolution,event,sha,run,attempt):
 if event.get("ref")!="refs/heads/main" or event.get("after")!=sha or event.get("deleted") is True or "pull_request" in event:raise ValueError("push main event required")
 base=event.get("before",ZERO)
 value={"schemaVersion":2,"repository":REPOSITORY,"commitSha":sha,"baseCommitSha":base,"ref":"refs/heads/main","workflowRunId":str(run),"workflowAttempt":int(attempt),"catalogSha256":catalog_digest(),"resolution":resolution}
 errors=validate(value)
 if errors:raise ValueError(",".join(errors))
 return value
def write(path,value):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,sort_keys=True,separators=(",",":"))+"\n")
def main():
 p=argparse.ArgumentParser();s=p.add_subparsers(dest="cmd",required=True)
 g=s.add_parser("generate");g.add_argument("--resolution",type=Path,required=True);g.add_argument("--output",type=Path,required=True)
 v=s.add_parser("validate");v.add_argument("--plan",type=Path,required=True);a=p.parse_args()
 try:
  if a.cmd=="generate":
   event=json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
   value=generate(json.loads(a.resolution.read_text()),event,os.environ["GITHUB_SHA"],os.environ["GITHUB_RUN_ID"],int(os.environ["GITHUB_RUN_ATTEMPT"]));write(a.output,value)
  else:value=json.loads(a.plan.read_text())
  errors=validate(value)
  if errors:raise ValueError(",".join(errors))
  print("candidate-plan:valid");return 0
 except (ValueError,OSError,json.JSONDecodeError) as e:print("candidate-plan:invalid:"+str(e),file=sys.stderr);return 3
if __name__=="__main__":raise SystemExit(main())
