CREATE TABLE estoque_lote (
    id BIGSERIAL PRIMARY KEY,
    produto_sku_id BIGINT NOT NULL,
    lote VARCHAR(100) NOT NULL,
    data_validade DATE NOT NULL,
    quantidade NUMERIC(14,3) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT fk_estoque_lote_produto_sku FOREIGN KEY (produto_sku_id) REFERENCES produto_sku(id)
);

CREATE INDEX idx_estoque_lote_produto_sku ON estoque_lote(produto_sku_id);
CREATE INDEX idx_estoque_lote_produto_sku_validade ON estoque_lote(produto_sku_id, data_validade);