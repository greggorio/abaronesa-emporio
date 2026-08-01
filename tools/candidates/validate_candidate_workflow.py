#!/usr/bin/env python3
"""Semantic validator for definitive S12 graph and protocols."""
import json,re,subprocess,sys
from pathlib import Path
import yaml
ROOT=Path(__file__).resolve().parents[2];PUBLISH=ROOT/".github/workflows/publish-candidate.yml";CI=ROOT/".github/workflows/ci.yml"
SHA_USE=re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+@[0-9a-f]{40}$")
def validate_workflows(ci=None,publish=None):
 ci=ci or CI.read_text();publish=publish or PUBLISH.read_text();errors=[]
 try:p=yaml.load(publish,Loader=yaml.BaseLoader);c=yaml.load(ci,Loader=yaml.BaseLoader)
 except Exception:return ["YAML"]
 if p.get("permissions")!={"contents":"read","actions":"read"}:errors.append("GLOBAL_PERMISSIONS")
 jobs=p.get("jobs",{})
 if list(jobs)!=["trust","predecessor","build","assemble","integrated","publish"]:errors.append("JOB_GRAPH")
 expected_build={"contents":"read","actions":"read","packages":"write","id-token":"write","attestations":"write"}
 expected_integrated={"contents":"read","actions":"read","packages":"read"}
 if jobs.get("build",{}).get("permissions")!=expected_build:errors.append("BUILD_PERMISSIONS")
 if jobs.get("integrated",{}).get("permissions")!=expected_integrated:errors.append("INTEGRATED_PERMISSIONS")
 for name in ("trust","predecessor","assemble","publish"):
  if "permissions" in jobs.get(name,{}):errors.append("INHERITED_PERMISSIONS:"+name)
 for name,job in jobs.items():
  if job.get("runs-on")!="ubuntu-24.04" or "timeout-minutes" not in job:errors.append("RUNTIME:"+name)
  for step in job.get("steps",[]):
   if step.get("uses") and not SHA_USE.fullmatch(step["uses"]):errors.append("ACTION_PIN")
 artifact_names=[step.get("with",{}).get("name") for job in jobs.values() for step in job.get("steps",[]) if step.get("uses","").startswith(("actions/upload-artifact@","actions/download-artifact@"))]
 for required_name in ("candidate-effective-plan","candidate-predecessor-context","candidate-pending","candidate-integration-result","candidate-outcome"):
  if required_name not in artifact_names:errors.append("ARTIFACT:"+required_name)
 if set(p.get("on",{}))!={"workflow_run"}:errors.append("TRIGGER")
 wr=p.get("on",{}).get("workflow_run",{})
 if wr!={"workflows":["CI"],"types":["completed"],"branches":["main"]}:errors.append("WORKFLOW_RUN")
 source=publish+"\n"+"\n".join(path.read_text() for path in (ROOT/"tools/candidates").glob("*.py"))
 required=("candidate-effective-plan","candidate-predecessor-context","candidate-pending","candidate-integration-result","candidate-outcome",
  '"imagetools","inspect"','"pull","--quiet","--policy","always"','"up","-d","--no-build","--pull","never","--wait","--wait-timeout","600"',
  "candidate-plan/candidate-plan.json","component-result.json","pending.json","integration-result.json","artifact-digest","validate_pending.py","cleanup_image.py")
 if not all(x in source for x in required):errors.append("DEFINITIVE_PROTOCOL")
 errors.extend(validate_trust_order(jobs.get("trust",{})))
 pending_validate=publish.find("python3 tools/candidates/validate_pending.py")
 compose_env=publish.find("python3 tools/candidates/compose_env.py")
 integrated_login=publish.find("docker/login-action@",publish.find("  integrated:"))
 harness=publish.find("python3 tools/candidates/integrated_harness.py")
 if min(pending_validate,compose_env,integrated_login,harness)<0 or not pending_validate<compose_env<integrated_login<harness:errors.append("PENDING_BEFORE_DOCKER")
 forbidden=("Repo"+"Digests","extract"+"all","gh api "+"--output","publish_"+"manifest:","\n  pre"+"vious:","as"+"sert ")
 if any(x in source for x in forbidden):errors.append("FORBIDDEN_LEGACY")
 if 'schemaVersion":2' not in (ROOT/"tools/candidates/candidate_plan.py").read_text().replace(" ",""):errors.append("PLAN_V2")
 if "candidate-plan/candidate-plan.json" not in ci or "candidate-plan/plan.json" in ci:errors.append("CI_PLAN_NAME")
 lineage_source=(ROOT/"tools/candidates/lineage.py").read_text()
 if '"schemaVersion":2' not in lineage_source.replace(" ","") or '"mode":mode' not in lineage_source.replace(" ",""):errors.append("EFFECTIVE_PLAN_V2")
 if "needs.predecessor.outputs.mode == 'continue'" not in str(jobs.get("build",{}).get("if","")) or "needs.predecessor.outputs.mode == 'continue'" not in str(jobs.get("assemble",{}).get("if","")):errors.append("TERMINAL_BUILD_GATE")
 terminal_steps=[step for step in jobs.get("publish",{}).get("steps",[]) if step.get("name")=="Create prior terminal outcome"]
 if len(terminal_steps)!=1 or "needs.predecessor.outputs.mode != 'continue'" not in str(terminal_steps[0].get("if","")):errors.append("TERMINAL_OUTCOME")
 return errors
def trust_stage(step):
 """Classify a trust step into the four mandatory stages, in order."""
 uses=str(step.get("uses",""));run=str(step.get("run",""))
 if uses.startswith("actions/checkout@"):return "checkout"
 if "workflow-run.json" in run and "RUN_JSON" in str(step.get("env",{})):return "persist"
 if uses.startswith("actions/download-artifact@") and step.get("with",{}).get("name")=="candidate-plan":return "download"
 if "tools/candidates/trust.py" in run:return "trust"
 return None
def validate_trust_order(trust):
 """actions/checkout cleans the workspace, so the event must be persisted after it.

 Persisting workflow-run.json before the checkout lets `clean: true` delete the
 file, and trust.py then fails with No such file or directory. The order
 checkout -> persist -> download -> trust is the contract, and clean must never
 be disabled to work around it.
 """
 errors=[]
 steps=trust.get("steps",[])
 stages=[stage for stage in (trust_stage(step) for step in steps) if stage]
 if stages!=["checkout","persist","download","trust"]:errors.append("TRUST_ORDER")
 for step in steps:
  if str(step.get("uses","")).startswith("actions/checkout@") and str(step.get("with",{}).get("clean","")).lower()=="false":errors.append("TRUST_CLEAN_DISABLED")
 return errors
def main():
 errors=validate_workflows()
 if errors:print("candidate-workflow:invalid:"+",".join(errors),file=sys.stderr);return 3
 print("candidate-workflow:valid");return 0
if __name__=="__main__":raise SystemExit(main())
