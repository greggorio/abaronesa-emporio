#!/usr/bin/env python3
"""Resolve published outcomes, Git lineage and cumulative effective plan."""
import argparse,json,os,re,subprocess,sys,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/"tools/releases"));sys.path.insert(0,str(ROOT/"tools/candidates"))
import artifact_io,candidate_manifest,candidate_plan,lineage,outcome
REPO="greggorio/abaronesa-emporio"
def api(endpoint):return json.loads(subprocess.check_output(["gh","api",endpoint],text=True))
def _run_errors(run):
 errors=[]
 repository=run.get("repository",{})
 if run.get("name")!="Publish Candidate" or run.get("event")!="workflow_run" or run.get("status")!="completed" or run.get("conclusion")!="success" or run.get("head_branch")!="main":errors.append("run state")
 if run.get("head_repository",{}).get("full_name")!=REPO or repository.get("full_name")!=REPO or repository.get("owner",{}).get("login")!="greggorio":errors.append("run repository")
 if not re.fullmatch(r"[0-9a-f]{40}",str(run.get("head_sha",""))) or not isinstance(run.get("run_attempt"),int) or run["run_attempt"]<1 or not re.fullmatch(r"[1-9][0-9]*",str(run.get("id",""))):errors.append("run identity")
 return errors
def _artifact_errors(artifact,run):
 errors=[]
 workflow_run=artifact.get("workflow_run",{})
 if not re.fullmatch(r"[1-9][0-9]*",str(artifact.get("id",""))) or not re.fullmatch(r"sha256:[0-9a-f]{64}",str(artifact.get("digest",""))):errors.append("artifact identity")
 if str(workflow_run.get("id"))!=str(run.get("id")) or workflow_run.get("head_sha")!=run.get("head_sha"):errors.append("artifact run binding")
 return errors
def _download(artifact,target,name,limits):
 archive=target.parent/(name+".zip");artifact_io.gh_download(REPO,artifact["id"],archive)
 artifact_io.safe_extract_named(archive,target,artifact["digest"],limits,name+".json");archive.unlink()
def _candidate_record(artifact,artifact_run,ov,root):
 if artifact.get("name")!="candidate-manifest" or artifact.get("expired") or _run_errors(artifact_run) or _artifact_errors(artifact,artifact_run):raise ValueError("referenced candidate artifact")
 cdir=root/f"candidate-{artifact['id']}";_download(artifact,cdir,"candidate",artifact_io.LIMITS)
 manifest=json.loads((cdir/"candidate.json").read_text());metadata=json.loads((cdir/"metadata.json").read_text())
 raw_digest=artifact["digest"].removeprefix("sha256:")
 if candidate_manifest.validate_manifest(manifest) or manifest["commitSha"]!=artifact_run["head_sha"] or manifest["workflow"]["runId"]!=str(artifact_run["id"]) or manifest["workflow"]["attempt"]!=artifact_run["run_attempt"]:raise ValueError("candidate run binding")
 if ov["candidateId"]!=manifest["candidateId"] or ov["commitSha"]!=manifest["commitSha"] or ov["candidateArtifactId"]!=str(artifact["id"]) or ov["candidateArtifactDigest"]!=raw_digest:raise ValueError("candidate outcome binding")
 expected_metadata={"schemaVersion":1,"stage":"final","candidateId":manifest["candidateId"],"repository":REPO,"commitSha":manifest["commitSha"],"workflowRunId":str(artifact_run["id"]),"workflowAttempt":manifest["workflow"]["attempt"],"manifestSha256":artifact_io.digest((cdir/"candidate.json").read_bytes())}
 if metadata!=expected_metadata:raise ValueError("candidate metadata")
 return {"candidateId":manifest["candidateId"],"commitSha":manifest["commitSha"],"workflowRunId":str(artifact_run["id"]),"artifactId":str(artifact["id"]),"artifactDigest":artifact["digest"],"manifest":manifest,"directory":cdir}
def discover(current,max_pages,root):
 published={}
 for page in range(1,max_pages+1):
  runs=api(f"/repos/{REPO}/actions/workflows/publish-candidate.yml/runs?status=success&branch=main&per_page=50&page={page}").get("workflow_runs",[])
  for run in runs:
   artifacts=api(f"/repos/{REPO}/actions/runs/{run['id']}/artifacts").get("artifacts",[])
   outs=[a for a in artifacts if a.get("name")=="candidate-outcome"]
   if len(outs)!=1 or outs[0].get("expired"):raise ValueError("successful run outcome absent duplicate or expired")
   odir=root/f"outcome-{run['id']}";_download(outs[0],odir,"outcome",{"outcome.json":16384,"outcome.json.sha256":128})
   ov=json.loads((odir/"outcome.json").read_text())
   if _run_errors(run) or _artifact_errors(outs[0],run) or outcome.validate(ov) or ov["workflowRunId"]!=str(run["id"]) or ov["workflowAttempt"]!=run["run_attempt"] or ov["commitSha"]!=run["head_sha"]:raise ValueError("outcome binding")
   if ov["status"]=="published":
    manifests=[a for a in artifacts if a.get("name")=="candidate-manifest"]
    if len(manifests)!=1 or manifests[0].get("expired"):raise ValueError("published candidate artifact absent duplicate or expired")
    record=_candidate_record(manifests[0],run,ov,root)
    if ov["predecessorCandidateId"]!=record["manifest"]["predecessor"]["candidateId"]:raise ValueError("published predecessor binding")
    published[(record["candidateId"],record["artifactId"])]=record
   elif ov["status"]=="already_published":
    if ov["predecessorCandidateId"]!=ov["candidateId"]:raise ValueError("already published predecessor binding")
    artifact=api(f"/repos/{REPO}/actions/artifacts/{ov['candidateArtifactId']}")
    if not isinstance(artifact,dict):raise ValueError("referenced candidate artifact")
    associated_id=artifact.get("workflow_run",{}).get("id")
    if not re.fullmatch(r"[1-9][0-9]*",str(associated_id)):raise ValueError("referenced candidate run")
    artifact_run=api(f"/repos/{REPO}/actions/runs/{associated_id}")
    if not isinstance(artifact_run,dict):raise ValueError("referenced candidate run")
    record=_candidate_record(artifact,artifact_run,ov,root)
    published[(record["candidateId"],record["artifactId"])]=record
  if len(runs)<50:break
 else:raise ValueError("pagination exhausted")
 return lineage.nearest(list(published.values()),current)
def main():
 p=argparse.ArgumentParser();p.add_argument("--plan",type=Path,required=True);p.add_argument("--output",type=Path,required=True);p.add_argument("--context",type=Path,required=True);p.add_argument("--max-pages",type=int,default=10);p.add_argument("--initial-mode",choices=["continue","superseded"],default="continue");a=p.parse_args()
 try:
  original=json.loads(a.plan.read_text());errors=candidate_plan.validate(original)
  if errors:raise ValueError(",".join(errors))
  a.context.mkdir(parents=True,exist_ok=True)
  with tempfile.TemporaryDirectory() as raw:
   mode,selected=("first",None) if a.initial_mode=="superseded" else discover(original["commitSha"],a.max_pages,Path(raw))
   paths=None if mode=="first" else (lineage.cumulative_paths(selected["commitSha"],original["commitSha"]) if mode=="selected" else [])
   final_mode="superseded" if a.initial_mode=="superseded" else lineage.mode_for(mode,paths)
   effective=lineage.effective(original,selected,paths,final_mode)
   effective_errors=lineage.validate_effective(effective,original)
   if effective_errors:raise ValueError(",".join(effective_errors))
   a.output.mkdir(parents=True,exist_ok=True);(a.output/"candidate-effective-plan.json").write_text(json.dumps(effective,sort_keys=True,separators=(",",":"))+"\n")
   selection=effective["predecessor"];(a.context/"selection.json").write_text(json.dumps(selection,sort_keys=True,separators=(",",":"))+"\n")
   if selected:
    previous=a.context/"previous";previous.mkdir(exist_ok=True)
    for name in ("candidate.json","candidate.json.sha256","metadata.json"):(previous/name).write_bytes((selected["directory"]/name).read_bytes())
  lineage.write_outputs(os.environ["GITHUB_OUTPUT"],effective,final_mode);return 0
 except Exception as e:print("predecessor:invalid:"+str(e),file=sys.stderr);return 3
if __name__=="__main__":raise SystemExit(main())
