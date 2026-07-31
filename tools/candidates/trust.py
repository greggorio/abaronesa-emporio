#!/usr/bin/env python3
import argparse,json,os,re,subprocess,sys
from pathlib import Path
import candidate_plan
REPO="greggorio/abaronesa-emporio"
def event(value):
 errors=[]
 if value.get("conclusion")!="success" or value.get("name")!="CI" or value.get("event")!="push":errors.append("run state")
 repository=value.get("repository",{})
 if value.get("head_branch")!="main" or value.get("head_repository",{}).get("full_name")!=REPO or repository.get("full_name")!=REPO or repository.get("owner",{}).get("login")!="greggorio":errors.append("run repository")
 if not re.fullmatch(r"[0-9a-f]{40}",str(value.get("head_sha",""))) or not isinstance(value.get("run_attempt"),int) or value["run_attempt"]<1:errors.append("run SHA attempt")
 return errors
def classify_head(current,head,runner=subprocess.run):
 if current==head:return "continue"
 if runner(["git","merge-base","--is-ancestor",current,head]).returncode==0:return "superseded"
 raise ValueError("HEAD unrelated")
def main():
 p=argparse.ArgumentParser();p.add_argument("--event",type=Path,required=True);p.add_argument("--plan",type=Path,required=True);a=p.parse_args()
 try:
  run=json.loads(a.event.read_text());plan=json.loads(a.plan.read_text());errors=event(run)+candidate_plan.validate(plan)
  if plan.get("commitSha")!=run.get("head_sha") or plan.get("workflowRunId")!=str(run.get("id")) or plan.get("workflowAttempt")!=run.get("run_attempt"):errors.append("plan run binding")
  subprocess.run(["git","fetch","--no-tags","origin","main"],check=True)
  head=subprocess.check_output(["git","rev-parse","origin/main"],text=True).strip()
  try:head_mode=classify_head(run.get("head_sha"),head)
  except ValueError as e:errors.append(str(e));head_mode="invalid"
  if errors:raise ValueError(",".join(errors))
  with open(os.environ["GITHUB_OUTPUT"],"a") as f:f.write("sha="+run["head_sha"]+"\n");f.write("head_mode="+head_mode+"\n")
  return 0
 except Exception as e:print("trust:invalid:"+str(e),file=sys.stderr);return 3
if __name__=="__main__":raise SystemExit(main())
