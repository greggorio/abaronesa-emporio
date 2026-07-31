#!/bin/sh
set -eu

INDEX_FILE="${INDEX_FILE:-/usr/share/nginx/html/index.html}"
RUNTIME_CONFIG_FILE="${RUNTIME_CONFIG_FILE:-/usr/share/nginx/html/runtime-config.js}"
THEME_INTERNAL_API_URL="http://website_back:8085"

require_url() {
  name="$1"
  case "$name" in
    VITE_ERP_API_URL) value="${VITE_ERP_API_URL:-}" ;;
    VITE_WEBSITE_API_URL) value="${VITE_WEBSITE_API_URL:-}" ;;
    *) echo "ERROR: unsupported runtime variable" >&2; exit 1 ;;
  esac
  if [ -z "$value" ]; then
    echo "ERROR: missing required variable $name" >&2
    exit 1
  fi
  case "$value" in
    http://*|https://*) ;;
    *)
      echo "ERROR: invalid public URL in $name" >&2
      exit 1
      ;;
  esac
}

require_url VITE_ERP_API_URL
require_url VITE_WEBSITE_API_URL

theme_json=""
attempt=1
while [ "$attempt" -le 3 ]; do
  if theme_json="$(curl --fail --silent --show-error --max-time 3 \
      "$THEME_INTERNAL_API_URL/api/themes/public/theme/active?tenantId=${THEME_TENANT_ID:-emporio}" 2>/dev/null)"; then
    break
  fi
  theme_json=""
  attempt=$((attempt + 1))
done
if [ -z "$theme_json" ]; then
  theme_json="$(curl --fail --silent --show-error --max-time 3 \
    "$VITE_WEBSITE_API_URL/api/themes/public/theme/active?tenantId=${THEME_TENANT_ID:-emporio}" 2>/dev/null || true)"
fi
if [ -n "$theme_json" ] && ! printf '%s' "$theme_json" | jq -e . >/dev/null 2>&1; then
  theme_json=""
fi

title="Emporio A Baronesa"
description="Emporio A Baronesa"
if [ -n "$theme_json" ]; then
  title="$(printf '%s' "$theme_json" | jq -r '.content.seoTitle // .content.heroTitle // "Emporio A Baronesa"')"
  description="$(printf '%s' "$theme_json" | jq -r '.content.seoDescription // "Emporio A Baronesa"')"
fi
title_escaped="$(printf '%s' "$title" | jq -Rr @html)"
description_escaped="$(printf '%s' "$description" | jq -Rr @html)"

config_tmp="$(mktemp "${RUNTIME_CONFIG_FILE}.XXXXXX")"
index_tmp="$(mktemp "${INDEX_FILE}.XXXXXX")"
cleanup() {
  rm -f "$config_tmp" "$index_tmp"
}
trap cleanup EXIT HUP INT TERM

printf 'window.RuntimeConfig = %s;\n' \
  "$(jq -cn \
      --arg erpApiUrl "$VITE_ERP_API_URL" \
      --arg websiteApiUrl "$VITE_WEBSITE_API_URL" \
      '{erpApiUrl:$erpApiUrl,websiteApiUrl:$websiteApiUrl}')" \
  > "$config_tmp"

chmod u+w "$INDEX_FILE" 2>/dev/null || true
awk -v title="$title_escaped" -v description="$description_escaped" '
  /<!-- SEO_START -->/ { skipping=1; next }
  /<!-- SEO_END -->/ { skipping=0; next }
  skipping { next }
  {
    gsub(/<script src="\/runtime-config\.js"><\/script>/, "")
    if (!inserted && /<\/head>/) {
      print "  <script src=\"/runtime-config.js\"></script>"
      print "  <!-- SEO_START -->"
      print "  <title>" title "</title>"
      print "  <meta name=\"description\" content=\"" description "\" />"
      print "  <!-- SEO_END -->"
      inserted=1
    }
    print
  }
' "$INDEX_FILE" > "$index_tmp"

mv "$config_tmp" "$RUNTIME_CONFIG_FILE"
mv "$index_tmp" "$INDEX_FILE"
chmod 0444 "$RUNTIME_CONFIG_FILE" "$INDEX_FILE"
trap - EXIT HUP INT TERM

if [ -z "$theme_json" ]; then
  echo "Theme unavailable; public defaults applied"
else
  echo "Theme metadata applied"
fi
echo "Runtime configuration generated for required public APIs"
exec "$@"
