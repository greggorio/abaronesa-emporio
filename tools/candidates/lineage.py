#!/usr/bin/env python3
"""Deterministic Git lineage and cumulative effective plan."""
import hashlib,json,re,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/"tools/releases"))
import catalog
REPOSITORY="greggorio/abaronesa-emporio";ZERO="0"*40
PRED_KEYS={"status","candidateId","commitSha","workflowRunId","artifactId","artifactDigest"}
EFFECTIVE_KEYS={"schemaVersion","kind","mode","repository","commitSha","sourceCi","catalog","predecessor","resolution"}
SOURCE_KEYS={"runId","attempt","baseCommitSha","planSha256"}
CATALOG_KEYS={"schemaVersion","sha256"}
MODES={"continue","already_published","no_changes","superseded"}
SHA_RE=re.compile(r"[0-9a-f]{40}")
DIGEST_RE=re.compile(r"sha256:[0-9a-f]{64}")
DECIMAL_RE=re.compile(r"[1-9][0-9]*")
def git(args,cwd=ROOT,binary=False):
 result=subprocess.run(["git",*args],cwd=cwd,capture_output=True)
 if result.returncode:raise ValueError("git "+" ".join(args))
 return result.stdout if binary else result.stdout.decode().strip()
def is_ancestor(a,b,cwd=ROOT):return subprocess.run(["git","merge-base","--is-ancestor",a,b],cwd=cwd,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode==0
def classify(candidate,current,cwd=ROOT):
 if candidate==current:return "same"
 if is_ancestor(candidate,current,cwd):return "ancestor"
 if is_ancestor(current,candidate,cwd):return "descendant"
 return "unrelated"
def nearest(candidates,current,cwd=ROOT):
 classified=[]
 for item in candidates:
  relation=classify(item["commitSha"],current,cwd)
  distance=int(git(["rev-list","--count",f"{item['commitSha']}..{current}"],cwd)) if relation=="ancestor" else None
  classified.append((relation,distance,item))
 if any(relation=="unrelated" for relation,_,_ in classified):raise ValueError("unrelated lineage")
 same=[item for relation,_,item in classified if relation=="same"]
 descendants=[item for relation,_,item in classified if relation=="descendant"]
 ancestors=[(distance,item) for relation,distance,item in classified if relation=="ancestor"]
 if len(same)>1 or len(descendants)>1 or (same and descendants):raise ValueError("ambiguous candidate lineage")
 if same:return "already_published",same[0]
 if descendants:return "superseded",descendants[0]
 if not ancestors:return "first",None
 ancestors.sort(key=lambda x:x[0])
 if len(ancestors)>1 and ancestors[0][0]==ancestors[1][0]:raise ValueError("ambiguous predecessor distance")
 return "selected",ancestors[0][1]
def cumulative_paths(previous,current,cwd=ROOT):
 raw=git(["diff","--name-only","-z",f"{previous}..{current}"],cwd,binary=True)
 return [p.decode("utf-8","strict") for p in raw.split(b"\0") if p]
def terminal_resolution(mode):
 warnings={"no_changes":"NO_CHANGES_SINCE_PREDECESSOR","already_published":"CANDIDATE_ALREADY_PUBLISHED","superseded":"SUPERSEDED_BY_MAIN_OR_CANDIDATE"}
 if mode not in warnings:raise ValueError("effective terminal mode")
 return {"classification":"no_changes" if mode=="no_changes" else "terminal","changedPaths":[],"directComponents":[],"buildComponents":[],"validationComponents":[],"inheritedComponents":list(catalog.CANONICAL),"warnings":[warnings[mode]]}
def effective(original,predecessor,paths=None,mode="continue"):
 first=predecessor is None
 if mode not in MODES:raise ValueError("effective mode")
 if mode=="continue":
  if first:resolution=catalog.resolve(catalog.load_yaml(),first_release=True)
  elif paths:resolution=catalog.resolve(catalog.load_yaml(),paths)
  else:raise ValueError("continue selected paths")
 else:resolution=terminal_resolution(mode)
 pred={"status":"first","candidateId":None,"commitSha":None,"workflowRunId":None,"artifactId":None,"artifactDigest":None} if first else {"status":"selected",**{k:predecessor[k] for k in PRED_KEYS-{"status"}}}
 value={"schemaVersion":2,"kind":"candidate-effective-plan","mode":mode,"repository":REPOSITORY,"commitSha":original["commitSha"],"sourceCi":{"runId":original["workflowRunId"],"attempt":original["workflowAttempt"],"baseCommitSha":original["baseCommitSha"],"planSha256":"sha256:"+hashlib.sha256((json.dumps(original,sort_keys=True,separators=(",",":"))+"\n").encode()).hexdigest()},"catalog":{"schemaVersion":1,"sha256":"sha256:"+hashlib.sha256(catalog.DEFAULT_CATALOG.read_bytes()).hexdigest()},"predecessor":pred,"resolution":resolution}
 return value
def validate_predecessor(value):
 errors=[]
 if not isinstance(value,dict) or set(value)!=PRED_KEYS:return ["EFFECTIVE_PREDECESSOR_SHAPE"]
 nullable=("candidateId","commitSha","workflowRunId","artifactId","artifactDigest")
 if value.get("status")=="first":
  if any(value.get(key) is not None for key in nullable):errors.append("EFFECTIVE_PREDECESSOR_FIRST")
 elif value.get("status")=="selected":
  if not re.fullmatch(r"candidate-[a-z0-9._-]+",str(value.get("candidateId",""))):errors.append("EFFECTIVE_PREDECESSOR_CANDIDATE")
  if not SHA_RE.fullmatch(str(value.get("commitSha",""))):errors.append("EFFECTIVE_PREDECESSOR_SHA")
  if not DECIMAL_RE.fullmatch(str(value.get("workflowRunId",""))):errors.append("EFFECTIVE_PREDECESSOR_RUN")
  if not DECIMAL_RE.fullmatch(str(value.get("artifactId",""))):errors.append("EFFECTIVE_PREDECESSOR_ARTIFACT")
  if not DIGEST_RE.fullmatch(str(value.get("artifactDigest",""))):errors.append("EFFECTIVE_PREDECESSOR_DIGEST")
 else:errors.append("EFFECTIVE_PREDECESSOR_STATUS")
 return errors
def canonical_resolution(predecessor,resolution,mode="continue"):
 if mode!="continue":return terminal_resolution(mode)
 if predecessor.get("status")=="first":return catalog.resolve(catalog.load_yaml(),first_release=True)
 paths=resolution.get("changedPaths") if isinstance(resolution,dict) else None
 if not isinstance(paths,list) or not paths:raise ValueError("effective resolution paths")
 return catalog.resolve(catalog.load_yaml(),paths)
def validate_effective(value,original=None):
 errors=[]
 if not isinstance(value,dict) or set(value)!=EFFECTIVE_KEYS:return ["EFFECTIVE_SHAPE"]
 mode=value.get("mode")
 if value.get("schemaVersion")!=2 or value.get("kind")!="candidate-effective-plan" or mode not in MODES or value.get("repository")!=REPOSITORY:errors.append("EFFECTIVE_IDENTITY")
 if not SHA_RE.fullmatch(str(value.get("commitSha",""))):errors.append("EFFECTIVE_SHA")
 source=value.get("sourceCi")
 if not isinstance(source,dict) or set(source)!=SOURCE_KEYS:errors.append("EFFECTIVE_SOURCE_SHAPE")
 else:
  if not DECIMAL_RE.fullmatch(str(source.get("runId",""))) or not isinstance(source.get("attempt"),int) or source["attempt"]<1:errors.append("EFFECTIVE_SOURCE_RUN")
  if not SHA_RE.fullmatch(str(source.get("baseCommitSha",""))) or not DIGEST_RE.fullmatch(str(source.get("planSha256",""))):errors.append("EFFECTIVE_SOURCE_BINDING")
 catalog_value=value.get("catalog")
 expected_catalog={"schemaVersion":1,"sha256":"sha256:"+hashlib.sha256(catalog.DEFAULT_CATALOG.read_bytes()).hexdigest()}
 if catalog_value!=expected_catalog:errors.append("EFFECTIVE_CATALOG")
 errors.extend(validate_predecessor(value.get("predecessor")))
 predecessor_status=value.get("predecessor",{}).get("status")
 if mode in ("already_published","no_changes") and predecessor_status!="selected":errors.append("EFFECTIVE_MODE_PREDECESSOR")
 try:
  if value.get("resolution")!=canonical_resolution(value.get("predecessor",{}),value.get("resolution"),mode):errors.append("EFFECTIVE_RESOLUTION")
 except Exception:errors.append("EFFECTIVE_RESOLUTION")
 if original is not None:
  expected_source={"runId":original.get("workflowRunId"),"attempt":original.get("workflowAttempt"),"baseCommitSha":original.get("baseCommitSha"),"planSha256":"sha256:"+hashlib.sha256((json.dumps(original,sort_keys=True,separators=(",",":"))+"\n").encode()).hexdigest()}
  if value.get("commitSha")!=original.get("commitSha") or source!=expected_source:errors.append("EFFECTIVE_ORIGINAL_PLAN")
 return errors
def mode_for(mode,paths):
 if mode in ("already_published","superseded"):return mode
 if mode=="selected" and not paths:return "no_changes"
 return "continue"
def write_outputs(path,effective_plan,mode):
 if mode!=effective_plan.get("mode"):raise ValueError("effective output mode")
 matrix={"include":[]};cat=catalog.load_yaml()
 for cid in effective_plan["resolution"]["buildComponents"]:matrix["include"].append({"component":cid,"context":cat["components"][cid]["build"]["context"],"dockerfile":cat["components"][cid]["build"]["dockerfile"],"repository":cat["components"][cid]["image_repository"]})
 with Path(path).open("a") as f:
  f.write("mode="+mode+"\n");f.write("matrix="+json.dumps(matrix,separators=(",",":"))+"\n");f.write("has_builds="+str(bool(matrix["include"])).lower()+"\n")
