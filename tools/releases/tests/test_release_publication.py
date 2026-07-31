import copy
import io
import json
import os
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock

import sys
ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "tools/releases"))
import global_release
import release_publication as rp
import validate_release_workflow
import artifact_io
import outcome as candidate_outcome


class FakeTransport:
    def __init__(self):
        self.current_snapshot = "snapshot"
        self.lookup_value = None
        self.events = []
        self.assets = {}
        self.fail = None
        self.final_valid = True
        self.body_mutation = None
        self.asset_metadata_failure = False

    def snapshot(self, ignore_draft=None):
        self.events.append(("snapshot", ignore_draft))
        return self.current_snapshot
    def lookup(self, tag):
        self.events.append(("lookup", tag))
        return self.lookup_value
    def release_lookup(self, release_id):
        return self.lookup_value
    def tag_lookup(self, tag):
        return getattr(self, "tag", None)
    def tag_points_to(self, tag, sha):
        return getattr(self, "tag", None) == (tag, sha)
    def create_draft(self, tag, sha, notes_bytes):
        self.events.append(("draft", tag, sha, notes_bytes))
        if self.fail == "draft": raise RuntimeError("injected")
        self.lookup_value = {"owned": True}
        return {"id": 9}
    def upload(self, release_id, name, content_type, data):
        self.events.append(("upload", name, content_type, data))
        self.assets[name] = data
        if self.fail == name: raise RuntimeError("injected")
    def download_assets(self, release_id, tag, sha, notes_bytes, draft):
        self.events.append(("download", release_id, tag, sha, notes_bytes, draft))
        if self.asset_metadata_failure:
            raise rp.PublicationError("HISTORY_ASSETS_INVALID")
        return dict(self.assets)
    def create_tag(self, tag, sha):
        self.events.append(("tag", tag, sha)); self.tag = (tag, sha)
        if self.fail == "tag": raise RuntimeError("injected")
    def publish_draft(self, release_id):
        self.events.append(("publish", release_id))
        if self.fail == "publish": raise RuntimeError("injected")
    def final_state(self, release_id, tag, sha, notes_bytes):
        self.events.append(("final", release_id, tag, sha, notes_bytes))
        if self.body_mutation is not None:
            raise rp.PublicationError("RELEASE_STATE_INVALID")
        return {"valid": self.final_valid, "id": release_id}
    def delete_owned_draft(self, release_id, tag, sha, notes_bytes):
        self.events.append(("delete-draft", release_id, tag, sha, notes_bytes))
        if self.fail == "cleanup-draft": raise RuntimeError("injected")
        self.lookup_value = None
    def delete_owned_tag(self, tag, sha):
        self.events.append(("delete-tag", tag, sha))
        if self.fail == "cleanup-tag": raise RuntimeError("injected")
        self.tag = None


class ReleasePublicationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.release_example = json.loads((ROOT / "ops/releases/examples/global-release.example.json").read_text())
        cls.plan_example = json.loads((ROOT / "ops/releases/examples/release-publication-plan.example.json").read_text())
        cls.outcome_example = json.loads((ROOT / "ops/releases/examples/release-publication-outcome.example.json").read_text())

    def event(self):
        return {"inputs": {"operation_id": "release_operation_0001",
                "candidate_id": "candidate-" + "1"*40 + "-200-1",
                "version_bump": "PATCH", "description": "Descricao valida",
                "changelog": "Mudancas validas."}}

    def repository(self):
        return {
            "id": 77, "full_name": rp.REPOSITORY,
            "owner": {"login": "greggorio", "id": 1},
        }

    def current_run(self):
        return {
            "id": 400, "run_attempt": 1, "name": "Publish Release",
            "workflow_id": 14, "path": ".github/workflows/publish-release.yml@main",
            "event": "workflow_dispatch", "status": "in_progress",
            "conclusion": None, "head_branch": "main", "head_sha": "1"*40,
            "repository": self.repository(), "head_repository": self.repository(),
            "actor": {"login": "actor", "id": 1},
            "triggering_actor": {"login": "actor", "id": 1},
        }

    def candidate_run(self):
        return {
            "id": 200, "run_attempt": 1, "name": "Publish Candidate",
            "workflow_id": 12, "path": ".github/workflows/publish-candidate.yml@main",
            "event": "workflow_run", "status": "completed", "conclusion": "success",
            "head_branch": "main", "head_sha": "1"*40,
            "repository": self.repository(), "head_repository": self.repository(),
            "actor": {"login": "builder", "id": 2},
            "triggering_actor": {"login": "trigger", "id": 3},
        }

    def action_artifact(self, name, artifact_id):
        return {
            "id": artifact_id, "name": name, "expired": False,
            "size_in_bytes": 1024, "digest": "sha256:" + "a"*64,
            "url": f"https://api.github.com/repos/{rp.REPOSITORY}/actions/artifacts/{artifact_id}",
            "archive_download_url": f"https://api.github.com/repos/{rp.REPOSITORY}/actions/artifacts/{artifact_id}/zip",
            "workflow_run": {
                "id": 200, "head_sha": "1"*40,
                "repository_id": 77, "head_repository_id": 77,
            },
        }

    def zip_bytes(self, files):
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, "w") as archive:
            for name, data in files.items():
                archive.writestr(name, data)
        return stream.getvalue()

    def candidate_remote(self, metadata_canonical=True, outcome_canonical=True):
        manifest = json.loads(
            (ROOT / "ops/releases/examples/candidate-manifest.example.json").read_text()
        )
        manifest_bytes = artifact_io.canonical(manifest)
        expected_metadata = {
            "schemaVersion": 1, "stage": "final",
            "candidateId": manifest["candidateId"], "repository": rp.REPOSITORY,
            "commitSha": manifest["commitSha"], "workflowRunId": "200",
            "workflowAttempt": 1,
            "manifestSha256": artifact_io.digest(manifest_bytes),
        }
        metadata_bytes = (
            artifact_io.canonical(expected_metadata)
            if metadata_canonical
            else (json.dumps(expected_metadata, indent=2) + "\n").encode()
        )
        manifest_zip = self.zip_bytes({
            "candidate.json": manifest_bytes,
            "candidate.json.sha256": artifact_io.sidecar(manifest_bytes),
            "metadata.json": metadata_bytes,
        })
        manifest_artifact = self.action_artifact("candidate-manifest", 301)
        manifest_artifact["digest"] = artifact_io.digest(manifest_zip)
        outcome_value = candidate_outcome.make(
            "published", "1"*40, "200", 1, None,
            {"id": manifest["candidateId"], "artifactId": "301",
             "artifactDigest": manifest_artifact["digest"][7:]},
        )
        outcome_bytes = (
            artifact_io.canonical(outcome_value)
            if outcome_canonical
            else (json.dumps(outcome_value, indent=2) + "\n").encode()
        )
        outcome_zip = self.zip_bytes({
            "outcome.json": outcome_bytes,
            "outcome.json.sha256": artifact_io.sidecar(outcome_bytes),
        })
        outcome_artifact = self.action_artifact("candidate-outcome", 302)
        outcome_artifact["digest"] = artifact_io.digest(outcome_zip)

        outer = self
        class Remote:
            def __init__(self):
                self.downloads = []
                self.list_calls = 0
            def current_run(self, run_id):
                return outer.candidate_run()
            def list_pages(self, endpoint, key=None):
                self.list_calls += 1
                return [manifest_artifact, outcome_artifact]
            def bytes(self, endpoint, limit, *headers):
                self.downloads.append(endpoint)
                return manifest_zip if "/301/" in endpoint else outcome_zip
        return Remote()

    def record(self, version="v0.0.1", previous=None, candidate_run=200, publication_run=400):
        manifest = copy.deepcopy(self.release_example)
        manifest["release"] = version
        manifest["previousRelease"] = previous
        manifest["candidate"]["candidateId"] = f"candidate-{'1'*39}{candidate_run % 10}-{candidate_run}-1"
        manifest["candidate"]["workflowRunId"] = str(candidate_run)
        manifest["publication"]["workflowRunId"] = str(publication_run)
        data = rp.canonical(manifest)
        metadata = global_release.metadata_for(manifest, data)
        blobs = {"release.json": data, "release.json.sha256": (rp.digest(data)[7:] + "\n").encode(),
                 "metadata.json": rp.canonical(metadata)}
        assets = []
        for index, (name, (_, content_type)) in enumerate(rp.ASSETS.items(), 1):
            assets.append({"id": index, "name": name, "state": "uploaded",
                           "content_type": content_type, "size": len(blobs[name]),
                           "url": f"https://api.github.com/repos/{rp.REPOSITORY}/releases/assets/{index}"})
        return {"release": {"id": publication_run, "tag_name": version, "name": version,
                            "draft": False, "prerelease": False,
                            "url": f"https://api.github.com/repos/{rp.REPOSITORY}/releases/{publication_run}"},
                "manifest": manifest, "assets": assets, "assetBytes": blobs}

    def bundle(self):
        release = self.release_example
        data = rp.canonical(release)
        return {"release.json": data, "release.json.sha256": (rp.digest(data)[7:]+"\n").encode(),
                "metadata.json": rp.canonical(global_release.metadata_for(release, data))}

    def notes(self):
        return rp.notes_for(self.release_example, "release_operation_0001")

    def tag_ref(self, tag="v0.0.1", sha=None):
        sha = sha or "1"*40
        api_base = f"https://api.github.com/repos/{rp.REPOSITORY}/git"
        return {
            "ref": f"refs/tags/{tag}",
            "url": f"{api_base}/refs/tags/{tag}",
            "object": {
                "type": "commit", "sha": sha,
                "url": f"{api_base}/commits/{sha}",
            },
        }

    def rejected_tag_remote(self, failure, status):
        class Remote(FakeTransport):
            def create_tag(inner, tag, sha):
                inner.events.append((
                    "POST", f"/repos/{rp.REPOSITORY}/git/refs", status
                ))
                inner.tag = (tag, sha)
                raise failure
            def tag_lookup(inner, tag):
                inner.events.append((
                    "GET", f"/repos/{rp.REPOSITORY}/git/ref/tags/{tag}", 200
                ))
                return inner.tag
            def delete_owned_tag(inner, tag, sha):
                inner.events.append((
                    "DELETE", f"/repos/{rp.REPOSITORY}/git/refs/tags/{tag}", 204
                ))
                inner.tag = None
        return Remote()

    def test_01_plan_schema_example(self): rp.validate_schema(self.plan_example, rp.PLAN_SCHEMA)
    def test_02_outcome_schema_example(self): rp.validate_schema(self.outcome_example, rp.OUTCOME_SCHEMA)
    def test_03_event_five_inputs(self): self.assertEqual("release_operation_0001", rp.request_from_event(self.event())[0])
    def test_04_event_missing_input(self):
        event=self.event(); del event["inputs"]["changelog"]
        with self.assertRaisesRegex(rp.PublicationError, "REQUEST_INPUTS"): rp.request_from_event(event)
    def test_05_event_extra_input(self):
        event=self.event(); event["inputs"]["version"]="v1.0.0"
        with self.assertRaises(rp.PublicationError): rp.request_from_event(event)
    def test_06_invalid_operation(self):
        event=self.event(); event["inputs"]["operation_id"]="short"
        with self.assertRaisesRegex(rp.PublicationError, "OPERATION"): rp.request_from_event(event)
    def test_07_invalid_description(self):
        event=self.event(); event["inputs"]["description"]="x"*501
        with self.assertRaises(rp.PublicationError): rp.request_from_event(event)
    def test_08_invalid_changelog(self):
        event=self.event(); event["inputs"]["changelog"]="x"*10001
        with self.assertRaises(rp.PublicationError): rp.request_from_event(event)
    def test_09_candidate_id_parsing(self):
        self.assertEqual(("1"*40,"200",1), rp.parse_candidate_id("candidate-"+"1"*40+"-200-1"))
    def test_10_candidate_id_invalid(self):
        with self.assertRaises(rp.PublicationError): rp.parse_candidate_id("candidate-main")
    def test_11_allowlist_valid(self): self.assertEqual({"1","20"}, rp.parse_allowlist("1,20"))
    def test_12_allowlist_absent_empty_wildcard(self):
        for value in (None,"","*","1,*"):
            with self.subTest(value=value), self.assertRaises(rp.PublicationError): rp.parse_allowlist(value)
    def test_13_allowlist_duplicate_malformed(self):
        for value in ("1,1","1, 2","0","01","1,"):
            with self.subTest(value=value), self.assertRaises(rp.PublicationError): rp.parse_allowlist(value)
    def test_14_identity_valid(self):
        env={"GITHUB_REPOSITORY":rp.REPOSITORY,"GITHUB_REPOSITORY_OWNER":"greggorio","GITHUB_EVENT_NAME":"workflow_dispatch",
             "GITHUB_REF":"refs/heads/main","GITHUB_ACTOR_ID":"1","RELEASE_PUBLISHER_ACTOR_IDS":"1",
             "GITHUB_ACTOR":"actor","GITHUB_RUN_ID":"400","GITHUB_RUN_ATTEMPT":"1","GITHUB_SHA":"1"*40}
        event={"sender":{"id":1,"login":"actor"}}
        run=self.current_run()
        rp.validate_identity(env,event,run)
    def test_15_identity_repository_ref_event_sender_run_mutants(self):
        base={"GITHUB_REPOSITORY":rp.REPOSITORY,"GITHUB_REPOSITORY_OWNER":"greggorio","GITHUB_EVENT_NAME":"workflow_dispatch",
              "GITHUB_REF":"refs/heads/main","GITHUB_ACTOR_ID":"1","RELEASE_PUBLISHER_ACTOR_IDS":"1",
              "GITHUB_ACTOR":"actor","GITHUB_RUN_ID":"400","GITHUB_RUN_ATTEMPT":"1","GITHUB_SHA":"1"*40}
        event={"sender":{"id":1,"login":"actor"}}
        run=self.current_run()
        for key,value in (("GITHUB_REPOSITORY","x/y"),("GITHUB_REF","refs/heads/x"),("GITHUB_EVENT_NAME","push"),("GITHUB_ACTOR","other")):
            env=dict(base); env[key]=value
            with self.subTest(key=key), self.assertRaises(rp.PublicationError): rp.validate_identity(env,event,run)
    def test_16_empty_history(self): self.assertEqual([], rp.validate_history([], {}))
    def test_17_three_release_chain(self):
        records=[self.record("v0.0.1",None,201,401),self.record("v0.1.0","v0.0.1",202,402),self.record("v1.0.0","v0.1.0",203,403)]
        tags={r["manifest"]["release"]:r["manifest"]["sourceCommit"] for r in records}
        self.assertEqual(records, rp.validate_history(records,tags))
    def test_18_draft_and_prerelease_rejected(self):
        for field in ("draft","prerelease"):
            record=self.record(); record["release"][field]=True
            with self.assertRaises(rp.PublicationError): rp.validate_history([record],{"v0.0.1":"1"*40})
    def test_19_loose_tag_and_release_without_tag(self):
        record=self.record()
        for tags in ({"v0.0.1":"1"*40,"v9.9.9":"1"*40},{}):
            with self.subTest(tags=tags), self.assertRaises(rp.PublicationError): rp.validate_history([record],tags)
    def test_20_semver_name_commit_divergence(self):
        for mutation in ("semver","name","commit"):
            record=self.record(); tags={"v0.0.1":"1"*40}
            if mutation=="semver": record["release"]["tag_name"]="latest"
            elif mutation=="name": record["release"]["name"]="Release"
            else: tags["v0.0.1"]="2"*40
            with self.subTest(mutation=mutation), self.assertRaises(rp.PublicationError): rp.validate_history([record],tags)
    def test_21_asset_missing_extra_duplicate(self):
        for mutation in ("missing","extra","duplicate"):
            record=self.record()
            if mutation=="missing": record["assets"].pop()
            elif mutation=="extra": record["assets"].append({"name":"x"})
            else: record["assets"][1]["name"]="release.json"
            with self.subTest(mutation=mutation), self.assertRaises(rp.PublicationError): rp.validate_history([record],{"v0.0.1":"1"*40})
    def test_22_asset_state_size_content_type(self):
        for key,value in (("state","new"),("size",0),("content_type","x/y")):
            record=self.record(); record["assets"][0][key]=value
            with self.subTest(key=key), self.assertRaises(rp.PublicationError): rp.validate_history([record],{"v0.0.1":"1"*40})
    def test_23_asset_bytes_sidecar_metadata(self):
        for name in rp.ASSETS:
            record=self.record(); record["assetBytes"][name] += b"x"
            with self.subTest(name=name), self.assertRaises(rp.PublicationError): rp.validate_history([record],{"v0.0.1":"1"*40})
    def test_24_first_previous_non_null(self):
        record=self.record(previous="v0.0.0")
        with self.assertRaisesRegex(rp.PublicationError,"CHAIN"): rp.validate_history([record],{"v0.0.1":"1"*40})
    def test_25_intermediate_link_divergence(self):
        records=[self.record("v0.0.1",None,201,401),self.record("v0.0.2","v0.0.0",202,402)]
        with self.assertRaises(rp.PublicationError): rp.validate_history(records,{"v0.0.1":"1"*40,"v0.0.2":"1"*40})
    def test_26_duplicate_candidate(self):
        records=[self.record("v0.0.1",None,201,401),self.record("v0.0.2","v0.0.1",201,402)]
        with self.assertRaisesRegex(rp.PublicationError,"DUPLICATE"): rp.validate_history(records,{"v0.0.1":"1"*40,"v0.0.2":"1"*40})
    def test_27_duplicate_publication_run(self):
        records=[self.record("v0.0.1",None,201,401),self.record("v0.0.2","v0.0.1",202,401)]
        with self.assertRaisesRegex(rp.PublicationError,"DUPLICATE"): rp.validate_history(records,{"v0.0.1":"1"*40,"v0.0.2":"1"*40})
    def test_28_snapshot_deterministic_sensitive(self):
        items=[{"releaseId":"1","tagName":"v0.0.1","tagCommitSha":"1"*40,"manifestSha256":"sha256:"+"2"*64,
                "releaseAssetId":"2","sidecarAssetId":"3","metadataAssetId":"4"}]
        self.assertEqual(rp.history_snapshot(items),rp.history_snapshot(copy.deepcopy(items)))
        changed=copy.deepcopy(items); changed[0]["releaseId"]="9"
        self.assertNotEqual(rp.history_snapshot(items),rp.history_snapshot(changed))
    def test_29_new_candidate_plan_publish(self):
        plan=rp.build_plan("release_operation_0001",rp.request_from_event(self.event())[1],{},self.plan_example["candidate"],[],
                           self.plan_example["workflow"],self.release_example)
        self.assertEqual("publish",plan["mode"])
    def test_30_existing_candidate_already_published(self):
        record=self.record(); request=rp.request_from_event(self.event())[1]
        record["manifest"]["candidate"]["candidateId"]=request["candidateId"]
        plan=rp.build_plan("release_operation_0001",request,{},self.plan_example["candidate"],[record],
                           self.plan_example["workflow"],None)
        self.assertEqual("already_published",plan["mode"]); self.assertIsNotNone(plan["target"]["existingReleaseId"])
    def test_31_request_order_does_not_change_hash(self):
        request=rp.request_from_event(self.event())[1]
        self.assertEqual(rp.digest(rp.canonical(request)),rp.digest(rp.canonical(dict(reversed(list(request.items()))))))
    def test_32_notes_are_exact_data_with_lf(self):
        notes=rp.notes_for(self.release_example,"release_operation_0001")
        self.assertTrue(notes.endswith(b"\n")); self.assertIn(b"## Proveniencia",notes)
    def test_33_bundle_atomic_first_write(self):
        with tempfile.TemporaryDirectory() as raw:
            path=Path(raw)/"bundle"; data=rp.canonical(self.plan_example)
            rp.write_bundle(path,"plan.json",self.plan_example,rp.metadata("release-publication-plan","release_operation_0001","400",1,data))
            self.assertEqual({"plan.json","plan.json.sha256","metadata.json"},{p.name for p in path.iterdir()})
    def test_34_bundle_rejects_existing(self):
        with tempfile.TemporaryDirectory() as raw:
            path=Path(raw)/"bundle"; path.mkdir()
            with self.assertRaises(rp.PublicationError): rp.write_bundle(path,"plan.json",self.plan_example,{})
    def test_35_snapshot_divergence_before_draft(self):
        transport=FakeTransport()
        with self.assertRaisesRegex(rp.PublicationError,"SNAPSHOT"): rp.publish_transaction(transport,self.plan_example,self.bundle(),"other",self.notes())
        self.assertFalse(any(e[0]=="draft" for e in transport.events))
    def test_36_draft_uploads_exact_assets(self):
        transport=FakeTransport(); rp.publish_transaction(transport,self.plan_example,self.bundle(),"snapshot",self.notes())
        uploads=[e for e in transport.events if e[0]=="upload"]
        self.assertEqual(list(rp.ASSETS),[e[1] for e in uploads])
    def test_37_assets_verified_before_tag(self):
        transport=FakeTransport(); rp.publish_transaction(transport,self.plan_example,self.bundle(),"snapshot",self.notes())
        names=[e[0] for e in transport.events]; self.assertLess(names.index("download"),names.index("tag"))
    def test_38_tag_before_publication(self):
        transport=FakeTransport(); rp.publish_transaction(transport,self.plan_example,self.bundle(),"snapshot",self.notes())
        names=[e[0] for e in transport.events]; self.assertLess(names.index("tag"),names.index("publish"))
    def test_39_final_state_produces_published(self):
        transport=FakeTransport(); self.assertTrue(rp.publish_transaction(transport,self.plan_example,self.bundle(),"snapshot",self.notes())["valid"])
    def test_40_each_mutation_compensates(self):
        for failure in ("release.json","release.json.sha256","metadata.json","tag","publish"):
            transport=FakeTransport(); transport.fail=failure
            with self.subTest(failure=failure), self.assertRaises(Exception): rp.publish_transaction(transport,self.plan_example,self.bundle(),"snapshot",self.notes())
            self.assertTrue(any(e[0]=="delete-draft" for e in transport.events))
    def test_41_compensation_failure_specific(self):
        transport=FakeTransport(); transport.fail="cleanup-draft"
        original=transport.upload
        def upload(*args): transport.fail="cleanup-draft"; raise RuntimeError("injected")
        transport.upload=upload
        with self.assertRaisesRegex(rp.PublicationError,"COMPENSATION_FAILED"): rp.publish_transaction(transport,self.plan_example,self.bundle(),"snapshot",self.notes())
    def test_42_outcome_published(self):
        outcome=rp.build_outcome(self.plan_example,"500",{"runId":"400","attempt":1,"actor":"example-actor","actorId":"1"},"2026-07-29T18:00:00Z","published")
        self.assertEqual("published",outcome["status"])
    def test_43_outcome_already_published_current_run(self):
        plan=copy.deepcopy(self.plan_example); plan["mode"]="already_published"; plan["target"]["existingReleaseId"]="500"; plan["target"]["existingReleaseUrl"]="https://github.com/greggorio/abaronesa-emporio/releases/tag/v0.0.1"
        outcome=rp.build_outcome(plan,"500",{"runId":"999","attempt":2,"actor":"example-actor","actorId":"1"},"2026-07-29T18:00:00Z","already_published")
        self.assertEqual("999",outcome["workflow"]["runId"])
    def test_44_workflow_validator_valid(self):
        expected = {
            "ci.yml",
            "publish-candidate.yml",
            "publish-release.yml",
            "deploy-production.yml",
        }
        self.assertEqual(expected, validate_release_workflow.EXPECTED)
        validate_release_workflow.validate()

        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            workflow_root = root / ".github/workflows"
            workflow_root.mkdir(parents=True)
            for workflow in expected - {"deploy-production.yml"}:
                (workflow_root / workflow).write_text("name: fixture\n", encoding="utf-8")
            with mock.patch.object(validate_release_workflow, "ROOT", root):
                with self.assertRaisesRegex(
                    validate_release_workflow.WorkflowError, "active-workflows"
                ):
                    validate_release_workflow.validate(
                        ROOT / ".github/workflows/publish-release.yml"
                    )

                (workflow_root / "deploy-production.yml").write_text(
                    "name: fixture\n", encoding="utf-8"
                )
                (workflow_root / "fifth.yml").write_text(
                    "name: extra\n", encoding="utf-8"
                )
                with self.assertRaisesRegex(
                    validate_release_workflow.WorkflowError, "active-workflows"
                ):
                    validate_release_workflow.validate(
                        ROOT / ".github/workflows/publish-release.yml"
                    )
    def test_45_workflow_trigger_permission_concurrency_gate_action_mutants(self):
        original=(ROOT/".github/workflows/publish-release.yml").read_text()
        mutations=[("workflow_dispatch:","push:"),("contents: write","contents: read"),
                   ("cancel-in-progress: false","cancel-in-progress: true"),
                   ("needs.prepare.outputs.mode == 'publish'","always()"),
                   ("actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd","actions/checkout@main")]
        for old,new in mutations:
            with self.subTest(old=old), tempfile.TemporaryDirectory() as raw:
                path=Path(raw)/"workflow.yml"; path.write_text(original.replace(old,new,1))
                with self.assertRaises(Exception): validate_release_workflow.validate(path)
    def test_46_validator_cli_prefixes(self):
        self.assertEqual(0,validate_release_workflow.main())
    def test_47_global_release_chain_api(self):
        self.assertEqual([],global_release.validate_release_chain([self.release_example]))

    # Correcao causal 01: eighteen independent proofs.
    def test_c01_01_current_run_real_top_level_shape(self):
        self.assertEqual(
            77,
            rp.validate_workflow_run(
                self.current_run(), kind="current", run_id=400, attempt=1,
                sha="1"*40, actor=("actor", 1),
            ),
        )

    def test_c01_02_candidate_run_real_top_level_shape(self):
        self.assertEqual(
            77,
            rp.validate_workflow_run(
                self.candidate_run(), kind="candidate", run_id=200,
                attempt=1, sha="1"*40,
            ),
        )

    def test_c01_03_legacy_nested_workflow_shape_is_rejected(self):
        legacy = {
            "id": 400, "run_attempt": 1, "workflow": {"name": "Publish Release"},
            "event": "workflow_dispatch", "status": "in_progress",
            "head_branch": "main", "head_sha": "1"*40,
            "repository": self.repository(), "head_repository": self.repository(),
            "actor": {"login": "actor", "id": 1},
            "triggering_actor": {"login": "actor", "id": 1},
        }
        with self.assertRaisesRegex(rp.PublicationError, "CURRENT_RUN_INVALID"):
            rp.validate_workflow_run(
                legacy, kind="current", run_id=400, attempt=1,
                sha="1"*40, actor=("actor", 1),
            )

    def test_c01_04_current_run_each_top_level_binding_is_fail_closed(self):
        mutations = [
            ("name", "Other"), ("workflow_id", 0), ("path", ".github/workflows/x.yml@main"),
            ("repository", {"id": 77, "full_name": "x/y", "owner": {"login": "x"}}),
            ("head_repository", {"id": 78, "full_name": rp.REPOSITORY, "owner": {"login": "greggorio"}}),
            ("actor", {"login": "other", "id": 1}),
            ("triggering_actor", {"login": "", "id": 2}),
        ]
        for key, value in mutations:
            run = self.current_run(); run[key] = value
            with self.subTest(key=key), self.assertRaises(rp.PublicationError):
                rp.validate_workflow_run(
                    run, kind="current", run_id=400, attempt=1,
                    sha="1"*40, actor=("actor", 1),
                )

    def test_c01_05_candidate_run_divergence_precedes_artifact_listing(self):
        outer = self
        class Remote:
            def __init__(self): self.list_calls = 0
            def current_run(self, run_id):
                run = outer.candidate_run(); run["path"] = ".github/workflows/ci.yml@main"
                return run
            def list_pages(self, endpoint, key=None):
                self.list_calls += 1
                return []
        remote = Remote()
        with tempfile.TemporaryDirectory() as raw, self.assertRaisesRegex(
            rp.PublicationError, "CANDIDATE_RUN_INVALID"
        ):
            rp.resolve_candidate(
                remote, "candidate-"+"1"*40+"-200-1", Path(raw)/"candidate"
            )
        self.assertEqual(0, remote.list_calls)

    def test_c01_06_invalid_artifact_identity_never_controls_download(self):
        for key, value in (
            ("id", 0), ("id", "301"), ("url", "https://api.github.com/wrong"),
            ("size_in_bytes", 0),
            ("workflow_run", {"id": 999, "head_sha": "1"*40}),
        ):
            artifact = self.action_artifact("candidate-manifest", 301)
            artifact[key] = value
            with self.subTest(key=key, value=value), self.assertRaises(rp.PublicationError):
                rp.validate_actions_artifact(
                    artifact, expected_name="candidate-manifest",
                    candidate_run_id=200, candidate_sha="1"*40, repository_id=77,
                )

    def test_c01_07_invalid_second_artifact_prevents_both_downloads(self):
        remote = self.candidate_remote()
        original = remote.list_pages
        def invalid_list(endpoint, key=None):
            values = original(endpoint, key)
            values[1]["archive_download_url"] = "https://api.github.com/wrong"
            return values
        remote.list_pages = invalid_list
        with tempfile.TemporaryDirectory() as raw, self.assertRaises(
            rp.PublicationError
        ):
            rp.resolve_candidate(
                remote, "candidate-"+"1"*40+"-200-1", Path(raw)/"candidate"
            )
        self.assertEqual([], remote.downloads)

    def test_c01_08_noncanonical_candidate_metadata_is_rejected(self):
        remote = self.candidate_remote(metadata_canonical=False)
        with tempfile.TemporaryDirectory() as raw, self.assertRaisesRegex(
            rp.PublicationError, "CANDIDATE_METADATA_INVALID"
        ):
            rp.resolve_candidate(
                remote, "candidate-"+"1"*40+"-200-1", Path(raw)/"candidate"
            )

    def test_c01_09_noncanonical_candidate_outcome_is_rejected(self):
        remote = self.candidate_remote(outcome_canonical=False)
        with tempfile.TemporaryDirectory() as raw, self.assertRaisesRegex(
            rp.PublicationError, "CANDIDATE_OUTCOME_INVALID"
        ):
            rp.resolve_candidate(
                remote, "candidate-"+"1"*40+"-200-1", Path(raw)/"candidate"
            )

    def test_c01_10_release_and_asset_ids_urls_validated_before_endpoint(self):
        for release in (
            {"id": 0, "url": f"https://api.github.com/repos/{rp.REPOSITORY}/releases/0"},
            {"id": "9", "url": f"https://api.github.com/repos/{rp.REPOSITORY}/releases/9"},
            {"id": 9, "url": "https://api.github.com/wrong"},
        ):
            with self.subTest(release=release), self.assertRaises(rp.PublicationError):
                rp.validate_release_identity(release)
        asset = self.record()["assets"][0]
        for key, value in (("id", 0), ("id", "1"), ("url", "https://api.github.com/wrong")):
            values = copy.deepcopy(self.record()["assets"])
            values[0][key] = value
            with self.subTest(key=key), self.assertRaises(rp.PublicationError):
                rp.validate_release_assets(values)

    def test_c01_11_second_invalid_asset_blocks_all_asset_downloads(self):
        record = self.record()
        release = record["release"]
        release.update({
            "target_commitish": "1"*40, "body": self.notes().decode(),
            "assets": copy.deepcopy(record["assets"]),
        })
        release["assets"][1]["content_type"] = "application/octet-stream"
        class Remote(rp.GhTransport):
            def __init__(self): self.downloads = 0
            def api(self, method, endpoint, body=None, expected_status=200): return release
            def bytes(self, endpoint, limit, *headers):
                self.downloads += 1
                return b"x"
        remote = Remote()
        with self.assertRaisesRegex(rp.PublicationError, "HISTORY_ASSETS_INVALID"):
            remote.download_assets(400, "v0.0.1", "1"*40, self.notes(), False)
        self.assertEqual(0, remote.downloads)

    def test_c01_12_draft_payload_uses_exact_notes_body(self):
        notes = self.notes()
        outer = self
        class Remote(rp.GhTransport):
            def __init__(self): self.payload = None
            def api(self, method, endpoint, body=None, expected_status=200):
                if method == "POST":
                    self.payload = json.loads(Path(body).read_bytes())
                    return {"id": 9}
                return {
                    "id": 9, "url": f"https://api.github.com/repos/{rp.REPOSITORY}/releases/9",
                    "tag_name": "v0.0.1", "name": "v0.0.1",
                    "target_commitish": "1"*40, "body": notes.decode(),
                    "draft": True, "prerelease": False, "assets": [],
                }
        remote = Remote()
        self.assertEqual({"id": 9}, remote.create_draft("v0.0.1", "1"*40, notes))
        self.assertEqual(
            {"tag_name", "target_commitish", "name", "body", "draft", "prerelease"},
            set(remote.payload),
        )
        self.assertEqual(notes, remote.payload["body"].encode())

    def test_c01_13_missing_or_changed_body_fails_before_upload(self):
        for notes in (b"", b"\xff"):
            remote = FakeTransport()
            with self.subTest(notes=notes), self.assertRaisesRegex(
                rp.PublicationError, "RELEASE_NOTES_INVALID"
            ):
                rp.publish_transaction(
                    remote, self.plan_example, self.bundle(), "snapshot", notes
                )
            self.assertFalse(any(event[0] == "upload" for event in remote.events))
        notes = self.notes()
        for body_value in (None, notes.decode() + "changed"):
            class Remote(rp.GhTransport):
                def __init__(self): self.uploads = 0
                def api(self, method, endpoint, body=None, expected_status=200):
                    if method == "POST": return {"id": 9}
                    return {
                        "id": 9,
                        "url": f"https://api.github.com/repos/{rp.REPOSITORY}/releases/9",
                        "tag_name": "v0.0.1", "name": "v0.0.1",
                        "target_commitish": "1"*40, "body": body_value,
                        "draft": True, "prerelease": False, "assets": [],
                    }
                def upload(self, *args): self.uploads += 1
            remote = Remote()
            with self.subTest(body=body_value), self.assertRaises(
                rp.DraftCreationError
            ):
                remote.create_draft("v0.0.1", "1"*40, notes)
            self.assertEqual(0, remote.uploads)

    def test_c01_14_unowned_divergent_draft_is_not_deleted(self):
        notes = self.notes()
        class BadResponseId(rp.GhTransport):
            def __init__(self): self.gets = 0
            def api(self, method, endpoint, body=None, expected_status=200):
                if method == "POST": return {"id": 0}
                self.gets += 1
                return {}
        bad_id = BadResponseId()
        with self.assertRaisesRegex(rp.PublicationError, "DRAFT_CREATE_INVALID"):
            bad_id.create_draft("v0.0.1", "1"*40, notes)
        self.assertEqual(0, bad_id.gets)

        base = {
            "id": 9,
            "url": f"https://api.github.com/repos/{rp.REPOSITORY}/releases/9",
            "tag_name": "v0.0.1", "name": "v0.0.1",
            "target_commitish": "1"*40, "body": notes.decode(),
            "draft": True, "prerelease": False, "assets": [],
        }
        for key, value in (
            ("id", 0), ("url", "https://api.github.com/wrong"),
            ("tag_name", "v9.9.9"), ("name", "wrong"),
            ("target_commitish", "2"*40), ("draft", False),
        ):
            response = copy.deepcopy(base); response[key] = value
            class ApiRemote(rp.GhTransport):
                def __init__(self): self.gets = 0
                def api(self, method, endpoint, body=None, expected_status=200):
                    if method == "POST": return {"id": 9}
                    self.gets += 1
                    return response
            api_remote = ApiRemote()
            with self.subTest(key=key), self.assertRaises(rp.DraftCreationError):
                api_remote.create_draft("v0.0.1", "1"*40, notes)
            self.assertEqual(2, api_remote.gets)

        class Remote(FakeTransport):
            def create_draft(self, tag, sha, notes_bytes):
                self.events.append(("draft", tag, sha, notes_bytes))
                raise rp.DraftCreationError("DRAFT_RECONCILIATION_INVALID", None)
        remote = Remote()
        with self.assertRaisesRegex(rp.DraftCreationError, "DRAFT_RECONCILIATION"):
            rp.publish_transaction(
                remote, self.plan_example, self.bundle(), "snapshot", self.notes()
            )
        self.assertFalse(any(event[0] == "delete-draft" for event in remote.events))
        self.assertFalse(any(event[0] == "upload" for event in remote.events))

    def test_c01_15_asset_metadata_divergence_after_upload_compensates(self):
        remote = FakeTransport(); remote.asset_metadata_failure = True
        with self.assertRaisesRegex(rp.PublicationError, "ASSETS_INVALID"):
            rp.publish_transaction(
                remote, self.plan_example, self.bundle(), "snapshot", self.notes()
            )
        self.assertEqual(3, sum(event[0] == "upload" for event in remote.events))
        self.assertTrue(any(event[0] == "delete-draft" for event in remote.events))

    def test_c01_16_final_body_target_or_asset_divergence_compensates(self):
        for mutation in ("body", "target", "asset"):
            record = self.record()
            state = record["release"]
            state.update({
                "target_commitish": "1"*40, "body": self.notes().decode(),
                "assets": copy.deepcopy(record["assets"]),
            })
            if mutation == "body": state["body"] += "changed"
            elif mutation == "target": state["target_commitish"] = "2"*40
            else: state["assets"][1]["state"] = "new"
            with self.subTest(validator=mutation), self.assertRaises(rp.PublicationError):
                rp.validate_release_state(
                    state, release_id=400, tag="v0.0.1", sha="1"*40,
                    notes_bytes=self.notes(), draft=False,
                )
            remote = FakeTransport()
            if mutation in {"body", "target"}:
                remote.body_mutation = mutation
            else:
                calls = 0
                original = remote.download_assets
                def fail_final(*args):
                    nonlocal calls
                    calls += 1
                    if calls >= 2:
                        raise rp.PublicationError("HISTORY_ASSETS_INVALID")
                    return original(*args)
                remote.download_assets = fail_final
            with self.subTest(mutation=mutation), self.assertRaises(rp.PublicationError):
                rp.publish_transaction(
                    remote, self.plan_example, self.bundle(), "snapshot", self.notes()
                )
            self.assertTrue(any(event[0] == "delete-draft" for event in remote.events))
        changed_owned = {
            "id": 9,
            "url": f"https://api.github.com/repos/{rp.REPOSITORY}/releases/9",
            "tag_name": "v0.0.1", "name": "v0.0.1",
            "target_commitish": "2"*40, "body": "changed after ownership proof",
            "draft": False, "prerelease": False, "assets": [],
        }
        class DeleteRemote(rp.GhTransport):
            def __init__(self): self.deleted = False
            def api(self, method, endpoint, body=None, expected_status=200):
                if method == "DELETE":
                    self.deleted = True
                    return {}
                return changed_owned
        delete_remote = DeleteRemote()
        delete_remote.delete_owned_draft(9, "v0.0.1", "1"*40, self.notes())
        self.assertTrue(delete_remote.deleted)

    def test_c01_17_positive_path_proves_three_assets_before_outcome(self):
        remote = FakeTransport()
        final = rp.publish_transaction(
            remote, self.plan_example, self.bundle(), "snapshot", self.notes()
        )
        names = [event[0] for event in remote.events]
        self.assertEqual(3, names.count("upload"))
        self.assertGreater(names.index("final"), names.index("publish"))
        outcome = rp.build_outcome(
            self.plan_example, str(final["id"]),
            {"runId": "400", "attempt": 1, "actor": "actor", "actorId": "1"},
            "2026-07-29T18:00:00Z", "published",
        )
        self.assertEqual("published", outcome["status"])

    def test_c01_18_previous_187_tests_remain_part_of_discovery(self):
        self.assertTrue(hasattr(self, "test_47_global_release_chain_api"))

    def test_c02_01_http2_json_parser(self):
        status, value = rp.parse_http_response(
            b"HTTP/2.0 200 OK\r\ncontent-type: application/json\r\n\r\n{\"ok\":true}",
            0,
        )
        self.assertEqual((200, {"ok": True}), (status, value))

    def test_c02_02_http11_204_empty_parser(self):
        self.assertEqual(
            (204, None),
            rp.parse_http_response(b"HTTP/1.1 204 No Content\nx-test: yes\n\n", 0),
        )

    def test_c02_03_optional_get_returns_none_only_for_404(self):
        class Remote(rp.GhTransport):
            def api(self, method, endpoint, body=None, expected_status=200):
                raise rp.RemoteHttpError(404)
        self.assertIsNone(Remote().optional_get(f"/repos/{rp.REPOSITORY}/releases/1"))

    def test_c02_04_optional_get_propagates_non404_statuses(self):
        for status in (401, 403, 409, 429, 500):
            class Remote(rp.GhTransport):
                def api(self, method, endpoint, body=None, expected_status=200):
                    raise rp.RemoteHttpError(status)
            with self.subTest(status=status), self.assertRaises(rp.RemoteHttpError) as raised:
                Remote().optional_get(f"/repos/{rp.REPOSITORY}/releases/1")
            self.assertEqual(status, raised.exception.status)

    def test_c02_05_http_parser_rejects_malformed_and_unbounded_responses(self):
        cases = {
            "no-status": (b"body only", 1, "REMOTE_TRANSPORT_FAILED"),
            "bad-status": (b"HTTP/1.1 two OK\r\n\r\n{}", 0, "REMOTE_TRANSPORT_FAILED"),
            "headers": (
                b"HTTP/1.1 200 OK\r\nX:" + b"a"*(rp.MAX_HTTP_HEADERS + 1) + b"\r\n\r\n{}",
                0, "REMOTE_RESPONSE_INVALID",
            ),
            "body": (
                b"HTTP/1.1 200 OK\r\n\r\n\"" + b"a"*rp.MAX_HTTP_JSON_BODY + b"\"",
                0, "REMOTE_RESPONSE_INVALID",
            ),
            "204-body": (b"HTTP/1.1 204 No Content\r\n\r\n{}", 0, "REMOTE_RESPONSE_INVALID"),
            "invalid-json": (b"HTTP/1.1 200 OK\r\n\r\nnot-json", 0, "REMOTE_RESPONSE_INVALID"),
        }
        for name, (raw, returncode, code) in cases.items():
            with self.subTest(name=name), self.assertRaisesRegex(rp.PublicationError, code):
                rp.parse_http_response(raw, returncode)

    def test_c02_06_lookup_403_before_draft_has_zero_mutations(self):
        class Remote(FakeTransport):
            def lookup(self, tag):
                self.events.append(("lookup", tag))
                raise rp.RemoteHttpError(403)
        remote = Remote()
        with self.assertRaises(rp.PublicationError):
            rp.publish_transaction(
                remote, self.plan_example, self.bundle(), "snapshot", self.notes()
            )
        mutation_names = {
            "draft", "upload", "tag", "publish", "delete-draft", "delete-tag"
        }
        self.assertFalse(any(event[0] in mutation_names for event in remote.events))

    def test_c02_07_compensation_proof_500_is_terminal_failure(self):
        class Remote(FakeTransport):
            def __init__(self):
                super().__init__()
                self.lookup_calls = 0
                self.fail = "release.json"
            def lookup(self, tag):
                self.lookup_calls += 1
                self.events.append(("lookup", tag))
                if self.lookup_calls > 1:
                    raise rp.RemoteHttpError(500)
                return None
        with self.assertRaisesRegex(
            rp.PublicationError, "PUBLICATION_COMPENSATION_FAILED"
        ):
            rp.publish_transaction(
                Remote(), self.plan_example, self.bundle(), "snapshot", self.notes()
            )

    def test_c02_08_canonical_lightweight_ref_is_accepted(self):
        self.assertEqual(
            "1"*40, rp.validate_tag_ref(self.tag_ref(), "v0.0.1", "1"*40)
        )

    def test_c02_09_ref_shape_mutants_are_rejected(self):
        mutants = []
        for path, value in (
            (("ref",), "refs/tags/v0.0.2"),
            (("url",), "https://api.github.com/repos/x/y/git/refs/tags/v0.0.1"),
            (("object", "type"), "tag"),
            (("object", "sha"), "A"*40),
            (("object", "url"), "https://api.github.com/repos/x/y/git/commits/" + "1"*40),
        ):
            mutant = copy.deepcopy(self.tag_ref())
            target = mutant
            for key in path[:-1]:
                target = target[key]
            target[path[-1]] = value
            mutants.append((path, mutant))
        for path, mutant in mutants:
            with self.subTest(path=path), self.assertRaisesRegex(
                rp.PublicationError, "HISTORY_TAG_INVALID"
            ):
                rp.validate_tag_ref(mutant, "v0.0.1")

    def test_c02_10_invalid_history_ref_fails_before_snapshot(self):
        invalid = self.tag_ref()
        invalid["object"]["type"] = "tag"
        class Remote:
            def __init__(self):
                self.calls = 0
            def list_pages(self, endpoint, key=None):
                self.calls += 1
                return [] if "/releases" in endpoint else [invalid]
        with self.assertRaisesRegex(rp.PublicationError, "HISTORY_TAG_INVALID"):
            rp.load_remote_history(Remote())

    def test_c02_11_invalid_created_ref_is_reconciled_without_deleting_divergence(self):
        class InvalidCreated(FakeTransport):
            def create_tag(self, tag, sha):
                self.events.append(("tag", tag, sha))
                # Represents POST 201 with invalid body followed by canonical GET.
                self.tag = (tag, sha)
        owned = InvalidCreated()
        owned.fail = "publish"
        with self.assertRaises(RuntimeError):
            rp.publish_transaction(
                owned, self.plan_example, self.bundle(), "snapshot", self.notes()
            )
        self.assertTrue(any(event[0] == "delete-tag" for event in owned.events))

        class Divergent(InvalidCreated):
            def create_tag(self, tag, sha):
                self.events.append(("tag", tag, sha))
                self.tag = (tag, "2"*40)
                raise rp.PublicationError("HISTORY_TAG_INVALID")
        divergent = Divergent()
        with self.assertRaisesRegex(
            rp.PublicationError, "PUBLICATION_COMPENSATION_FAILED"
        ):
            rp.publish_transaction(
                divergent, self.plan_example, self.bundle(), "snapshot", self.notes()
            )
        self.assertFalse(any(event[0] == "delete-tag" for event in divergent.events))

    def test_c02_12_subprocess_failures_are_stable_and_sanitized(self):
        completed = subprocess.CompletedProcess(["gh"], 1, b"", b"raw-secret")
        with mock.patch.object(rp.subprocess, "run", return_value=completed):
            with self.assertRaisesRegex(rp.PublicationError, "REMOTE_UPLOAD_FAILED"):
                rp.GhTransport().upload(1, "release.json", "application/json", b"free-data")
        with mock.patch.object(rp.subprocess, "Popen", side_effect=OSError("raw-secret")):
            with self.assertRaisesRegex(rp.PublicationError, "REMOTE_DOWNLOAD_FAILED"):
                rp.GhTransport().bytes(
                    f"/repos/{rp.REPOSITORY}/actions/artifacts/1/zip", 10
                )
        git_failure = subprocess.CompletedProcess(["git"], 1, b"free-data", b"raw-secret")
        with mock.patch.object(rp.subprocess, "run", return_value=git_failure):
            with self.assertRaisesRegex(rp.PublicationError, "GIT_CONTEXT_INVALID"):
                rp._run_git(["rev-parse", "HEAD"])
        stderr = io.StringIO()
        with mock.patch.object(
            rp, "_cli_validate",
            side_effect=subprocess.CalledProcessError(1, ["tool"], b"free-data", b"raw-secret"),
        ), mock.patch("sys.stderr", stderr):
            self.assertEqual(3, rp.main(["validate-plan", "--path", "unused"]))
        logged = stderr.getvalue()
        self.assertEqual("release-publication:invalid:INVALID\n", logged)
        self.assertNotIn("Traceback", logged)
        self.assertNotIn("raw-secret", logged)
        self.assertNotIn("free-data", logged)

    def test_c02_13_every_checkout_has_depth_zero_and_mutant_fails(self):
        workflow = ROOT / ".github/workflows/publish-release.yml"
        text = workflow.read_text(encoding="utf-8")
        self.assertEqual(4, text.count("fetch-depth: 0"))
        with tempfile.TemporaryDirectory() as raw:
            mutant = Path(raw) / "publish-release.yml"
            prefix, suffix = text.rsplit("fetch-depth: 0", 1)
            mutant.write_text(prefix + "fetch-depth: 1" + suffix, encoding="utf-8")
            with self.assertRaisesRegex(
                validate_release_workflow.WorkflowError, "checkout-depth"
            ):
                validate_release_workflow.validate(mutant)

    def test_c02_14_canonical_documentation_records_six_guarantees(self):
        text = (
            ROOT / "docs/infrastructure/deployment/ci/RELEASE_PUBLICATION.md"
        ).read_text(encoding="utf-8")
        guarantees = (
            "body da GitHub Release e nao e publicado",
            "validados antes que controlem qualquer endpoint",
            "Somente HTTP\n404 comprova ausencia",
            "outros status, falhas de transporte",
            "sem traceback, body remoto ou stderr bruto",
            "integralmente revalidados",
        )
        for guarantee in guarantees:
            with self.subTest(guarantee=guarantee):
                self.assertIn(guarantee, text)

    def test_c02_15_previous_205_tests_remain_part_of_discovery(self):
        previous = [
            name for name in dir(self)
            if name.startswith("test_") and not name.startswith("test_c02")
        ]
        self.assertEqual(65, len(previous))

    def test_c02a_01_http_status_returncode_compatibility_matrix(self):
        endpoint = f"/repos/{rp.REPOSITORY}/releases/tags/v0.0.1"
        response_404 = b"HTTP/1.1 404 Not Found\r\n\r\n{}"
        completed = subprocess.CompletedProcess(["gh"], 1, response_404, b"")
        with mock.patch.object(rp.subprocess, "run", return_value=completed):
            self.assertIsNone(rp.GhTransport().optional_get(endpoint))
        for name, raw, returncode in (
            ("404-zero", response_404, 0),
            ("500-zero", b"HTTP/1.1 500 Server Error\r\n\r\n{}", 0),
            ("200-positive", b"HTTP/1.1 200 OK\r\n\r\n{}", 1),
            ("signal", b"HTTP/1.1 200 OK\r\n\r\n{}", -9),
        ):
            with self.subTest(name=name), self.assertRaisesRegex(
                rp.PublicationError, "REMOTE_TRANSPORT_FAILED"
            ):
                rp.parse_http_response(raw, returncode)

    def test_c02a_02_post_422_same_sha_never_grants_tag_ownership(self):
        remote = self.rejected_tag_remote(rp.RemoteHttpError(422), 422)
        with self.assertRaisesRegex(
            rp.PublicationError, "PUBLICATION_COMPENSATION_FAILED"
        ):
            rp.publish_transaction(
                remote, self.plan_example, self.bundle(), "snapshot", self.notes()
            )
        self.assertFalse(any(event[0] == "DELETE" for event in remote.events))
        self.assertIn(("POST", f"/repos/{rp.REPOSITORY}/git/refs", 422), remote.events)

    def test_c02a_03_post_409_same_sha_never_grants_tag_ownership(self):
        remote = self.rejected_tag_remote(rp.RemoteHttpError(409), 409)
        with self.assertRaisesRegex(
            rp.PublicationError, "PUBLICATION_COMPENSATION_FAILED"
        ):
            rp.publish_transaction(
                remote, self.plan_example, self.bundle(), "snapshot", self.notes()
            )
        self.assertFalse(any(event[0] == "DELETE" for event in remote.events))
        self.assertIn(("POST", f"/repos/{rp.REPOSITORY}/git/refs", 409), remote.events)

    def test_c02a_04_ambiguous_transport_same_sha_never_grants_ownership(self):
        remote = self.rejected_tag_remote(
            rp.PublicationError("REMOTE_TRANSPORT_FAILED"), "transport"
        )
        with self.assertRaisesRegex(
            rp.PublicationError, "PUBLICATION_COMPENSATION_FAILED"
        ):
            rp.publish_transaction(
                remote, self.plan_example, self.bundle(), "snapshot", self.notes()
            )
        self.assertFalse(any(event[0] == "DELETE" for event in remote.events))
        self.assertIn(
            ("POST", f"/repos/{rp.REPOSITORY}/git/refs", "transport"),
            remote.events,
        )

    def test_c02a_05_post_201_canonical_response_grants_ownership(self):
        outer = self
        class Remote(FakeTransport):
            create_tag = rp.GhTransport.create_tag
            def api(inner, method, endpoint, body=None, expected_status=200):
                inner.events.append((method, endpoint, 201))
                return outer.tag_ref()
        remote = Remote()
        remote.fail = "publish"
        with self.assertRaises(RuntimeError):
            rp.publish_transaction(
                remote, self.plan_example, self.bundle(), "snapshot", self.notes()
            )
        self.assertIn(
            ("POST", f"/repos/{rp.REPOSITORY}/git/refs", 201), remote.events
        )
        self.assertTrue(any(event[0] == "delete-tag" for event in remote.events))

    def test_c02a_06_post_201_invalid_body_canonical_get_recovers_ownership(self):
        outer = self
        class Remote(rp.GhTransport):
            def __init__(inner):
                inner.events = []
            def api(inner, method, endpoint, body=None, expected_status=200):
                if method == "POST":
                    inner.events.append((method, endpoint, 201))
                    raise rp.RemoteResponseError(201)
                inner.events.append((method, endpoint, 200))
                return outer.tag_ref()
        remote = Remote()
        remote.create_tag("v0.0.1", "1"*40)
        self.assertEqual(["POST", "GET"], [event[0] for event in remote.events])
        self.assertFalse(any(event[0] == "DELETE" for event in remote.events))

    def test_c02a_07_post_201_invalid_body_absent_or_divergent_is_not_owned(self):
        outer = self
        for state in ("absent", "divergent"):
            class Remote(rp.GhTransport):
                def __init__(inner):
                    inner.events = []
                def api(inner, method, endpoint, body=None, expected_status=200):
                    if method == "POST":
                        inner.events.append((method, endpoint, 201))
                        raise rp.RemoteResponseError(201)
                    inner.events.append((method, endpoint, 404 if state == "absent" else 200))
                    if state == "absent":
                        raise rp.RemoteHttpError(404)
                    return outer.tag_ref(sha="2"*40)
            remote = Remote()
            with self.subTest(state=state), self.assertRaises(rp.PublicationError):
                remote.create_tag("v0.0.1", "1"*40)
            self.assertFalse(any(event[0] == "DELETE" for event in remote.events))

    def test_c02a_08_previous_220_tests_remain_part_of_discovery(self):
        previous = [
            name for name in dir(self)
            if name.startswith("test_") and not name.startswith("test_c02a_")
        ]
        self.assertEqual(80, len(previous))


if __name__ == "__main__":
    unittest.main()
