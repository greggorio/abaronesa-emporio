from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
MODULE_PATH = ROOT / "ops/deploy/deployment-remote.py"
SPEC = importlib.util.spec_from_file_location("deployment_remote_helper", MODULE_PATH)
assert SPEC and SPEC.loader
helper = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = helper
SPEC.loader.exec_module(helper)


class CliCauseTest(unittest.TestCase):
    """The helper used to send the CLI's stderr to /dev/null, so every
    pre-journal failure — env file mode, Docker config, compose model, binary
    guard — reached the runner as the same opaque transport verdict. The cause is
    now preserved, but only in a closed shape: anything else must degrade to no
    cause rather than leak bytes into a log or artifact."""

    def test_canonical_single_key_cause_is_preserved(self) -> None:
        for raw in (
            b'{"errorCode":"COMPOSE_CONFIG_FAILED"}\n',
            b'{"errorCode":"UNSAFE_PATH"}',
            b'noise from a chatty child\n{"errorCode":"DEPENDENCY_UNAVAILABLE"}\n',
        ):
            with self.subTest(raw=raw):
                self.assertIsNotNone(helper._cli_cause(raw))

    def test_recognised_codes_match_the_real_cli_vocabulary(self) -> None:
        for code in (
            "UNSAFE_PATH", "INVALID_CONTRACT", "RELEASE_MISMATCH",
            "CURRENT_STATE_CONFLICT", "DEPENDENCY_UNAVAILABLE",
            "COMPOSE_CONFIG_FAILED", "DOCKER_CONFIG_INVALID",
            "OPERATIONAL_FAILURE", "PULL_FAILED", "BACKUP_FAILED",
        ):
            with self.subTest(code=code):
                payload = ('{"errorCode":"' + code + '"}\n').encode()
                self.assertEqual(code, helper._cli_cause(payload))

    def test_anything_outside_the_closed_shape_yields_no_cause(self) -> None:
        for raw in (
            b"",
            b"\n",
            b"not json at all\n",
            b'{"errorCode":"lowercase"}\n',
            b'{"errorCode":"WITH SPACE"}\n',
            b'{"errorCode":"/absolute/path"}\n',
            b'{"errorCode":"AB"}\n',
            b'{"errorCode":"' + b"A" * 200 + b'"}\n',
            b'{"errorCode":"OK","secret":"tok"}\n',
            b'{"errorCode":123}\n',
            b'{"errorCode":null}\n',
            b'["errorCode"]\n',
            b'{"errorCode":"OK"}' + b"x" * (helper.STDOUT_LIMIT + 1),
            "{\"errorCode\":\"ACENTUAÇÃO\"}\n".encode(),
        ):
            with self.subTest(raw=raw[:40]):
                self.assertIsNone(helper._cli_cause(raw))

    def test_run_bounded_returns_stdout_returncode_and_diagnostic(self) -> None:
        stdout, returncode, diagnostic = helper._run_bounded(
            ["/bin/sh", "-c", 'printf "out\\n"; printf "%s" \'{"errorCode":"X_CODE"}\' >&2; exit 6']
        )
        self.assertEqual(b"out\n", stdout)
        self.assertEqual(6, returncode)
        self.assertEqual("X_CODE", helper._cli_cause(diagnostic))

    def test_diagnostic_never_reaches_stdout(self) -> None:
        stdout, _returncode, diagnostic = helper._run_bounded(
            ["/bin/sh", "-c", 'printf "loud diagnostic\\n" >&2; exit 3']
        )
        self.assertEqual(b"", stdout)
        self.assertIn(b"loud diagnostic", diagnostic)


if __name__ == "__main__":
    unittest.main()
