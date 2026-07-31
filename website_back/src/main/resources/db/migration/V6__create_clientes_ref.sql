CREATE TABLE clientes_ref (
    id BIGINT PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Índices
CREATE INDEX idx_clientes_ref_nome ON clientes_ref (nome);
CREATE INDEX idx_clientes_ref_email ON clientes_ref (email);