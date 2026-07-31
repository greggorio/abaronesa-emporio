#!/usr/bin/env python3
import argparse,json,os,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/"tools/releases"));sys.path.insert(0,str(ROOT/"tools/candidates"))
import lineage
p=argparse.ArgumentParser();p.add_argument("--effective",type=Path,required=True);a=p.parse_args()
effective=json.loads(a.effective.read_text());errors=lineage.validate_effective(effective)
if errors:raise SystemExit("outcome-context:invalid:"+",".join(errors))
pred=effective["predecessor"]
with open(os.environ["GITHUB_OUTPUT"],"a") as f:
 f.write("predecessor_id="+(pred["candidateId"] or "")+"\n")
 f.write("predecessor_artifact_id="+(pred["artifactId"] or "")+"\n")
 f.write("predecessor_artifact_digest="+((pred["artifactDigest"] or "").removeprefix("sha256:"))+"\n")
