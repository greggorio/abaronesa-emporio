#!/usr/bin/env python3
"""Bind pending, effective plan, predecessor and integration receipt into final v2."""
import argparse,json,os,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/"tools/releases"))
import artifact_io,candidate_manifest,lineage,validate_pending
def finalize(pending_dir,receipt_dir,effective_path,context_dir,output):
 pending_path=pending_dir/"pending.json";receipt_path=receipt_dir/"integration-result.json"
 selection_path=context_dir/"selection.json"
 pending=validate_pending.load_bundle(pending_dir,effective_path,selection_path,os.environ.get("GITHUB_RUN_ID"),os.environ.get("GITHUB_RUN_ATTEMPT"))
 if {path.name for path in receipt_dir.iterdir()}!={"integration-result.json","integration-result.json.sha256"} or not artifact_io.verify_pair(receipt_path):raise ValueError("receipt artifact files or sidecar")
 receipt=json.loads(receipt_path.read_text());effective=json.loads(effective_path.read_text())
 errors=candidate_manifest.validate_receipt(receipt,pending)
 if errors:raise ValueError(",".join(errors))
 final=candidate_manifest.finalize(pending,receipt)
 errors=candidate_manifest.validate_manifest(final,effective,receipt)
 if errors:raise ValueError(",".join(errors))
 data=artifact_io.canonical(final)
 final_metadata={"schemaVersion":1,"stage":"final","candidateId":final["candidateId"],"repository":"greggorio/abaronesa-emporio","commitSha":final["commitSha"],"workflowRunId":final["workflow"]["runId"],"workflowAttempt":final["workflow"]["attempt"],"manifestSha256":artifact_io.digest(data)}
 artifact_io.atomic_bundle(output,{"candidate.json":data,"candidate.json.sha256":artifact_io.sidecar(data),"metadata.json":artifact_io.canonical(final_metadata)})
 if {path.name for path in output.iterdir()}!={"candidate.json","candidate.json.sha256","metadata.json"} or not artifact_io.verify_pair(output/"candidate.json") or json.loads((output/"metadata.json").read_text())!=final_metadata or candidate_manifest.validate_manifest(json.loads((output/"candidate.json").read_text()),effective,receipt):raise ValueError("final post-write verification")
 return final
def main():
 p=argparse.ArgumentParser();p.add_argument("--pending-dir",type=Path,required=True);p.add_argument("--receipt-dir",type=Path,required=True);p.add_argument("--effective",type=Path,required=True);p.add_argument("--context",type=Path,required=True);p.add_argument("--output",type=Path,required=True);a=p.parse_args()
 try:finalize(a.pending_dir,a.receipt_dir,a.effective,a.context,a.output);return 0
 except Exception as e:print("finalize:invalid:"+str(e),file=sys.stderr);return 3
if __name__=="__main__":raise SystemExit(main())
