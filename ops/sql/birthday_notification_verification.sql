-- Script para verificar a estrutura da tabela e índice criado
-- Isso simula o comando \d birthday_notification_log que seria usado no psql

-- Estrutura da tabela (comando equivalente no PostgreSQL)
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_name = 'birthday_notification_log'
ORDER BY ordinal_position;

-- Índices da tabela
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'birthday_notification_log';

-- Consulta na tabela flyway_schema_history para confirmar a migração (depois de aplicada)
SELECT version, description, type, installed_on
FROM flyway_schema_history
WHERE version = '20251225000000'
ORDER BY installed_on DESC;