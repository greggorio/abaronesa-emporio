#!/usr/bin/env python3
"""Create strict component-result.json from remote registry evidence."""
import argparse,json,os,re,subprocess,sys
from pathlib import Path
KEYS={"schemaVersion","component","repository","tag","digest","immutableRef","commitSha","workflowRunId","workflowAttempt","builtAt","labels","checks"}
LABELS=("org.opencontainers.image.source","org.opencontainers.image.revision","org.opencontainers.image.version","org.opencontainers.image.created")
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
 if not str(value.get("builtAt","")).endswith("Z"):errors.append("RESULT_TIME")
 return errors
def main():
 p=argparse.ArgumentParser();s=p.add_subparsers(dest="cmd",required=True)
 r=s.add_parser("remote");r.add_argument("--component",required=True);r.add_argument("--repository",required=True);r.add_argument("--tag",required=True);r.add_argument("--output",type=Path,required=True);x=p.parse_args()
 try:
  digest=remote_digest(x.tag);labels=local_labels(x.tag);x.output.parent.mkdir(parents=True,exist_ok=True)
  commit=os.environ["EFFECTIVE_SHA"];run=os.environ["GITHUB_RUN_ID"];attempt=int(os.environ["GITHUB_RUN_ATTEMPT"])
  value={"schemaVersion":1,"component":x.component,"repository":x.repository,"tag":x.tag,"digest":digest,"immutableRef":x.repository+"@"+digest,"commitSha":commit,"workflowRunId":run,"workflowAttempt":attempt,"builtAt":labels["org.opencontainers.image.created"],"labels":labels,"checks":{"build":"passed","test":"passed","scan":"passed"}}
  errors=validate(value,x.component,x.repository,commit,run,attempt)
  if errors:raise ValueError(",".join(errors))
  x.output.write_text(json.dumps(value,sort_keys=True,separators=(",",":"))+"\n")
  return 0
 except Exception as e:print("component-result:invalid:"+str(e),file=sys.stderr);return 3
if __name__=="__main__":raise SystemExit(main())
