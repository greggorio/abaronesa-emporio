#!/usr/bin/env bash
set -euo pipefail

DB_HOST=${DB_HOST:-localhost}
DB_PORT=${DB_PORT:-5432}
DB_NAME=${DB_NAME:-espresso_db}
DB_USER=${DB_USERNAME:-$DB_USER}
DB_USER=${DB_USER:-gregorio}

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
SQL_FILE="$SCRIPT_DIR/../sql/dev_reset_quiz_tables.sql"

if [[ -n "${DB_PASSWORD:-}" ]]; then
  export PGPASSWORD="$DB_PASSWORD"
fi

echo "Dropping quiz tables on $DB_HOST:$DB_PORT/$DB_NAME as user $DB_USER..."
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -f "$SQL_FILE"
echo "Done. Start the app to let Hibernate recreate tables."

