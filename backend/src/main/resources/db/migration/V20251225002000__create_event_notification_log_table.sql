-- Log de notificações de eventos (dedupe)
CREATE TABLE event_notification_log (
    id BIGSERIAL PRIMARY KEY,
    evento_id BIGINT NOT NULL,
    ano INTEGER NOT NULL,
    tipo VARCHAR(10) NOT NULL, -- 'PRE' ou 'DAY'
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_event_notification_unique
ON event_notification_log (evento_id, ano, tipo);
