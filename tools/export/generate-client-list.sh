#!/usr/bin/env bash
set -euo pipefail

API_URL=${API_URL:-http://localhost:8080}
EMAIL=${API_EMAIL:-root@localhost}
PASSWORD=${API_PASSWORD:-123456}
OUTPUT_DIR=${OUTPUT_DIR:-backend/outputs/clientes}
PAGE=0
PAGE_SIZE=100
SORT_FIELD="nome"
SORT_DIRECTION="asc"
FILTER_JSON=""

usage() {
  cat <<USAGE
Usage: $0 [options]

Options:
  -p <page>       Página zero-based (default: $PAGE)
  -s <size>       Quantidade de linhas por página (default: $PAGE_SIZE)
  -r <field>      Campo de ordenação (default: $SORT_FIELD)
  -d <direction>  Direção de ordenação (asc|desc) (default: $SORT_DIRECTION)
  -f <filter>     Filter DTO (JSON string, ex: '{"nome":{"operator":"contains","value":"Ana"}}')
  -o <dir>        Diretório de saída (default: $OUTPUT_DIR)
  -h              Mostra esta ajuda
USAGE
}

while getopts ":p:s:r:d:f:o:h" opt; do
  case $opt in
    p) PAGE=$OPTARG ;; 
    s) PAGE_SIZE=$OPTARG ;; 
    r) SORT_FIELD=$OPTARG ;; 
    d) SORT_DIRECTION=$OPTARG ;; 
    f) FILTER_JSON=$OPTARG ;; 
    o) OUTPUT_DIR=$OPTARG ;; 
    h) usage; exit 0 ;; 
    *) usage; exit 1 ;;
  esac
 done

LOG_DIR="$OUTPUT_DIR/logs"
LOG_FILE="$LOG_DIR/clientes-sync.log"
JSON_FILE="$OUTPUT_DIR/clientes.json"
HTML_FILE="$OUTPUT_DIR/clientes.html"

mkdir -p "$OUTPUT_DIR" "$LOG_DIR"

log() {
  local type="$1"
  shift
  local message="$*"
  local timestamp
  timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
  echo "[$timestamp] [$type] $message" | tee -a "$LOG_FILE"
}

if ! command -v jq >/dev/null 2>&1; then
  log ERROR "jq não encontrado. Instale jq e tente novamente."
  exit 1
fi

log INFO "Iniciando sincronização de clientes (pagina=$PAGE, tamanho=$PAGE_SIZE, ordenacao=$SORT_FIELD $SORT_DIRECTION)"

AUTH_PAYLOAD=$(jq -n --arg email "$EMAIL" --arg password "$PASSWORD" '{email:$email,password:$password}')
AUTH_RESPONSE=$(curl -sS -X POST "$API_URL/api/auth/login" -H "Content-Type: application/json" -d "$AUTH_PAYLOAD")
ACCESS_TOKEN=$(echo "$AUTH_RESPONSE" | jq -r '.accessToken // empty')

if [[ -z "$ACCESS_TOKEN" ]]; then
  log ERROR "Falha ao obter accessToken: $AUTH_RESPONSE"
  exit 1
fi

log INFO "Access token adquirido"

curl_cmd=(curl -sS -G "$API_URL/api/clientes")
curl_cmd+=(-H "Authorization: Bearer $ACCESS_TOKEN")
curl_cmd+=(--data-urlencode "pagina=$PAGE")
curl_cmd+=(--data-urlencode "tamanho=$PAGE_SIZE")
curl_cmd+=(--data-urlencode "ordenacao=$SORT_FIELD")
curl_cmd+=(--data-urlencode "direcao=$SORT_DIRECTION")
if [[ -n "$FILTER_JSON" ]]; then
  curl_cmd+=(--data-urlencode "filter=$FILTER_JSON")
fi

log INFO "Buscando dados clientes"
CLIENTES_RESPONSE=$("${curl_cmd[@]}")

if [[ -z "$CLIENTES_RESPONSE" ]]; then
  log ERROR "Resposta vazia do endpoint /api/clientes"
  exit 1
fi

printf '%s' "$CLIENTES_RESPONSE" > "$JSON_FILE"
log INFO "Dados salvos em $JSON_FILE"

TOTAL=$(jq -r '.totalElementos // 0' "$JSON_FILE")
PAGINA_ATUAL=$(jq -r '.paginaAtual // 0' "$JSON_FILE")

log INFO "Total clientes retornados: $TOTAL (página ${PAGINA_ATUAL})"

cat <<HTML_HEAD > "$HTML_FILE"
<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="utf-8" />
  <title>Listagem de Clientes</title>
  <style>
    body { font-family: Motiva Sans, Arial, sans-serif; margin: 0; padding: 1rem; background: #fafafa; }
    table { width: 100%; border-collapse: collapse; margin-top: 1rem; }
    th, td { padding: 0.75rem; border-bottom: 1px solid #ececec; text-align: left; }
    th { background-color: #f1f1f1; color: #333; position: sticky; top: 0; }
    .summary { font-size: 0.95rem; color: #555; }
    .chip { display: inline-flex; align-items: center; border-radius: 999px; border: 1px solid; padding: 0.1rem 0.6rem; font-size: 0.75rem; text-transform: capitalize; }
    .chip.ativo { border-color: #3da35d; color: #30703f; }
    .chip.inativo { border-color: #d14343; color: #9b2020; }
  </style>
</head>
<body>
  <h1>Clientes sincronizados</h1>
  <p class="summary">Página atual: ${PAGINA_ATUAL} · Linhas por página: ${PAGE_SIZE} · Filtro aplicado: ${FILTER_JSON:-"(nenhum)"}</p>
  <table>
    <thead>
      <tr>
        <th>Nome</th>
        <th>Documento</th>
        <th>Contato</th>
        <th>Cidade / Estado</th>
        <th>Grupo</th>
        <th>Status</th>
      </tr>
    </thead>
    <tbody>
HTML_HEAD

jq -r '.table_data // [] | .[] |
  "<tr>" +
  "<td>" + (.nome // "") + "</td>" +
  "<td>" + (.cpf // "") + "</td>" +
  "<td>" + ([.email, .telefone] | map(select(length > 0)) | join(" · ")) + "</td>" +
  "<td>" + ([.cidade, .estado] | map(select(length > 0)) | join(" / ")) + "</td>" +
  "<td>" + (.grupo // "") + "</td>" +
  "<td><span class=\"chip " + (if .ativo then "ativo" else "inativo" end) + "\">" + (if .ativo then "Ativo" else "Inativo" end) + "</span></td>" +
  "</tr>"'
  "$JSON_FILE" >> "$HTML_FILE"

cat <<HTML_FOOT >> "$HTML_FILE"
    </tbody>
  </table>
</body>
</html>
HTML_FOOT

log INFO "HTML gerado em $HTML_FILE"
log INFO "Execução concluída"
