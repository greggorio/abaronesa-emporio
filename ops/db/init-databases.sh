#!/bin/sh
set -eu

is_identifier() { printf '%s' "$1" | grep -Eq '^[a-z_][a-z0-9_]{0,62}$'; }
for name in "$POSTGRES_ADMIN_USER" "$ERP_DB_NAME" "$ERP_DB_USER" "$WEBSITE_DB_NAME" "$WEBSITE_DB_USER"; do
  is_identifier "$name" || { echo "Identificador PostgreSQL invalido" >&2; exit 1; }
done
[ "$POSTGRES_ADMIN_USER" != "$ERP_DB_USER" ] && [ "$POSTGRES_ADMIN_USER" != "$WEBSITE_DB_USER" ] \
  && [ "$ERP_DB_USER" != "$WEBSITE_DB_USER" ] && [ "$ERP_DB_NAME" != "$WEBSITE_DB_NAME" ] \
  || { echo "Usuarios e bancos devem ser distintos" >&2; exit 1; }

ensure_role() {
  role=$1 password=$2
  exists=$(psql -v ON_ERROR_STOP=1 --username "$POSTGRES_ADMIN_USER" --dbname postgres -At \
    -v role="$role" <<'SQL'
SELECT 1 FROM pg_roles WHERE rolname = :'role';
SQL
)
  if [ "$exists" != 1 ]; then
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_ADMIN_USER" --dbname postgres \
      -v role="$role" -v password="$password" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'role', :'password') \gexec
SQL
  else
    can_login=$(psql -v ON_ERROR_STOP=1 --username "$POSTGRES_ADMIN_USER" --dbname postgres -At \
      -v role="$role" <<'SQL'
SELECT rolcanlogin FROM pg_roles WHERE rolname = :'role';
SQL
)
    [ "$can_login" = t ] || { echo "Role preexistente incompativel" >&2; exit 1; }
  fi
}
ensure_database() {
  database=$1 owner=$2
  exists=$(psql -v ON_ERROR_STOP=1 --username "$POSTGRES_ADMIN_USER" --dbname postgres -At \
    -v database="$database" <<'SQL'
SELECT 1 FROM pg_database WHERE datname = :'database';
SQL
)
  if [ "$exists" != 1 ]; then
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_ADMIN_USER" --dbname postgres \
      -v database="$database" -v owner="$owner" <<'SQL'
SELECT format('CREATE DATABASE %I OWNER %I', :'database', :'owner') \gexec
SQL
  else
    actual_owner=$(psql -v ON_ERROR_STOP=1 --username "$POSTGRES_ADMIN_USER" --dbname postgres -At \
      -v database="$database" <<'SQL'
SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname = :'database';
SQL
)
    [ "$actual_owner" = "$owner" ] || { echo "Owner preexistente incompativel" >&2; exit 1; }
  fi
}

ensure_role "$ERP_DB_USER" "$ERP_DB_PASSWORD"
ensure_role "$WEBSITE_DB_USER" "$WEBSITE_DB_PASSWORD"
ensure_database "$ERP_DB_NAME" "$ERP_DB_USER"
ensure_database "$WEBSITE_DB_NAME" "$WEBSITE_DB_USER"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_ADMIN_USER" --dbname postgres \
  -v erp_user="$ERP_DB_USER" -v website_user="$WEBSITE_DB_USER" \
  -v erp_db="$ERP_DB_NAME" -v website_db="$WEBSITE_DB_NAME" <<'SQL'
SELECT format('REVOKE CONNECT ON DATABASE %I FROM %I', :'erp_db', :'website_user') \gexec
SELECT format('REVOKE CONNECT ON DATABASE %I FROM %I', :'website_db', :'erp_user') \gexec
SELECT format('REVOKE CONNECT ON DATABASE %I FROM PUBLIC', :'erp_db') \gexec
SELECT format('REVOKE CONNECT ON DATABASE %I FROM PUBLIC', :'website_db') \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO %I', :'erp_db', :'erp_user') \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO %I', :'website_db', :'website_user') \gexec
SQL
echo "Bancos comerciais inicializados"
