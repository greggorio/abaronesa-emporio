#!/usr/bin/env python3
"""Validate the complete pending artifact before environment or Docker use."""
import argparse,json,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/"tools/releases"))
sys.path.insert(0,str(ROOT/"tools/candidates"))
import artifact_io,candidate_manifest,lineage

FILES={"pending.json","pending.json.sha256","metadata.json"}

def load_bundle(directory,effective_path=None,selection_path=None,expected_run=None,expected_attempt=None):
 directory=Path(directory)
 if not directory.is_dir() or {path.name for path in directory.iterdir()}!=FILES:raise ValueError("pending artifact files")
 pending_path=directory/"pending.json"
 if not artifact_io.verify_pair(pending_path):raise ValueError("pending sidecar")
 pending=json.loads(pending_path.read_text())
 effective=json.loads(Path(effective_path).read_text()) if effective_path else None
 if effective is not None:
  errors=lineage.validate_effective(effective)
  if errors:raise ValueError(",".join(errors))
 selection=json.loads(Path(selection_path).read_text()) if selection_path else None
 if selection is not None and (effective is None or selection!=effective["predecessor"]):raise ValueError("pending predecessor selection")
 previous=None
 if selection is not None:
  previous_dir=Path(selection_path).parent/"previous"
  if selection.get("status")=="selected":
   if not previous_dir.is_dir() or {path.name for path in previous_dir.iterdir()}!={"candidate.json","candidate.json.sha256","metadata.json"}:raise ValueError("pending predecessor files")
   previous_path=previous_dir/"candidate.json"
   if not artifact_io.verify_pair(previous_path):raise ValueError("pending predecessor sidecar")
   previous=json.loads(previous_path.read_text());previous_metadata=json.loads((previous_dir/"metadata.json").read_text())
   if candidate_manifest.validate_manifest(previous):raise ValueError("pending predecessor manifest")
   expected_previous_metadata={"schemaVersion":1,"stage":"final","candidateId":previous.get("candidateId"),"repository":candidate_manifest.REPOSITORY,"commitSha":previous.get("commitSha"),"workflowRunId":previous.get("workflow",{}).get("runId"),"workflowAttempt":previous.get("workflow",{}).get("attempt"),"manifestSha256":artifact_io.digest(previous_path.read_bytes())}
   if previous_metadata!=expected_previous_metadata or previous.get("candidateId")!=selection.get("candidateId") or previous.get("commitSha")!=selection.get("commitSha") or previous.get("workflow",{}).get("runId")!=selection.get("workflowRunId"):raise ValueError("pending predecessor binding")
  elif previous_dir.exists():raise ValueError("first pending has predecessor files")
 metadata=json.loads((directory/"metadata.json").read_text())
 expected={"schemaVersion":1,"stage":"pending","repository":candidate_manifest.REPOSITORY,"commitSha":pending.get("commitSha"),"workflowRunId":pending.get("workflow",{}).get("runId"),"workflowAttempt":pending.get("workflow",{}).get("attempt"),"pendingSha256":artifact_io.digest(pending_path.read_bytes())}
 if metadata!=expected:raise ValueError("pending metadata")
 errors=candidate_manifest.validate_pending(pending,effective,previous)
 if errors:raise ValueError(",".join(errors))
 if expected_run is not None and pending["workflow"]["runId"]!=str(expected_run):raise ValueError("pending publisher run")
 if expected_attempt is not None and pending["workflow"]["attempt"]!=int(expected_attempt):raise ValueError("pending publisher attempt")
 return pending

def main(argv=None):
 parser=argparse.ArgumentParser()
 parser.add_argument("--pending-dir",type=Path,required=True)
 parser.add_argument("--effective",type=Path,required=True)
 parser.add_argument("--selection",type=Path,required=True)
 parser.add_argument("--run",required=True)
 parser.add_argument("--attempt",type=int,required=True)
 args=parser.parse_args(argv)
 try:
  load_bundle(args.pending_dir,args.effective,args.selection,args.run,args.attempt)
  print("candidate-pending:valid")
  return 0
 except Exception as error:
  print("candidate-pending:invalid:"+str(error),file=sys.stderr)
  return 3

if __name__=="__main__":raise SystemExit(main())
