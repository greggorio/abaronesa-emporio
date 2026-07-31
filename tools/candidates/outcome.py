#!/usr/bin/env python3
import argparse,json,re,sys
from pathlib import Path
import artifact_io
KEYS={"schemaVersion","status","repository","commitSha","workflowRunId","workflowAttempt","candidateId","candidateArtifactId","candidateArtifactDigest","predecessorCandidateId"}
STATUSES={"published","already_published","no_changes","superseded"}
def validate(value):
 errors=[]
 if set(value)!=KEYS or value.get("schemaVersion")!=1:errors.append("OUTCOME_SHAPE")
 if value.get("status") not in STATUSES or value.get("repository")!="greggorio/abaronesa-emporio" or not re.fullmatch(r"[0-9a-f]{40}",str(value.get("commitSha",""))):errors.append("OUTCOME_IDENTITY")
 if not re.fullmatch(r"[1-9][0-9]*",str(value.get("workflowRunId",""))) or not isinstance(value.get("workflowAttempt"),int) or value["workflowAttempt"]<1:errors.append("OUTCOME_RUN")
 refs=(value.get("candidateId"),value.get("candidateArtifactId"),value.get("candidateArtifactDigest"))
 if value.get("status") in {"published","already_published"}:
  if not refs[0] or not re.fullmatch(r"[1-9][0-9]*",str(refs[1])) or not re.fullmatch(r"[0-9a-f]{64}",str(refs[2])):errors.append("OUTCOME_CANDIDATE")
  if value.get("status")=="already_published" and value.get("predecessorCandidateId")!=value.get("candidateId"):errors.append("OUTCOME_PREDECESSOR")
 elif refs!=(None,None,None):errors.append("OUTCOME_NULLS")
 return errors
def make(status,sha,run,attempt,predecessor=None,candidate=None):
 value={"schemaVersion":1,"status":status,"repository":"greggorio/abaronesa-emporio","commitSha":sha,"workflowRunId":str(run),"workflowAttempt":int(attempt),"candidateId":None,"candidateArtifactId":None,"candidateArtifactDigest":None,"predecessorCandidateId":predecessor}
 if candidate:value.update(candidateId=candidate["id"],candidateArtifactId=str(candidate["artifactId"]),candidateArtifactDigest=candidate["artifactDigest"])
 errors=validate(value)
 if errors:raise ValueError(",".join(errors))
 return value
def main():
 p=argparse.ArgumentParser();p.add_argument("--status",required=True);p.add_argument("--sha",required=True);p.add_argument("--run",required=True);p.add_argument("--attempt",type=int,required=True);p.add_argument("--predecessor");p.add_argument("--candidate-id");p.add_argument("--artifact-id");p.add_argument("--artifact-digest");p.add_argument("--output",type=Path,required=True);a=p.parse_args()
 try:
  candidate={"id":a.candidate_id,"artifactId":a.artifact_id,"artifactDigest":a.artifact_digest} if a.candidate_id else None
  value=make(a.status,a.sha,a.run,a.attempt,a.predecessor or None,candidate);data=artifact_io.canonical(value);artifact_io.atomic_bundle(a.output,{"outcome.json":data,"outcome.json.sha256":artifact_io.sidecar(data)});return 0
 except Exception as e:print("outcome:invalid:"+str(e),file=sys.stderr);return 3
if __name__=="__main__":raise SystemExit(main())
