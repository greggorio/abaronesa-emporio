#!/usr/bin/env python3
"""Resolve changed paths from GitHub event metadata without lossy shell transport."""

from __future__ import annotations

import json
import argparse
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/releases"))
import catalog

ZERO = "0" * 40


def changed_paths(base: str, head: str, pull_request: bool) -> list[str] | None:
    if not base or base == ZERO:
        return None
    separator = f"{base}...{head}" if pull_request else f"{base}..{head}"
    process = subprocess.run(
        ["git", "diff", "--name-only", "-z", separator],
        cwd=ROOT, capture_output=True,
    )
    if process.returncode:
        return None
    try:
        return [part.decode("utf-8", "strict") for part in process.stdout.split(b"\0") if part]
    except UnicodeDecodeError:
        return None


def resolve_event(event: dict, sha: str) -> dict:
    is_pr = "pull_request" in event
    base = event.get("pull_request", {}).get("base", {}).get("sha") if is_pr else event.get("before")
    paths = changed_paths(base or "", sha, is_pr)
    cat = catalog.load_yaml()
    if paths is None:
        result = catalog.resolve(cat, first_release=True)
        result["warnings"] = sorted(set(result["warnings"] + ["DIFF_BASE_UNAVAILABLE_FAIL_CLOSED"]))
        return result
    if not paths:
        return catalog.resolve(cat, [".github/workflows/ci.yml"])
    return catalog.resolve(cat, paths)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(argv)
    event_path = Path(os.environ["GITHUB_EVENT_PATH"])
    result = resolve_event(json.loads(event_path.read_text()), os.environ["GITHUB_SHA"])
    output = json.dumps(result, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    print(output)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(output + "\n", encoding="utf-8")
    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with Path(github_output).open("a", encoding="utf-8") as stream:
            stream.write(f"resolution={output}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
