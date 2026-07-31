CREATE TABLE sessao_cobrancas (
    id BIGSERIAL PRIMARY KEY,
    sessao_mesa_id BIGINT NOT NULL,
    sessao_convidado_id BIGINT NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    valor DECIMAL(10, 2) NOT NULL,
    evento_id BIGINT,
    isento BOOLEAN NOT NULL DEFAULT FALSE,
    motivo_isencao VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ATIVA',
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    criado_por VARCHAR(255)
);

-- Create indexes for better query performance
CREATE INDEX idx_sessao_cobrancas_sessao_mesa_id_status ON sessao_cobrancas(sessao_mesa_id, status);
CREATE INDEX idx_sessao_cobrancas_sessao_convidado_id_status ON sessao_cobrancas(sessao_convidado_id, status);
CREATE INDEX idx_sessao_cobrancas_tipo ON sessao_cobrancas(tipo);