CREATE TABLE rewards (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    image_url VARCHAR(500),
    valid_until TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    redeemed_at TIMESTAMP,
    notification_history_id BIGINT
);

-- Índices
CREATE INDEX idx_rewards_user_id ON rewards (user_id);
CREATE INDEX idx_rewards_status ON rewards (status);
CREATE INDEX idx_rewards_valid_until ON rewards (valid_until);