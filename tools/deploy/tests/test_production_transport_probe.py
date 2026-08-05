from __future__ import annotations

import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from typing import Any
from unittest import mock

from tools.deploy import production_transport_probe as probe

SHA = "a" * 40
REAL_RUN = subprocess.run


def environment(temporary: Path) -> dict[str, str]:
    identity = temporary / "fixture_identity"
    generated = REAL_RUN(
        ("/usr/bin/ssh-keygen", "-q", "-t", "ed25519", "-N", "", "-f", os.fspath(identity)),
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if generated.returncode != 0:
        raise RuntimeError("fixture key generation failed")
    public = (temporary / "fixture_identity.pub").read_text(encoding="ascii").split()
    fingerprint = REAL_RUN(
        ("/usr/bin/ssh-keygen", "-lf", os.fspath(identity), "-E", "sha256"),
        check=True,
        stdout=subprocess.PIPE,
    ).stdout.decode("ascii").split()[1]
    return {
        "TRUSTED_REPOSITORY": probe.REPOSITORY,
        "TRUSTED_WORKFLOW_REF": f"{probe.REPOSITORY}/{probe.WORKFLOW}@{probe.REF}",
        "TRUSTED_EVENT": "workflow_dispatch",
        "TRUSTED_REF": probe.REF,
        "TRUSTED_SHA": SHA,
        "TRUSTED_RUN_ID": "123",
        "TRUSTED_RUN_ATTEMPT": "1",
        "TRUSTED_ACTOR": "emporio-deployer[bot]",
        "TRUSTED_ACTOR_ID": "313092947",
        "TRUSTED_TRIGGERING_ACTOR": "emporio-deployer[bot]",
        "TRUSTED_SENDER_ID": "313092947",
        "DEPLOYER_ACTOR_IDS": "313092947",
        "RUNNER_TEMP": os.fspath(temporary),
        "PRODUCTION_SSH_HOST": "192.0.2.10",
        "PRODUCTION_SSH_PORT": "22",
        "PRODUCTION_SSH_PRIVATE_KEY": identity.read_text(encoding="ascii"),
        "PRODUCTION_SSH_KNOWN_HOSTS": f"192.0.2.10 {public[0]} {public[1]}\n",
        "PRODUCTION_SSH_PUBLIC_KEY_SHA256": fingerprint,
        "TRUST_RESULT": "success",
        "PROBE_RESULT": "success",
    }


def successful_runner(
    argv: tuple[str, ...], **kwargs: Any
) -> subprocess.CompletedProcess[bytes]:
    if argv[:3] == ("/usr/bin/git", "rev-parse", "HEAD"):
        return subprocess.CompletedProcess(argv, 0, (SHA + "\n").encode(), b"")
    if argv[0] == "/usr/bin/ssh":
        if argv[-2:] != probe.REMOTE_COMMAND:
            raise AssertionError(argv)
        if "SSH_AUTH_SOCK" in kwargs["env"]:
            raise AssertionError("agent forwarded")
        config = Path(argv[2]).read_text(encoding="utf-8")
        for marker in (
            "StrictHostKeyChecking yes",
            "IdentitiesOnly yes",
            "IdentityAgent none",
            "BatchMode yes",
        ):
            if marker not in config:
                raise AssertionError(marker)
        value = {
            "controlSha": SHA,
            "deployRoot": probe.DEPLOY_ROOT,
            "protocol": probe.PROTOCOL,
            "schemaVersion": 1,
            "user": probe.REMOTE_USER,
        }
        return subprocess.CompletedProcess(argv, 0, probe.canonical(value), b"")
    if Path(argv[0]).name == "ssh-keygen":
        return REAL_RUN(argv, **kwargs)
    if Path(argv[0]).name == "shred":
        Path(argv[-1]).unlink()
        return subprocess.CompletedProcess(argv, 0, b"", b"")
    raise AssertionError(argv)


class ProductionTransportProbeTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.environment = mock.patch.dict(os.environ, environment(self.root), clear=False)
        self.environment.start()
        self.addCleanup(self.environment.stop)
        self.addCleanup(self.temporary.cleanup)

    def test_full_probe_artifact_and_cleanup_contract(self) -> None:
        trust = self.root / "trust"
        result = self.root / "probe"
        outcome = self.root / "outcome"
        with mock.patch.object(probe.subprocess, "run", side_effect=successful_runner):
            probe.trust(trust)
            probe.probe(trust, result)
            probe.outcome(trust, result, outcome)
            probe.validate(result, outcome)
        self.assertFalse(any(self.root.glob("emporio-production-transport-*")))
        self.assertEqual(probe._load_bundle(result, probe.PROBE_FILE)["controlSha"], SHA)

    def test_trust_rejects_each_identity_mutant(self) -> None:
        mutants = (
            ("TRUSTED_EVENT", "push"),
            ("TRUSTED_REF", "refs/heads/dev"),
            ("TRUSTED_ACTOR_ID", "999"),
            ("TRUSTED_TRIGGERING_ACTOR", "other"),
            ("TRUSTED_SENDER_ID", "999"),
            ("TRUSTED_WORKFLOW_REF", "other/workflow@refs/heads/main"),
        )
        for name, value in mutants:
            with self.subTest(name=name), mock.patch.dict(os.environ, {name: value}):
                with mock.patch.object(probe.subprocess, "run", side_effect=successful_runner):
                    with self.assertRaises(probe.ProbeError):
                        probe.trust(self.root / f"trust-{name}")

    def test_probe_failure_still_shreds_private_material(self) -> None:
        trust = self.root / "trust"
        with mock.patch.object(probe.subprocess, "run", side_effect=successful_runner):
            probe.trust(trust)

        def failing_runner(argv: tuple[str, ...], **kwargs: Any):
            if argv[0] == "/usr/bin/ssh":
                return subprocess.CompletedProcess(argv, 255, b"", b"Permission denied")
            return successful_runner(argv, **kwargs)

        with mock.patch.object(probe.subprocess, "run", side_effect=failing_runner):
            with self.assertRaisesRegex(probe.ProbeError, "SSH_AUTHENTICATION_FAILED"):
                probe.probe(trust, self.root / "probe")
        self.assertFalse(any(self.root.glob("emporio-production-transport-*")))

    def test_artifact_sidecar_and_binding_mutants_are_rejected(self) -> None:
        trust = self.root / "trust"
        with mock.patch.object(probe.subprocess, "run", side_effect=successful_runner):
            probe.trust(trust)
        (trust / probe._sidecar_name(probe.TRUST_FILE)).write_text(
            "sha256:" + "0" * 64 + "\n", encoding="ascii"
        )
        with self.assertRaisesRegex(probe.ProbeError, "ARTIFACT_DIGEST_INVALID"):
            probe._load_bundle(trust, probe.TRUST_FILE)


if __name__ == "__main__":
    unittest.main()
