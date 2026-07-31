CREATE TABLE movimento_estoque_lote (
    id BIGSERIAL PRIMARY KEY,
    movimento_estoque_id BIGINT NOT NULL,
    estoque_lote_id BIGINT NOT NULL,
    quantidade NUMERIC(14,3) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT fk_movimento_estoque_lote_movimento_estoque FOREIGN KEY (movimento_estoque_id) REFERENCES movimento_estoque(id),
    CONSTRAINT fk_movimento_estoque_lote_estoque_lote FOREIGN KEY (estoque_lote_id) REFERENCES estoque_lote(id),
    CONSTRAINT uk_movimento_estoque_lote UNIQUE (movimento_estoque_id, estoque_lote_id)
);

CREATE INDEX idx_movimento_estoque_lote_movimento ON movimento_estoque_lote(movimento_estoque_id);
CREATE INDEX idx_movimento_estoque_lote_estoque ON movimento_estoque_lote(estoque_lote_id);