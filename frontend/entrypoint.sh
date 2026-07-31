#!/bin/sh
set -eu

INDEX_FILE="${INDEX_FILE:-/usr/share/nginx/html/index.html}"
RUNTIME_CONFIG_FILE="${RUNTIME_CONFIG_FILE:-/usr/share/nginx/html/runtime-config.js}"

if [ -z "${VITE_BASE_API_URL:-}" ]; then
  echo "ERROR: missing required variable VITE_BASE_API_URL" >&2
  exit 1
fi

release_control_mode="${RELEASE_CONTROL_MODE:-disabled}"
case "$release_control_mode" in
  disabled|deployer) ;;
  *)
    echo "ERROR: invalid RELEASE_CONTROL_MODE" >&2
    exit 1
    ;;
esac

case "$VITE_BASE_API_URL" in
  http://*|https://*) ;;
  *)
    echo "ERROR: invalid public URL in VITE_BASE_API_URL" >&2
    exit 1
    ;;
esac

config_tmp="$(mktemp "${RUNTIME_CONFIG_FILE}.XXXXXX")"
index_tmp="$(mktemp "${INDEX_FILE}.XXXXXX")"
cleanup() {
  rm -f "$config_tmp" "$index_tmp"
}
trap cleanup EXIT HUP INT TERM

printf 'window.RuntimeConfig = %s;\n' \
  "$(jq -cn --arg apiBaseUrl "$VITE_BASE_API_URL" --arg releaseControlMode "$release_control_mode" '{apiBaseUrl:$apiBaseUrl,releaseControlMode:$releaseControlMode}')" \
  > "$config_tmp"

chmod u+w "$INDEX_FILE" 2>/dev/null || true
awk '{
  gsub(/<script src="\/runtime-config\.js"><\/script>/, "")
  if (!inserted && /<head>/) {
    sub(/<head>/, "<head>\\n  <script src=\"/runtime-config.js\"></script>")
    inserted=1
  }
  print
}
' "$INDEX_FILE" > "$index_tmp"

mv "$config_tmp" "$RUNTIME_CONFIG_FILE"
mv "$index_tmp" "$INDEX_FILE"
chmod 0444 "$RUNTIME_CONFIG_FILE" "$INDEX_FILE"
trap - EXIT HUP INT TERM

echo "Runtime configuration generated for VITE_BASE_API_URL and RELEASE_CONTROL_MODE"
exec "$@"
