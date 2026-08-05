from __future__ import annotations

import os
import subprocess
import tempfile
import unittest
from pathlib import Path

from tools.deploy import ssh_material


class SshMaterialTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory(prefix="ssh-material-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.ed25519 = self.root / "ed25519"
        self.rsa = self.root / "rsa"
        for path, kind in ((self.ed25519, "ed25519"), (self.rsa, "rsa")):
            subprocess.run(
                ("/usr/bin/ssh-keygen", "-q", "-t", kind, "-N", "", "-f", os.fspath(path)),
                check=True,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
        public = self.ed25519.with_suffix(".pub").read_text(encoding="ascii").split()
        self.known_hosts = f"192.0.2.10 {public[0]} {public[1]}\n"
        self.fingerprint = subprocess.run(
            ("/usr/bin/ssh-keygen", "-lf", os.fspath(self.ed25519), "-E", "sha256"),
            check=True,
            stdout=subprocess.PIPE,
        ).stdout.decode("ascii").split()[1]

    def materialize(self, name: str, private: bytes | str, fingerprint: str | None = None):
        return ssh_material.materialize_ssh_configuration(
            directory=self.root / name,
            host="192.0.2.10",
            port=22,
            private_key=private,
            known_hosts=self.known_hosts,
            expected_fingerprint=self.fingerprint if fingerprint is None else fingerprint,
        )

    def test_real_ed25519_with_or_without_lf_materializes_identically(self) -> None:
        payload = self.ed25519.read_bytes()
        first = self.materialize("material", payload)
        first_key = (first.directory / "identity").read_bytes()
        first_config = first.config.read_bytes()
        ssh_material.cleanup_ssh_configuration(first.directory)
        second = self.materialize("material", payload.rstrip(b"\n"))
        self.assertEqual(first_key, (second.directory / "identity").read_bytes())
        self.assertEqual(first_config, second.config.read_bytes())
        self.assertEqual(self.fingerprint, second.fingerprint)
        self.assertIn(b"IdentityAgent none\n", second.config.read_bytes())
        ssh_material.cleanup_ssh_configuration(second.directory)

    def test_private_key_shape_mutants_fail_closed(self) -> None:
        private = self.ed25519.read_bytes()
        mutants = {
            "crlf": private.replace(b"\n", b"\r\n"),
            "double-lf": private + b"\n",
            "truncated": private[:-30],
            "trailing": private + b"data",
            "payload": private.replace(private.splitlines()[1][:1], b"!", 1),
        }
        for name, value in mutants.items():
            with self.subTest(name=name), self.assertRaises(ssh_material.SshMaterialError) as raised:
                self.materialize(name, value)
            self.assertEqual("SSH_KEY_FORMAT_INVALID", raised.exception.code)
            self.assertFalse((self.root / name).exists())

    def test_rsa_wrong_missing_fingerprint_and_bad_known_hosts_fail(self) -> None:
        with self.assertRaises(ssh_material.SshMaterialError) as rsa:
            self.materialize("rsa-material", self.rsa.read_bytes())
        self.assertEqual("SSH_KEY_FORMAT_INVALID", rsa.exception.code)
        self.assertFalse((self.root / "rsa-material").exists())

        for name, fingerprint in (("wrong", "SHA256:" + "A" * 43), ("missing", "")):
            with self.subTest(name=name), self.assertRaises(ssh_material.SshMaterialError) as raised:
                self.materialize(name, self.ed25519.read_bytes(), fingerprint)
            self.assertEqual("SSH_KEY_FINGERPRINT_MISMATCH", raised.exception.code)
            self.assertFalse((self.root / name).exists())

        with self.assertRaises(ssh_material.SshMaterialError) as hosts:
            ssh_material.materialize_ssh_configuration(
                directory=self.root / "hosts",
                host="192.0.2.10",
                port=22,
                private_key=self.ed25519.read_bytes(),
                known_hosts=self.known_hosts.replace("192.0.2.10", "192.0.2.11"),
                expected_fingerprint=self.fingerprint,
            )
        self.assertEqual("SSH_KNOWN_HOSTS_INVALID", hosts.exception.code)
        self.assertFalse((self.root / "hosts").exists())

    def test_closed_diagnostic_classification(self) -> None:
        cases = (
            (b"Permission denied (publickey).", "capabilities", "SSH_AUTHENTICATION_FAILED"),
            (b"Host key verification failed.", "capabilities", "SSH_KNOWN_HOSTS_INVALID"),
            (b"Connection timed out", "capabilities", "SSH_CONNECTION_FAILED"),
            (b"remote emitted an invalid result", "capabilities", "REMOTE_CAPABILITY_MISMATCH"),
        )
        for stderr, stage, expected in cases:
            with self.subTest(expected=expected):
                self.assertEqual(expected, ssh_material.classify_ssh_failure(stderr, stage=stage))

    def test_no_agent_shell_secret_argv_or_permissive_fallback(self) -> None:
        configuration = self.materialize("closed", self.ed25519.read_bytes())
        source = Path(ssh_material.__file__).read_text(encoding="utf-8")
        config = configuration.config.read_text(encoding="ascii")
        for marker in (
            "BatchMode yes", "IdentitiesOnly yes", "IdentityAgent none",
            "StrictHostKeyChecking yes", "ForwardAgent no", "ClearAllForwardings yes",
        ):
            self.assertIn(marker, config)
        for forbidden in ("shell=True", "SSH_AUTH_SOCK", "StrictHostKeyChecking no"):
            self.assertNotIn(forbidden, source + config)
        self.assertEqual(0o600, configuration.config.stat().st_mode & 0o777)
        ssh_material.cleanup_ssh_configuration(configuration.directory)
        self.assertFalse(configuration.directory.exists())


if __name__ == "__main__":
    unittest.main()
