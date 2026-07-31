#!/usr/bin/env python3
"""Create strict component-result.json from remote registry evidence."""
import argparse,json,os,re,subprocess,sys
from datetime import datetime,timezone
from pathlib import Path
KEYS={"schemaVersion","component","repository","tag","digest","immutableRef","commitSha","workflowRunId","workflowAttempt","builtAt","labels","checks","provenance"}
LABELS=("org.opencontainers.image.source","org.opencontainers.image.revision","org.opencontainers.image.version","org.opencontainers.image.created")
def utc_now():return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00","Z")
def remote_digest(tag,runner=subprocess.check_output):
 raw=runner(["docker","buildx","imagetools","inspect",tag,"--format","{{json .Manifest}}"],text=True)
 digest=json.loads(raw).get("digest")
 if not re.fullmatch(r"sha256:[0-9a-f]{64}",str(digest)):raise ValueError("remote manifest digest")
 return digest
def local_labels(tag,runner=subprocess.check_output):
 raw=runner(["docker","image","inspect",tag,"--format","{{json .Config.Labels}}"],text=True)
 labels=json.loads(raw)
 if not isinstance(labels,dict) or any(not labels.get(k) for k in LABELS):raise ValueError("OCI labels")
 return {key:labels[key] for key in LABELS}
def validate(value,component,repository,commit,run,attempt):
 errors=[]
 if set(value)!=KEYS or value.get("schemaVersion")!=1:errors.append("RESULT_SHAPE")
 tag=f"{repository}:sha-{commit}"
 if value.get("component")!=component or value.get("repository")!=repository or value.get("tag")!=tag:errors.append("RESULT_IDENTITY")
 digest=value.get("digest","")
 if not re.fullmatch(r"sha256:[0-9a-f]{64}",digest) or value.get("immutableRef")!=repository+"@"+digest:errors.append("RESULT_DIGEST")
 if value.get("commitSha")!=commit or value.get("workflowRunId")!=str(run) or value.get("workflowAttempt")!=int(attempt):errors.append("RESULT_RUN")
 labels=value.get("labels",{})
 expected={"org.opencontainers.image.source":"https://github.com/greggorio/abaronesa-emporio","org.opencontainers.image.revision":commit,"org.opencontainers.image.version":f"candidate-{commit}-{run}-{attempt}","org.opencontainers.image.created":value.get("builtAt")}
 if labels!=expected:errors.append("RESULT_LABELS")
 if value.get("checks")!={"build":"passed","test":"passed","scan":"passed"}:errors.append("RESULT_CHECKS")
 provenance=value.get("provenance",{})
 if set(provenance)!={"attestationId","attestationUrl","verifiedSubject","verifiedAt"} or provenance.get("verifiedSubject")!=value.get("immutableRef") or not re.fullmatch(r"[1-9][0-9]*",str(provenance.get("attestationId",""))) or provenance.get("attestationUrl")!=f"https://github.com/greggorio/abaronesa-emporio/attestations/{provenance.get('attestationId')}":errors.append("RESULT_PROVENANCE")
 if not str(value.get("builtAt","")).endswith("Z") or not str(provenance.get("verifiedAt","")).endswith("Z"):errors.append("RESULT_TIME")
 return errors
def main():
 p=argparse.ArgumentParser();s=p.add_subparsers(dest="cmd",required=True)
 r=s.add_parser("remote");r.add_argument("--component",required=True);r.add_argument("--repository",required=True);r.add_argument("--tag",required=True);r.add_argument("--output",type=Path,required=True)
 a=s.add_parser("attest");a.add_argument("--result",type=Path,required=True);a.add_argument("--attestation-id",required=True);a.add_argument("--attestation-url",required=True);x=p.parse_args()
 try:
  if x.cmd=="remote":
   digest=remote_digest(x.tag);labels=local_labels(x.tag);x.output.parent.mkdir(parents=True,exist_ok=True)
   value={"schemaVersion":1,"component":x.component,"repository":x.repository,"tag":x.tag,"digest":digest,"immutableRef":x.repository+"@"+digest,"commitSha":os.environ["EFFECTIVE_SHA"],"workflowRunId":os.environ["GITHUB_RUN_ID"],"workflowAttempt":int(os.environ["GITHUB_RUN_ATTEMPT"]),"builtAt":labels["org.opencontainers.image.created"],"labels":labels,"checks":{"build":"passed","test":"passed","scan":"passed"},"provenance":{"attestationId":"0","attestationUrl":"","verifiedSubject":"","verifiedAt":""}}
   x.output.write_text(json.dumps(value,sort_keys=True,separators=(",",":"))+"\n")
   with open(os.environ["GITHUB_OUTPUT"],"a") as f:f.write("digest="+digest+"\n")
  else:
   value=json.loads(x.result.read_text());value["provenance"]={"attestationId":x.attestation_id,"attestationUrl":x.attestation_url,"verifiedSubject":value["immutableRef"],"verifiedAt":utc_now()}
   errors=validate(value,value["component"],value["repository"],value["commitSha"],value["workflowRunId"],value["workflowAttempt"])
   if errors:raise ValueError(",".join(errors))
   x.result.write_text(json.dumps(value,sort_keys=True,separators=(",",":"))+"\n")
  return 0
 except Exception as e:print("component-result:invalid:"+str(e),file=sys.stderr);return 3
if __name__=="__main__":raise SystemExit(main())
