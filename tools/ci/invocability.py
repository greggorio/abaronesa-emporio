#!/usr/bin/env python3
"""Prove workflow Python commands are accepted without executing their work."""

from __future__ import annotations

import ast
import json
import os
import re
import shlex
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
CI = ROOT / ".github/workflows/ci.yml"
PUBLISH = ROOT / ".github/workflows/publish-candidate.yml"
EXPECTED_COMMANDS = 27
SELF_COMMANDS = {
    "tools/ci/validate_ci.py",
    "tools/ci/invocability.py",
}
EXPRESSION = re.compile(r"\$\{\{(.*?)\}\}")


@dataclass(frozen=True)
class Invocation:
    workflow: str
    job: str
    step: str
    line: int
    command: str
    argv: tuple[str, ...]

    @property
    def script(self) -> str:
        return self.argv[1]

    @property
    def label(self) -> str:
        return f"{self.workflow}:{self.job}:{self.step}:line-{self.line}"


def _synthetic(expression: re.Match[str]) -> str:
    value = expression.group(1).strip()
    if "&& format(" in value or value.endswith("|| ''"):
        return ""
    if "artifact-digest" in value or "digest" in value:
        return "a" * 64
    if "attestation-url" in value:
        return "https://example.invalid/attestation"
    if "repository" in value:
        return "ghcr.io/greggorio/abaronesa-emporio-backend"
    if "component" in value:
        return "backend"
    if "sha" in value:
        return "1" * 40
    if any(token in value for token in ("run_id", "run_attempt", "artifact-id", "attestation-id")):
        return "1"
    if any(token in value for token in ("head_mode", "outputs.mode", "outputs.status")):
        return "continue"
    if "predecessor_id" in value or "candidate_id" in value:
        return "candidate-synthetic"
    return "synthetic"


def synthesize(command: str) -> tuple[str, ...]:
    rendered = EXPRESSION.sub(_synthetic, command)
    try:
        argv = tuple(shlex.split(rendered))
    except ValueError as error:
        raise ValueError(f"shell syntax:{error}") from error
    if len(argv) < 2 or argv[0] != "python3" or not argv[1].startswith("tools/") or not argv[1].endswith(".py"):
        raise ValueError("python tool command")
    return argv


def extract(text: str, workflow: str) -> list[Invocation]:
    try:
        data = yaml.load(text, Loader=yaml.BaseLoader)
    except yaml.YAMLError as error:
        raise ValueError(f"yaml:{error}") from error
    commands: list[Invocation] = []
    for job_name, job in data.get("jobs", {}).items():
        for index, step in enumerate(job.get("steps", []), start=1):
            for line_number, raw in enumerate(str(step.get("run", "")).splitlines(), start=1):
                command = raw.strip()
                if not re.match(r"^python3\s+tools/[^\s]+\.py(?:\s|$)", command):
                    continue
                argv = synthesize(command)
                if argv[1] in SELF_COMMANDS:
                    continue
                commands.append(
                    Invocation(
                        workflow=workflow,
                        job=str(job_name),
                        step=str(step.get("name", f"step-{index}")),
                        line=line_number,
                        command=command,
                        argv=argv,
                    )
                )
    return commands


def inventory(ci: str | None = None, publish: str | None = None) -> tuple[list[Invocation], list[str]]:
    errors: list[str] = []
    try:
        commands = extract(ci if ci is not None else CI.read_text(), "ci.yml")
        commands += extract(publish if publish is not None else PUBLISH.read_text(), "publish-candidate.yml")
    except (OSError, ValueError) as error:
        return [], [f"inventory:{error}"]
    if len(commands) != EXPECTED_COMMANDS:
        errors.append(f"command-count:{len(commands)}:expected:{EXPECTED_COMMANDS}")
    for command in commands:
        path = ROOT / command.script
        if not path.is_file():
            errors.append(f"{command.label}:missing-script:{command.script}")
    return commands, errors


PROBE = r'''
import argparse,json,os,runpy,sys
from pathlib import Path

script=Path(sys.argv[1]).resolve()
root=Path(os.environ["INVOCABILITY_ROOT"]).resolve()
temporary=Path(os.environ["INVOCABILITY_TMP"]).resolve()
arguments=sys.argv[2:]
sys.argv=[str(script),*arguments]
sys.path[:0]=[str(script.parent),str(root),str(root/"tools/releases"),str(root/"tools/candidates")]

class Accepted(Exception): pass

original=argparse.ArgumentParser.parse_args
def parse_args(parser,args=None,namespace=None):
    parsed=original(parser,args,namespace)
    print("INVOCABILITY_ACCEPTED:"+json.dumps(vars(parsed),sort_keys=True,default=str))
    raise Accepted()
argparse.ArgumentParser.parse_args=parse_args

def audit(event,args):
    if event in {"socket.connect","socket.bind","subprocess.Popen","os.system"}:
        raise RuntimeError("forbidden-before-parse:"+event)
    if event=="open" and len(args)>1:
        mode=args[1]
        writing=(isinstance(mode,str) and any(flag in mode for flag in "wax+")) or (isinstance(mode,int) and mode & 3 != 0)
        if writing:
            target=Path(args[0]).resolve()
            if temporary != target and temporary not in target.parents:
                raise RuntimeError("write-outside-temporary-before-parse")
    if event in {"os.mkdir","os.remove","os.rmdir","os.rename"}:
        targets=args[:2] if event=="os.rename" else args[:1]
        for value in targets:
            target=Path(value).resolve()
            if temporary != target and temporary not in target.parents:
                raise RuntimeError("mutation-outside-temporary-before-parse")
sys.addaudithook(audit)

try:
    runpy.run_path(str(script),run_name="__main__")
except Accepted:
    raise SystemExit(0)
except SystemExit as error:
    raise
raise SystemExit("command completed without parse_args")
'''


def _argument_free(script: Path, argv: tuple[str, ...]) -> bool:
    if len(argv) != 2:
        return False
    source = script.read_text(encoding="utf-8")
    tree = ast.parse(source, filename=str(script))
    return "parse_args" not in source and any(
        isinstance(node, ast.If)
        and any(isinstance(child, ast.Call) for child in ast.walk(node))
        for node in tree.body
    )


def probe(command: Invocation, temporary: Path) -> str | None:
    script = ROOT / command.script
    if _argument_free(script, command.argv):
        return None
    environment = os.environ.copy()
    environment.update(
        INVOCABILITY_ROOT=str(ROOT),
        INVOCABILITY_TMP=str(temporary),
        PYTHONDONTWRITEBYTECODE="1",
        GITHUB_OUTPUT=str(temporary / "github-output"),
        GITHUB_ENV=str(temporary / "github-env"),
        GH_TOKEN="synthetic-not-used",
        GITHUB_RUN_ID="1",
        GITHUB_RUN_ATTEMPT="1",
        CANDIDATE_CREATED_AT="2026-01-01T00:00:00Z",
        CANDIDATE_POSTGRES_IMAGE="postgres@sha256:" + "a" * 64,
    )
    completed = subprocess.run(
        [sys.executable, "-c", PROBE, str(script), *command.argv[2:]],
        cwd=temporary,
        env=environment,
        text=True,
        capture_output=True,
        timeout=15,
        check=False,
    )
    if completed.returncode == 0 and "INVOCABILITY_ACCEPTED:" in completed.stdout:
        return None
    output = " ".join((completed.stderr or completed.stdout).strip().split())
    return f"{command.label}:refused:exit-{completed.returncode}:{output[:500]}:command={command.command}"


def validate(ci: str | None = None, publish: str | None = None) -> list[str]:
    commands, errors = inventory(ci, publish)
    if errors:
        return errors
    with tempfile.TemporaryDirectory(prefix="emporio-invocability-") as raw:
        temporary = Path(raw)
        for command in commands:
            try:
                error = probe(command, temporary)
            except (OSError, subprocess.TimeoutExpired, SyntaxError, ValueError) as failure:
                error = f"{command.label}:probe-error:{failure}:command={command.command}"
            if error:
                errors.append(error)
    return errors


def main() -> int:
    errors = validate()
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    commands, _ = inventory()
    modes = {"parse_args": 0, "argument-free": 0}
    for command in commands:
        if _argument_free(ROOT / command.script, command.argv):
            modes["argument-free"] += 1
        else:
            modes["parse_args"] += 1
    print(f"invocability:valid:commands={len(commands)}:parse_args={modes['parse_args']}:argument-free={modes['argument-free']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
