import copy,json,os,subprocess,sys,tempfile,unittest
from pathlib import Path
from unittest import mock

ROOT=Path(__file__).resolve().parents[3]
sys.path[:0]=[str(ROOT/"tools/candidates"),str(ROOT/"tools/releases")]
import artifact_io,candidate_manifest,candidate_plan,catalog,cleanup_image,compose_env,finalize_candidate,integrated_harness,lineage,outcome,previous_candidate,publish_guard,trust,validate_pending

class CausalCorrectionsTest(unittest.TestCase):
 def example(self):return json.loads(candidate_manifest.EXAMPLE.read_text())
 def pending(self):return {key:value for key,value in self.example().items() if key!="integration"}
 def effective(self,pending=None):
  value=pending or self.pending()
  return {"schemaVersion":2,"kind":"candidate-effective-plan","mode":"continue","repository":value["repository"],"commitSha":value["commitSha"],"sourceCi":value["sourceCi"],"catalog":value["catalog"],"predecessor":value["predecessor"],"resolution":value["resolution"]}
 def receipt(self,pending=None):
  pending=pending or self.pending();integration=self.example()["integration"]
  return {"schemaVersion":1,"status":"passed","repository":pending["repository"],"commitSha":pending["commitSha"],"workflowRunId":pending["workflow"]["runId"],"workflowAttempt":pending["workflow"]["attempt"],"pendingSha256":artifact_io.digest(artifact_io.canonical(pending)),"checkedAt":integration["checkedAt"],"services":integration["services"],"probes":integration["probes"],"cleanup":integration["cleanup"]}
 def bundle(self,root,pending=None,effective=None):
  root=Path(root);pending=pending or self.pending();effective=effective or self.effective(pending)
  directory=root/"pending";data=artifact_io.canonical(pending)
  metadata={"schemaVersion":1,"stage":"pending","repository":pending["repository"],"commitSha":pending["commitSha"],"workflowRunId":pending["workflow"]["runId"],"workflowAttempt":pending["workflow"]["attempt"],"pendingSha256":artifact_io.digest(data)}
  artifact_io.atomic_bundle(directory,{"pending.json":data,"pending.json.sha256":artifact_io.sidecar(data),"metadata.json":artifact_io.canonical(metadata)})
  effective_path=root/"effective.json";effective_path.write_bytes(artifact_io.canonical(effective))
  selection=root/"selection.json";selection.write_bytes(artifact_io.canonical(effective["predecessor"]))
  return directory,effective_path,selection
 def receipt_bundle(self,root,pending):
  directory=Path(root)/"receipt";receipt=self.receipt(pending);data=artifact_io.canonical(receipt)
  artifact_io.atomic_bundle(directory,{"integration-result.json":data,"integration-result.json.sha256":artifact_io.sidecar(data)})
  return directory

 def test_01_distinct_ci_and_publisher_runs_complete_flow(self):
  pending=self.pending()
  self.assertEqual("100",pending["sourceCi"]["runId"])
  self.assertEqual("200",pending["workflow"]["runId"])
  with tempfile.TemporaryDirectory() as raw:
   root=Path(raw);pd,effective,selection=self.bundle(root,pending);rd=self.receipt_bundle(root,pending);ctx=root/"context";ctx.mkdir();(ctx/"selection.json").write_bytes(selection.read_bytes())
   final=finalize_candidate.finalize(pd,rd,effective,ctx,root/"final")
   self.assertEqual([],candidate_manifest.validate_manifest(final,self.effective(pending),self.receipt(pending)))

 def test_02_effective_plan_is_strict_and_canonical(self):
  plan=candidate_plan.generate(catalog.resolve(catalog.load_yaml(),first_release=True),{"ref":"refs/heads/main","before":"0"*40,"after":"1"*40},"1"*40,"100",1)
  valid=lineage.effective(plan,None)
  self.assertEqual([],lineage.validate_effective(valid,plan))
  mutants=[]
  extra=copy.deepcopy(valid);extra["extra"]=1;mutants.append(extra)
  resolution=copy.deepcopy(valid);resolution["resolution"]["warnings"]=[];mutants.append(resolution)
  predecessor=copy.deepcopy(valid);predecessor["predecessor"]["artifactId"]="1";mutants.append(predecessor)
  for mutant in mutants:self.assertTrue(lineage.validate_effective(mutant))

 def test_03_lineage_classifies_entire_set_before_terminal_decision(self):
  for relations in (["same","unrelated"],["descendant","unrelated"]):
   with self.subTest(relations=relations),mock.patch("lineage.classify",side_effect=relations):
    with self.assertRaisesRegex(ValueError,"unrelated"):lineage.nearest([{"commitSha":"1"*40},{"commitSha":"2"*40}],"3"*40)

 def _discovery_fixture(self,mutate):
  manifest=self.example();run={"id":200,"run_attempt":1,"name":"Publish Candidate","event":"workflow_run","status":"completed","conclusion":"success","head_branch":"main","head_sha":manifest["commitSha"],"head_repository":{"full_name":previous_candidate.REPO},"repository":{"full_name":previous_candidate.REPO,"owner":{"login":"greggorio"}}}
  workflow_run={"id":200,"head_sha":manifest["commitSha"]}
  outcome_artifact={"id":10,"name":"candidate-outcome","expired":False,"digest":"sha256:"+"a"*64,"workflow_run":workflow_run}
  candidate_artifact={"id":20,"name":"candidate-manifest","expired":False,"digest":"sha256:"+"b"*64,"workflow_run":workflow_run}
  candidate_ref={"id":manifest["candidateId"],"artifactId":"20","artifactDigest":"b"*64}
  outcome_value=outcome.make("published",manifest["commitSha"],"200",1,None,candidate_ref)
  mutate(outcome_value,candidate_artifact,run,manifest)
  def api(endpoint):
   if "/runs?" in endpoint:return {"workflow_runs":[run]}
   return {"artifacts":[outcome_artifact,candidate_artifact]}
  def download(artifact,target,name,limits):
   target.mkdir(parents=True,exist_ok=True)
   if name=="outcome":
    data=artifact_io.canonical(outcome_value);(target/"outcome.json").write_bytes(data);(target/"outcome.json.sha256").write_bytes(artifact_io.sidecar(data))
   else:
    data=artifact_io.canonical(manifest);(target/"candidate.json").write_bytes(data);(target/"candidate.json.sha256").write_bytes(artifact_io.sidecar(data))
    metadata={"schemaVersion":1,"stage":"final","candidateId":manifest["candidateId"],"repository":previous_candidate.REPO,"commitSha":manifest["commitSha"],"workflowRunId":"200","workflowAttempt":1,"manifestSha256":artifact_io.digest(data)}
    (target/"metadata.json").write_bytes(artifact_io.canonical(metadata))
  return api,download,manifest

 def test_04_outcome_rest_manifest_metadata_exact_binding(self):
  api,download,manifest=self._discovery_fixture(lambda *args:None)
  with tempfile.TemporaryDirectory() as raw,mock.patch.object(previous_candidate,"api",side_effect=api),mock.patch.object(previous_candidate,"_download",side_effect=download):
   self.assertEqual("already_published",previous_candidate.discover(manifest["commitSha"],1,Path(raw))[0])
  mutations=[
   lambda value,artifact,run,manifest:value.update(candidateId="candidate-wrong"),
   lambda value,artifact,run,manifest:value.update(candidateArtifactId="21"),
   lambda value,artifact,run,manifest:value.update(candidateArtifactDigest="c"*64),
   lambda value,artifact,run,manifest:value.update(workflowAttempt=2),
  ]
  for mutate in mutations:
   api,download,manifest=self._discovery_fixture(mutate)
   with self.subTest(mutate=mutate),tempfile.TemporaryDirectory() as raw,mock.patch.object(previous_candidate,"api",side_effect=api),mock.patch.object(previous_candidate,"_download",side_effect=download):
    with self.assertRaisesRegex(ValueError,"binding"):previous_candidate.discover(manifest["commitSha"],1,Path(raw))

 def test_05_pending_bundle_tamper_blocks_all_docker_commands(self):
  mutations=("sidecar","metadata","component","image","binding")
  for mutation in mutations:
   with self.subTest(mutation=mutation),tempfile.TemporaryDirectory() as raw:
    root=Path(raw);pending=self.pending();directory,effective,selection=self.bundle(root,pending)
    if mutation=="sidecar":(directory/"pending.json.sha256").write_text("0"*64+"\n")
    elif mutation=="metadata":
     metadata=json.loads((directory/"metadata.json").read_text());metadata["workflowRunId"]="999";(directory/"metadata.json").write_bytes(artifact_io.canonical(metadata))
    else:
     value=json.loads((directory/"pending.json").read_text())
     if mutation=="component":value["components"][0]["state"]="inherited"
     elif mutation=="image":value["components"][0]["immutableRef"]=value["components"][1]["immutableRef"]
     else:value["sourceCi"]["runId"]="999"
     data=artifact_io.canonical(value);(directory/"pending.json").write_bytes(data);(directory/"pending.json.sha256").write_bytes(artifact_io.sidecar(data))
     metadata=json.loads((directory/"metadata.json").read_text());metadata["pendingSha256"]=artifact_io.digest(data);(directory/"metadata.json").write_bytes(artifact_io.canonical(metadata))
    calls=[]
    with self.assertRaises(ValueError):integrated_harness.execute_bundle(directory,effective,selection,Path("base"),Path("override"),"candidate-200-1",root/"out",lambda args,**kwargs:calls.append(args),lambda args,**kwargs:calls.append(args))
    self.assertEqual([],calls)

 def test_06_receipt_rejects_extra_duplicate_and_divergent_binding(self):
  pending=self.pending();valid=self.receipt(pending)
  mutants=[]
  extra=copy.deepcopy(valid);extra["extra"]=1;mutants.append(extra)
  service=copy.deepcopy(valid);service["services"][1]=copy.deepcopy(service["services"][0]);mutants.append(service)
  probe=copy.deepcopy(valid);probe["probes"][1]=copy.deepcopy(probe["probes"][0]);mutants.append(probe)
  binding=copy.deepcopy(valid);binding["workflowRunId"]="999";mutants.append(binding)
  for mutant in mutants:self.assertTrue(candidate_manifest.validate_receipt(mutant,pending))

 def test_07_trust_requires_canonical_repository_full_name(self):
  good={"conclusion":"success","name":"CI","event":"push","head_branch":"main","head_repository":{"full_name":trust.REPO},"repository":{"full_name":trust.REPO,"owner":{"login":"greggorio"}},"head_sha":"1"*40,"run_attempt":1}
  self.assertEqual([],trust.event(good));bad=copy.deepcopy(good);bad["repository"]["full_name"]="fork/repo";self.assertTrue(trust.event(bad))

 def test_08_component_time_tag_labels_state_origin_and_provenance_are_bound(self):
  pending=self.pending()
  changes=[
   lambda c:c.update(builtAt="not-a-time"),
   lambda c:c.update(tag=c["imageRepository"]+":latest"),
   lambda c:c["labels"].update({"org.opencontainers.image.revision":"2"*40}),
   lambda c:c.update(state="inherited"),
   lambda c:c.update(originCandidateId="candidate-old"),
   lambda c:c["provenance"].update(verifiedSubject="ghcr.io/wrong@sha256:"+"0"*64),
  ]
  for change in changes:
   mutant=copy.deepcopy(pending);change(mutant["components"][0]);self.assertTrue(candidate_manifest.validate_pending(mutant),change)

 def _harness_doubles(self,failure):
  calls=[]
  class Done:
   def __init__(self,code=0):self.returncode=code
  def runner(args,**kwargs):
   calls.append(tuple(args));joined=" ".join(args)
   if failure=="down" and " down " in " "+joined+" ":return Done(1)
   if failure=="remove" and args[:3]==["docker","image","rm"]:return Done(1)
   if args[:3]==["docker","image","inspect"]:return Done(0 if failure=="inspect" else 1)
   if failure=="logout" and args[:2]==["docker","logout"]:return Done(1)
   return Done()
  model={"services":{name:{} for name in integrated_harness.SERVICES}};model["services"]["gateway"]["ports"]=[{"host_ip":"127.0.0.1","published":"49123","target":8080}]
  rows=[{"Service":name,"State":"running","Health":"healthy"} for name in integrated_harness.SERVICES]
  def output(args,**kwargs):
   calls.append(tuple(args))
   if "config" in args:return json.dumps(model)
   if "ps" in args and "--format" in args:return json.dumps(rows)
   if failure=="count" and args[:2]==["docker","ps"]:raise subprocess.CalledProcessError(1,args)
   return ""
  return calls,runner,output

 def test_09_harness_cleanup_attempts_every_step_after_each_failure(self):
  probes=[{"id":probe,"status":"passed"} for probe in candidate_manifest.PROBES[:-1]]
  for failure in ("down","remove","inspect","count","logout"):
   calls,runner,output=self._harness_doubles(failure)
   with self.subTest(failure=failure),tempfile.TemporaryDirectory() as raw,mock.patch.dict(os.environ,{"CANDIDATE_GATEWAY_PORT":"49123"}),mock.patch("integrated_harness.probe_candidate.run",return_value=probes):
    with self.assertRaisesRegex(ValueError,"cleanup"):integrated_harness.execute(self.pending(),Path("base"),Path("override"),"candidate-200-1",Path(raw),runner,output)
    joined=[" ".join(call) for call in calls]
    self.assertTrue(any(" down " in " "+call+" " for call in joined))
    self.assertEqual(6,sum(call.startswith("docker image rm ") for call in joined))
    self.assertEqual(6,sum(call.startswith("docker image inspect ") for call in joined))
    self.assertTrue(any(call.startswith("docker ps ") for call in joined))
    self.assertTrue(any(call.startswith("docker volume ls ") for call in joined))
    self.assertTrue(any(call.startswith("docker network ls ") for call in joined))
    self.assertTrue(any(call=="docker logout ghcr.io" for call in joined))

 def test_10_build_cleanup_is_cumulative(self):
  class Done:
   def __init__(self,code):self.returncode=code
  for failure in ("logout","remove","inspect"):
   calls=[]
   def runner(args,**kwargs):
    calls.append(args)
    if args[:2]==["docker","logout"]:return Done(1 if failure=="logout" else 0)
    if args[:3]==["docker","image","rm"]:return Done(1 if failure=="remove" else 0)
    return Done(0 if failure=="inspect" else 1)
   self.assertTrue(cleanup_image.cleanup("repo@sha256:"+"0"*64,runner))
   self.assertEqual(3,len(calls))

 def test_11_partial_staging_failure_leaves_no_residue(self):
  with tempfile.TemporaryDirectory() as raw:
   directory=Path(raw);original=artifact_io._stage;count=0
   def stage(target,name,data):
    nonlocal count
    count+=1
    if count==2:raise OSError("staging failure")
    return original(target,name,data)
   with mock.patch("artifact_io._stage",side_effect=stage):
    with self.assertRaises(OSError):artifact_io.atomic_bundle(directory,{"one":b"1","two":b"2"})
   self.assertEqual([],list(directory.iterdir()))

 def test_12_publish_guard_same_advanced_and_unrelated(self):
  with mock.patch("publish_guard.lineage.classify",return_value="same"):self.assertEqual("continue",publish_guard.decide("1"*40,"1"*40,"first",None))
  with mock.patch("publish_guard.lineage.classify",return_value="ancestor"):self.assertEqual("superseded",publish_guard.decide("1"*40,"2"*40,"first",None))
  with mock.patch("publish_guard.lineage.classify",return_value="unrelated"):
   with self.assertRaisesRegex(ValueError,"HEAD"):publish_guard.decide("1"*40,"2"*40,"first",None)

 def test_13_github_env_uses_real_lf(self):
  with tempfile.TemporaryDirectory() as raw:
   root=Path(raw);directory,effective,selection=self.bundle(root);env_file=root/"env"
   argv=["compose_env.py","--pending-dir",str(directory),"--effective",str(effective),"--selection",str(selection),"--run","200","--attempt","1"]
   with mock.patch.object(sys,"argv",argv),mock.patch.dict(os.environ,{"GITHUB_ENV":str(env_file),"CANDIDATE_POSTGRES_IMAGE":"postgres@example"}):compose_env.main()
   data=env_file.read_bytes();self.assertNotIn(b"\\n",data);self.assertGreater(data.count(b"\n"),20)

if __name__=="__main__":unittest.main()
