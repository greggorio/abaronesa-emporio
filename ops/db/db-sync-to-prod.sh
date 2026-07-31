#!/usr/bin/env bash
set -euo pipefail

# Table-by-table DB sync from local (dev) to production (Dockerized Postgres)

# Local DB (dev)
LOCAL_PGHOST="127.0.0.1"
LOCAL_PGPORT="5432"
LOCAL_PGUSER="postgres"
LOCAL_PGPASSWORD="postgres"
LOCAL_DB="cafe_db"

# Production
REMOTE_SSH="root@31.97.251.16"
REMOTE_PROJECT_DIR="/opt/sistemas/cafe_erp"
REMOTE_CONTAINER="cafe_erp-db"
REMOTE_DB="cafe_erp_db"
REMOTE_USER="cafe_erp_user"

# Tables to sync
TABLES=(
  categoria
  subcategoria
  categoria_despesa
  tipo_receita
  configuracoes
  grupo_usuario
  permissoes
  permissoes_grupos
  usuarios
  usuario_roles
  grupo_cliente
  grupo_cliente_desconto
  fornecedor
  mesa
  produto
  produto_midia
  produto_sku
  pedido
  item_pedido
  pagamento
  pagamento_alocacao
  conta_pagar
  conta_pagar_parcela
  notificacao
  dynamic_form_definitions
  sessao_mesa
  sessao_convidado
  chamado
)

timestamp() { date +%F_%H%M%S; }

get_column_list() {
  local table="$1"
  PGPASSWORD="$LOCAL_PGPASSWORD" psql -h "$LOCAL_PGHOST" -p "$LOCAL_PGPORT" -U "$LOCAL_PGUSER" -d "$LOCAL_DB" -At \
    -c "SELECT string_agg(quote_ident(column_name), ',' ORDER BY ordinal_position) FROM information_schema.columns WHERE table_schema='public' AND table_name='${table}'" \
    | tr -d '\n'
}

main() {
  echo "[1/5] Exportando tabelas do banco local..."
  local export_dir="/tmp/cafe_export_$(timestamp)"
  mkdir -p "$export_dir"

  declare -A COLS
  for t in "${TABLES[@]}"; do
    echo "  - Exportando $t"
    # Descobrir colunas na ordem do schema local
    local cols
    cols=$(get_column_list "$t")
    COLS["$t"]="$cols"
    # Exportar CSV com header (ordem padrão da tabela)
    PGPASSWORD="$LOCAL_PGPASSWORD" psql \
      -h "$LOCAL_PGHOST" -p "$LOCAL_PGPORT" -U "$LOCAL_PGUSER" -d "$LOCAL_DB" \
      -v ON_ERROR_STOP=1 -c "\\COPY \"$t\" TO STDOUT WITH CSV HEADER" > "$export_dir/$t.csv"
  done

  echo "[2/5] Preparando script de importação..."
  local ct_base
  ct_base="$(basename "$export_dir")"
  local import_sql="$export_dir/import.sql"
  local ct_dir="/tmp/$ct_base"

  {
    echo "BEGIN;";
    echo "SET session_replication_role = 'replica';";
    for t in "${TABLES[@]}"; do
      echo "TRUNCATE TABLE \"$t\" RESTART IDENTITY CASCADE;";
      echo "COPY \"$t\" ( ${COLS[$t]} ) FROM '$ct_dir/$t.csv' WITH (FORMAT CSV, HEADER true);";
    done
    echo "SET session_replication_role = 'origin';";
    cat <<'EOSQL'
DO $$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT n.nspname AS schema_name,
           t.relname AS table_name,
           a.attname AS column_name,
           s.relname AS seq_name
    FROM pg_class s
    JOIN pg_depend d ON d.objid = s.oid AND d.deptype = 'a'
    JOIN pg_class t ON d.refobjid = t.oid
    JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = d.refobjsubid
    JOIN pg_namespace n ON n.oid = t.relnamespace
    WHERE s.relkind = 'S' AND n.nspname = 'public'
  LOOP
    EXECUTE format('SELECT setval(%L, COALESCE((SELECT MAX(%I) FROM %I.%I), 0) + 1, false);',
                   r.schema_name || '.' || r.seq_name, r.column_name, r.schema_name, r.table_name);
  END LOOP;
END $$;
EOSQL
    echo "COMMIT;";
  } > "$import_sql"

  echo "[3/5] Enviando arquivos para o servidor..."
  scp -r "$export_dir" "$REMOTE_SSH:/tmp/"

  echo "[4/5] Importando dados no Postgres do container..."
  ssh "$REMOTE_SSH" "bash -lc 'set -e; ct_dir=/tmp/$ct_base; docker cp \"$ct_dir\" \"$REMOTE_CONTAINER\":/tmp/; source /opt/sistemas/cafe_erp/.env; docker exec -e PGPASSWORD=\"\$DB_PASSWORD\" -i \"$REMOTE_CONTAINER\" psql -h localhost -U \"$REMOTE_USER\" -d \"$REMOTE_DB\" -v ON_ERROR_STOP=1 -f \"$ct_dir/import.sql\"'"

  echo "[5/5] Subindo backend novamente..."
  ssh "$REMOTE_SSH" "bash -lc 'cd $REMOTE_PROJECT_DIR && docker compose up -d backend && docker compose ps'"

  echo "✓ Sincronização concluída"
  echo "- Pasta local de export: $export_dir"
}

main "$@"
