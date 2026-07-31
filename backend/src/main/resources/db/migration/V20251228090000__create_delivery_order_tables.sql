-- Tabela principal de pedidos de delivery/retirada
CREATE TABLE delivery_order (
    id BIGSERIAL PRIMARY KEY,
    tipo VARCHAR(20) NOT NULL, -- DELIVERY | RETIRADA (pickup)
    status VARCHAR(30) NOT NULL,
    customer_name VARCHAR(255),
    customer_phone VARCHAR(30),
    customer_email VARCHAR(255),
    customer_cpf VARCHAR(20),
    dropoff_address TEXT,
    dropoff_notes TEXT,
    delivery_fee_cents INTEGER NOT NULL DEFAULT 0,
    items_total_cents INTEGER NOT NULL DEFAULT 0,
    total_cents INTEGER NOT NULL DEFAULT 0,
    currency VARCHAR(10) NOT NULL DEFAULT 'BRL',
    external_id VARCHAR(100), -- id externo opcional (ex: sistema parceiro)
    external_reference VARCHAR(100), -- usado no provedor de pagamento
    mp_payment_id VARCHAR(100),
    mp_status VARCHAR(50),
    mp_status_detail VARCHAR(100),
    mp_payment_method VARCHAR(50),
    mp_qr_code TEXT,
    mp_qr_code_base64 TEXT,
    mp_raw_response JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMP,
    canceled_at TIMESTAMP,
    canceled_reason VARCHAR(255)
);

CREATE INDEX idx_delivery_order_status ON delivery_order(status);
CREATE INDEX idx_delivery_order_tipo_status ON delivery_order(tipo, status);
CREATE INDEX idx_delivery_order_mp_payment_id ON delivery_order(mp_payment_id);
CREATE INDEX idx_delivery_order_external_reference ON delivery_order(external_reference);

-- Itens do pedido de delivery
CREATE TABLE delivery_order_item (
    id BIGSERIAL PRIMARY KEY,
    delivery_order_id BIGINT NOT NULL REFERENCES delivery_order(id) ON DELETE CASCADE,
    produto_id BIGINT,
    sku_id BIGINT,
    nome VARCHAR(255) NOT NULL,
    variacao VARCHAR(255),
    quantidade INTEGER NOT NULL,
    preco_unit_cents INTEGER NOT NULL,
    observacoes TEXT,
    estacao VARCHAR(50), -- bar/cozinha etc.
    status VARCHAR(30) NOT NULL DEFAULT 'queued',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_delivery_order_item_order ON delivery_order_item(delivery_order_id);
CREATE INDEX idx_delivery_order_item_status ON delivery_order_item(status);
