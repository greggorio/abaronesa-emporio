-- Criar tabela de notificações do usuário (inbox)
CREATE TABLE user_notifications (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id BIGINT NOT NULL,  -- ID do usuário do ERP (sem foreign key direta)
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    image_url VARCHAR(500),
    deeplink VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP NULL,
    source VARCHAR(50) NOT NULL, -- REWARD, BIRTHDAY, MANUAL, etc
    payload_json TEXT -- Dados adicionais como JSON
);

-- Índices para performance
CREATE INDEX idx_user_notifications_user_id ON user_notifications (user_id);
CREATE INDEX idx_user_notifications_user_unread ON user_notifications (user_id, read_at) WHERE read_at IS NULL;
CREATE INDEX idx_user_notifications_created_at ON user_notifications (created_at DESC);