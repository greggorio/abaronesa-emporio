#!/usr/bin/env bash
set -euo pipefail

# Importa produtos a partir de um PDF usando pdftotext + curl.
# Usa autenticação via /api/auth/login com usuário root.
#
# Variáveis ajustáveis:
#   PDF_PATH         Caminho para o PDF. Default: relatorio_produtos.pdf
#   BASE_URL         URL base da API. Default: http://localhost:8080
#   EMAIL            Usuário para login. Default: root@localhost
#   PASSWORD         Senha para login. Default: 123456
#   CATEGORIA_ID     Categoria padrão. Default: 1
#
# Dependências: pdftotext, python3, curl

PDF_PATH=${PDF_PATH:-relatorio_produtos.pdf}
BASE_URL=${BASE_URL:-http://localhost:8080}
EMAIL=${EMAIL:-root@localhost}
PASSWORD=${PASSWORD:-123456}
CATEGORIA_ID=${CATEGORIA_ID:-1}

for dep in pdftotext python3 curl; do
  if ! command -v "$dep" >/dev/null 2>&1; then
    echo "Dependência ausente: $dep" >&2
    exit 1
  fi
done

if [[ ! -f "$PDF_PATH" ]]; then
  echo "PDF não encontrado: $PDF_PATH" >&2
  exit 1
fi

echo "Obtendo token em $BASE_URL/api/auth/login..."
TOKEN=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | \
  python3 -c 'import sys, json; data=json.load(sys.stdin); print(data.get("accessToken",""))')

if [[ -z "$TOKEN" ]]; then
  echo "Falha ao obter token. Verifique BASE_URL/credenciais." >&2
  exit 1
fi

TXT_PATH=$(mktemp)
DATA_PATH=$(mktemp)
trap 'rm -f "$TXT_PATH" "$DATA_PATH"' EXIT

echo "Extraindo texto do PDF..."
pdftotext -layout "$PDF_PATH" "$TXT_PATH"

echo "Convertendo para NDJSON..."
TXT_PATH="$TXT_PATH" CATEGORIA_ID="$CATEGORIA_ID" python3 - <<'PY' > "$DATA_PATH"
import os, re, json, math

txt_path = os.environ["TXT_PATH"]
categoria_id = int(os.environ.get("CATEGORIA_ID", "1"))

with open(txt_path, encoding="utf-8", errors="ignore") as f:
    lines = f.read().splitlines()

def col_positions(header: str):
    def idx(marker, default):
        pos = header.find(marker)
        return pos if pos >= 0 else default
    desc = idx("Descrição", 8)
    if desc == 8:
        desc = idx("Descricao", desc)
    unid = idx("Unid.", desc + 10)
    vr_venda = idx("Vr.Venda", unid + 20)
    vr_custo = idx("Vr.Custo", vr_venda + 8)
    return desc, unid, vr_venda, vr_custo

desc_start = unid_start = vr_start = vr_custo_start = None
produtos = []
atual = None
codigo_re = re.compile(r"^\s*(\d+)")

def first_non_space(s: str) -> int:
    for i, ch in enumerate(s):
        if not ch.isspace():
            return i
    return -1

def parse_val(s: str):
    if not s:
        return 0
    s = s.strip().replace(".", "").replace(",", ".")
    try:
        return float(s)
    except ValueError:
        return 0

def limpar_desc(desc: str) -> str:
    markers = [
        "Bitbyte Informática", "Bitbyte Informatica",
        "VILLA CUSTOM",
        "Relatório de Produto", "Relatorio de Produto",
        "Código Descrição", "Codigo Descricao",
        "Data:", "Pág.:", "Pag.:"
    ]
    for m in markers:
        if m in desc:
            desc = desc.split(m)[0]
    return desc.strip()

for line in lines:
    if desc_start is None and "Descrição" in line and "Vr.Venda" in line:
        desc_start, unid_start, vr_start, vr_custo_start = col_positions(line)
        continue
    if desc_start is None:
        continue
    t = line.rstrip("\n")
    if not t.strip():
        continue
    if t.startswith("Agrupado por") or t.startswith("Relatório") or t.startswith("Relatorio") or t.startswith("Bitbyte") or t.startswith("VILLA") or t.startswith("Pág.") or t.startswith("Pag."):
        continue

    prefix = t[:desc_start]
    m = codigo_re.match(prefix)
    if m:
        codigo = m.group(1)
        descricao = limpar_desc(t[desc_start:unid_start].strip())
        vr_raw = t[vr_start: vr_custo_start if vr_custo_start else len(t)]
        preco = parse_val(vr_raw)
        if descricao:
            atual = {"codigo": codigo, "descricao": descricao, "preco": preco}
            produtos.append(atual)
        continue

    if atual and first_non_space(t) >= 0 and first_non_space(t) < unid_start:
        extra = t.strip()
        extra = limpar_desc(extra)
        if extra:
            atual["descricao"] = (atual["descricao"] + " " + extra).strip()

# Deduplicar por nome (case-insensitive)
vistos = set()
for p in produtos:
    nome = limpar_desc(p["descricao"].strip())
    if not nome:
        continue
    key = nome.lower()
    if key in vistos:
        continue
    vistos.add(key)
    payload = {
        "nome": nome,
        "descricao": nome,
        "codigoInterno": p["codigo"],
        "categoriaId": categoria_id,
        "tipoPrecificacao": "SIMPLES",
        "unidadeMedida": "UN",
        "unidadeBase": "UNIDADE",
        "tipoCalculoMargem": "SOBRE_CUSTO",
        "precoVenda": round(p["preco"], 2)
    }
    print(json.dumps(payload, ensure_ascii=False))
PY

total=0
ok=0
fail=0

echo "Enviando produtos para $BASE_URL/api/produtos ..."
while IFS= read -r line; do
  [[ -z "$line" ]] && continue
  ((++total))
  status=0
  http_code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/produtos" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "$line") || status=$?
  if [[ $status -ne 0 ]]; then
    ((++fail))
    echo "Falha (curl exit $status) => $line" >&2
    continue
  fi
  if [[ "$http_code" == "200" || "$http_code" == "201" ]]; then
    ((++ok))
  else
    ((++fail))
    echo "Falha ($http_code) => $line" >&2
  fi
done < "$DATA_PATH"

echo "Importação concluída. Total: $total | Sucesso: $ok | Falhas: $fail"
