#!/usr/bin/env python3
import argparse,json,os,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/"tools/releases"));sys.path.insert(0,str(ROOT/"tools/candidates"))
import artifact_io,candidate_manifest,image_result,catalog,lineage
def main():
 p=argparse.ArgumentParser();p.add_argument("--effective",type=Path,required=True);p.add_argument("--results",type=Path,required=True);p.add_argument("--context",type=Path,required=True);p.add_argument("--output",type=Path,required=True);a=p.parse_args()
 try:
  effective=json.loads(a.effective.read_text());effective_errors=lineage.validate_effective(effective)
  if effective_errors:raise ValueError(",".join(effective_errors))
  selection=json.loads((a.context/"selection.json").read_text())
  if selection!=effective["predecessor"]:raise ValueError("effective predecessor selection")
  results=[]
  for path in sorted(a.results.glob("candidate-component-*/component-result.json")):
   value=json.loads(path.read_text());cid=value.get("component");repo=catalog.load_yaml()["components"].get(cid,{}).get("image_repository")
   errors=image_result.validate(value,cid,repo,effective["commitSha"],os.environ["GITHUB_RUN_ID"],os.environ["GITHUB_RUN_ATTEMPT"])
   if errors:raise ValueError(",".join(errors))
   results.append(value)
  previous_path=a.context/"previous/candidate.json";previous=json.loads(previous_path.read_text()) if previous_path.exists() else None
  if effective["predecessor"]["status"]=="selected":
   if previous is None or not artifact_io.verify_pair(previous_path):raise ValueError("selected predecessor bundle")
   metadata=json.loads((previous_path.parent/"metadata.json").read_text())
   if candidate_manifest.validate_manifest(previous) or previous["candidateId"]!=effective["predecessor"]["candidateId"] or previous["commitSha"]!=effective["predecessor"]["commitSha"] or previous["workflow"]["runId"]!=effective["predecessor"]["workflowRunId"] or metadata.get("candidateId")!=previous["candidateId"] or metadata.get("manifestSha256")!=artifact_io.digest(previous_path.read_bytes()):raise ValueError("selected predecessor binding")
  elif previous is not None:raise ValueError("first predecessor has previous bundle")
  pending=candidate_manifest.pending_from(effective,results,previous,os.environ["CANDIDATE_CREATED_AT"],os.environ["GITHUB_RUN_ID"],int(os.environ["GITHUB_RUN_ATTEMPT"]))
  pending_errors=candidate_manifest.validate_pending(pending,effective,previous)
  if pending_errors:raise ValueError(",".join(pending_errors))
  data=artifact_io.canonical(pending);metadata={"schemaVersion":1,"stage":"pending","repository":candidate_manifest.REPOSITORY,"commitSha":pending["commitSha"],"workflowRunId":pending["workflow"]["runId"],"workflowAttempt":pending["workflow"]["attempt"],"pendingSha256":artifact_io.digest(data)}
  artifact_io.atomic_bundle(a.output,{"pending.json":data,"pending.json.sha256":artifact_io.sidecar(data),"metadata.json":artifact_io.canonical(metadata)})
  return 0
 except Exception as e:print("assemble:invalid:"+str(e),file=sys.stderr);return 3
if __name__=="__main__":raise SystemExit(main())
