import copy,json,os,sys,tempfile,unittest
from pathlib import Path
from unittest import mock

ROOT=Path(__file__).resolve().parents[3]
sys.path[:0]=[str(ROOT/"tools/candidates"),str(ROOT/"tools/releases")]
import artifact_io,candidate_manifest,candidate_plan,catalog,integrated_harness,lineage,outcome,previous_candidate,validate_candidate_workflow

class TerminalAmendmentTest(unittest.TestCase):
 def manifest(self):return json.loads(candidate_manifest.EXAMPLE.read_text())
 def plan(self):
  return candidate_plan.generate(catalog.resolve(catalog.load_yaml(),first_release=True),{"ref":"refs/heads/main","before":"0"*40,"after":"1"*40},"1"*40,"100",1)
 def selected(self,directory=None):
  manifest=self.manifest()
  return {"candidateId":manifest["candidateId"],"commitSha":manifest["commitSha"],"workflowRunId":manifest["workflow"]["runId"],"artifactId":"20","artifactDigest":"sha256:"+"b"*64,"directory":directory}
 def write_candidate(self,directory,metadata_mutation=None):
  directory=Path(directory);directory.mkdir(parents=True,exist_ok=True);manifest=self.manifest();data=artifact_io.canonical(manifest)
  (directory/"candidate.json").write_bytes(data);(directory/"candidate.json.sha256").write_bytes(artifact_io.sidecar(data))
  metadata={"schemaVersion":1,"stage":"final","candidateId":manifest["candidateId"],"repository":manifest["repository"],"commitSha":manifest["commitSha"],"workflowRunId":manifest["workflow"]["runId"],"workflowAttempt":manifest["workflow"]["attempt"],"manifestSha256":artifact_io.digest(data)}
  if metadata_mutation:metadata_mutation(metadata)
  (directory/"metadata.json").write_bytes(artifact_io.canonical(metadata))

 def test_01_effective_and_validator_accept_all_four_modes(self):
  plan=self.plan();selected=self.selected()
  values=[
   lineage.effective(plan,None,None,"continue"),
   lineage.effective(plan,selected,[],"already_published"),
   lineage.effective(plan,selected,[],"no_changes"),
   lineage.effective(plan,None,None,"superseded"),
  ]
  self.assertEqual(lineage.MODES,{value["mode"] for value in values})
  for value in values:
   self.assertEqual(2,value["schemaVersion"])
   self.assertEqual([],lineage.validate_effective(value,plan))

 def test_02_terminal_cli_writes_v2_empty_matrix_and_success(self):
  cases={"no_changes":"selected","already_published":"already_published","superseded":"superseded"}
  for expected,discovery_mode in cases.items():
   with self.subTest(mode=expected),tempfile.TemporaryDirectory() as raw:
    root=Path(raw);plan_path=root/"plan.json";plan_path.write_bytes(artifact_io.canonical(self.plan()));candidate_dir=root/"candidate";self.write_candidate(candidate_dir)
    selected=self.selected(candidate_dir);output=root/"effective";context=root/"context";github_output=root/"github-output"
    argv=["previous_candidate.py","--plan",str(plan_path),"--output",str(output),"--context",str(context)]
    paths=[] if expected=="no_changes" else None
    with mock.patch.object(sys,"argv",argv),mock.patch.dict(os.environ,{"GITHUB_OUTPUT":str(github_output)}),mock.patch.object(previous_candidate,"discover",return_value=(discovery_mode,selected)),mock.patch.object(lineage,"cumulative_paths",return_value=paths or []):
     self.assertEqual(0,previous_candidate.main())
    effective=json.loads((output/"candidate-effective-plan.json").read_text());self.assertEqual(expected,effective["mode"]);self.assertEqual(2,effective["schemaVersion"]);self.assertEqual([],lineage.validate_effective(effective,self.plan()))
    outputs=dict(line.split("=",1) for line in github_output.read_text().splitlines());self.assertEqual(expected,outputs["mode"]);self.assertEqual({"include":[]},json.loads(outputs["matrix"]));self.assertEqual("false",outputs["has_builds"])

 def test_03_terminal_modes_gate_jobs_and_produce_outcomes(self):
  self.assertEqual([],validate_candidate_workflow.validate_workflows())
  source=(ROOT/".github/workflows/publish-candidate.yml").read_text()
  self.assertIn("needs.predecessor.outputs.mode == 'continue'",source)
  self.assertIn("needs.predecessor.outputs.mode != 'continue'",source)
  candidate={"id":"candidate-existing","artifactId":"20","artifactDigest":"b"*64}
  values=[
   outcome.make("no_changes","1"*40,"300",1,"candidate-old"),
   outcome.make("superseded","1"*40,"300",1,None),
   outcome.make("already_published","1"*40,"300",1,"candidate-existing",candidate),
  ]
  for value in values:self.assertEqual([],outcome.validate(value))

 def test_04_terminal_resolution_and_warning_mutants_fail(self):
  for mode in ("no_changes","already_published","superseded"):
   predecessor=None if mode=="superseded" else self.selected()
   value=lineage.effective(self.plan(),predecessor,[],mode)
   for field,mutation in (("classification","wrong"),("warnings",["WRONG_WARNING"]),("inheritedComponents",[])):
    mutant=copy.deepcopy(value);mutant["resolution"][field]=mutation
    with self.subTest(mode=mode,field=field):self.assertIn("EFFECTIVE_RESOLUTION",lineage.validate_effective(mutant))

 def test_05_terminal_effective_rejects_pending_before_docker(self):
  pending={key:value for key,value in self.manifest().items() if key!="integration"}
  effective=lineage.effective(self.plan(),None,None,"superseded")
  with tempfile.TemporaryDirectory() as raw:
   root=Path(raw);pending_dir=root/"pending";data=artifact_io.canonical(pending)
   metadata={"schemaVersion":1,"stage":"pending","repository":pending["repository"],"commitSha":pending["commitSha"],"workflowRunId":pending["workflow"]["runId"],"workflowAttempt":pending["workflow"]["attempt"],"pendingSha256":artifact_io.digest(data)}
   artifact_io.atomic_bundle(pending_dir,{"pending.json":data,"pending.json.sha256":artifact_io.sidecar(data),"metadata.json":artifact_io.canonical(metadata)})
   effective_path=root/"effective.json";effective_path.write_bytes(artifact_io.canonical(effective));selection=root/"selection.json";selection.write_bytes(artifact_io.canonical(effective["predecessor"]));calls=[]
   with self.assertRaisesRegex(ValueError,"mode"):integrated_harness.execute_bundle(pending_dir,effective_path,selection,Path("base"),Path("override"),"candidate-300-1",root/"out",lambda args,**kwargs:calls.append(args),lambda args,**kwargs:calls.append(args))
   self.assertEqual([],calls)

 def _already_fixture(self,mutation=None):
  manifest=self.manifest();sha=manifest["commitSha"]
  current_run={"id":300,"run_attempt":1,"name":"Publish Candidate","event":"workflow_run","status":"completed","conclusion":"success","head_branch":"main","head_sha":sha,"head_repository":{"full_name":previous_candidate.REPO},"repository":{"full_name":previous_candidate.REPO,"owner":{"login":"greggorio"}}}
  published_run={**current_run,"id":200}
  outcome_artifact={"id":10,"name":"candidate-outcome","expired":False,"digest":"sha256:"+"a"*64,"workflow_run":{"id":300,"head_sha":sha}}
  candidate_artifact={"id":20,"name":"candidate-manifest","expired":False,"digest":"sha256:"+"b"*64,"workflow_run":{"id":200,"head_sha":sha}}
  reference={"id":manifest["candidateId"],"artifactId":"20","artifactDigest":"b"*64}
  outcome_value=outcome.make("already_published",sha,"300",1,manifest["candidateId"],reference)
  state={"artifact":candidate_artifact,"published_run":published_run,"metadata_mutation":None,"artifact_missing":False}
  if mutation:mutation(outcome_value,state)
  def api(endpoint):
   if "/runs?" in endpoint:return {"workflow_runs":[current_run]}
   if endpoint.endswith("/runs/300/artifacts"):return {"artifacts":[outcome_artifact]}
   if "/actions/artifacts/20" in endpoint:return {} if state["artifact_missing"] else state["artifact"]
   if endpoint.endswith("/runs/"+str(state["artifact"].get("workflow_run",{}).get("id"))):return state["published_run"]
   raise AssertionError(endpoint)
  def download(artifact,target,name,limits):
   target.mkdir(parents=True,exist_ok=True)
   if name=="outcome":
    data=artifact_io.canonical(outcome_value);(target/"outcome.json").write_bytes(data);(target/"outcome.json.sha256").write_bytes(artifact_io.sidecar(data))
   else:self.write_candidate(target,state["metadata_mutation"])
  return api,download,manifest

 def test_06_already_published_reference_mutants_fail_closed(self):
  api,download,manifest=self._already_fixture()
  with tempfile.TemporaryDirectory() as raw,mock.patch.object(previous_candidate,"api",side_effect=api),mock.patch.object(previous_candidate,"_download",side_effect=download):
   self.assertEqual("already_published",previous_candidate.discover(manifest["commitSha"],1,Path(raw))[0])
  mutations=[
   lambda value,state:state.update(artifact_missing=True),
   lambda value,state:state["artifact"].update(expired=True),
   lambda value,state:state["artifact"].update(name="wrong"),
   lambda value,state:state["artifact"].update(digest="sha256:"+"c"*64),
   lambda value,state:(state["artifact"]["workflow_run"].update(id=201),state["published_run"].update(id=201)),
   lambda value,state:value.update(candidateId="candidate-wrong",predecessorCandidateId="candidate-wrong"),
   lambda value,state:state.update(metadata_mutation=lambda metadata:metadata.update(candidateId="candidate-wrong")),
  ]
  for mutation in mutations:
   api,download,manifest=self._already_fixture(mutation)
   with self.subTest(mutation=mutation),tempfile.TemporaryDirectory() as raw,mock.patch.object(previous_candidate,"api",side_effect=api),mock.patch.object(previous_candidate,"_download",side_effect=download):
    with self.assertRaises(ValueError):previous_candidate.discover(manifest["commitSha"],1,Path(raw))

 def test_07_predecessor_candidate_binding_for_published_and_already(self):
  candidate={"id":"candidate-existing","artifactId":"20","artifactDigest":"b"*64}
  already=outcome.make("already_published","1"*40,"300",1,"candidate-existing",candidate);already["predecessorCandidateId"]="candidate-other"
  self.assertIn("OUTCOME_PREDECESSOR",outcome.validate(already))
  api,download,manifest=self._published_fixture_with_wrong_predecessor()
  with tempfile.TemporaryDirectory() as raw,mock.patch.object(previous_candidate,"api",side_effect=api),mock.patch.object(previous_candidate,"_download",side_effect=download):
   with self.assertRaisesRegex(ValueError,"predecessor"):previous_candidate.discover(manifest["commitSha"],1,Path(raw))

 def _published_fixture_with_wrong_predecessor(self):
  manifest=self.manifest();sha=manifest["commitSha"];run={"id":200,"run_attempt":1,"name":"Publish Candidate","event":"workflow_run","status":"completed","conclusion":"success","head_branch":"main","head_sha":sha,"head_repository":{"full_name":previous_candidate.REPO},"repository":{"full_name":previous_candidate.REPO,"owner":{"login":"greggorio"}}}
  workflow_run={"id":200,"head_sha":sha};out_artifact={"id":10,"name":"candidate-outcome","expired":False,"digest":"sha256:"+"a"*64,"workflow_run":workflow_run};candidate_artifact={"id":20,"name":"candidate-manifest","expired":False,"digest":"sha256:"+"b"*64,"workflow_run":workflow_run}
  value=outcome.make("published",sha,"200",1,"candidate-wrong",{"id":manifest["candidateId"],"artifactId":"20","artifactDigest":"b"*64})
  def api(endpoint):return {"workflow_runs":[run]} if "/runs?" in endpoint else {"artifacts":[out_artifact,candidate_artifact]}
  def download(artifact,target,name,limits):
   target.mkdir(parents=True,exist_ok=True)
   if name=="outcome":
    data=artifact_io.canonical(value);(target/"outcome.json").write_bytes(data);(target/"outcome.json.sha256").write_bytes(artifact_io.sidecar(data))
   else:self.write_candidate(target)
  return api,download,manifest

 def test_08_terminal_outputs_use_real_lf(self):
  selected=self.selected()
  for mode in ("already_published","no_changes","superseded"):
   effective=lineage.effective(self.plan(),None if mode=="superseded" else selected,[],mode)
   with self.subTest(mode=mode),tempfile.TemporaryDirectory() as raw:
    path=Path(raw)/"output";lineage.write_outputs(path,effective,mode);data=path.read_bytes()
    self.assertEqual(3,data.count(b"\n"));self.assertNotIn(b"\\n",data);self.assertIn(b"matrix={\"include\":[]}\n",data);self.assertIn(b"has_builds=false\n",data)

if __name__=="__main__":unittest.main()
