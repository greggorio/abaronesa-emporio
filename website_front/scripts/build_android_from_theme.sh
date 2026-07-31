#!/usr/bin/env bash
set -euo pipefail

VILLA_API_URL="${VILLA_API_URL:-http://localhost:8085}"

if [[ -z "${TENANT_ID:-}" ]]; then
  echo "Selecione o tenant:"
  echo "1) espresso"
  echo "2) villa"
  read -r -p "Opcao (1/2): " tenant_choice
  case "${tenant_choice}" in
    1) TENANT_ID="espresso" ;;
    2) TENANT_ID="villa" ;;
    *) echo "Opcao invalida."; exit 1 ;;
  esac
else
  TENANT_ID="${TENANT_ID}"
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${PROJECT_ROOT}"

echo "==> Gerando arquivos Android do tema (tenant: ${TENANT_ID})"
VILLA_API_URL="${VILLA_API_URL}" TENANT_ID="${TENANT_ID}" python scripts/prepare_android_from_theme.py

echo "==> Build web"
npm run build

echo "==> Capacitor sync (android)"
npx cap sync android

echo "OK: build Android preparado para tenant ${TENANT_ID}"
