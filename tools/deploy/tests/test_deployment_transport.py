from __future__ import annotations

import copy
import importlib.util
import io
import json
import os
import shutil
import stat
import sys
import tarfile
import tempfile
import unittest
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[3]
MODULE_PATH = ROOT / "tools/deploy/deployment_transport.py"
SPEC = importlib.util.spec_from_file_location("deployment_transport", MODULE_PATH)
transport = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = transport
SPEC.loader.exec_module(transport)

REMOTE_PATH = ROOT / "ops/deploy/deployment-remote.py"
REMOTE_SPEC = importlib.util.spec_from_file_location("deployment_remote_s21", REMOTE_PATH)
remote = importlib.util.module_from_spec(REMOTE_SPEC)
assert REMOTE_SPEC and REMOTE_SPEC.loader
sys.modules[REMOTE_SPEC.name] = remote
REMOTE_SPEC.loader.exec_module(remote)

TARGET = ROOT / "ops/releases/examples/global-release.example.json"
COMPOSE = ROOT / "ops/compose/compose.prod.yml"
OPERATION = "deployment_0123456789abcdef"
SHA = "1" * 40


class FakeRunner:
    def __init__(self, results=None):
        self.results = list(results or [])
        self.calls = []

    def run(self, argv, *, timeout_seconds):
        self.calls.append((argv, timeout_seconds))
        if not self.results:
            raise AssertionError("unexpected process")
        value = self.results.pop(0)
        if isinstance(value, Exception):
            raise value
        return value


class FakeRemote:
    def __init__(self, failure=None, result=None, cleanup_failure=False):
        self.failure = failure
        self.result = result or {
            "databaseRestoreRequired": False,
            "errorCode": None,
            "operationId": OPERATION,
            "state": "SUCCEEDED",
        }
        self.cleanup_failure = cleanup_failure
        self.calls = []

    def _call(self, name):
        self.calls.append(name)
        if self.failure == name:
            raise transport.DeploymentTransportError("SSH_UNAVAILABLE")

    def capabilities(self, control_sha): self._call("capabilities")
    def upload(self, archive, operation): self._call("upload")
    def install(self, operation, release, archive_sha256): self._call("install")
    def execute(self, operation, release): self._call("execute"); return self.result
    def cleanup(self, operation):
        self.calls.append("cleanup")
        if self.cleanup_failure:
            raise transport.DeploymentTransportError("REMOTE_CLEANUP_FAILED")


class DeploymentTransportTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp(prefix="s21-transport-", dir="/tmp"))
        self.addCleanup(shutil.rmtree, self.root, True)
        os.chmod(self.root, 0o700)

    def trust_env(self):
        return {
            "TRUSTED_REPOSITORY": transport.REPOSITORY,
            "TRUSTED_OWNER": transport.OWNER,
            "TRUSTED_EVENT": "workflow_dispatch",
            "TRUSTED_REF": "refs/heads/main",
            "TRUSTED_SHA": SHA,
            "TRUSTED_RUN_ID": "123",
            "TRUSTED_RUN_ATTEMPT": "1",
            "TRUSTED_ACTOR_ID": "456",
            "TRUSTED_OPERATION_ID": OPERATION,
            "TRUSTED_RELEASE": "v1.2.3",
            "DEPLOYER_ACTOR_IDS": "123,456,789",
        }

    def request(self):
        trust = transport.validate_trust_environment(self.trust_env())
        return transport.build_deployment_request(trust, "2026-07-31T18:00:00Z")

    def assert_code(self, code, function, *args, **kwargs):
        with self.assertRaises(transport.DeploymentTransportError) as caught:
            function(*args, **kwargs)
        self.assertEqual(code, caught.exception.code)
        self.assertEqual(code, str(caught.exception))

    def assert_remote_code(self, code, function, *args, **kwargs):
        with self.assertRaises(remote.RemoteError) as caught:
            function(*args, **kwargs)
        self.assertEqual(code, caught.exception.code)
        self.assertEqual(code, str(caught.exception))

    def generate_bundle(self, name="bundle"):
        output = self.root / name
        transport.deployment_plan.generate_bundle(
            target_path=TARGET, current_path=None, current_manifest_path=None,
            compose_path=COMPOSE, planned_at="2026-07-31T18:00:00Z",
            output_path=output,
        )
        return output

    def remote_layout(self, name="remote"):
        root = self.root / name
        incoming = root / "shared/deploy/incoming"
        snapshots = root / "shared/deploy/snapshots"
        releases = root / "releases"
        for directory in (root, root / "shared", root / "shared/deploy", incoming,
                          snapshots, releases):
            directory.mkdir(mode=0o700, exist_ok=True)
            os.chmod(directory, 0o700)
        patcher = mock.patch.multiple(
            remote,
            DEPLOY_ROOT=root,
            INCOMING_ROOT=incoming,
            SNAPSHOT_ROOT=snapshots,
            RELEASES_ROOT=releases,
            INSTALLED_STATE=root / "shared/deploy/installed-state.json",
        )
        return root, incoming, snapshots, releases, patcher

    def copy_private_bundle(self, source, destination):
        shutil.copytree(source, destination)
        os.chmod(destination, 0o700)
        for path in destination.iterdir():
            os.chmod(path, 0o600)

    def incoming_archive(self, bundle, incoming, name="incoming.tar.part"):
        archive = incoming / name
        archive_digest = transport.create_bundle_archive(bundle, archive)
        os.chmod(archive, 0o600)
        return archive, archive_digest

    def handoff(self, name="handoff"):
        manifest, _release, _tag, _assets, payloads = self.release_fixture()
        environment = self.trust_env()
        environment["TRUSTED_RELEASE"] = manifest["release"]
        request = transport.build_deployment_request(
            transport.validate_trust_environment(environment),
            "2026-07-31T18:00:00Z",
        )
        directory = self.root / name
        directory.mkdir(mode=0o700)
        (directory / "deployment-request.json").write_bytes(transport.canonical(request))
        for asset_name, data in payloads.items():
            (directory / asset_name).write_bytes(data)
        transport.validate_handoff(directory)
        return directory, request

    def test_01_nominal_dispatch_builds_canonical_request(self):
        request = self.request()
        self.assertEqual("deployment-request", request["kind"])
        self.assertEqual(OPERATION, request["operationId"])
        self.assertEqual("v1.2.3", request["targetRelease"])
        self.assertEqual(transport.canonical(request), transport.canonical(copy.deepcopy(request)))

    def test_02_each_trust_binding_divergence_fails(self):
        mutations = {
            "TRUSTED_REPOSITORY": "fork/repository",
            "TRUSTED_OWNER": "other",
            "TRUSTED_EVENT": "push",
            "TRUSTED_REF": "refs/heads/feature",
            "TRUSTED_SHA": "A" * 40,
        }
        for key, value in mutations.items():
            with self.subTest(key=key):
                env = self.trust_env(); env[key] = value
                self.assert_code("INVALID_DISPATCH", transport.validate_trust_environment, env)

    def test_03_actor_allowlist_is_numeric_and_id_based(self):
        for actor, allowlist in (("", "456"), ("abc", "456"), ("456", "123"), ("456", "greggorio")):
            with self.subTest(actor=actor, allowlist=allowlist):
                env = self.trust_env(); env["TRUSTED_ACTOR_ID"] = actor; env["DEPLOYER_ACTOR_IDS"] = allowlist
                self.assert_code("ACTOR_NOT_ALLOWED", transport.validate_trust_environment, env)

    def test_04_operation_release_and_additional_environment_fail(self):
        for key, value in (("TRUSTED_OPERATION_ID", "short"), ("TRUSTED_RELEASE", "1.2.3")):
            env = self.trust_env(); env[key] = value
            self.assert_code("INVALID_DISPATCH", transport.validate_trust_environment, env)
        env = self.trust_env(); env["EXTRA"] = "value"
        self.assert_code("INVALID_DISPATCH", transport.validate_trust_environment, env)

    def release_fixture(self):
        manifest = json.loads(TARGET.read_text())
        manifest_raw = transport.global_release.canonical(manifest)
        payloads = {
            "release.json": manifest_raw,
            "release.json.sha256": (transport.digest(manifest_raw).removeprefix("sha256:") + "\n").encode(),
            "metadata.json": transport.global_release.canonical(
                transport.global_release.metadata_for(manifest, manifest_raw)
            ),
        }
        assets = []
        for index, (name, (_limit, content_type)) in enumerate(
            transport.release_publication.ASSETS.items(), 1
        ):
            assets.append({
                "id": index, "name": name, "size": len(payloads[name]),
                "state": "uploaded", "content_type": content_type,
                "url": f"https://api.github.com/repos/{transport.REPOSITORY}/releases/assets/{index}",
            })
        release = {
            "id": 99,
            "url": f"https://api.github.com/repos/{transport.REPOSITORY}/releases/99",
            "tag_name": manifest["release"], "name": manifest["release"],
            "target_commitish": manifest["sourceCommit"], "draft": False,
            "prerelease": False, "published_at": "2026-07-31T17:00:00Z",
            "body": "canonical release notes\n",
            "assets": assets,
        }
        sha = manifest["sourceCommit"]
        tag = {
            "ref": f"refs/tags/{manifest['release']}",
            "url": f"https://api.github.com/repos/{transport.REPOSITORY}/git/refs/tags/{manifest['release']}",
            "object": {
                "type": "commit", "sha": sha,
                "url": f"https://api.github.com/repos/{transport.REPOSITORY}/git/commits/{sha}",
            },
        }
        return manifest, release, tag, assets, payloads

    def test_05_release_assets_and_bindings_are_reused_and_validated(self):
        manifest, release, tag, assets, payloads = self.release_fixture()
        actual = transport.validate_release_artifacts(
            requested_release=manifest["release"], release_record=release,
            tag_ref=tag, assets=assets, payloads=payloads,
        )
        self.assertEqual(manifest, actual)
        for mutation in ("draft", "prerelease", "tag", "extra", "sidecar", "metadata"):
            with self.subTest(mutation=mutation):
                m, r, t, a, p = self.release_fixture()
                if mutation in {"draft", "prerelease"}: r[mutation] = True
                elif mutation == "tag": r["tag_name"] = "v9.9.9"
                elif mutation == "extra": a.append(copy.deepcopy(a[0])); a[-1]["name"] = "extra"
                elif mutation == "sidecar": p["release.json.sha256"] = b"0" * 64
                else: p["metadata.json"] = transport.canonical({"wrong": True})
                self.assert_code(
                    "RELEASE_ASSETS_INVALID", transport.validate_release_artifacts,
                    requested_release=m["release"], release_record=r, tag_ref=t,
                    assets=a, payloads=p,
                )

    def test_06_first_install_snapshot_is_exact_and_closed(self):
        request = self.request()
        snapshot = {
            "schemaVersion": 1, "kind": "production-snapshot",
            "operationId": OPERATION, "targetRelease": "v1.2.3",
            "mode": "FIRST_INSTALL", "capturedAt": "2026-07-31T18:00:01Z",
            "currentRelease": None, "installedStateSha256": None,
            "currentManifestSha256": None,
        }
        (self.root / "production-snapshot.json").write_bytes(transport.canonical(snapshot))
        self.assertEqual(snapshot, transport.validate_snapshot(self.root, request))
        (self.root / "extra").write_bytes(b"x")
        self.assert_code("REMOTE_SNAPSHOT_INVALID", transport.validate_snapshot, self.root, request)

    def test_07_archive_has_exact_regular_entries_modes_and_streaming_digest(self):
        bundle = self.generate_bundle()
        archive = self.root / "bundle.tar"
        archive_digest = transport.create_bundle_archive(bundle, archive)
        self.assertRegex(archive_digest, r"^sha256:[0-9a-f]{64}$")
        with tarfile.open(archive, "r:") as tar:
            self.assertEqual(list(transport.BUNDLE_FILES), tar.getnames())
            self.assertTrue(all(item.isfile() and item.mode == 0o600 for item in tar.getmembers()))

    def test_08_unsafe_archive_member_types_and_names_fail(self):
        for name, kind in (("../manifest.json", "file"), ("/manifest.json", "file"), ("manifest.json", "symlink")):
            with self.subTest(name=name, kind=kind):
                archive = self.root / (str(abs(hash((name, kind)))) + ".tar")
                with tarfile.open(archive, "w", format=tarfile.USTAR_FORMAT) as tar:
                    info = tarfile.TarInfo(name)
                    if kind == "symlink": info.type = tarfile.SYMTYPE; info.linkname = "target"; tar.addfile(info)
                    else: info.size = 1; info.mode = 0o600; tar.addfile(info, io.BytesIO(b"x"))
                self.assert_code("BUNDLE_INVALID", transport.validate_bundle_archive, archive)

    def test_09_ssh_material_is_private_and_configuration_is_closed(self):
        ssh = Path(shutil.which("ssh", path="/usr/bin:/bin"))
        scp = Path(shutil.which("scp", path="/usr/bin:/bin"))
        cfg = transport.materialize_ssh_configuration(
            directory=self.root / "ssh", host="production.example.invalid", port=22,
            private_key=b"fixture-private-key", known_hosts=b"fixture known host",
            ssh_binary=ssh, scp_binary=scp,
        )
        self.assertEqual("deploy-emporio@production.example.invalid", cfg.destination)
        self.assertEqual(0o700, stat.S_IMODE(cfg.directory.stat().st_mode))
        self.assertEqual({0o600}, {stat.S_IMODE(p.stat().st_mode) for p in cfg.directory.iterdir()})
        text = cfg.config.read_text()
        for marker in ("Host *", "BatchMode yes", "StrictHostKeyChecking yes", "ForwardAgent no", "ClearAllForwardings yes"):
            self.assertIn(marker, text)

    def test_10_malformed_host_port_and_unsafe_material_fail_before_runner(self):
        ssh = Path(shutil.which("ssh", path="/usr/bin:/bin")); scp = Path(shutil.which("scp", path="/usr/bin:/bin"))
        for host, port in (("-oProxyCommand=bad", 22), ("host;bad", 22), ("host/path", 22), ("::1", 22), ("host", 0), ("host", 65536)):
            with self.subTest(host=host, port=port):
                self.assert_code(
                    "SSH_CONFIGURATION_INVALID", transport.materialize_ssh_configuration,
                    directory=self.root / ("ssh-" + str(abs(hash((host, port))))),
                    host=host, port=port, private_key=b"key", known_hosts=b"host",
                    ssh_binary=ssh, scp_binary=scp,
                )

    def config(self):
        return transport.SshConfiguration(
            self.root, self.root / "config", "deploy-emporio@host.invalid",
            Path("/usr/bin/ssh"), Path("/usr/bin/scp"),
        )

    def test_11_openssh_argv_has_fixed_user_helper_path_and_no_shell(self):
        capability = {
            "controlSha": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "deployRoot": transport.DEPLOY_ROOT, "protocol": transport.PROTOCOL,
            "schemaVersion": 1, "user": transport.REMOTE_USER,
        }
        runner = FakeRunner([transport.ProcessResult(0, transport.canonical(capability))])
        client = transport.OpenSshTransport(self.config(), runner)
        client.capabilities("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        argv, timeout = runner.calls[0]
        self.assertEqual(("/usr/bin/ssh", "-F", str(self.root / "config"), "deploy-emporio@host.invalid", transport.REMOTE_HELPER, "capabilities"), tuple(map(str, argv)))
        self.assertEqual(60, timeout)

    def test_12_remote_result_exit_state_and_identity_are_bound(self):
        for exit_code, state in ((0, "SUCCEEDED"), (20, "ROLLED_BACK"), (21, "FAILED")):
            with self.subTest(exit_code=exit_code):
                value = {"databaseRestoreRequired": state != "SUCCEEDED", "errorCode": None if state == "SUCCEEDED" else "REMOTE_EXECUTION_FAILED", "operationId": OPERATION, "state": state}
                runner = FakeRunner([transport.ProcessResult(exit_code, transport.canonical(value))])
                result = transport.OpenSshTransport(self.config(), runner).execute(OPERATION, "v1.2.3")
                self.assertEqual(state, result["state"])
        value = {"databaseRestoreRequired": False, "errorCode": None, "operationId": "other_operation_123456", "state": "SUCCEEDED"}
        client = transport.OpenSshTransport(self.config(), FakeRunner([transport.ProcessResult(0, transport.canonical(value))]))
        self.assert_code("REMOTE_RESULT_INVALID", client.execute, OPERATION, "v1.2.3")

    def test_13_loss_before_remote_mutation_is_confirmed_failed(self):
        remote = FakeRemote(failure="capabilities")
        outcome = transport.execute_remote(
            request=self.request(), transport=remote, archive=self.root / "unused",
            archive_sha256="sha256:" + "a" * 64,
        )
        self.assertEqual(("CONFIRMED", "FAILED", False), (outcome["transportStatus"], outcome["deploymentState"], outcome["databaseRestoreRequired"]))

    def test_14_loss_during_or_after_execute_is_indeterminate(self):
        for phase in ("install", "execute"):
            with self.subTest(phase=phase):
                remote = FakeRemote(failure=phase)
                outcome = transport.execute_remote(
                    request=self.request(), transport=remote, archive=self.root / "unused",
                    archive_sha256="sha256:" + "a" * 64,
                )
                self.assertEqual("INDETERMINATE", outcome["transportStatus"])
                self.assertIsNone(outcome["deploymentState"])
                self.assertIsNone(outcome["databaseRestoreRequired"])

    def test_15_confirmed_success_and_indeterminate_never_conflate(self):
        success = transport.execute_remote(
            request=self.request(), transport=FakeRemote(), archive=self.root / "unused",
            archive_sha256="sha256:" + "a" * 64,
        )
        self.assertEqual(("CONFIRMED", "SUCCEEDED", None), (success["transportStatus"], success["deploymentState"], success["errorCode"]))
        uncertain = transport.build_outcome(
            self.request(), result=None, transport_status="INDETERMINATE",
            error_code="REMOTE_RESULT_UNAVAILABLE",
        )
        self.assertIsNone(uncertain["deploymentState"])
        self.assertIsNone(uncertain["databaseRestoreRequired"])

    def test_16_cleanup_is_always_attempted_after_upload(self):
        for failure in (None, "install", "execute"):
            remote = FakeRemote(failure=failure)
            transport.execute_remote(
                request=self.request(), transport=remote, archive=self.root / "unused",
                archive_sha256="sha256:" + "a" * 64,
            )
            self.assertEqual("cleanup", remote.calls[-1])

    def test_17_cli_error_is_single_line_and_sanitized(self):
        stderr = io.StringIO()
        with mock.patch.dict(os.environ, {}, clear=True), mock.patch("sys.stderr", stderr):
            code = transport.main(["trust", "--output", str(self.root / "out.json")])
        self.assertEqual(3, code)
        self.assertEqual(["deployment-transport:INVALID_DISPATCH"], stderr.getvalue().splitlines())

    def test_18_prepare_validates_own_run_and_uses_remote_run_started_at(self):
        manifest, release, tag, _assets, payloads = self.release_fixture()
        trust_dir = self.root / "trust"
        trust_dir.mkdir(mode=0o700)
        (trust_dir / "deployment-trust.json").write_bytes(
            transport.canonical(transport.validate_trust_environment(self.trust_env()))
        )

        class Github:
            def __init__(self): self.downloads = []
            def api(inner, method, endpoint, body=None, expected_status=200):
                if "/actions/runs/" in endpoint:
                    return {
                        "id": 123, "run_attempt": 1, "event": "workflow_dispatch",
                        "head_branch": "main", "head_sha": SHA,
                        "name": f"deploy-production-{OPERATION}",
                        "display_title": f"deploy-production-{OPERATION}",
                        "path": ".github/workflows/deploy-production.yml",
                        "html_url": f"https://github.com/{transport.REPOSITORY}/actions/runs/123",
                        "repository": {"full_name": transport.REPOSITORY},
                        "head_repository": {"full_name": transport.REPOSITORY},
                        "actor": {"id": 456},
                        "run_started_at": "2026-07-31T18:00:00Z",
                    }
                if "/releases/tags/" in endpoint: return release
                if "/git/ref/tags/" in endpoint: return tag
                raise AssertionError(endpoint)
            def bytes(inner, endpoint, limit, *headers):
                name = next(asset["name"] for asset in release["assets"]
                            if asset["url"].removeprefix("https://api.github.com") == endpoint)
                inner.downloads.append(name)
                return payloads[name]

        github = Github()
        release_name = manifest["release"]
        trust = self.trust_env(); trust["TRUSTED_RELEASE"] = release_name
        (trust_dir / "deployment-trust.json").write_bytes(
            transport.canonical(transport.validate_trust_environment(trust))
        )
        request = transport.prepare_handoff(
            trust_path=trust_dir, output=self.root / "handoff", remote=github
        )
        self.assertEqual("2026-07-31T18:00:00Z", request["plannedAt"])
        self.assertEqual(set(payloads), set(github.downloads))
        self.assertEqual(
            {"deployment-request.json", "release.json", "release.json.sha256", "metadata.json"},
            {item.name for item in (self.root / "handoff").iterdir()},
        )
        self.assertEqual(manifest["release"], json.loads((self.root / "handoff/release.json").read_text())["release"])

    def test_19_prepare_rejects_every_run_identity_mutant_before_release_access(self):
        trust_file = self.root / "trust-mutants.json"
        trust_file.write_bytes(
            transport.canonical(transport.validate_trust_environment(self.trust_env()))
        )
        nominal = {
            "id": 123,
            "run_attempt": 1,
            "event": "workflow_dispatch",
            "head_branch": "main",
            "head_sha": SHA,
            "name": f"deploy-production-{OPERATION}",
            "display_title": f"deploy-production-{OPERATION}",
            "path": ".github/workflows/deploy-production.yml",
            "html_url": f"https://github.com/{transport.REPOSITORY}/actions/runs/123",
            "repository": {"full_name": transport.REPOSITORY},
            "head_repository": {"full_name": transport.REPOSITORY},
            "actor": {"id": 456},
            "run_started_at": "2026-07-31T18:00:00Z",
        }
        mutants = {
            "name": "Deploy Production",
            "display_title": "deploy-production-other_operation_123456",
            "path": ".github/workflows/deploy-production.yml@main",
            "html_url": f"https://github.com/{transport.REPOSITORY}/actions/runs/124",
            "id": 124,
            "run_attempt": 2,
            "event": "push",
            "head_branch": "dev",
            "head_sha": "b" * 40,
            "repository": {"full_name": "other/repository"},
            "head_repository": {"full_name": "other/repository"},
            "actor": {"id": 457},
        }

        for field, value in mutants.items():
            with self.subTest(field=field):
                current = dict(nominal)
                current[field] = value

                class Github:
                    release_access = 0

                    def api(inner, method, endpoint, body=None, expected_status=200):
                        if "/actions/runs/" in endpoint:
                            return current
                        inner.release_access += 1
                        raise AssertionError("release access must not occur")

                    def bytes(inner, *args):
                        raise AssertionError("download must not occur")

                github = Github()
                self.assert_code(
                    "INVALID_DISPATCH",
                    transport.prepare_handoff,
                    trust_path=trust_file,
                    output=self.root / f"handoff-mutant-{field}",
                    remote=github,
                )
                self.assertEqual(0, github.release_access)

    def test_19a_prepare_accepts_literal_historical_rest_run_shape(self):
        manifest, release, tag, _assets, payloads = self.release_fixture()
        operation_id = "dep_6bd76dcff84a42ba88705b5448aa5c3c"
        run_id = 30981846816
        source_sha = "cf3385f1012b9661ddbc2e83d5241aaa8633f8fd"
        actor_id = 313092947
        trust = self.trust_env()
        trust.update(
            {
                "TRUSTED_OPERATION_ID": operation_id,
                "TRUSTED_RUN_ID": str(run_id),
                "TRUSTED_SHA": source_sha,
                "TRUSTED_ACTOR_ID": str(actor_id),
                "DEPLOYER_ACTOR_IDS": str(actor_id),
                "TRUSTED_RELEASE": manifest["release"],
            }
        )
        trust_file = self.root / "historical-trust.json"
        trust_file.write_bytes(
            transport.canonical(transport.validate_trust_environment(trust))
        )

        class Github:
            def api(inner, method, endpoint, body=None, expected_status=200):
                if "/actions/runs/" in endpoint:
                    return {
                        "id": run_id,
                        "run_attempt": 1,
                        "event": "workflow_dispatch",
                        "head_branch": "main",
                        "head_sha": source_sha,
                        "name": f"deploy-production-{operation_id}",
                        "display_title": f"deploy-production-{operation_id}",
                        "path": ".github/workflows/deploy-production.yml",
                        "html_url": (
                            f"https://github.com/{transport.REPOSITORY}/actions/runs/{run_id}"
                        ),
                        "repository": {"full_name": transport.REPOSITORY},
                        "head_repository": {"full_name": transport.REPOSITORY},
                        "actor": {"id": actor_id},
                        "run_started_at": "2026-08-05T06:34:49Z",
                    }
                if "/releases/tags/" in endpoint:
                    return release
                if "/git/ref/tags/" in endpoint:
                    return tag
                raise AssertionError(endpoint)

            def bytes(inner, endpoint, limit, *headers):
                name = next(
                    asset["name"]
                    for asset in release["assets"]
                    if asset["url"].removeprefix("https://api.github.com") == endpoint
                )
                return payloads[name]

        request = transport.prepare_handoff(
            trust_path=trust_file,
            output=self.root / "historical-handoff",
            remote=Github(),
        )
        self.assertEqual(operation_id, request["operationId"])
        self.assertEqual(run_id, request["workflowRunId"])
        self.assertEqual(source_sha, request["controlSha"])

    def test_20_invalid_asset_inventory_fails_before_first_download(self):
        _manifest, release, _tag, _assets, _payloads = self.release_fixture()
        release["assets"][0]["state"] = "new"
        class Github:
            downloads = 0
            def api(inner, method, endpoint, body=None, expected_status=200):
                if "/actions/runs/" in endpoint:
                    return {
                        "id": 123, "run_attempt": 1, "event": "workflow_dispatch",
                        "head_branch": "main", "head_sha": SHA,
                        "name": f"deploy-production-{OPERATION}",
                        "display_title": f"deploy-production-{OPERATION}",
                        "path": ".github/workflows/deploy-production.yml",
                        "html_url": f"https://github.com/{transport.REPOSITORY}/actions/runs/123",
                        "repository": {"full_name": transport.REPOSITORY},
                        "head_repository": {"full_name": transport.REPOSITORY},
                        "actor": {"id": 456}, "run_started_at": "2026-07-31T18:00:00Z",
                    }
                return release
            def bytes(inner, *args): inner.downloads += 1; return b""
        trust_file = self.root / "trust.json"
        trust_file.write_bytes(transport.canonical(transport.validate_trust_environment(self.trust_env())))
        github = Github()
        self.assert_code(
            "RELEASE_ASSETS_INVALID", transport.prepare_handoff,
            trust_path=trust_file, output=self.root / "handoff-invalid", remote=github,
        )
        self.assertEqual(0, github.downloads)

    def test_20_update_snapshot_binds_canonical_state_manifest_and_hashes(self):
        bundle = self.generate_bundle("update-source")
        snapshot_dir = self.root / "update-snapshot"
        snapshot_dir.mkdir()
        state = json.loads((bundle / "installed-state.next.json").read_text())
        state["installedAt"] = "2026-07-31T18:00:02Z"
        state["reconciled"] = True
        state_raw = transport.canonical(state)
        manifest_raw = (bundle / "manifest.json").read_bytes()
        (snapshot_dir / "installed-state.json").write_bytes(state_raw)
        (snapshot_dir / "current-manifest.json").write_bytes(manifest_raw)
        target_release = json.loads(manifest_raw)["release"]
        request = self.request(); request["targetRelease"] = target_release
        snapshot = {
            "schemaVersion": 1, "kind": "production-snapshot",
            "operationId": OPERATION, "targetRelease": target_release, "mode": "UPDATE",
            "capturedAt": "2026-07-31T18:00:01Z", "currentRelease": target_release,
            "installedStateSha256": transport.digest(state_raw),
            "currentManifestSha256": transport.digest(manifest_raw),
        }
        (snapshot_dir / "production-snapshot.json").write_bytes(transport.canonical(snapshot))
        self.assertEqual(snapshot, transport.validate_snapshot(snapshot_dir, request))

    def test_21_partial_update_snapshot_fails_closed(self):
        directory = self.root / "partial-snapshot"; directory.mkdir()
        snapshot = {
            "schemaVersion": 1, "kind": "production-snapshot", "operationId": OPERATION,
            "targetRelease": "v1.2.3", "mode": "UPDATE",
            "capturedAt": "2026-07-31T18:00:01Z", "currentRelease": "v1.0.0",
            "installedStateSha256": "sha256:" + "a" * 64,
            "currentManifestSha256": "sha256:" + "b" * 64,
        }
        (directory / "production-snapshot.json").write_bytes(transport.canonical(snapshot))
        self.assert_code("REMOTE_SNAPSHOT_INVALID", transport.validate_snapshot, directory, self.request())

    def test_22_planner_first_install_selects_all_six_commercial_components(self):
        bundle = self.generate_bundle("first-install")
        plan = json.loads((bundle / "deployment-plan.json").read_text())
        self.assertEqual(
            {"backend", "website_back", "frontend", "website_front", "whatsapp_service", "gateway"},
            {item["component"] for item in plan["components"]},
        )

    def _archive_with(self, suffix, members, *, fmt=tarfile.USTAR_FORMAT):
        archive = self.root / f"unsafe-{suffix}.tar"
        with tarfile.open(archive, "w", format=fmt) as tar:
            for info, body in members:
                tar.addfile(info, io.BytesIO(body) if body is not None else None)
        return archive

    def test_23_archive_extra_entry_is_rejected(self):
        info = tarfile.TarInfo("extra"); info.size = 1; info.mode = 0o600
        archive = self._archive_with("extra", [(info, b"x")])
        self.assert_code("BUNDLE_INVALID", transport.validate_bundle_archive, archive)

    def test_24_archive_duplicate_entry_is_rejected(self):
        members = []
        for _ in range(2):
            info = tarfile.TarInfo("manifest.json"); info.size = 1; info.mode = 0o600
            members.append((info, b"x"))
        archive = self._archive_with("duplicate", members)
        self.assert_code("BUNDLE_INVALID", transport.validate_bundle_archive, archive)

    def test_25_archive_device_and_fifo_entries_are_rejected(self):
        for label, member_type in (("device", tarfile.CHRTYPE), ("fifo", tarfile.FIFOTYPE)):
            with self.subTest(label=label):
                info = tarfile.TarInfo("manifest.json"); info.type = member_type; info.mode = 0o600
                archive = self._archive_with(label, [(info, None)])
                self.assert_code("BUNDLE_INVALID", transport.validate_bundle_archive, archive)

    def test_26_archive_pax_headers_are_rejected(self):
        info = tarfile.TarInfo("manifest.json"); info.size = 1; info.mode = 0o600
        info.pax_headers = {"comment": "not-allowed"}
        archive = self._archive_with("pax", [(info, b"x")], fmt=tarfile.PAX_FORMAT)
        self.assert_code("BUNDLE_INVALID", transport.validate_bundle_archive, archive)

    def test_27_archive_over_size_limit_is_rejected_before_open(self):
        archive = self.root / "oversize.tar"
        with archive.open("wb") as stream:
            stream.truncate(transport.MAX_ARCHIVE + 1)
        self.assert_code("BUNDLE_INVALID", transport.validate_bundle_archive, archive)

    def test_28_hostile_path_does_not_change_openssh_resolution(self):
        with mock.patch.dict(os.environ, {"PATH": str(self.root)}, clear=False):
            self.assertIn(str(transport.resolve_openssh("ssh")), {"/usr/bin/ssh", "/bin/ssh"})
            self.assertIn(str(transport.resolve_openssh("scp")), {"/usr/bin/scp", "/bin/scp"})

    def test_29_subprocess_timeout_kills_and_reaps_child(self):
        self.assert_code(
            "SSH_UNAVAILABLE", transport.SubprocessRunner().run,
            ("/usr/bin/python3", "-c", "import time; time.sleep(5)"), timeout_seconds=1,
        )

    def test_30_cleanup_failure_preserves_confirmed_remote_state(self):
        outcome = transport.execute_remote(
            request=self.request(), transport=FakeRemote(cleanup_failure=True),
            archive=self.root / "unused", archive_sha256="sha256:" + "a" * 64,
        )
        self.assertEqual("CONFIRMED", outcome["transportStatus"])
        self.assertEqual("SUCCEEDED", outcome["deploymentState"])
        self.assertEqual("REMOTE_CLEANUP_FAILED", outcome["errorCode"])

    def test_31_outcome_schema_rejects_unlisted_transport_error(self):
        self.assert_code(
            "INTERNAL_ERROR", transport.build_outcome, self.request(), result=None,
            transport_status="INDETERMINATE", error_code="SECRET remote.example.invalid",
        )

    def test_32_cli_shapes_match_workflow_handoff_directories(self):
        parser = transport.build_parser()
        self.assertEqual("prepare", parser.parse_args(["prepare", "--trust", "trust", "--output", "handoff"]).command)
        self.assertEqual("deploy", parser.parse_args(["deploy", "--handoff", "handoff", "--output", "result"]).command)
        self.assertEqual("outcome", parser.parse_args(["outcome", "--handoff", "handoff", "--result", "result", "--output", "outcome"]).command)

    def test_33_release_sidecar_uses_s13_s14_raw_hex_lf_format(self):
        _manifest, release, tag, assets, payloads = self.release_fixture()
        sidecar = payloads["release.json.sha256"]
        self.assertRegex(sidecar.decode("ascii"), r"^[0-9a-f]{64}\n$")
        self.assertNotIn(b"sha256:", sidecar)
        self.assertNotIn(b"release.json", sidecar)
        transport.validate_release_artifacts(
            requested_release=release["tag_name"], release_record=release,
            tag_ref=tag, assets=assets, payloads=payloads,
        )

    def _deploy_client(self):
        class Client:
            def __init__(inner): inner.calls = []
            def capabilities(inner, control_sha): inner.calls.append("capabilities")
            def snapshot(inner, operation, release):
                inner.calls.append("snapshot")
                inner.last_operation = operation
                inner.last_release = release
                return {
                    "schemaVersion": 1, "kind": "production-snapshot",
                    "operationId": operation, "targetRelease": release,
                    "mode": "FIRST_INSTALL", "capturedAt": "2026-07-31T18:00:01Z",
                    "currentRelease": None, "installedStateSha256": None,
                    "currentManifestSha256": None,
                }
            def download_snapshot(inner, operation, mode, destination):
                inner.calls.append("download_snapshot")
                destination.mkdir(mode=0o700)
                value = {
                    "schemaVersion": 1, "kind": "production-snapshot",
                    "operationId": inner.last_operation,
                    "targetRelease": inner.last_release, "mode": "FIRST_INSTALL",
                    "capturedAt": "2026-07-31T18:00:01Z", "currentRelease": None,
                    "installedStateSha256": None, "currentManifestSha256": None,
                }
                (destination / "production-snapshot.json").write_bytes(transport.canonical(value))
            def upload(inner, archive, operation): inner.calls.append("upload")
            def install(inner, operation, release, archive_sha): inner.calls.append("install")
            def execute(inner, operation, release):
                inner.calls.append("execute")
                return {"databaseRestoreRequired": False, "errorCode": None,
                        "operationId": operation, "state": "SUCCEEDED"}
            def cleanup(inner, operation): inner.calls.append("cleanup")
        return Client()

    def test_34_deploy_result_artifact_contains_no_snapshot_bundle_or_archive(self):
        handoff, _request = self.handoff("private-handoff")
        client = self._deploy_client()
        output = self.root / "deployment-result"
        with mock.patch.object(transport, "OpenSshTransport", return_value=client):
            outcome = transport.deploy_handoff(
                handoff=handoff, output=output, configuration=self.config(), runner=FakeRunner()
            )
        self.assertEqual("SUCCEEDED", outcome["deploymentState"])
        self.assertEqual({"deployment-result.json"}, {item.name for item in output.iterdir()})
        self.assertEqual("cleanup", client.calls[-1])
        self.assertFalse(any(path.name.startswith(".deployment-work-") for path in self.root.iterdir()))

    def test_35_bundle_failure_after_valid_snapshot_still_cleans_remote_snapshot(self):
        handoff, _request = self.handoff("failure-handoff")
        client = self._deploy_client()
        output = self.root / "failure-result"
        with (
            mock.patch.object(transport, "OpenSshTransport", return_value=client),
            mock.patch.object(transport.deployment_plan, "generate_bundle", side_effect=RuntimeError("fixture")),
        ):
            outcome = transport.deploy_handoff(
                handoff=handoff, output=output, configuration=self.config(), runner=FakeRunner()
            )
        self.assertEqual(("CONFIRMED", "FAILED", "BUNDLE_GENERATION_FAILED"),
                         (outcome["transportStatus"], outcome["deploymentState"], outcome["errorCode"]))
        self.assertEqual("cleanup", client.calls[-1])
        self.assertNotIn("upload", client.calls)

    def test_36_outcome_cli_persists_artifact_before_nonzero_terminal_exit(self):
        handoff, request = self.handoff("outcome-handoff")
        failed = transport.build_outcome(
            request,
            result={"state": "FAILED", "databaseRestoreRequired": False,
                    "errorCode": "SSH_UNAVAILABLE"},
            transport_status="CONFIRMED", error_code="SSH_UNAVAILABLE",
        )
        result = self.root / "prior-result"; result.mkdir()
        (result / "deployment-result.json").write_bytes(transport.canonical(failed))
        output = self.root / "workflow-outcome"
        code = transport.main([
            "outcome", "--handoff", str(handoff), "--result", str(result),
            "--output", str(output),
        ])
        self.assertEqual(4, code)
        self.assertEqual(failed, json.loads((output / "deployment-workflow-outcome.json").read_text()))

    def test_37_deploy_cli_persists_artifact_before_nonzero_terminal_exit(self):
        handoff, request = self.handoff("deploy-cli-handoff")
        failed = transport.build_outcome(
            request,
            result={"state": "FAILED", "databaseRestoreRequired": False,
                    "errorCode": "SSH_UNAVAILABLE"},
            transport_status="CONFIRMED", error_code="SSH_UNAVAILABLE",
        )
        output = self.root / "deploy-cli-result"
        def fake_deploy(**kwargs):
            kwargs["output"].mkdir(mode=0o700)
            (kwargs["output"] / "deployment-result.json").write_bytes(transport.canonical(failed))
            return failed
        environment = {
            "PRODUCTION_SSH_HOST": "production.example.invalid",
            "PRODUCTION_SSH_PORT": "22",
            "PRODUCTION_SSH_PRIVATE_KEY": "fixture-key",
            "PRODUCTION_SSH_KNOWN_HOSTS": "fixture-host",
        }
        with (
            mock.patch.dict(os.environ, environment, clear=True),
            mock.patch.object(transport, "resolve_openssh", return_value=Path("/usr/bin/ssh")),
            mock.patch.object(transport, "materialize_ssh_configuration", return_value=self.config()),
            mock.patch.object(transport, "deploy_handoff", side_effect=fake_deploy),
        ):
            code = transport.main([
                "deploy", "--handoff", str(handoff), "--output", str(output)
            ])
        self.assertEqual(4, code)
        self.assertEqual(failed, json.loads((output / "deployment-result.json").read_text()))

    def test_38_semantically_equal_noncanonical_snapshot_state_is_rejected(self):
        bundle = self.generate_bundle("noncanonical-source")
        directory = self.root / "noncanonical-snapshot"; directory.mkdir()
        state = json.loads((bundle / "installed-state.next.json").read_text())
        state["installedAt"] = "2026-07-31T18:00:02Z"
        state["reconciled"] = True
        state_raw = (json.dumps(state, indent=2, ensure_ascii=False) + "\n").encode()
        manifest_raw = (bundle / "manifest.json").read_bytes()
        (directory / "installed-state.json").write_bytes(state_raw)
        (directory / "current-manifest.json").write_bytes(manifest_raw)
        release = json.loads(manifest_raw)["release"]
        snapshot = {
            "schemaVersion": 1, "kind": "production-snapshot", "operationId": OPERATION,
            "targetRelease": release, "mode": "UPDATE",
            "capturedAt": "2026-07-31T18:00:03Z", "currentRelease": release,
            "installedStateSha256": transport.digest(state_raw),
            "currentManifestSha256": transport.digest(manifest_raw),
        }
        (directory / "production-snapshot.json").write_bytes(transport.canonical(snapshot))
        request = self.request(); request["targetRelease"] = release
        self.assert_code("REMOTE_SNAPSHOT_INVALID", transport.validate_snapshot, directory, request)

    def test_39_remote_archive_hash_mismatch_precedes_staging_or_destination_mutation(self):
        bundle = self.generate_bundle("hash-bundle")
        release = json.loads((bundle / "manifest.json").read_text())["release"]
        _root, incoming, _snapshots, releases, patcher = self.remote_layout("hash-remote")
        with patcher, mock.patch.object(remote, "_validate_identity"), mock.patch.object(remote, "_validate_root"):
            self.incoming_archive(bundle, incoming, f"{OPERATION}.tar.part")
            self.assert_remote_code("BUNDLE_INVALID", remote.install, OPERATION, release, "sha256:" + "f" * 64)
            self.assertFalse((releases / release).exists())
            self.assertFalse((releases / f".{OPERATION}.installing").exists())

    def test_40_interrupted_identical_staging_resumes_but_divergent_archive_preserves_it(self):
        first = self.generate_bundle("staging-first")
        second = self.root / "staging-second"
        transport.deployment_plan.generate_bundle(
            target_path=TARGET, current_path=None, current_manifest_path=None,
            compose_path=COMPOSE, planned_at="2026-07-31T18:00:01Z", output_path=second,
        )
        release = json.loads((first / "manifest.json").read_text())["release"]

        _root, incoming, _snapshots, releases, patcher = self.remote_layout("resume-remote")
        with patcher, mock.patch.object(remote, "_validate_identity"), mock.patch.object(remote, "_validate_root"):
            staging = releases / f".{OPERATION}.installing"
            self.copy_private_bundle(first, staging)
            _archive, archive_digest = self.incoming_archive(first, incoming, f"{OPERATION}.tar.part")
            result = remote.install(OPERATION, release, archive_digest)
            self.assertTrue(result["installed"])
            self.assertTrue((releases / release).is_dir())
            self.assertFalse(staging.exists())

        _root, incoming, _snapshots, releases, patcher = self.remote_layout("divergent-remote")
        with patcher, mock.patch.object(remote, "_validate_identity"), mock.patch.object(remote, "_validate_root"):
            staging = releases / f".{OPERATION}.installing"
            self.copy_private_bundle(first, staging)
            before = {p.name: p.read_bytes() for p in staging.iterdir()}
            _archive, archive_digest = self.incoming_archive(second, incoming, f"{OPERATION}.tar.part")
            self.assert_remote_code("BUNDLE_CONFLICT", remote.install, OPERATION, release, archive_digest)
            self.assertEqual(before, {p.name: p.read_bytes() for p in staging.iterdir()})
            self.assertFalse((releases / release).exists())

    def test_41_existing_identical_destination_replays_and_different_destination_is_immutable(self):
        first = self.generate_bundle("destination-first")
        second = self.root / "destination-second"
        transport.deployment_plan.generate_bundle(
            target_path=TARGET, current_path=None, current_manifest_path=None,
            compose_path=COMPOSE, planned_at="2026-07-31T18:00:01Z", output_path=second,
        )
        release = json.loads((first / "manifest.json").read_text())["release"]
        _root, incoming, _snapshots, releases, patcher = self.remote_layout("destination-remote")
        with patcher, mock.patch.object(remote, "_validate_identity"), mock.patch.object(remote, "_validate_root"):
            destination = releases / release
            self.copy_private_bundle(first, destination)
            _archive, archive_digest = self.incoming_archive(first, incoming, f"{OPERATION}.tar.part")
            self.assertTrue(remote.install(OPERATION, release, archive_digest)["installed"])
            before = {p.name: p.read_bytes() for p in destination.iterdir()}
            (incoming / f"{OPERATION}.tar.part").unlink()
            _archive, divergent_digest = self.incoming_archive(second, incoming, f"{OPERATION}.tar.part")
            self.assert_remote_code("BUNDLE_CONFLICT", remote.install, OPERATION, release, divergent_digest)
            self.assertEqual(before, {p.name: p.read_bytes() for p in destination.iterdir()})

    def test_42_remote_cleanup_is_operation_scoped_and_preserves_production_state(self):
        root, incoming, snapshots, releases, patcher = self.remote_layout("cleanup-remote")
        protected_release = releases / "v0.0.1"; protected_release.mkdir(mode=0o700)
        protected = {
            "journal": root / "shared/deploy/deployment-journal.json",
            "state": root / "shared/deploy/installed-state.json",
            "backup": root / "shared/deploy/backup.tar",
        }
        for path in protected.values(): path.write_bytes(b"preserve")
        (root / "current").symlink_to("releases/v0.0.1")
        (root / "previous").symlink_to("releases/v0.0.1")
        part = incoming / f"{OPERATION}.tar.part"; part.write_bytes(b"part"); os.chmod(part, 0o600)
        for path in (snapshots / OPERATION, snapshots / f"{OPERATION}.staging"):
            path.mkdir(mode=0o700); child = path / "fixture"; child.write_bytes(b"x"); os.chmod(child, 0o600)
        with patcher, mock.patch.object(remote, "_validate_identity"), mock.patch.object(remote, "_validate_root"):
            self.assertEqual({"cleaned": True, "operationId": OPERATION}, remote.cleanup(OPERATION))
        self.assertFalse(part.exists())
        self.assertFalse((snapshots / OPERATION).exists())
        self.assertFalse((snapshots / f"{OPERATION}.staging").exists())
        self.assertTrue(protected_release.is_dir())
        self.assertTrue((root / "current").is_symlink())
        self.assertTrue((root / "previous").is_symlink())
        self.assertTrue(all(path.read_bytes() == b"preserve" for path in protected.values()))

    def test_43_partial_remote_snapshot_staging_fails_without_mutating_its_bytes(self):
        _root, _incoming, snapshots, _releases, patcher = self.remote_layout("snapshot-remote")
        staging = snapshots / f"{OPERATION}.staging"; staging.mkdir(mode=0o700)
        marker = staging / "partial"; marker.write_bytes(b"diagnostic-bytes"); os.chmod(marker, 0o600)
        before = marker.read_bytes()
        with patcher, mock.patch.object(remote, "_validate_identity"), mock.patch.object(remote, "_validate_root"):
            self.assert_remote_code("REMOTE_SNAPSHOT_INVALID", remote.snapshot, OPERATION, "v0.0.1")
        self.assertEqual(before, marker.read_bytes())
        self.assertEqual({"partial"}, {path.name for path in staging.iterdir()})
        self.assertFalse((snapshots / OPERATION).exists())

    def test_44_remote_capabilities_enforces_nonroot_exact_user_and_nominal_shape(self):
        with mock.patch.object(remote, "_validate_root"):
            with mock.patch.object(remote.os, "geteuid", return_value=0):
                self.assert_remote_code("REMOTE_CAPABILITY_MISMATCH", remote.capabilities)
            wrong = type("Account", (), {"pw_name": "another-user"})()
            with mock.patch.object(remote.os, "geteuid", return_value=1234), mock.patch.object(remote.pwd, "getpwuid", return_value=wrong):
                self.assert_remote_code("REMOTE_CAPABILITY_MISMATCH", remote.capabilities)
        with mock.patch.object(remote, "_validate_identity"), mock.patch.object(remote, "_validate_root"), \
                mock.patch.object(remote, "_installed_control_sha", return_value="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"):
            self.assertEqual(
                {"controlSha": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                 "deployRoot": "/opt/sistemas/emporio", "protocol": "emporio-deployment-transport",
                 "schemaVersion": 1, "user": "deploy-emporio"},
                remote.capabilities(),
            )

    def test_45_prepare_output_remains_private(self):
        manifest, release, tag, _assets, payloads = self.release_fixture()
        trust = self.trust_env()
        trust["TRUSTED_RELEASE"] = manifest["release"]
        trust_file = self.root / "private-trust.json"
        trust_file.write_bytes(
            transport.canonical(transport.validate_trust_environment(trust))
        )

        class Github:
            def api(inner, method, endpoint, body=None, expected_status=200):
                if "/actions/runs/" in endpoint:
                    return {
                        "id": 123, "run_attempt": 1, "event": "workflow_dispatch",
                        "head_branch": "main", "head_sha": SHA,
                        "name": f"deploy-production-{OPERATION}",
                        "display_title": f"deploy-production-{OPERATION}",
                        "path": ".github/workflows/deploy-production.yml",
                        "html_url": f"https://github.com/{transport.REPOSITORY}/actions/runs/123",
                        "repository": {"full_name": transport.REPOSITORY},
                        "head_repository": {"full_name": transport.REPOSITORY},
                        "actor": {"id": 456},
                        "run_started_at": "2026-07-31T18:00:00Z",
                    }
                if "/releases/tags/" in endpoint:
                    return release
                if "/git/ref/tags/" in endpoint:
                    return tag
                raise AssertionError(endpoint)

            def bytes(inner, endpoint, limit, *headers):
                name = next(
                    asset["name"] for asset in release["assets"]
                    if asset["url"].removeprefix("https://api.github.com") == endpoint
                )
                return payloads[name]

        output = self.root / "private-prepare"
        transport.prepare_handoff(trust_path=trust_file, output=output, remote=Github())
        self.assertEqual(0o700, stat.S_IMODE(output.stat().st_mode))
        self.assertEqual(
            {0o600},
            {stat.S_IMODE(path.stat().st_mode) for path in output.iterdir()},
        )

    def test_46_public_artifact_modes_are_rematerialized_private_and_identical(self):
        ingress, _request = self.handoff("public-ingress")
        expected = {path.name: path.read_bytes() for path in ingress.iterdir()}
        os.chmod(ingress, 0o755)
        for path in ingress.iterdir():
            os.chmod(path, 0o644)
        private_path = None
        with transport.private_handoff(ingress, self.root) as private:
            private_path = private
            self.assertEqual(0o700, stat.S_IMODE(private.stat().st_mode))
            self.assertEqual(
                {0o600},
                {stat.S_IMODE(path.stat().st_mode) for path in private.iterdir()},
            )
            self.assertEqual(
                expected, {path.name: path.read_bytes() for path in private.iterdir()}
            )
        self.assertIsNotNone(private_path)
        self.assertFalse(private_path.exists())

    def test_47_hostile_ingress_and_toctou_fail_before_ssh_environment(self):
        class ForbiddenEnvironment(dict):
            def get(self, key, default=None):
                if key.startswith("PRODUCTION_SSH_"):
                    raise AssertionError(f"environment-read:{key}")
                return super().get(key, default)

        for mutation in ("directory-mode", "file-mode", "symlink", "extra"):
            with self.subTest(mutation=mutation):
                ingress, _request = self.handoff(f"hostile-{mutation}")
                if mutation == "directory-mode":
                    os.chmod(ingress, 0o777)
                elif mutation == "file-mode":
                    os.chmod(ingress / "release.json", 0o666)
                elif mutation == "symlink":
                    target = ingress / "release.json"
                    raw = target.read_bytes()
                    target.unlink()
                    replacement = self.root / f"{mutation}-release.json"
                    replacement.write_bytes(raw)
                    target.symlink_to(replacement)
                else:
                    (ingress / "extra").write_bytes(b"x")
                with mock.patch.object(
                    transport.os, "environ", ForbiddenEnvironment(os.environ)
                ):
                    self.assertEqual(
                        3,
                        transport.main([
                            "deploy", "--handoff", str(ingress),
                            "--output", str(self.root / f"out-{mutation}"),
                        ]),
                    )

        ingress, _request = self.handoff("hostile-toctou")
        replacement = self.root / "replacement-release.json"
        replacement.write_bytes((ingress / "release.json").read_bytes())
        os.chmod(replacement, 0o644)
        original_open = transport.os.open
        replaced = False

        def racing_open(path, *args, **kwargs):
            nonlocal replaced
            if path == "release.json" and not replaced:
                replaced = True
                os.replace(replacement, ingress / "release.json")
            return original_open(path, *args, **kwargs)

        with mock.patch.object(transport.os, "open", side_effect=racing_open):
            self.assert_code("INVALID_DISPATCH", transport.validate_handoff, ingress)

    def test_48_bad_port_persists_confirmed_local_failure_without_process(self):
        ingress, request = self.handoff("bad-port-handoff")
        output = self.root / "bad-port-result"
        environment = {
            "PRODUCTION_SSH_HOST": "production.example.invalid",
            "PRODUCTION_SSH_PORT": "not-a-port",
            "PRODUCTION_SSH_PRIVATE_KEY": "fixture-key",
            "PRODUCTION_SSH_KNOWN_HOSTS": "fixture-host",
        }
        with (
            mock.patch.dict(os.environ, environment, clear=True),
            mock.patch.object(transport, "SubprocessRunner", side_effect=AssertionError("process")),
            mock.patch.object(transport, "resolve_openssh", side_effect=AssertionError("openssh")),
        ):
            code = transport.main([
                "deploy", "--handoff", str(ingress), "--output", str(output)
            ])
        self.assertEqual(4, code)
        artifact = output / "deployment-result.json"
        value = json.loads(artifact.read_text())
        self.assertEqual(
            ("CONFIRMED", "FAILED", "SSH_CONFIGURATION_INVALID"),
            (value["transportStatus"], value["deploymentState"], value["errorCode"]),
        )
        self.assertEqual(request["operationId"], value["operationId"])
        self.assertEqual(0o600, stat.S_IMODE(artifact.stat().st_mode))

        second, _request = self.handoff("bad-port-persist-failure")
        with (
            mock.patch.dict(os.environ, environment, clear=True),
            mock.patch.object(transport, "_persist_command_outcome", side_effect=OSError("fixture")),
        ):
            self.assertEqual(
                3,
                transport.main([
                    "deploy", "--handoff", str(second),
                    "--output", str(self.root / "unwritten-local-result"),
                ]),
            )

    def test_49_missing_openssh_persists_confirmed_unavailable_without_process(self):
        ingress, _request = self.handoff("missing-openssh-handoff")
        output = self.root / "missing-openssh-result"
        environment = {
            "PRODUCTION_SSH_HOST": "production.example.invalid",
            "PRODUCTION_SSH_PORT": "22",
            "PRODUCTION_SSH_PRIVATE_KEY": "fixture-key",
            "PRODUCTION_SSH_KNOWN_HOSTS": "fixture-host",
        }
        with (
            mock.patch.dict(os.environ, environment, clear=True),
            mock.patch.object(
                transport, "resolve_openssh",
                side_effect=transport.DeploymentTransportError("SSH_UNAVAILABLE"),
            ),
            mock.patch.object(transport, "SubprocessRunner", side_effect=AssertionError("process")),
        ):
            code = transport.main([
                "deploy", "--handoff", str(ingress), "--output", str(output)
            ])
        self.assertEqual(4, code)
        value = json.loads((output / "deployment-result.json").read_text())
        self.assertEqual(
            ("CONFIRMED", "FAILED", "SSH_UNAVAILABLE"),
            (value["transportStatus"], value["deploymentState"], value["errorCode"]),
        )

    def test_50_absent_remote_result_persists_indeterminate_unavailable(self):
        ingress, request = self.handoff("absent-result-handoff")
        output = self.root / "absent-result-outcome"
        code = transport.main([
            "outcome", "--handoff", str(ingress),
            "--result", str(self.root / "does-not-exist"),
            "--output", str(output),
        ])
        self.assertEqual(4, code)
        value = json.loads((output / "deployment-workflow-outcome.json").read_text())
        self.assertEqual("REMOTE_RESULT_UNAVAILABLE", value["errorCode"])
        self.assertEqual(request["operationId"], value["operationId"])

    def test_51_noncanonical_truncated_and_extra_results_persist_invalid(self):
        ingress, request = self.handoff("invalid-result-handoff")
        valid = transport.build_outcome(
            request,
            result={"state": "SUCCEEDED", "databaseRestoreRequired": False,
                    "errorCode": None},
            transport_status="CONFIRMED", error_code=None,
        )
        payloads = {
            "noncanonical": (json.dumps(valid, indent=2) + "\n").encode(),
            "truncated": b'{"schemaVersion":1',
            "extra": transport.canonical({**valid, "unexpected": True}),
        }
        for label, raw in payloads.items():
            with self.subTest(label=label):
                result = self.root / f"result-{label}"
                result.mkdir()
                (result / "deployment-result.json").write_bytes(raw)
                output = self.root / f"outcome-{label}"
                self.assertEqual(
                    4,
                    transport.main([
                        "outcome", "--handoff", str(ingress),
                        "--result", str(result), "--output", str(output),
                    ]),
                )
                value = json.loads(
                    (output / "deployment-workflow-outcome.json").read_text()
                )
                self.assertEqual("REMOTE_RESULT_INVALID", value["errorCode"])

    def test_52_result_binding_mismatch_uses_trusted_private_bindings(self):
        ingress, request = self.handoff("binding-result-handoff")
        value = transport.build_outcome(
            request,
            result={"state": "SUCCEEDED", "databaseRestoreRequired": False,
                    "errorCode": None},
            transport_status="CONFIRMED", error_code=None,
        )
        value["workflowRunId"] += 1
        result = self.root / "binding-result"
        result.mkdir()
        (result / "deployment-result.json").write_bytes(transport.canonical(value))
        output = self.root / "binding-outcome"
        self.assertEqual(
            4,
            transport.main([
                "outcome", "--handoff", str(ingress),
                "--result", str(result), "--output", str(output),
            ]),
        )
        actual = json.loads((output / "deployment-workflow-outcome.json").read_text())
        self.assertEqual("REMOTE_RESULT_INVALID", actual["errorCode"])
        for key in ("operationId", "targetRelease", "workflowRunId", "workflowRunAttempt", "controlSha"):
            self.assertEqual(request[key], actual[key])

    def test_53_canonical_schema_incomplete_result_never_promotes_success(self):
        ingress, _request = self.handoff("incomplete-result-handoff")
        result = self.root / "incomplete-result"
        result.mkdir()
        (result / "deployment-result.json").write_bytes(
            transport.canonical({"transportStatus": "CONFIRMED", "deploymentState": "SUCCEEDED"})
        )
        output = self.root / "incomplete-outcome"
        self.assertEqual(
            4,
            transport.main([
                "outcome", "--handoff", str(ingress),
                "--result", str(result), "--output", str(output),
            ]),
        )
        actual = json.loads((output / "deployment-workflow-outcome.json").read_text())
        self.assertEqual("REMOTE_RESULT_INVALID", actual["errorCode"])

    def test_54_failed_states_require_valid_error_in_schema_and_remote_result(self):
        request = self.request()
        for state in ("ROLLED_BACK", "FAILED"):
            for error in (None, "", "x"):
                with self.subTest(state=state, error=error):
                    value = {
                        "schemaVersion": 1, "kind": "deployment-workflow-outcome",
                        "operationId": request["operationId"],
                        "targetRelease": request["targetRelease"],
                        "workflowRunId": request["workflowRunId"],
                        "workflowRunAttempt": request["workflowRunAttempt"],
                        "controlSha": request["controlSha"],
                        "transportStatus": "CONFIRMED", "deploymentState": state,
                        "databaseRestoreRequired": True, "errorCode": error,
                    }
                    self.assert_code(
                        "REMOTE_RESULT_INVALID", transport._validate_schema,
                        value, transport.OUTCOME_SCHEMA, "REMOTE_RESULT_INVALID",
                    )
                    remote_value = {
                        "databaseRestoreRequired": True, "errorCode": error,
                        "operationId": OPERATION, "state": state,
                    }
                    exit_code = 20 if state == "ROLLED_BACK" else 21
                    client = transport.OpenSshTransport(
                        self.config(),
                        FakeRunner([transport.ProcessResult(exit_code, transport.canonical(remote_value))]),
                    )
                    self.assert_code("REMOTE_RESULT_INVALID", client.execute, OPERATION, "v1.2.3")

    def test_55_valid_result_is_byte_preserved_and_only_clean_success_exits_zero(self):
        ingress, request = self.handoff("valid-result-handoff")
        cases = (
            ("success", {"state": "SUCCEEDED", "databaseRestoreRequired": False,
                         "errorCode": None}, 0),
            ("failed", {"state": "FAILED", "databaseRestoreRequired": True,
                        "errorCode": "REMOTE_EXECUTION_FAILED"}, 4),
        )
        for label, remote_result, expected_exit in cases:
            with self.subTest(label=label):
                value = transport.build_outcome(
                    request, result=remote_result,
                    transport_status="CONFIRMED", error_code=None,
                )
                raw = transport.canonical(value)
                result = self.root / f"valid-result-{label}"
                result.mkdir()
                (result / "deployment-result.json").write_bytes(raw)
                output = self.root / f"valid-outcome-{label}"
                self.assertEqual(
                    expected_exit,
                    transport.main([
                        "outcome", "--handoff", str(ingress),
                        "--result", str(result), "--output", str(output),
                    ]),
                )
                artifact = output / "deployment-workflow-outcome.json"
                self.assertEqual(raw, artifact.read_bytes())
                self.assertEqual(0o600, stat.S_IMODE(artifact.stat().st_mode))

    def _assert_t02a_artifact(self, output, request, error_code):
        artifact = output / "deployment-result.json"
        raw = artifact.read_bytes()
        value = json.loads(raw)
        self.assertEqual(raw, transport.canonical(value))
        transport._validate_schema(
            value, transport.OUTCOME_SCHEMA, "REMOTE_RESULT_INVALID"
        )
        self.assertEqual(
            ("CONFIRMED", "FAILED", error_code),
            (
                value["transportStatus"],
                value["deploymentState"],
                value["errorCode"],
            ),
        )
        for binding in (
            "operationId", "targetRelease", "workflowRunId",
            "workflowRunAttempt", "controlSha",
        ):
            self.assertEqual(request[binding], value[binding])
        self.assertEqual(0o600, stat.S_IMODE(artifact.stat().st_mode))

    def test_56_t02a_ssh_mkdtemp_oserror_persists_confirmed_failure(self):
        ingress, request = self.handoff("t02a-mkdtemp-handoff")
        output = self.root / "t02a-mkdtemp-result"
        environment = {
            "PRODUCTION_SSH_HOST": "production.example.invalid",
            "PRODUCTION_SSH_PORT": "22",
            "PRODUCTION_SSH_PRIVATE_KEY": "fixture-key",
            "PRODUCTION_SSH_KNOWN_HOSTS": "fixture-host",
        }
        real_mkdtemp = tempfile.mkdtemp

        def fail_only_ssh_workspace(*args, **kwargs):
            if kwargs.get("prefix") == ".deployment-ssh-":
                raise OSError("fixture ssh workspace failure")
            return real_mkdtemp(*args, **kwargs)

        with (
            mock.patch.dict(os.environ, environment, clear=True),
            mock.patch.object(
                transport.tempfile, "mkdtemp", side_effect=fail_only_ssh_workspace
            ),
            mock.patch.object(
                transport, "SubprocessRunner", side_effect=AssertionError("process")
            ) as runner,
            mock.patch.object(
                transport, "deploy_handoff", side_effect=AssertionError("remote")
            ) as deploy,
        ):
            code = transport.main([
                "deploy", "--handoff", str(ingress), "--output", str(output),
            ])
        self.assertEqual(4, code)
        self._assert_t02a_artifact(
            output, request, "SSH_CONFIGURATION_INVALID"
        )
        runner.assert_not_called()
        deploy.assert_not_called()
        self.assertEqual([], list(self.root.glob(".deployment-ssh-*")))

    def test_57_t02a_partial_ssh_materialization_oserror_is_removed(self):
        ingress, request = self.handoff("t02a-partial-handoff")
        output = self.root / "t02a-partial-result"
        environment = {
            "PRODUCTION_SSH_HOST": "production.example.invalid",
            "PRODUCTION_SSH_PORT": "22",
            "PRODUCTION_SSH_PRIVATE_KEY": "fixture-key",
            "PRODUCTION_SSH_KNOWN_HOSTS": "fixture-host",
        }
        partial_workspace = None

        def fail_after_partial_materialization(**kwargs):
            nonlocal partial_workspace
            partial_workspace = Path(kwargs["directory"])
            partial_workspace.mkdir(mode=0o700)
            (partial_workspace / "identity").write_bytes(b"partial-key")
            os.chmod(partial_workspace / "identity", 0o600)
            raise OSError("fixture partial materialization failure")

        with (
            mock.patch.dict(os.environ, environment, clear=True),
            mock.patch.object(
                transport, "resolve_openssh", return_value=Path("/usr/bin/ssh")
            ),
            mock.patch.object(
                transport, "materialize_ssh_configuration",
                side_effect=fail_after_partial_materialization,
            ),
            mock.patch.object(
                transport, "SubprocessRunner", side_effect=AssertionError("process")
            ) as runner,
            mock.patch.object(
                transport, "deploy_handoff", side_effect=AssertionError("remote")
            ) as deploy,
        ):
            code = transport.main([
                "deploy", "--handoff", str(ingress), "--output", str(output),
            ])
        self.assertEqual(4, code)
        self._assert_t02a_artifact(
            output, request, "SSH_CONFIGURATION_INVALID"
        )
        self.assertIsNotNone(partial_workspace)
        self.assertFalse(partial_workspace.exists())
        runner.assert_not_called()
        deploy.assert_not_called()

    def test_58_t02a_typed_ssh_unavailable_is_preserved(self):
        ingress, request = self.handoff("t02a-unavailable-handoff")
        output = self.root / "t02a-unavailable-result"
        environment = {
            "PRODUCTION_SSH_HOST": "production.example.invalid",
            "PRODUCTION_SSH_PORT": "22",
            "PRODUCTION_SSH_PRIVATE_KEY": "fixture-key",
            "PRODUCTION_SSH_KNOWN_HOSTS": "fixture-host",
        }
        with (
            mock.patch.dict(os.environ, environment, clear=True),
            mock.patch.object(
                transport, "resolve_openssh",
                side_effect=transport.DeploymentTransportError("SSH_UNAVAILABLE"),
            ),
            mock.patch.object(
                transport, "SubprocessRunner", side_effect=AssertionError("process")
            ) as runner,
            mock.patch.object(
                transport, "deploy_handoff", side_effect=AssertionError("remote")
            ) as deploy,
        ):
            code = transport.main([
                "deploy", "--handoff", str(ingress), "--output", str(output),
            ])
        self.assertEqual(4, code)
        self._assert_t02a_artifact(output, request, "SSH_UNAVAILABLE")
        runner.assert_not_called()
        deploy.assert_not_called()
        self.assertEqual([], list(self.root.glob(".deployment-ssh-*")))

    def test_59_t02a_persistence_failure_after_oserror_stays_exit_three(self):
        ingress, _request = self.handoff("t02a-persist-failure-handoff")
        output = self.root / "t02a-persist-failure-result"
        environment = {
            "PRODUCTION_SSH_HOST": "production.example.invalid",
            "PRODUCTION_SSH_PORT": "22",
            "PRODUCTION_SSH_PRIVATE_KEY": "fixture-key",
            "PRODUCTION_SSH_KNOWN_HOSTS": "fixture-host",
        }
        real_mkdtemp = tempfile.mkdtemp

        def fail_only_ssh_workspace(*args, **kwargs):
            if kwargs.get("prefix") == ".deployment-ssh-":
                raise OSError("fixture ssh workspace failure")
            return real_mkdtemp(*args, **kwargs)

        with (
            mock.patch.dict(os.environ, environment, clear=True),
            mock.patch.object(
                transport.tempfile, "mkdtemp", side_effect=fail_only_ssh_workspace
            ),
            mock.patch.object(
                transport, "_persist_command_outcome",
                side_effect=OSError("fixture persistence failure"),
            ),
            mock.patch.object(
                transport, "SubprocessRunner", side_effect=AssertionError("process")
            ) as runner,
            mock.patch.object(
                transport, "deploy_handoff", side_effect=AssertionError("remote")
            ) as deploy,
        ):
            code = transport.main([
                "deploy", "--handoff", str(ingress), "--output", str(output),
            ])
        self.assertEqual(3, code)
        self.assertFalse(output.exists())
        runner.assert_not_called()
        deploy.assert_not_called()
        self.assertEqual([], list(self.root.glob(".deployment-ssh-*")))


if __name__ == "__main__":
    unittest.main()
