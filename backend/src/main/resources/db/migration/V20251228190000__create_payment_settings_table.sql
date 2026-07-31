CREATE TABLE payment_settings (
    id BIGSERIAL PRIMARY KEY,
    active_gateway VARCHAR(50) NOT NULL DEFAULT 'MERCADOPAGO',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
