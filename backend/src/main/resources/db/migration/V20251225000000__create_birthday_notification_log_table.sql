-- Criar tabela para log de notificações de aniversário (dedupe)
CREATE TABLE birthday_notification_log (
    id BIGSERIAL PRIMARY KEY,
    cliente_id BIGINT NOT NULL,
    ano INTEGER NOT NULL,
    tipo VARCHAR(10) NOT NULL, -- 'PRE' ou 'DAY'
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Criar índice único para evitar duplicados: (cliente_id, ano, tipo)
CREATE UNIQUE INDEX idx_birthday_notification_unique 
ON birthday_notification_log (cliente_id, ano, tipo);