#!/usr/bin/env python3
"""Fail-closed structural validation for the two Flyway migration sets."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DIRECTORIES = (
    ROOT / "backend/src/main/resources/db/migration",
    ROOT / "website_back/src/main/resources/db/migration",
)
NAME = re.compile(r"^V([0-9]+(?:\.[0-9]+)*)__([A-Za-z0-9][A-Za-z0-9_]*)\.sql$")


def validate(directories: tuple[Path, ...] = DIRECTORIES) -> list[str]:
    errors: list[str] = []
    for directory in directories:
        versions: dict[tuple[int, ...], str] = {}
        if not directory.is_dir():
            errors.append(f"MIGRATION_DIRECTORY_MISSING:{directory}")
            continue
        for path in sorted(directory.iterdir(), key=lambda item: item.name):
            if path.name == ".gitkeep":
                continue
            if not path.is_file():
                errors.append(f"MIGRATION_ENTRY_NOT_FILE:{path.name}")
                continue
            match = NAME.fullmatch(path.name)
            if not match:
                errors.append(f"MIGRATION_NAME_INVALID:{path.name}")
                continue
            version = tuple(int(part) for part in match.group(1).split("."))
            if version in versions:
                errors.append(f"MIGRATION_VERSION_DUPLICATE:{versions[version]}:{path.name}")
            versions[version] = path.name
            try:
                content = path.read_text(encoding="utf-8")
            except (OSError, UnicodeError):
                errors.append(f"MIGRATION_UNREADABLE:{path.name}")
                continue
            if not content.strip():
                errors.append(f"MIGRATION_EMPTY:{path.name}")
    return errors


def main() -> int:
    errors = validate()
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print("migrations:valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
