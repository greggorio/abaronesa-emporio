CREATE TABLE product_signage (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    template_preference VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    render_hash VARCHAR(255),
    palette JSONB,
    phrases JSONB,
    template_applied VARCHAR(255),
    last_attempt_at TIMESTAMPTZ,
    last_result_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_product_signage_product FOREIGN KEY (product_id) REFERENCES produto(id) ON DELETE CASCADE,
    CONSTRAINT chk_product_signage_status CHECK (status IN ('pending', 'ai_generated', 'rendered', 'linked', 'error'))
);

CREATE INDEX idx_product_signage_product_id ON product_signage(product_id);
