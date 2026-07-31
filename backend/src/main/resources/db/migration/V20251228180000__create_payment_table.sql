CREATE TABLE payment (
    id BIGSERIAL PRIMARY KEY,
    gateway VARCHAR(50) NOT NULL,
    method VARCHAR(50),
    external_reference VARCHAR(100),
    provider_payment_id VARCHAR(100),
    normalized_status VARCHAR(50),
    provider_status VARCHAR(50),
    provider_status_detail VARCHAR(100),
    amount NUMERIC(18,2),
    raw_payload TEXT,
    paid_at TIMESTAMP NULL,
    canceled_at TIMESTAMP NULL,
    expired_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE UNIQUE INDEX ux_payment_gateway_provider_id ON payment (gateway, provider_payment_id);
CREATE INDEX ix_payment_gateway_external_ref ON payment (gateway, external_reference);
