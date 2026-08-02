import copy,json,os,sys,tempfile,unittest
from pathlib import Path
from unittest import mock
ROOT=Path(__file__).resolve().parents[3];sys.path[:0]=[str(ROOT/"tools/releases"),str(ROOT/"tools/candidates")]
import artifact_io,candidate_manifest,finalize_candidate
class CandidateManifestV2Test(unittest.TestCase):
 def setUp(self):self.example=json.loads(candidate_manifest.EXAMPLE.read_text())
 def test_01_example_v2_valid(self):self.assertEqual([],candidate_manifest.validate_manifest(self.example))
 def test_02_schema_and_extra_fail(self):
  for mutate in (lambda x:x.update(schemaVersion=1),lambda x:x.update(extra=True),lambda x:x["components"][0]["checks"].update(health="passed"),lambda x:x["components"][0]["labels"].pop("org.opencontainers.image.created")):
   value=copy.deepcopy(self.example);mutate(value);self.assertTrue(candidate_manifest.validate_manifest(value))
 def test_03_source_predecessor_integration_required(self):
  for key in ("sourceCi","predecessor","integration"):
   value=copy.deepcopy(self.example);value.pop(key);self.assertTrue(candidate_manifest.validate_manifest(value))
 def test_04_component_binding(self):
  for change in (
   lambda c:c.update(digest="sha256:"+"9"*64),
   lambda c:c.update(immutableRef=c["imageRepository"]+"@sha256:"+"9"*64),
   lambda c:c.update(immutableRef="ghcr.io/greggorio/abaronesa-emporio-gateway@"+c["digest"]),
   lambda c:c.update(provenance={"attestationId":"1","attestationUrl":"https://github.com/greggorio/abaronesa-emporio/attestations/1","verifiedSubject":c["immutableRef"],"verifiedAt":"2026-07-29T12:10:00Z"}),
  ):
   value=copy.deepcopy(self.example);change(value["components"][0]);self.assertTrue(candidate_manifest.validate_manifest(value),change)
 def _pending_receipt(self):
  pending={k:v for k,v in self.example.items() if k!="integration"};integration=self.example["integration"]
  receipt={"schemaVersion":1,"status":"passed","repository":pending["repository"],"commitSha":pending["commitSha"],"workflowRunId":pending["workflow"]["runId"],"workflowAttempt":pending["workflow"]["attempt"],"pendingSha256":artifact_io.digest(artifact_io.canonical(pending)),"checkedAt":integration["checkedAt"],"services":integration["services"],"probes":integration["probes"],"cleanup":integration["cleanup"]}
  return pending,receipt
 def test_05_pending_receipt_final_pipeline(self):
  pending,receipt=self._pending_receipt();final=candidate_manifest.finalize(pending,receipt);self.assertEqual([],candidate_manifest.validate_manifest(final));self.assertEqual(artifact_io.digest(artifact_io.canonical(receipt)),final["integration"]["receiptSha256"])
 def test_06_receipt_mutants(self):
  pending,receipt=self._pending_receipt()
  for key,value in (("commitSha","2"*40),("workflowRunId","99"),("pendingSha256","sha256:"+"0"*64)):
   mutant=copy.deepcopy(receipt);mutant[key]=value
   with self.assertRaises(candidate_manifest.ManifestError):candidate_manifest.finalize(pending,mutant)
 def test_07_finalizer_metadata_sidecar_plan_predecessor(self):
  pending,receipt=self._pending_receipt()
  with tempfile.TemporaryDirectory() as raw:
   root=Path(raw);pd=root/"pending";rd=root/"receipt";ctx=root/"ctx";pd.mkdir();rd.mkdir();ctx.mkdir()
   pdata=artifact_io.canonical(pending);(pd/"pending.json").write_bytes(pdata);(pd/"pending.json.sha256").write_bytes(artifact_io.sidecar(pdata))
   metadata={"schemaVersion":1,"stage":"pending","repository":pending["repository"],"commitSha":pending["commitSha"],"workflowRunId":pending["workflow"]["runId"],"workflowAttempt":pending["workflow"]["attempt"],"pendingSha256":artifact_io.digest(pdata)};(pd/"metadata.json").write_bytes(artifact_io.canonical(metadata))
   rdata=artifact_io.canonical(receipt);(rd/"integration-result.json").write_bytes(rdata);(rd/"integration-result.json.sha256").write_bytes(artifact_io.sidecar(rdata))
   effective={"schemaVersion":2,"kind":"candidate-effective-plan","mode":"continue","repository":pending["repository"],"commitSha":pending["commitSha"],"sourceCi":pending["sourceCi"],"catalog":pending["catalog"],"predecessor":pending["predecessor"],"resolution":pending["resolution"]};ep=root/"effective.json";ep.write_bytes(artifact_io.canonical(effective));(ctx/"selection.json").write_bytes(artifact_io.canonical(pending["predecessor"]))
   # finalize_candidate reads GITHUB_RUN_ID/GITHUB_RUN_ATTEMPT from the
   # environment. Inside Actions those carry the real run, which never matches
   # this fixture, so pin them to the fixture's own identity. Production keeps
   # reading the real workflow identity; only the test is isolated.
   env={"GITHUB_RUN_ID":str(pending["workflow"]["runId"]),"GITHUB_RUN_ATTEMPT":str(pending["workflow"]["attempt"])}
   with mock.patch.dict(os.environ,env,clear=False):
    final=finalize_candidate.finalize(pd,rd,ep,ctx,root/"final");self.assertEqual(2,final["schemaVersion"])
   self.assertEqual({"candidate.json","candidate.json.sha256","metadata.json"},{p.name for p in (root/"final").iterdir()})
 def test_07b_finalizer_fixture_is_independent_of_external_ids(self):
  """Causal proof: a hostile ambient run identity must not change the outcome."""
  for ambient in ({"GITHUB_RUN_ID":"999999999","GITHUB_RUN_ATTEMPT":"7"},{}):
   with self.subTest(ambient=sorted(ambient)):
    with mock.patch.dict(os.environ,ambient,clear=True):
     self.test_07_finalizer_metadata_sidecar_plan_predecessor()
 def test_07c_finalizer_still_rejects_a_foreign_run(self):
  """The isolation must not disable the binding it is meant to satisfy."""
  pending,receipt=self._pending_receipt()
  with tempfile.TemporaryDirectory() as raw:
   root=Path(raw);pd=root/"pending";rd=root/"receipt";ctx=root/"ctx";pd.mkdir();rd.mkdir();ctx.mkdir()
   pdata=artifact_io.canonical(pending);(pd/"pending.json").write_bytes(pdata);(pd/"pending.json.sha256").write_bytes(artifact_io.sidecar(pdata))
   metadata={"schemaVersion":1,"stage":"pending","repository":pending["repository"],"commitSha":pending["commitSha"],"workflowRunId":pending["workflow"]["runId"],"workflowAttempt":pending["workflow"]["attempt"],"pendingSha256":artifact_io.digest(pdata)};(pd/"metadata.json").write_bytes(artifact_io.canonical(metadata))
   rdata=artifact_io.canonical(receipt);(rd/"integration-result.json").write_bytes(rdata);(rd/"integration-result.json.sha256").write_bytes(artifact_io.sidecar(rdata))
   effective={"schemaVersion":2,"kind":"candidate-effective-plan","mode":"continue","repository":pending["repository"],"commitSha":pending["commitSha"],"sourceCi":pending["sourceCi"],"catalog":pending["catalog"],"predecessor":pending["predecessor"],"resolution":pending["resolution"]};ep=root/"effective.json";ep.write_bytes(artifact_io.canonical(effective));(ctx/"selection.json").write_bytes(artifact_io.canonical(pending["predecessor"]))
   foreign={"GITHUB_RUN_ID":str(int(pending["workflow"]["runId"])+1),"GITHUB_RUN_ATTEMPT":str(pending["workflow"]["attempt"])}
   with mock.patch.dict(os.environ,foreign,clear=True):
    with self.assertRaisesRegex(ValueError,"pending publisher run"):finalize_candidate.finalize(pd,rd,ep,ctx,root/"final")
 def test_08_finalizer_rejects_metadata_tamper(self):
  pending,receipt=self._pending_receipt()
  with self.assertRaises(candidate_manifest.ManifestError):candidate_manifest.finalize(pending,{**receipt,"pendingSha256":"sha256:"+"0"*64})
 def test_09_sidecar_raw_canonical(self):
  data=b"{}\n";side=artifact_io.sidecar(data);self.assertRegex(side.decode(),r"^[0-9a-f]{64}\n$");self.assertNotIn("sha256:",side.decode())
if __name__=="__main__":unittest.main()
