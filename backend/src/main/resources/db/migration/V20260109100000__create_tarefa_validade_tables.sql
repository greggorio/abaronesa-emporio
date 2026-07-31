CREATE TABLE tarefa_validade (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(20) NOT NULL DEFAULT 'RASCUNHO',
    observacao TEXT,
    usuario_id BIGINT,
    criado_em TIMESTAMP NOT NULL DEFAULT now(),
    finalizado_em TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT fk_tarefa_validade_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE TABLE tarefa_validade_item (
    id BIGSERIAL PRIMARY KEY,
    tarefa_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    lote VARCHAR(100) DEFAULT 'SEM_LOTE',
    data_validade DATE DEFAULT DATE '1900-01-01',
    quantidade NUMERIC(14,3) NOT NULL,
    acao VARCHAR(10) NOT NULL DEFAULT 'SET',
    criado_em TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT fk_tarefa_validade_item_tarefa FOREIGN KEY (tarefa_id) REFERENCES tarefa_validade(id) ON DELETE CASCADE,
    CONSTRAINT fk_tarefa_validade_item_sku FOREIGN KEY (sku_id) REFERENCES produto_sku(id)
);

CREATE INDEX idx_tarefa_validade_item_tarefa ON tarefa_validade_item(tarefa_id);
CREATE INDEX idx_tarefa_validade_item_sku ON tarefa_validade_item(sku_id);

CREATE TABLE tarefa_validade_divergencia (
    id BIGSERIAL PRIMARY KEY,
    tarefa_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    estoque_agregado NUMERIC(14,3) NOT NULL,
    soma_lotes NUMERIC(14,3) NOT NULL,
    diferenca NUMERIC(14,3) NOT NULL,
    acao_tomada VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    movimento_estoque_id BIGINT,
    criado_em TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT fk_tarefa_validade_div_tarefa FOREIGN KEY (tarefa_id) REFERENCES tarefa_validade(id) ON DELETE CASCADE,
    CONSTRAINT fk_tarefa_validade_div_sku FOREIGN KEY (sku_id) REFERENCES produto_sku(id),
    CONSTRAINT fk_tarefa_validade_div_mov FOREIGN KEY (movimento_estoque_id) REFERENCES movimento_estoque(id)
);

CREATE INDEX idx_tarefa_validade_div_tarefa ON tarefa_validade_divergencia(tarefa_id);
CREATE INDEX idx_tarefa_validade_div_sku ON tarefa_validade_divergencia(sku_id);
