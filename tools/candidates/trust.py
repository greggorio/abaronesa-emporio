#!/usr/bin/env python3
import argparse,json,os,re,sys,urllib.error,urllib.request
from pathlib import Path
import candidate_plan
REPO="greggorio/abaronesa-emporio"
API="https://api.github.com"
REF_ENDPOINT=API+"/repos/"+REPO+"/git/ref/heads/main"
COMPARE_ENDPOINT=API+"/repos/"+REPO+"/compare/{base}...{head}"
API_TIMEOUT=30
SHA_RE=re.compile(r"[0-9a-f]{40}")
def token():
 value=(os.environ.get("GH_TOKEN") or "").strip()
 if not value:raise ValueError("github token missing")
 return value
def api_json(url,opener=None,timeout=API_TIMEOUT):
 """Read a fixed GitHub endpoint. Never leaks the token into an error."""
 request=urllib.request.Request(url,headers={"Authorization":"Bearer "+token(),"Accept":"application/vnd.github+json","X-GitHub-Api-Version":"2022-11-28","User-Agent":"emporio-candidate-trust"})
 try:
  with (opener or urllib.request.urlopen)(request,timeout=timeout) as response:
   status=getattr(response,"status",None) or response.getcode()
   if not 200<=int(status)<300:raise ValueError("github api status")
   return json.loads(response.read().decode("utf-8"))
 except ValueError:raise
 except Exception:raise ValueError("github api unavailable")
def main_sha(opener=None):
 """Resolve refs/heads/main of the canonical repository without touching Git."""
 payload=api_json(REF_ENDPOINT,opener)
 if not isinstance(payload,dict) or payload.get("ref")!="refs/heads/main":raise ValueError("github api ref")
 obj=payload.get("object")
 if not isinstance(obj,dict) or obj.get("type")!="commit":raise ValueError("github api ref type")
 sha=str(obj.get("sha",""))
 if not SHA_RE.fullmatch(sha):raise ValueError("github api ref sha")
 return sha
def head_relation(received,opener=None):
 """Lineage of the received SHA against main, with git merge-base semantics.

 Returns "same" when both point at the same commit and "ancestor" when main is
 ahead of the received SHA. Behind, diverged, or any unusable answer fails
 closed, so nothing is published on an uncertain comparison.
 """
 if not SHA_RE.fullmatch(str(received or "")):raise ValueError("received sha")
 head=main_sha(opener)
 if head==received:return "same"
 payload=api_json(COMPARE_ENDPOINT.format(base=received,head=head),opener)
 if not isinstance(payload,dict):raise ValueError("github api compare")
 status=payload.get("status")
 if status=="identical":return "same"
 if status=="ahead":return "ancestor"
 raise ValueError("HEAD unrelated")
def classify_head(received,opener=None):
 return "continue" if head_relation(received,opener)=="same" else "superseded"
def event(value):
 errors=[]
 if value.get("conclusion")!="success" or value.get("name")!="CI" or value.get("event")!="push":errors.append("run state")
 repository=value.get("repository",{})
 if value.get("head_branch")!="main" or value.get("head_repository",{}).get("full_name")!=REPO or repository.get("full_name")!=REPO or repository.get("owner",{}).get("login")!="greggorio":errors.append("run repository")
 if not re.fullmatch(r"[0-9a-f]{40}",str(value.get("head_sha",""))) or not isinstance(value.get("run_attempt"),int) or value["run_attempt"]<1:errors.append("run SHA attempt")
 return errors
def main():
 p=argparse.ArgumentParser();p.add_argument("--event",type=Path,required=True);p.add_argument("--plan",type=Path,required=True);a=p.parse_args()
 try:
  run=json.loads(a.event.read_text());plan=json.loads(a.plan.read_text());errors=event(run)+candidate_plan.validate(plan)
  if plan.get("commitSha")!=run.get("head_sha") or plan.get("workflowRunId")!=str(run.get("id")) or plan.get("workflowAttempt")!=run.get("run_attempt"):errors.append("plan run binding")
  try:head_mode=classify_head(run.get("head_sha"))
  except ValueError as e:errors.append(str(e));head_mode="invalid"
  if errors:raise ValueError(",".join(errors))
  with open(os.environ["GITHUB_OUTPUT"],"a") as f:f.write("sha="+run["head_sha"]+"\n");f.write("head_mode="+head_mode+"\n")
  return 0
 except Exception as e:print("trust:invalid:"+str(e),file=sys.stderr);return 3
if __name__=="__main__":raise SystemExit(main())
