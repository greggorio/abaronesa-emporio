import copy,json,os,sys,tempfile,unittest
from pathlib import Path
from unittest import mock

ROOT=Path(__file__).resolve().parents[3]
sys.path[:0]=[str(ROOT/"tools/releases"),str(ROOT/"tools/candidates")]
import artifact_io,candidate_manifest,global_release


class GlobalReleaseTest(unittest.TestCase):
    def candidate(self):
        return json.loads(global_release.CANDIDATE_EXAMPLE.read_text())

    def request(self, bump="PATCH"):
        value=json.loads(global_release.REQUEST_EXAMPLE.read_text());value["versionBump"]=bump;return value

    def kwargs(self, workspace=ROOT):
        return {
            "candidate_artifact_id":"300",
            "candidate_artifact_digest":"sha256:"+"3"*64,
            "published_at":"2026-07-29T15:00:00Z",
            "workflow_run_id":"400",
            "workflow_attempt":1,
            "actor":"greggorio",
            "actor_id":"1",
            "workspace":workspace,
        }

    def build(self, existing=None, request=None, candidate=None, workspace=ROOT):
        return global_release.build_release(
            candidate or self.candidate(),
            request or self.request(),
            existing or [],
            **self.kwargs(workspace),
        )

    def prior(self, version, candidate_id):
        value=copy.deepcopy(json.loads(global_release.RELEASE_EXAMPLE.read_text()))
        value["release"]=version;value["previousRelease"]=None;value["candidate"]["candidateId"]=candidate_id
        return value

    def migration_workspace(self, files):
        temporary=tempfile.TemporaryDirectory();workspace=Path(temporary.name)
        for _,_,location in global_release.DATABASES:
            root=workspace/location;root.mkdir(parents=True)
            for name,data in files.get(location,[]):(root/name).write_bytes(data)
        return temporary,workspace

    def test_01_request_and_release_examples_are_valid(self):
        request=self.request();release=json.loads(global_release.RELEASE_EXAMPLE.read_text())
        self.assertEqual([],global_release.validate_request(request,self.candidate()))
        self.assertEqual([],global_release.validate_release(release,candidate=self.candidate(),request=request,expected_databases=global_release.inventories()))

    def test_02_first_release_major_minor_patch(self):
        self.assertEqual({"MAJOR":"v1.0.0","MINOR":"v0.1.0","PATCH":"v0.0.1"},{bump:global_release.bump_version(None,bump) for bump in ("MAJOR","MINOR","PATCH")})

    def test_03_bump_from_v2_7_9(self):
        self.assertEqual({"MAJOR":"v3.0.0","MINOR":"v2.8.0","PATCH":"v2.7.10"},{bump:global_release.bump_version("v2.7.9",bump) for bump in ("MAJOR","MINOR","PATCH")})

    def test_04_numeric_order_prefers_v2_10_0(self):
        ordered=global_release._ordered_existing([self.prior("v2.10.0","candidate-b"),self.prior("v2.9.99","candidate-a")],"candidate-new")
        self.assertEqual(["v2.9.99","v2.10.0"],[item["release"] for item in ordered])

    def test_05_semver_invalid_prerelease_leading_zero_and_overflow(self):
        for value in ("1.2.3","v1.2.3-rc.1","v01.2.3","v1.2.3+build"," v1.2.3","v2147483648.0.0"):
            with self.subTest(value=value),self.assertRaises(global_release.GlobalReleaseError):global_release.parse_semver(value)
        for bump,previous in (("MAJOR","v2147483647.1.1"),("MINOR","v1.2147483647.1"),("PATCH","v1.1.2147483647")):
            with self.subTest(bump=bump),self.assertRaisesRegex(global_release.GlobalReleaseError,"overflow"):global_release.bump_version(previous,bump)

    def test_06_duplicate_previous_version_rejected(self):
        with self.assertRaisesRegex(global_release.GlobalReleaseError,"duplicate"):global_release._ordered_existing([self.prior("v1.0.0","candidate-a"),self.prior("v1.0.0","candidate-b")],"candidate-new")

    def test_07_candidate_already_released_rejected(self):
        prior=self.prior("v1.0.0",self.candidate()["candidateId"])
        with self.assertRaisesRegex(global_release.GlobalReleaseError,"already"):self.build([prior])

    def test_08_previous_release_tamper_rejected(self):
        release=self.build([self.prior("v2.7.9","candidate-old")]);release["previousRelease"]="v2.7.8"
        self.assertIn("PREVIOUS_RELEASE",global_release.validate_release(release,expected_previous="v2.7.9"))

    def test_09_request_candidate_mismatch_rejected(self):
        request=self.request();request["candidateId"]="candidate-2222222222222222222222222222222222222222-1-1"
        with self.assertRaisesRegex(global_release.GlobalReleaseError,"REQUEST_CANDIDATE"):self.build(request=request)

    def test_10_invalid_candidate_rejected_by_canonical_validator(self):
        candidate=self.candidate();candidate["deployable"]=True
        with self.assertRaisesRegex(global_release.GlobalReleaseError,"candidate invalid"):self.build(candidate=candidate)

    def test_11_six_components_are_copied_integrally_and_in_order(self):
        candidate=self.candidate();release=self.build(candidate=candidate)
        self.assertEqual(candidate["components"],release["components"])
        self.assertEqual(candidate_manifest.ORDER,[item["id"] for item in release["components"]])

    def test_12_component_digest_immutable_or_provenance_tamper_rejected(self):
        for mutation in (
            lambda item:item.update(digest="sha256:"+"f"*64),
            lambda item:item.update(immutableRef="ghcr.io/wrong@sha256:"+"f"*64),
            lambda item:item["provenance"].update(verifiedSubject="ghcr.io/wrong@sha256:"+"f"*64),
        ):
            release=self.build();mutation(release["components"][0])
            with self.subTest(mutation=mutation):self.assertTrue(global_release.validate_release(release))

    def test_13_erp_and_website_inventories_are_deterministic_and_sorted(self):
        first=global_release.inventories();second=global_release.inventories()
        self.assertEqual(first,second);self.assertEqual(["erp","website"],[item["id"] for item in first])
        for database in first:
            keys=[global_release._flyway_key(item["version"]) for item in database["migrations"]]
            self.assertEqual(sorted(keys),keys)

    def test_14_one_sql_byte_changes_file_and_set_digest(self):
        location=global_release.DATABASES[0][2]
        files={location:[("V1__one.sql",b"SELECT 1;\n")],global_release.DATABASES[1][2]:[("V1__one.sql",b"SELECT 1;\n")]}
        temporary,workspace=self.migration_workspace(files)
        try:
            before=global_release.inventories(workspace)[0];(workspace/location/"V1__one.sql").write_bytes(b"SELECT 2;\n");after=global_release.inventories(workspace)[0]
            self.assertNotEqual(before["migrations"][0]["sha256"],after["migrations"][0]["sha256"]);self.assertNotEqual(before["migrationSetSha256"],after["migrationSetSha256"])
        finally:temporary.cleanup()

    def test_15_gitkeep_is_ignored(self):
        location=global_release.DATABASES[0][2];files={location:[("V1__one.sql",b"x"),(".gitkeep",b"")],global_release.DATABASES[1][2]:[("V1__one.sql",b"x")]}
        temporary,workspace=self.migration_workspace(files)
        try:self.assertEqual(1,len(global_release.inventories(workspace)[0]["migrations"]))
        finally:temporary.cleanup()

    def test_16_unknown_symlink_subdirectory_and_missing_root_rejected(self):
        location=global_release.DATABASES[0][2];other=global_release.DATABASES[1][2]
        for kind in ("unknown","symlink","subdirectory","missing"):
            files={location:[("V1__one.sql",b"x")],other:[("V1__one.sql",b"x")]};temporary,workspace=self.migration_workspace(files);root=workspace/location
            try:
                if kind=="unknown":(root/"README.txt").write_text("x")
                elif kind=="symlink":(root/"V2__link.sql").symlink_to(root/"V1__one.sql")
                elif kind=="subdirectory":(root/"nested").mkdir()
                else:
                    for path in root.iterdir():path.unlink()
                    root.rmdir()
                with self.subTest(kind=kind),self.assertRaises(global_release.GlobalReleaseError):global_release.inventories(workspace)
            finally:temporary.cleanup()

    def test_17_normalized_flyway_duplicate_rejected(self):
        location=global_release.DATABASES[0][2];files={location:[("V1__one.sql",b"x"),("V1_0__duplicate.sql",b"y")],global_release.DATABASES[1][2]:[("V1__one.sql",b"x")]}
        temporary,workspace=self.migration_workspace(files)
        try:
            with self.assertRaisesRegex(global_release.GlobalReleaseError,"duplicate"):global_release.inventories(workspace)
        finally:temporary.cleanup()

    def test_18_backup_and_rollback_policy_tamper_rejected(self):
        for key,value in (("backupPolicy","optional"),("rollbackPolicy","down")):
            release=self.build();release["databases"][0][key]=value
            with self.subTest(key=key):self.assertTrue(global_release.validate_release(release))

    def test_19_candidate_artifact_binding_invalid(self):
        for artifact_id,digest in (("0","sha256:"+"3"*64),("300","3"*64),("300","sha256:bad")):
            kwargs=self.kwargs();kwargs["candidate_artifact_id"]=artifact_id;kwargs["candidate_artifact_digest"]=digest
            with self.subTest(artifact_id=artifact_id,digest=digest),self.assertRaisesRegex(global_release.GlobalReleaseError,"artifact"):global_release.build_release(self.candidate(),self.request(),[],**kwargs)

    def test_20_publication_binding_invalid(self):
        cases=({"workflow_run_id":"200"},{"workflow_run_id":"0"},{"workflow_attempt":0},{"actor":" greggorio"},{"actor_id":"0"})
        for changes in cases:
            kwargs=self.kwargs();kwargs.update(changes)
            with self.subTest(changes=changes),self.assertRaises(global_release.GlobalReleaseError):global_release.build_release(self.candidate(),self.request(),[],**kwargs)

    def test_21_existing_sidecar_or_metadata_divergence_rejected(self):
        for target in ("sidecar","metadata"):
            with self.subTest(target=target),tempfile.TemporaryDirectory() as raw:
                directory=Path(raw);release=self.build();global_release.write_release_bundle(directory,release)
                if target=="sidecar":(directory/"release.json.sha256").write_text("0"*64+"\n")
                else:
                    metadata=json.loads((directory/"metadata.json").read_text());metadata["release"]="v9.9.9";(directory/"metadata.json").write_bytes(artifact_io.canonical(metadata))
                with self.assertRaises(global_release.GlobalReleaseError):global_release.load_existing_release(directory/"release.json")

    def test_22_noncanonical_existing_json_rejected(self):
        with tempfile.TemporaryDirectory() as raw:
            directory=Path(raw);release=self.build();global_release.write_release_bundle(directory,release)
            data=(json.dumps(release,indent=2,ensure_ascii=False)+"\n").encode();(directory/"release.json").write_bytes(data);(directory/"release.json.sha256").write_bytes(artifact_io.sidecar(data));(directory/"metadata.json").write_bytes(artifact_io.canonical(global_release.metadata_for(release,data)))
            with self.assertRaisesRegex(global_release.GlobalReleaseError,"canonical"):global_release.load_existing_release(directory/"release.json")

    def test_23_new_bundle_atomic_and_no_partial_files(self):
        with tempfile.TemporaryDirectory() as raw:
            directory=Path(raw)/"bundle";original=artifact_io._stage;count=0
            def stage(target,name,data):
                nonlocal count
                count+=1
                if count==2:raise OSError("stage")
                return original(target,name,data)
            with mock.patch("artifact_io._stage",side_effect=stage):
                with self.assertRaises(OSError):global_release.write_release_bundle(directory,self.build())
            self.assertFalse(directory.exists() and any(directory.iterdir()))
            global_release.write_release_bundle(directory,self.build());self.assertEqual(global_release.BUNDLE_FILES,{path.name for path in directory.iterdir()})

    def test_24_overwrite_failures_restore_previous_bundle(self):
        failures=("staging","rename-1","rename-2","rename-3")
        for failure in failures:
            with self.subTest(failure=failure),tempfile.TemporaryDirectory() as raw:
                directory=Path(raw)/"bundle";old=self.build();global_release.write_release_bundle(directory,old);before={name:(directory/name).read_bytes() for name in global_release.BUNDLE_FILES};new=copy.deepcopy(old);new["description"]="Nova descricao ficticia"
                if failure=="staging":
                    original=artifact_io._stage
                    def stage(target,name,data):
                        if Path(target).name.startswith(".global-release-"):raise OSError("stage")
                        return original(target,name,data)
                    context=mock.patch("artifact_io._stage",side_effect=stage)
                    with context,self.assertRaises(OSError):global_release.write_release_bundle(directory,new,overwrite=True)
                else:
                    fail_at=int(failure[-1]);calls=0
                    def replacer(source,target):
                        nonlocal calls
                        calls+=1
                        if calls==fail_at:raise OSError("replace")
                        os.replace(source,target)
                    with self.assertRaises(OSError):global_release.write_release_bundle(directory,new,overwrite=True,replacer=replacer)
                self.assertEqual(before,{name:(directory/name).read_bytes() for name in global_release.BUNDLE_FILES})

    def test_25_existing_release_argument_order_does_not_change_bytes(self):
        first=self.prior("v2.9.99","candidate-old-a");second=self.prior("v2.10.0","candidate-old-b");second["previousRelease"]="v2.9.99"
        with tempfile.TemporaryDirectory() as raw:
            root=Path(raw);first_dir=root/"first";second_dir=root/"second";global_release.write_release_bundle(first_dir,first);global_release.write_release_bundle(second_dir,second)
            common=["generate","--candidate",str(global_release.CANDIDATE_EXAMPLE),"--candidate-artifact-id","300","--candidate-artifact-digest","sha256:"+"3"*64,"--request",str(global_release.REQUEST_EXAMPLE),"--published-at","2026-07-29T15:00:00Z","--workflow-run-id","400","--workflow-attempt","1","--actor","greggorio","--actor-id","1"]
            output_a=root/"a";output_b=root/"b"
            self.assertEqual(0,global_release.main(common+["--existing-release",str(first_dir/"release.json"),"--existing-release",str(second_dir/"release.json"),"--output",str(output_a)]))
            self.assertEqual(0,global_release.main(common+["--existing-release",str(second_dir/"release.json"),"--existing-release",str(first_dir/"release.json"),"--output",str(output_b)]))
            self.assertEqual((output_a/"release.json").read_bytes(),(output_b/"release.json").read_bytes())

    def test_26_cli_validate_and_generate_exits_and_prefixes(self):
        self.assertEqual(0,global_release.main(["validate","--manifest",str(global_release.RELEASE_EXAMPLE)]))
        with tempfile.TemporaryDirectory() as raw:
            output=Path(raw)/"generated"
            args=["generate","--candidate",str(global_release.CANDIDATE_EXAMPLE),"--candidate-artifact-id","300","--candidate-artifact-digest","sha256:"+"3"*64,"--request",str(global_release.REQUEST_EXAMPLE),"--published-at","2026-07-29T15:00:00Z","--workflow-run-id","400","--workflow-attempt","1","--actor","greggorio","--actor-id","1","--output",str(output)]
            self.assertEqual(0,global_release.main(args));self.assertEqual(global_release.BUNDLE_FILES,{path.name for path in output.iterdir()})
            invalid=Path(raw)/"invalid.json";invalid.write_text("{}")
            with mock.patch("sys.stderr") as stderr:
                self.assertEqual(3,global_release.main(["validate","--manifest",str(invalid)]));self.assertTrue(stderr.write.call_args_list[0].args[0].startswith("global-release:invalid:"))

    def test_27_missing_and_extra_keys_fail(self):
        values=[self.request(),json.loads(global_release.RELEASE_EXAMPLE.read_text())]
        for value in values:
            key=next(iter(value));missing=copy.deepcopy(value);missing.pop(key);extra=copy.deepcopy(value);extra["extra"]=True
            validator=global_release.validate_request if "versionBump" in value else global_release.validate_release
            with self.subTest(kind="missing",key=key):self.assertTrue(validator(missing))
            with self.subTest(kind="extra",key=key):self.assertTrue(validator(extra))

    def test_28_global_example_detects_real_migration_drift(self):
        release=json.loads(global_release.RELEASE_EXAMPLE.read_text());actual=global_release.inventories();self.assertEqual([],global_release.validate_release(release,expected_databases=actual))
        drift=copy.deepcopy(actual);drift[0]["migrationSetSha256"]="sha256:"+"0"*64
        self.assertIn("DATABASE_INVENTORY",global_release.validate_release(release,expected_databases=drift))


if __name__=="__main__":
    unittest.main()
