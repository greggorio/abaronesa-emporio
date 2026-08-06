"""Development launcher for the local publisher and deployer runtimes.

The production image runs its own bootstrap and never imports this module; the
runtime profile refuses to use it at all. It exists so that starting a runtime
locally is one command that executes inside the locked environment, instead of a
shell pipeline that has to name the ASGI path, the host and the port on every
invocation and that runs the launcher itself outside that environment.

Migrations are applied before the server starts because a developer expects a
usable database after one command. That convenience is deliberately confined to
the development profile: `runtime` keeps migrations an explicit, separate act.
"""

from __future__ import annotations

import argparse
import os
import sys
from collections.abc import Sequence
from pathlib import Path

ASGI_APP = "emporio_release_control.main:app"
DEFAULT_HOST = "127.0.0.1"
# Ports are fixed per mode so the frontend's loopback allowlist and the CORS
# origins stay predictable; both remain overridable for a second local instance.
DEFAULT_PORT = {"publisher": 8090, "deployer": 8091}
DEVELOPMENT = "development"
CONFIG_HOME = Path.home() / ".config/emporio/release-control"


def _project_root() -> Path:
    """Locate the directory holding alembic.ini, from src/<package>/dev.py."""
    return Path(__file__).resolve().parents[2]


def _default_env_file(mode: str) -> Path:
    return CONFIG_HOME / f"{mode}-runtime.env"


def _load_env_file(path: Path) -> int:
    """Export the settings this runtime needs, without overriding the shell.

    Settings reads only real environment variables — there is no env_file — so a
    developer had to source a file by hand before every start, and forgetting it
    produced a wall of pydantic errors instead of a usable hint. Loading it here
    keeps that strictness where it was designed to matter, the runtime profile,
    while making the development launcher a single command. Values already
    present in the environment always win, so an explicit export still overrides
    the file.
    """
    loaded = 0
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        line = line.removeprefix("export ").lstrip()
        name, separator, value = line.partition("=")
        if not separator:
            continue
        name = name.strip()
        if not name.startswith("RELEASE_CONTROL_") or name in os.environ:
            continue
        os.environ[name] = value.strip().strip("'\"")
        loaded += 1
    return loaded


def _parse(mode: str, argv: Sequence[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog=mode, description=f"Run the {mode} runtime locally."
    )
    parser.add_argument("--host", default=DEFAULT_HOST)
    parser.add_argument("--port", type=int, default=DEFAULT_PORT[mode])
    parser.add_argument(
        "--env-file",
        type=Path,
        default=Path(os.environ.get("RELEASE_CONTROL_ENV_FILE", _default_env_file(mode))),
        help="settings to export before starting; the shell always wins",
    )
    parser.add_argument(
        "--skip-migrations",
        action="store_true",
        help="start the server without applying pending migrations first",
    )
    parser.add_argument(
        "--reload", action="store_true", help="restart the server on code changes"
    )
    return parser.parse_args(argv)


def _missing_settings() -> list[str]:
    """Name the absent settings instead of letting a traceback stand in for them."""
    from pydantic import ValidationError

    from .config import Settings

    try:
        Settings()
    except ValidationError as error:
        return sorted(
            str(item["loc"][0])
            for item in error.errors()
            if item["type"] == "missing" and item["loc"]
        )
    return []


def _run(mode: str, argv: Sequence[str] | None = None) -> int:
    args = _parse(mode, argv)

    origin = "environment"
    if args.env_file.is_file():
        # Printed on every start: auto-loading is convenient, and the cost of
        # convenience is running against stale settings without noticing.
        origin = f"{args.env_file} + environment"
        _load_env_file(args.env_file)

    profile = os.environ.get("RELEASE_CONTROL_PROFILE", "runtime")
    if profile != DEVELOPMENT:
        print(
            f"{mode}: this launcher is development-only, but "
            f"RELEASE_CONTROL_PROFILE is {profile!r}.",
            file=sys.stderr,
        )
        if not args.env_file.is_file():
            print(f"{mode}: no settings file at {args.env_file}.", file=sys.stderr)
        return 2

    # The invoked script is the authority on the mode: the same package serves
    # both, and a stale value in the environment must not decide which one runs.
    os.environ["RELEASE_CONTROL_MODE"] = mode

    missing = _missing_settings()
    if missing:
        print(f"{mode}: settings missing from {origin}:", file=sys.stderr)
        for name in missing:
            print(f"  RELEASE_CONTROL_{name.upper()}", file=sys.stderr)
        return 2

    root = _project_root()
    if not args.skip_migrations:
        from alembic import command
        from alembic.config import Config

        configuration = Config(str(root / "alembic.ini"))
        configuration.set_main_option("script_location", str(root / "migrations"))
        command.upgrade(configuration, "head")

    import uvicorn

    print(f"{mode}: http://{args.host}:{args.port}  (profile {profile}, settings from {origin})")
    uvicorn.run(ASGI_APP, host=args.host, port=args.port, reload=args.reload)
    return 0


def publisher() -> int:
    return _run("publisher")


def deployer() -> int:
    return _run("deployer")
