#!/usr/bin/env python3
"""Offline secret detector that reports fingerprints, never matched values."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ALLOWLIST = ROOT / "tools/ci/secret-allowlist.json"
RULES = (
    ("PRIVATE_KEY", re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")),
    ("GITHUB_TOKEN", re.compile(rb"\b(?:ghp|github_pat)_[A-Za-z0-9_]{20,}\b")),
    ("GOOGLE_API_KEY", re.compile(rb"\bAIza[0-9A-Za-z_-]{30,}\b")),
    ("SENSITIVE_ASSIGNMENT", re.compile(
        rb"(?im)^[ \t]*(?:export[ \t]+)?(?:[A-Z0-9_]*(?:PASSWORD|SECRET|TOKEN|API_KEY|ACCESS_KEY|CLIENT_SECRET)[A-Z0-9_]*)[ \t]*[:=][ \t]*[\"']?([A-Za-z0-9+/_.:@-]{12,})"
    )),
)
PLACEHOLDERS = (b"replace-with", b"__set_", b"from-secret-manager")


def fingerprint(rule: str, path: str, captured: bytes) -> str:
    digest = hashlib.sha256()
    digest.update(rule.encode()); digest.update(b"\0")
    digest.update(path.encode("utf-8", "surrogateescape")); digest.update(b"\0")
    digest.update(captured)
    return digest.hexdigest()[:16]


def findings_for(path: str, content: bytes) -> list[str]:
    findings: list[str] = []
    for rule, pattern in RULES:
        if rule == "SENSITIVE_ASSIGNMENT" and not (
            path.endswith((".env", ".properties", ".yml", ".yaml"))
            or ".env." in path
        ):
            continue
        for match in pattern.finditer(content):
            captured = match.group(1) if match.lastindex else match.group(0)
            if any(marker in captured.lower() for marker in PLACEHOLDERS):
                continue
            findings.append(f"{rule}:{path}:{fingerprint(rule, path, captured)}")
    return findings


def tracked_files() -> list[str]:
    result = subprocess.run(["git", "ls-files", "-z"], cwd=ROOT, capture_output=True, check=True)
    return [item.decode("utf-8", "surrogateescape") for item in result.stdout.split(b"\0") if item]


def allowlisted_findings() -> set[str]:
    try:
        data = json.loads(ALLOWLIST.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return set()
    return {
        f"{item['rule']}:{item['path']}:{item['fingerprint']}"
        for item in data.get("entries", [])
        if isinstance(item, dict)
        and set(item) == {"fingerprint", "rule", "path", "justification"}
        and item["justification"].strip()
    }


def scan_paths(paths: list[str]) -> tuple[list[str], int, list[str]]:
    findings: list[str] = []
    scanned = 0
    unsupported: list[str] = []
    for relative in paths:
        path = ROOT / relative
        try:
            content = path.read_bytes()
        except OSError:
            unsupported.append(
                f"UNREADABLE:{relative}:{fingerprint('UNREADABLE', relative, b'')}"
            )
            continue
        findings.extend(findings_for(relative, content))
        scanned += 1
    return findings, scanned, unsupported


def scan_history() -> tuple[list[str], int, list[str]]:
    revisions = subprocess.run(
        ["git", "rev-list", "--all"], cwd=ROOT, capture_output=True
    )
    if revisions.returncode:
        return [], 0, []
    findings: list[str] = []
    scanned = 0
    unsupported: list[str] = []
    for commit in revisions.stdout.decode("ascii").splitlines():
        tree = subprocess.run(
            ["git", "ls-tree", "-r", "-z", "--name-only", commit],
            cwd=ROOT, capture_output=True, check=True,
        )
        for raw_path in (item for item in tree.stdout.split(b"\0") if item):
            path = raw_path.decode("utf-8", "surrogateescape")
            result = subprocess.run(
                ["git", "show", f"{commit}:{path}"],
                cwd=ROOT, capture_output=True,
            )
            if result.returncode:
                unsupported.append(
                    f"HISTORY_UNREADABLE:{path}:{fingerprint('HISTORY_UNREADABLE', path, commit.encode())}"
                )
                continue
            content = result.stdout
            findings.extend(findings_for(path, content))
            scanned += 1
    return findings, scanned, unsupported


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--tracked", action="store_true")
    parser.add_argument("paths", nargs="*")
    args = parser.parse_args(argv)
    paths = tracked_files() if args.tracked else args.paths
    if not paths:
        print("secret-scan:error:no input", file=sys.stderr)
        return 2
    findings, scanned, unsupported = scan_paths(paths)
    history_scanned = 0
    if args.tracked:
        history_findings, history_scanned, history_unsupported = scan_history()
        findings.extend(history_findings)
        unsupported.extend(history_unsupported)
    allowed = allowlisted_findings()
    allowed_count = sum(item in allowed for item in findings)
    findings = [item for item in findings if item not in allowed]
    summary = (
        f"scanned={scanned}:allowed={allowed_count}:unsupported={len(unsupported)}:"
        f"history_scanned={history_scanned}"
    )
    if findings or unsupported:
        print("\n".join(sorted(set(findings + unsupported))), file=sys.stderr)
        print(f"secret-scan:failed:{summary}", file=sys.stderr)
        return 1
    print(f"secret-scan:clean:{summary}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
