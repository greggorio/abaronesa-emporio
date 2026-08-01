import copy,hashlib,json,os,subprocess,sys,tempfile,unittest,urllib.error,zipfile
from pathlib import Path
from unittest import mock
ROOT=Path(__file__).resolve().parents[3];sys.path[:0]=[str(ROOT/"tools/candidates"),str(ROOT/"tools/releases")]
import artifact_io,candidate_plan,catalog,image_result,integrated_harness,lineage,outcome,previous_candidate,trust,validate_candidate_workflow as workflow
class DefinitiveContractTest(unittest.TestCase):
 def resolution(self):return catalog.resolve(catalog.load_yaml(),first_release=True)
 def plan(self):
  return candidate_plan.generate(self.resolution(),{"ref":"refs/heads/main","before":"0"*40,"after":"1"*40},"1"*40,"100",1)
 def test_01_workflow_graph_permissions_and_protocol(self):self.assertEqual([],workflow.validate_workflows())
 def test_02_plan_v2_exact_and_lf(self):
  value=self.plan();self.assertEqual(2,value["schemaVersion"]);self.assertEqual(set(candidate_plan.KEYS),set(value))
  with tempfile.TemporaryDirectory() as raw:
   path=Path(raw)/"plan";candidate_plan.write(path,value);self.assertTrue(path.read_bytes().endswith(b"\n"));self.assertNotIn(b"\\n",path.read_bytes())
 def test_03_plan_mutants(self):
  for key,value in (("schemaVersion",1),("repository","fork/repo"),("commitSha","A"*40),("baseCommitSha","x"),("ref","refs/pull/1"),("workflowRunId","0"),("workflowAttempt",0),("catalogSha256","sha256:"+"0"*64)):
   mutant=self.plan();mutant[key]=value
   with self.subTest(key=key):self.assertTrue(candidate_plan.validate(mutant))
  mutant=self.plan();mutant["extra"]=1;self.assertIn("PLAN_SHAPE",candidate_plan.validate(mutant))
 def test_04_plan_event_mutants(self):
  for event in ({"ref":"refs/pull/1","after":"1"*40},{"ref":"refs/heads/main","after":"2"*40},{"ref":"refs/heads/main","after":"1"*40,"pull_request":{}}):
   with self.assertRaises(ValueError):candidate_plan.generate(self.resolution(),event,"1"*40,"100",1)
 def _repo(self):
  temp=tempfile.TemporaryDirectory();repo=Path(temp.name)
  subprocess.run(["git","init","-q",str(repo)],check=True);subprocess.run(["git","-C",str(repo),"config","user.email","fixture@invalid"],check=True);subprocess.run(["git","-C",str(repo),"config","user.name","Fixture"],check=True)
  commits=[]
  for i in range(4):
   (repo/"f").write_text(str(i));subprocess.run(["git","-C",str(repo),"add","f"],check=True);subprocess.run(["git","-C",str(repo),"commit","-qm",str(i)],check=True);commits.append(subprocess.check_output(["git","-C",str(repo),"rev-parse","HEAD"],text=True).strip())
  return temp,repo,commits
 def test_05_lineage_same_ancestor_descendant(self):
  temp,repo,c=self._repo()
  try:self.assertEqual("same",lineage.classify(c[3],c[3],repo));self.assertEqual("ancestor",lineage.classify(c[1],c[3],repo));self.assertEqual("descendant",lineage.classify(c[3],c[1],repo))
  finally:temp.cleanup()
 def test_06_lineage_unrelated_and_rewrite(self):
  temp,repo,c=self._repo()
  try:
   subprocess.run(["git","-C",str(repo),"checkout","--orphan","other"],check=True,stdout=subprocess.DEVNULL);(repo/"f").write_text("other");subprocess.run(["git","-C",str(repo),"add","f"],check=True);subprocess.run(["git","-C",str(repo),"commit","-qm","other"],check=True);other=subprocess.check_output(["git","-C",str(repo),"rev-parse","HEAD"],text=True).strip()
   self.assertEqual("unrelated",lineage.classify(c[3],other,repo))
   with self.assertRaisesRegex(ValueError,"unrelated"):lineage.nearest([{"commitSha":other}],c[3],repo)
  finally:temp.cleanup()
 def test_07_nearest_by_distance_and_same_descendant(self):
  temp,repo,c=self._repo()
  try:
   candidates=[{"commitSha":c[0],"candidateId":"a"},{"commitSha":c[2],"candidateId":"b"}];self.assertEqual("b",lineage.nearest(candidates,c[3],repo)[1]["candidateId"])
   self.assertEqual("already_published",lineage.nearest([{"commitSha":c[3]}],c[3],repo)[0]);self.assertEqual("superseded",lineage.nearest([{"commitSha":c[3]}],c[1],repo)[0])
  finally:temp.cleanup()
 def test_08_cumulative_nul_and_no_changes(self):
  temp,repo,c=self._repo()
  try:self.assertEqual(["f"],lineage.cumulative_paths(c[0],c[3],repo));self.assertEqual("no_changes",lineage.mode_for("selected",[]))
  finally:temp.cleanup()
 def test_09_effective_first_and_transitive(self):
  first=lineage.effective(self.plan(),None);self.assertEqual(catalog.CANONICAL,first["resolution"]["buildComponents"])
  pred={"candidateId":"old","commitSha":"2"*40,"workflowRunId":"90","artifactId":"9","artifactDigest":"sha256:"+"9"*64}
  inc=lineage.effective(self.plan(),pred,["backend/src/A.java"]);self.assertEqual(["backend"],inc["resolution"]["buildComponents"]);self.assertIn("gateway",inc["resolution"]["validationComponents"])
 def test_10_github_output_real_lf(self):
  with tempfile.TemporaryDirectory() as raw:
   path=Path(raw)/"out";lineage.write_outputs(path,lineage.effective(self.plan(),None),"continue");data=path.read_bytes();self.assertNotIn(b"\\n",data);self.assertEqual(3,data.count(b"\n"))
 def test_11_safe_gh_command_binary_stdout(self):
  with tempfile.TemporaryDirectory() as raw:
   target=Path(raw)/"a.zip"
   with mock.patch("artifact_io.subprocess.run") as run:
    run.return_value.returncode=0;run.return_value.args=[];artifact_io.gh_download("greggorio/abaronesa-emporio","12",target)
    args=run.call_args.args[0];self.assertEqual(["gh","api","/repos/greggorio/abaronesa-emporio/actions/artifacts/12/zip"],args);self.assertNotIn("--output",args);self.assertIsNotNone(run.call_args.kwargs["stdout"])
 def _zip(self,entries):
  raw=tempfile.TemporaryDirectory();path=Path(raw.name)/"a.zip"
  with zipfile.ZipFile(path,"w") as z:
   for name,data in entries:z.writestr(name,data)
  return raw,path
 def test_12_safe_zip_positive_and_traversal_duplicate_extra(self):
  data=b"{}\n";entries=[("candidate.json",data),("candidate.json.sha256",artifact_io.sidecar(data)),("metadata.json",b"{}\n")]
  raw,path=self._zip(entries)
  try:
   artifact_io.safe_extract(path,Path(raw.name)/"out",artifact_io.digest(path.read_bytes()))
   for bad in ([("../candidate.json",data),*entries[1:]],[*entries,("extra",b"x")]):
    fixture,z=self._zip(bad)
    try:
     with self.assertRaises(ValueError):artifact_io.safe_extract(z,Path(fixture.name)/"out",artifact_io.digest(z.read_bytes()))
    finally:fixture.cleanup()
  finally:raw.cleanup()
 def test_13_zip_digest_and_size(self):
  raw,path=self._zip([])
  try:
   with self.assertRaises(ValueError):artifact_io.safe_extract(path,Path(raw.name)/"out","sha256:"+"0"*64)
   path.write_bytes(b"x"*(artifact_io.MAX_ZIP+1))
   with self.assertRaises(ValueError):artifact_io.safe_extract(path,Path(raw.name)/"out",artifact_io.digest(path.read_bytes()))
  finally:raw.cleanup()
 def test_14_remote_digest_uses_imagetools_not_repodigests(self):
  calls=[]
  def runner(args,text):calls.append(args);return '{"digest":"sha256:'+"a"*64+'"}'
  self.assertEqual("sha256:"+"a"*64,image_result.remote_digest("repo:tag",runner));self.assertIn("imagetools",calls[0]);self.assertNotIn("Repo"+"Digests"," ".join(calls[0]))
 def test_15_component_result_strict(self):
  example=json.loads((ROOT/"ops/releases/examples/candidate-manifest.example.json").read_text());c=example["components"][0]
  value={"schemaVersion":1,"component":c["id"],"repository":c["imageRepository"],"tag":c["tag"],"digest":c["digest"],"immutableRef":c["immutableRef"],"commitSha":c["commitSha"],"workflowRunId":c["workflowRunId"],"workflowAttempt":c["workflowAttempt"],"builtAt":c["builtAt"],"labels":c["labels"],"checks":c["checks"],"provenance":c["provenance"]}
  self.assertEqual([],image_result.validate(value,c["id"],c["imageRepository"],c["commitSha"],c["workflowRunId"],1))
  for key in ("labels","checks","provenance"):
   mutant=copy.deepcopy(value);mutant[key]["extra"]="x";self.assertTrue(image_result.validate(mutant,c["id"],c["imageRepository"],c["commitSha"],c["workflowRunId"],1))
 def test_16_outcomes_all_modes_and_raw_digest(self):
  candidate={"id":"c","artifactId":"12","artifactDigest":"a"*64}
  for status in outcome.STATUSES:
   predecessor="c" if status=="already_published" else None
   value=outcome.make(status,"1"*40,"100",1,predecessor,candidate if status in {"published","already_published"} else None);self.assertEqual([],outcome.validate(value))
  bad=outcome.make("published","1"*40,"100",1,None,candidate);bad["candidateArtifactDigest"]="sha256:"+"a"*64;self.assertTrue(outcome.validate(bad))
 def test_17_atomic_bundle_lf_and_rollback(self):
  with tempfile.TemporaryDirectory() as raw:
   directory=Path(raw);artifact_io.atomic_bundle(directory,{"a.json":b"{}\n","a.json.sha256":artifact_io.sidecar(b"{}\n")});self.assertTrue((directory/"a.json").exists())
   with self.assertRaises(ValueError):artifact_io.atomic_bundle(directory,{"a.json":b"x"})
 def test_18_legacy_tokens_absent(self):
  source="\n".join(p.read_text() for p in (ROOT/"tools/candidates").glob("*.py"))+(ROOT/".github/workflows/publish-candidate.yml").read_text()
  for token in ("Repo"+"Digests","extract"+"all","gh api "+"--output","publish_"+"manifest:","\n  pre"+"vious:"):self.assertNotIn(token,source)
 def test_19_workflow_mutants(self):
  source=workflow.PUBLISH.read_text()
  for old,new in (("  predecessor:","  previous:"),("contents: read\n  actions: read","contents: read\n  packages: write"),("docker/login-action@5e57","docker/login-action@main"),("name: candidate-outcome","name: removed-outcome")):
   with self.subTest(old=old):self.assertTrue(workflow.validate_workflows(publish=source.replace(old,new,1)))
 def test_20_cleanup_protocol_tokens(self):
  source=(ROOT/"tools/candidates/integrated_harness.py").read_text()
  for token in ('["docker","image","rm",ref]','["docker","image","inspect",ref]','["docker","logout","ghcr.io"]',"project-residue"):self.assertIn(token,source)
  self.assertNotIn("prune",source)
 def test_21_trust_event_mutants_without_assert(self):
  good={"conclusion":"success","name":"CI","event":"push","head_branch":"main","head_repository":{"full_name":"greggorio/abaronesa-emporio"},"repository":{"full_name":"greggorio/abaronesa-emporio","owner":{"login":"greggorio"}},"head_sha":"1"*40,"run_attempt":1}
  self.assertEqual([],trust.event(good))
  for path,value in (("event","pull_request"),("conclusion","failure"),("head_branch","dev"),("head_sha","A"*40),("run_attempt",0)):
   bad=copy.deepcopy(good);bad[path]=value;self.assertTrue(trust.event(bad))
  self.assertNotIn("as"+"sert ",(ROOT/"tools/candidates/trust.py").read_text())
 def test_22_success_run_requires_one_live_outcome(self):
  run={"id":10,"head_sha":"1"*40}
  for artifacts in ([],[{"name":"candidate-outcome","expired":True}],[{"name":"candidate-outcome","expired":False},{"name":"candidate-outcome","expired":False}]):
   def api(endpoint,artifacts=artifacts):return {"workflow_runs":[run]} if "/runs?" in endpoint else {"artifacts":artifacts}
   with tempfile.TemporaryDirectory() as raw,mock.patch.object(previous_candidate,"api",side_effect=api):
    with self.assertRaisesRegex(ValueError,"outcome"):previous_candidate.discover("2"*40,1,Path(raw))
 def test_23_pagination_full_fails_closed(self):
  runs=[{"id":i,"head_sha":format(i+1,"040x")} for i in range(50)]
  def api(endpoint):return {"workflow_runs":runs} if "/runs?" in endpoint else {"artifacts":[{"name":"candidate-outcome","expired":False}]}
  with tempfile.TemporaryDirectory() as raw,mock.patch.object(previous_candidate,"api",side_effect=api),mock.patch.object(previous_candidate,"_download",side_effect=ValueError("invalid outcome")):
   with self.assertRaises(ValueError):previous_candidate.discover("f"*40,1,Path(raw))
 def test_24_harness_exact_commands_receipt_and_cleanup(self):
  pending={k:v for k,v in json.loads((ROOT/"ops/releases/examples/candidate-manifest.example.json").read_text()).items() if k!="integration"}
  calls=[]
  class Done:
   def __init__(self,code=0):self.returncode=code
  def runner(args,**kwargs):calls.append(args);return Done(1 if args[:3]==["docker","image","inspect"] else 0)
  model={"services":{name:{} for name in integrated_harness.SERVICES}};model["services"]["gateway"]["ports"]=[{"host_ip":"127.0.0.1","published":"49123","target":8080}]
  rows=[{"Service":name,"State":"running","Health":"healthy"} for name in integrated_harness.SERVICES]
  def output(args,**kwargs):
   if "config" in args:return json.dumps(model)
   if "ps" in args and "--format" in args:return json.dumps(rows)
   return ""
  probes=[{"id":x,"status":"passed"} for x in ("website_root","erp_root","website_theme_api","erp_login","erp_whatsapp_api","publisher_route_absent","deployer_route_absent","unknown_host_denied")]
  with tempfile.TemporaryDirectory() as raw,mock.patch.dict(os.environ,{"CANDIDATE_GATEWAY_PORT":"49123"}),mock.patch("integrated_harness.probe_candidate.run",return_value=probes):
   receipt=integrated_harness.execute(pending,Path("base.yml"),Path("override.yml"),"candidate-100-1",Path(raw),runner,output)
   self.assertEqual(9,len(receipt["probes"]));self.assertTrue(any(x[-3:]==["--policy","always"] or "--policy" in x for x in calls));self.assertTrue(any("--wait-timeout" in x and "600" in x for x in calls))
   self.assertTrue((Path(raw)/"integration-result.json").exists())
 def test_25_harness_residue_fails(self):
  pending={k:v for k,v in json.loads((ROOT/"ops/releases/examples/candidate-manifest.example.json").read_text()).items() if k!="integration"}
  class Done:returncode=0
  model={"services":{name:{} for name in integrated_harness.SERVICES}};model["services"]["gateway"]["ports"]=[{"host_ip":"127.0.0.1","published":"49123","target":8080}]
  rows=[{"Service":name,"State":"running","Health":"healthy"} for name in integrated_harness.SERVICES]
  def output(args,**kwargs):
   if "config" in args:return json.dumps(model)
   if "ps" in args and "--format" in args:return json.dumps(rows)
   if args[:2]==["docker","ps"]:return "residue\n"
   return ""
  probes=[{"id":x,"status":"passed"} for x in ("website_root","erp_root","website_theme_api","erp_login","erp_whatsapp_api","publisher_route_absent","deployer_route_absent","unknown_host_denied")]
  with tempfile.TemporaryDirectory() as raw,mock.patch.dict(os.environ,{"CANDIDATE_GATEWAY_PORT":"49123"}),mock.patch("integrated_harness.probe_candidate.run",return_value=probes):
   with self.assertRaisesRegex(ValueError,"cleanup"):integrated_harness.execute(pending,Path("base"),Path("over"),"candidate-100-1",Path(raw),lambda *a,**k:Done(),output)
 def test_26_lineage_tie_fails(self):
  candidates=[{"commitSha":"1"*40},{"commitSha":"2"*40}]
  with mock.patch("lineage.classify",return_value="ancestor"),mock.patch("lineage.git",return_value="2"):
   with self.assertRaisesRegex(ValueError,"ambiguous"):lineage.nearest(candidates,"3"*40)
 def _trust_job(self):
  import yaml
  return yaml.load((ROOT/".github/workflows/publish-candidate.yml").read_text(),Loader=yaml.BaseLoader)["jobs"]["trust"]
 def test_28_trust_order_is_checkout_persist_download_trust(self):
  """S30 correction-02 F: checkout cleans the workspace, so persist must follow it."""
  trust_job=self._trust_job()
  self.assertEqual([],workflow.validate_trust_order(trust_job))
  self.assertEqual(["checkout","persist","download","trust"],[s for s in (workflow.trust_stage(step) for step in trust_job["steps"]) if s])
 def test_29_trust_order_mutants(self):
  trust_job=self._trust_job();steps=trust_job["steps"]
  stages={workflow.trust_stage(step):index for index,step in enumerate(steps) if workflow.trust_stage(step)}
  self.assertEqual({"checkout","persist","download","trust"},set(stages))
  regressed=copy.deepcopy(steps)
  regressed.insert(stages["checkout"],regressed.pop(stages["persist"]))
  self.assertEqual(["persist","checkout","download","trust"],[s for s in (workflow.trust_stage(step) for step in regressed) if s])
  self.assertIn("TRUST_ORDER",workflow.validate_trust_order({"steps":regressed}))
  for stage in ("checkout","persist","download","trust"):
   with self.subTest(removed=stage):
    reduced=[step for step in copy.deepcopy(steps) if workflow.trust_stage(step)!=stage]
    self.assertIn("TRUST_ORDER",workflow.validate_trust_order({"steps":reduced}))
  swapped=copy.deepcopy(steps)
  swapped.insert(stages["download"],swapped.pop(stages["trust"]))
  self.assertIn("TRUST_ORDER",workflow.validate_trust_order({"steps":swapped}))
 def test_30_trust_cannot_disable_checkout_clean(self):
  """Disabling clean is the forbidden shortcut around the ordering contract."""
  steps=copy.deepcopy(self._trust_job()["steps"])
  for step in steps:
   if str(step.get("uses","")).startswith("actions/checkout@"):step.setdefault("with",{})["clean"]="false"
  self.assertIn("TRUST_CLEAN_DISABLED",workflow.validate_trust_order({"steps":steps}))
 def test_31_regressed_workflow_fails_full_validator(self):
  """The regression must be rejected by the top-level validator, not only the helper."""
  publish=(ROOT/".github/workflows/publish-candidate.yml").read_text()
  checkout='      - name: Checkout received SHA\n        uses: actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd # v6.0.2\n        with: {ref: "${{ github.event.workflow_run.head_sha }}", fetch-depth: 0, persist-credentials: false}\n'
  persist='      - name: Persist untrusted event without interpolation\n        env: {RUN_JSON: "${{ toJSON(github.event.workflow_run) }}"}\n        run: python3 -c \'import os,pathlib; pathlib.Path("workflow-run.json").write_text(os.environ["RUN_JSON"]+"\\n")\'\n'
  self.assertIn(checkout+persist,publish)
  regressed=publish.replace(checkout+persist,persist+checkout,1)
  self.assertNotEqual(publish,regressed)
  self.assertIn("TRUST_ORDER",workflow.validate_workflows(publish=regressed))
 class Response:
  def __init__(self,payload,status=200):self._body=json.dumps(payload).encode();self.status=status
  def read(self):return self._body
  def __enter__(self):return self
  def __exit__(self,*rest):return False
 def opener(self,main_sha,status=None,ref_payload=None,compare_payload=None,ref_status=200,seen=None):
  """Local double for the two canonical endpoints. No network is touched."""
  def call(request,timeout=None):
   if seen is not None:seen.append((request.full_url,dict(request.headers),timeout))
   if request.full_url.endswith("/git/ref/heads/main"):
    return self.Response(ref_payload if ref_payload is not None else {"ref":"refs/heads/main","object":{"type":"commit","sha":main_sha}},ref_status)
   return self.Response(compare_payload if compare_payload is not None else {"status":status})
  return call
 def test_27_head_relation_by_api_identical_and_ahead(self):
  with mock.patch.dict(os.environ,{"GH_TOKEN":"fixture-token"},clear=False):
   self.assertEqual("same",trust.head_relation("1"*40,self.opener("1"*40)))
   self.assertEqual("same",trust.head_relation("1"*40,self.opener("2"*40,"identical")))
   self.assertEqual("ancestor",trust.head_relation("1"*40,self.opener("2"*40,"ahead")))
   self.assertEqual("continue",trust.classify_head("1"*40,self.opener("1"*40)))
   self.assertEqual("superseded",trust.classify_head("1"*40,self.opener("2"*40,"ahead")))
 def test_32_head_relation_fails_closed(self):
  cases={
   "behind":self.opener("2"*40,"behind"),
   "diverged":self.opener("2"*40,"diverged"),
   "absent status":self.opener("2"*40,None),
   "compare not a mapping":self.opener("2"*40,compare_payload=["ahead"]),
   "wrong ref":self.opener("2"*40,"ahead",ref_payload={"ref":"refs/heads/other","object":{"type":"commit","sha":"2"*40}}),
   "wrong object type":self.opener("2"*40,"ahead",ref_payload={"ref":"refs/heads/main","object":{"type":"tag","sha":"2"*40}}),
   "invalid sha":self.opener("2"*40,"ahead",ref_payload={"ref":"refs/heads/main","object":{"type":"commit","sha":"zz"}}),
   "ref not a mapping":self.opener("2"*40,"ahead",ref_payload=["refs/heads/main"]),
  }
  with mock.patch.dict(os.environ,{"GH_TOKEN":"fixture-token"},clear=False):
   for label,opener in cases.items():
    with self.subTest(case=label):
     with self.assertRaises(ValueError):trust.head_relation("1"*40,opener)
   for label,failure in (("404",urllib.error.HTTPError("u",404,"nf",None,None)),("timeout",TimeoutError()),("invalid json",ValueError("no json")),("transport",OSError("boom"))):
    with self.subTest(case=label):
     def raiser(request,timeout=None):raise failure
     with self.assertRaises(ValueError):trust.head_relation("1"*40,raiser)
   with self.assertRaises(ValueError):trust.head_relation("not-a-sha",self.opener("1"*40))
   with self.assertRaises(ValueError):trust.head_relation("1"*40,self.opener("2"*40,"ahead",ref_status=500))
 def test_33_token_is_required_and_never_leaked(self):
  secret="fixture-token-must-not-leak"
  with mock.patch.dict(os.environ,{"GH_TOKEN":""},clear=False):
   with self.assertRaisesRegex(ValueError,"github token missing"):trust.head_relation("1"*40,self.opener("1"*40))
  with mock.patch.dict(os.environ,{},clear=True):
   with self.assertRaisesRegex(ValueError,"github token missing"):trust.head_relation("1"*40,self.opener("1"*40))
  with mock.patch.dict(os.environ,{"GH_TOKEN":secret},clear=False):
   def raiser(request,timeout=None):raise OSError(secret)
   with self.assertRaises(ValueError) as caught:trust.head_relation("1"*40,raiser)
   self.assertNotIn(secret,str(caught.exception))
   with self.assertRaises(ValueError) as caught:trust.head_relation("1"*40,self.opener("2"*40,"diverged"))
   self.assertNotIn(secret,str(caught.exception))
 def test_34_endpoints_are_fixed_and_authenticated(self):
  seen=[]
  with mock.patch.dict(os.environ,{"GH_TOKEN":"fixture-token"},clear=False):
   trust.head_relation("1"*40,self.opener("2"*40,"ahead",seen=seen))
  self.assertEqual(2,len(seen))
  self.assertEqual("https://api.github.com/repos/greggorio/abaronesa-emporio/git/ref/heads/main",seen[0][0])
  self.assertEqual("https://api.github.com/repos/greggorio/abaronesa-emporio/compare/"+"1"*40+"..."+"2"*40,seen[1][0])
  for url,headers,timeout in seen:
   self.assertIn("greggorio/abaronesa-emporio",url)
   self.assertTrue(url.startswith("https://api.github.com/"))
   self.assertEqual("Bearer fixture-token",headers.get("Authorization"))
   self.assertIsNotNone(timeout)
  source=(ROOT/"tools/candidates/trust.py").read_text()
  self.assertIn('REF_ENDPOINT=API+"/repos/"+REPO+"/git/ref/heads/main"',source)
  self.assertEqual("greggorio/abaronesa-emporio",trust.REPO)
 def test_35_head_resolution_never_uses_git(self):
  for name in ("trust.py","publish_guard.py"):
   with self.subTest(module=name):
    source=(ROOT/"tools/candidates"/name).read_text()
    for marker in ("subprocess",'"fetch"',"'fetch'",'"origin/main"',"'origin/main'",'"merge-base"',"'merge-base'"):
     self.assertNotIn(marker,source)
  self.assertEqual([],workflow.validate_authenticated_head(self._jobs()))
 def _jobs(self):
  import yaml
  return yaml.load((ROOT/".github/workflows/publish-candidate.yml").read_text(),Loader=yaml.BaseLoader)["jobs"]
 def test_36_authenticated_head_contract_mutants(self):
  token={"GH_TOKEN":"${{ github.token }}"}
  jobs=self._jobs()
  stripped=copy.deepcopy(jobs)
  for step in stripped["trust"]["steps"]:
   if "tools/candidates/trust.py" in str(step.get("run","")):step.pop("env",None)
  self.assertIn("TRUST_TOKEN",workflow.validate_authenticated_head(stripped))
  moved=copy.deepcopy(jobs)
  for step in moved["trust"]["steps"]:
   if "tools/candidates/trust.py" in str(step.get("run","")):step.pop("env",None)
   elif str(step.get("uses","")).startswith("actions/download-artifact@"):step["env"]=dict(token)
  self.assertIn("TRUST_TOKEN",workflow.validate_authenticated_head(moved))
  spread=copy.deepcopy(jobs)
  for step in spread["trust"]["steps"]:
   if str(step.get("uses","")).startswith("actions/checkout@"):step["env"]=dict(token)
  self.assertIn("TRUST_TOKEN_SPREAD",workflow.validate_authenticated_head(spread))
  guardless=copy.deepcopy(jobs)
  for step in guardless["publish"]["steps"]:
   if "tools/candidates/publish_guard.py" in str(step.get("run","")):step.pop("env",None)
  self.assertIn("GUARD_TOKEN",workflow.validate_authenticated_head(guardless))
  for job in ("trust","publish"):
   with self.subTest(job=job):
    persisted=copy.deepcopy(jobs)
    for step in persisted[job]["steps"]:
     if str(step.get("uses","")).startswith("actions/checkout@"):step.setdefault("with",{})["persist-credentials"]="true"
    self.assertIn("PERSISTED_CREDENTIALS:"+job,workflow.validate_authenticated_head(persisted))
if __name__=="__main__":unittest.main()
