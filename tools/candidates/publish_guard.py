#!/usr/bin/env python3
"""Recheck exact HEAD and candidate idempotency immediately before publish."""
import argparse,os,subprocess,sys,tempfile
from pathlib import Path
import lineage,previous_candidate
def decide(sha,head,mode,selected):
 relation=lineage.classify(sha,head)
 if relation in ("unrelated","descendant"):raise ValueError("HEAD lineage")
 if relation=="ancestor":return "superseded"
 if mode=="already_published":return "already_published"
 if mode=="superseded":return "superseded"
 return "continue"
def main():
 p=argparse.ArgumentParser();p.add_argument("--sha",required=True);a=p.parse_args()
 try:
  subprocess.run(["git","fetch","--no-tags","origin","main"],check=True)
  head=subprocess.check_output(["git","rev-parse","origin/main"],text=True).strip()
  with tempfile.TemporaryDirectory() as raw:mode,selected=previous_candidate.discover(a.sha,10,Path(raw))
  result=decide(a.sha,head,mode,selected)
  with open(os.environ["GITHUB_OUTPUT"],"a") as f:
   f.write("mode="+result+"\n")
   if result=="already_published" and selected:
    f.write("candidate_id="+selected["candidateId"]+"\n");f.write("artifact_id="+selected["artifactId"]+"\n");f.write("artifact_digest="+selected["artifactDigest"].removeprefix("sha256:")+"\n")
  return 0
 except Exception as e:print("publish-guard:invalid:"+str(e),file=sys.stderr);return 3
if __name__=="__main__":raise SystemExit(main())
