#!/usr/bin/env bash
set -euo pipefail
umask 077
SCRIPT_DIRECTORY="${BASH_SOURCE[0]%/*}"
cd -P -- "${SCRIPT_DIRECTORY}/../.."
REPOSITORY_ROOT="${PWD}"
exec python3 "${REPOSITORY_ROOT}/tools/deploy/deployment_cli.py" "$@"
