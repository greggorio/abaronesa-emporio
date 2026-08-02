#!/usr/bin/env python3
"""Candidate schema v2, pending assembly and strict finalization."""
import argparse,hashlib,json,re,sys
from datetime import datetime
from pathlib import Path
import jsonschema
import catalog
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/"tools/candidates"))
import artifact_io,lineage
SCHEMA=ROOT/"ops/releases/candidate-manifest.schema.json";EXAMPLE=ROOT/"ops/releases/examples/candidate-manifest.example.json"
REPOSITORY="greggorio/abaronesa-emporio";ORDER=catalog.CANONICAL
PENDING_KEYS={"schemaVersion","kind","deployable","candidateId","repository","commitSha","ref","createdAt","workflow","sourceCi","catalog","predecessor","resolution","components"}
RECEIPT_KEYS={"schemaVersion","status","repository","commitSha","workflowRunId","workflowAttempt","pendingSha256","checkedAt","services","probes","cleanup"}
WORKFLOW_KEYS={"runId","attempt","url"}
COMPONENT_KEYS={"id","imageRepository","tag","digest","immutableRef","commitSha","workflowRunId","workflowAttempt","builtAt","state","originCandidateId","labels","checks"}
LABEL_KEYS={"org.opencontainers.image.source","org.opencontainers.image.revision","org.opencontainers.image.version","org.opencontainers.image.created"}
SERVICES=["postgresql","backend","website_back","frontend","website_front","whatsapp_service","gateway"]
PROBES=["website_root","erp_root","website_theme_api","erp_login","erp_whatsapp_api","publisher_route_absent","deployer_route_absent","unknown_host_denied","whatsapp_internal"]
SHA_RE=re.compile(r"[0-9a-f]{40}");DIGEST_RE=re.compile(r"sha256:[0-9a-f]{64}");DECIMAL_RE=re.compile(r"[1-9][0-9]*")
class ManifestError(ValueError):pass
class PreviousManifestRequired(ManifestError):pass
def read_json(path):value=json.loads(Path(path).read_text());return value
def file_digest(path):return artifact_io.digest(Path(path).read_bytes())
def canonical_bytes(value):return artifact_io.canonical(value)
def _utc(value):
 if not isinstance(value,str) or not value.endswith("Z"):return False
 try:return datetime.fromisoformat(value[:-1]+"+00:00").utcoffset().total_seconds()==0
 except (ValueError,AttributeError):return False
def _validate_component(item,pending,previous=None):
 errors=[];cid=item.get("id") if isinstance(item,dict) else None
 if not isinstance(item,dict) or set(item)!=COMPONENT_KEYS:return [f"{cid}:component shape"]
 cat=catalog.load_yaml();contract=cat["components"].get(cid,{})
 repo=contract.get("image_repository")
 if not repo or item.get("imageRepository")!=repo:errors.append(f"{cid}:repository")
 digest=item.get("digest","");commit=item.get("commitSha","");run=str(item.get("workflowRunId",""));attempt=item.get("workflowAttempt")
 if not DIGEST_RE.fullmatch(str(digest)) or item.get("immutableRef")!=repo+"@"+str(digest):errors.append(f"{cid}:digest")
 if not SHA_RE.fullmatch(str(commit)) or not DECIMAL_RE.fullmatch(run) or not isinstance(attempt,int) or attempt<1:errors.append(f"{cid}:component run")
 if item.get("tag")!=f"{repo}:sha-{commit}":errors.append(f"{cid}:tag")
 if not _utc(item.get("builtAt")):errors.append(f"{cid}:builtAt")
 labels=item.get("labels")
 expected_labels={"org.opencontainers.image.source":"https://github.com/"+REPOSITORY,"org.opencontainers.image.revision":commit,"org.opencontainers.image.version":f"candidate-{commit}-{run}-{attempt}","org.opencontainers.image.created":item.get("builtAt")}
 if labels!=expected_labels:errors.append(f"{cid}:labels")
 if item.get("checks")!={"build":"passed","test":"passed","scan":"passed"}:errors.append(f"{cid}:checks")
 builds=pending.get("resolution",{}).get("buildComponents",[])
 inherited=pending.get("resolution",{}).get("inheritedComponents",[])
 if cid in builds:
  if item.get("state")!="built" or item.get("originCandidateId") is not None or commit!=pending.get("commitSha") or run!=str(pending.get("workflow",{}).get("runId","")) or attempt!=pending.get("workflow",{}).get("attempt"):errors.append(f"{cid}:built binding")
 elif cid in inherited:
  if item.get("state")!="inherited" or not isinstance(item.get("originCandidateId"),str) or not item["originCandidateId"]:errors.append(f"{cid}:inherited binding")
  if previous is not None:
   prior=next((component for component in previous.get("components",[]) if component.get("id")==cid),None)
   expected=dict(prior) if prior else None
   if expected:
    expected["state"]="inherited";expected["originCandidateId"]=prior.get("originCandidateId") or previous.get("candidateId")
   if item!=expected:errors.append(f"{cid}:inherited identity")
 else:errors.append(f"{cid}:partition")
 return errors
def validate_pending(value,effective=None,previous=None):
 errors=[]
 if not isinstance(value,dict) or set(value)!=PENDING_KEYS:return ["pending shape"]
 if value.get("schemaVersion")!=2 or value.get("kind")!="ci-candidate" or value.get("deployable") is not False or value.get("repository")!=REPOSITORY or value.get("ref")!="refs/heads/main":errors.append("pending identity")
 commit=value.get("commitSha","");workflow=value.get("workflow")
 if not SHA_RE.fullmatch(str(commit)) or not _utc(value.get("createdAt")):errors.append("pending commit time")
 if not isinstance(workflow,dict) or set(workflow)!=WORKFLOW_KEYS:errors.append("pending workflow shape")
 else:
  run=str(workflow.get("runId",""));attempt=workflow.get("attempt")
  if not DECIMAL_RE.fullmatch(run) or not isinstance(attempt,int) or attempt<1 or workflow.get("url")!=f"https://github.com/{REPOSITORY}/actions/runs/{run}":errors.append("pending workflow")
  if value.get("candidateId")!=f"candidate-{commit}-{run}-{attempt}":errors.append("pending candidate id")
 errors.extend("pending "+error for error in lineage.validate_predecessor(value.get("predecessor")))
 try:
  if value.get("resolution")!=lineage.canonical_resolution(value.get("predecessor",{}),value.get("resolution"),"continue"):errors.append("pending resolution")
 except Exception:errors.append("pending resolution")
 expected_catalog={"schemaVersion":1,"sha256":"sha256:"+hashlib.sha256(catalog.DEFAULT_CATALOG.read_bytes()).hexdigest()}
 if value.get("catalog")!=expected_catalog:errors.append("pending catalog")
 source=value.get("sourceCi")
 if not isinstance(source,dict) or set(source)!=lineage.SOURCE_KEYS or not DECIMAL_RE.fullmatch(str(source.get("runId",""))) or not isinstance(source.get("attempt"),int) or source.get("attempt",0)<1 or not SHA_RE.fullmatch(str(source.get("baseCommitSha",""))) or not DIGEST_RE.fullmatch(str(source.get("planSha256",""))):errors.append("pending source CI")
 components=value.get("components")
 if not isinstance(components,list) or [item.get("id") for item in components if isinstance(item,dict)]!=ORDER:errors.append("component order")
 else:
  for item in components:errors.extend(_validate_component(item,value,previous))
 if effective is not None:
  effective_errors=lineage.validate_effective(effective)
  if effective_errors:errors.extend("effective:"+error for error in effective_errors)
  elif effective.get("mode")!="continue":errors.append("pending effective mode")
  elif commit!=effective["commitSha"] or source!=effective["sourceCi"] or value.get("catalog")!=effective["catalog"] or value.get("predecessor")!=effective["predecessor"] or value.get("resolution")!=effective["resolution"]:errors.append("pending effective binding")
 return errors
def validate_receipt(receipt,pending):
 errors=[]
 if not isinstance(receipt,dict) or set(receipt)!=RECEIPT_KEYS:return ["receipt shape"]
 pending_digest=artifact_io.digest(artifact_io.canonical(pending))
 expected={"repository":pending.get("repository"),"commitSha":pending.get("commitSha"),"workflowRunId":pending.get("workflow",{}).get("runId"),"workflowAttempt":pending.get("workflow",{}).get("attempt"),"pendingSha256":pending_digest}
 if receipt.get("schemaVersion")!=1 or receipt.get("status")!="passed" or any(receipt.get(key)!=value for key,value in expected.items()) or not _utc(receipt.get("checkedAt")):errors.append("receipt identity binding")
 services=receipt.get("services")
 if not isinstance(services,list) or [item.get("id") for item in services if isinstance(item,dict)]!=SERVICES or any(item!={"id":service,"state":"running","health":"healthy"} for item,service in zip(services or [],SERVICES)):errors.append("receipt services")
 probes=receipt.get("probes")
 if not isinstance(probes,list) or [item.get("id") for item in probes if isinstance(item,dict)]!=PROBES or any(item!={"id":probe,"status":"passed"} for item,probe in zip(probes or [],PROBES)):errors.append("receipt probes")
 if receipt.get("cleanup")!={"containers":0,"volumes":0,"networks":0,"imagesAbsent":6}:errors.append("receipt cleanup")
 return errors
def validate_manifest(value,effective=None,receipt=None,previous=None):
 errors=[]
 schema=read_json(SCHEMA)
 for e in jsonschema.Draft202012Validator(schema,format_checker=jsonschema.FormatChecker()).iter_errors(value):errors.append("schema:"+"/".join(map(str,e.path))+":"+e.message)
 if errors:return errors
 pending={key:value[key] for key in PENDING_KEYS}
 errors.extend(validate_pending(pending,effective,previous))
 integration=value.get("integration",{})
 if integration.get("workflowRunId")!=value["workflow"]["runId"] or integration.get("workflowAttempt")!=value["workflow"]["attempt"]:errors.append("integration run binding")
 if receipt is not None:
  errors.extend(validate_receipt(receipt,pending))
  expected_integration={"workflowRunId":receipt.get("workflowRunId"),"workflowAttempt":receipt.get("workflowAttempt"),"pendingSha256":receipt.get("pendingSha256"),"receiptSha256":artifact_io.digest(artifact_io.canonical(receipt)),"checkedAt":receipt.get("checkedAt"),"status":receipt.get("status"),"services":receipt.get("services"),"probes":receipt.get("probes"),"cleanup":receipt.get("cleanup")}
  if integration!=expected_integration:errors.append("integration receipt binding")
 return errors
def atomic_json_pair(path,value):
 data=artifact_io.canonical(value);artifact_io.atomic_bundle(Path(path).parent,{Path(path).name:data,Path(path).name+".sha256":artifact_io.sidecar(data)})
def write_atomic(path,value,overwrite=False):
 path=Path(path);side=path.with_name(path.name+".sha256")
 if overwrite:
  path.unlink(missing_ok=True);side.unlink(missing_ok=True)
 atomic_json_pair(path,value)
def _pair_is_valid(path,sidecar):return Path(sidecar).read_bytes()==artifact_io.sidecar(Path(path).read_bytes())
def component_from_result(result,state="built",origin=None):
 return {"id":result["component"],"imageRepository":result["repository"],"tag":result["tag"],"digest":result["digest"],"immutableRef":result["immutableRef"],"commitSha":result["commitSha"],"workflowRunId":result["workflowRunId"],"workflowAttempt":result["workflowAttempt"],"builtAt":result["builtAt"],"state":state,"originCandidateId":origin,"labels":result["labels"],"checks":result["checks"]}
def pending_from(effective,results,previous,created_at,run,attempt):
 if effective.get("mode")!="continue":raise ManifestError("pending effective mode")
 resolution=effective["resolution"];built={r["component"]:r for r in results};prior={c["id"]:c for c in previous.get("components",[])} if previous else {}
 if set(built)!=set(resolution["buildComponents"]):raise ManifestError("result partition")
 components=[]
 for cid in ORDER:
  if cid in built:components.append(component_from_result(built[cid]))
  else:
   if cid not in prior:raise PreviousManifestRequired(cid)
   inherited=dict(prior[cid]);inherited["state"]="inherited";inherited["originCandidateId"]=inherited.get("originCandidateId") or previous["candidateId"];components.append(inherited)
 candidate_id=f"candidate-{effective['commitSha']}-{run}-{attempt}"
 return {"schemaVersion":2,"kind":"ci-candidate","deployable":False,"candidateId":candidate_id,"repository":REPOSITORY,"commitSha":effective["commitSha"],"ref":"refs/heads/main","createdAt":created_at,"workflow":{"runId":str(run),"attempt":int(attempt),"url":f"https://github.com/{REPOSITORY}/actions/runs/{run}"},"sourceCi":effective["sourceCi"],"catalog":effective["catalog"],"predecessor":effective["predecessor"],"resolution":resolution,"components":components}
def finalize(pending,receipt):
 errors=validate_pending(pending)
 errors.extend(validate_receipt(receipt,pending))
 if errors:raise ManifestError(";".join(errors))
 final=dict(pending);final["integration"]={"workflowRunId":receipt["workflowRunId"],"workflowAttempt":receipt["workflowAttempt"],"pendingSha256":receipt["pendingSha256"],"receiptSha256":artifact_io.digest(artifact_io.canonical(receipt)),"checkedAt":receipt["checkedAt"],"status":receipt["status"],"services":receipt["services"],"probes":receipt["probes"],"cleanup":receipt["cleanup"]}
 errors=validate_manifest(final,receipt=receipt)
 if errors:raise ManifestError(";".join(errors))
 return final
def main(argv=None):
 p=argparse.ArgumentParser();p.add_argument("validate",nargs="?");p.add_argument("--manifest",type=Path,default=EXAMPLE);a=p.parse_args(argv)
 try:
  errors=validate_manifest(read_json(a.manifest))
  if errors:raise ManifestError(";".join(errors))
  print("candidate:valid");return 0
 except Exception as e:print("candidate:invalid:"+str(e),file=sys.stderr);return 3
if __name__=="__main__":raise SystemExit(main())
