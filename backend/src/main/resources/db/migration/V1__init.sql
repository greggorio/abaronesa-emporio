-- Migration inicial do banco de dados

-- Criação da tabela grupo_usuario
CREATE TABLE IF NOT EXISTS grupo_usuario (
    id BIGSERIAL PRIMARY KEY,
    descricao VARCHAR(255) NOT NULL,
    ativo BOOLEAN DEFAULT TRUE
);

-- Criação da tabela categoria
CREATE TABLE IF NOT EXISTS categoria (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255),
    icone VARCHAR(255),
    cover VARCHAR(255),
    exibir_no_cardapio BOOLEAN DEFAULT FALSE,
    ordem INTEGER DEFAULT 0
);

-- Criação da tabela categoria_despesa
CREATE TABLE IF NOT EXISTS categoria_despesa (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    ativo BOOLEAN DEFAULT TRUE
);

-- Criação da tabela tipo_receita
CREATE TABLE IF NOT EXISTS tipo_receita (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL
);

-- Seed inicial de tipo_receita
INSERT INTO tipo_receita (id, nome)
VALUES
    (1, 'Geral'),
    (2, 'Mensalista')
ON CONFLICT (id) DO UPDATE SET nome = EXCLUDED.nome;

-- Criação da tabela fornecedor
CREATE TABLE IF NOT EXISTS fornecedor (
    id BIGSERIAL PRIMARY KEY,
    razao_social VARCHAR(255) NOT NULL,
    nome_fantasia VARCHAR(255),
    cnpj VARCHAR(14) UNIQUE,
    telefone VARCHAR(20),
    email VARCHAR(255),
    contato VARCHAR(255),
    endereco VARCHAR(255),
    cidade VARCHAR(255),
    estado VARCHAR(2),
    cep VARCHAR(10),
    ativo BOOLEAN DEFAULT TRUE
);

-- Criação da tabela mesa
CREATE TABLE IF NOT EXISTS mesa (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(100) UNIQUE NOT NULL,
    rotulo VARCHAR(100) NOT NULL,
    ativo BOOLEAN DEFAULT TRUE,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Seed inicial de mesa
INSERT INTO mesa (id, slug, rotulo, ativo)
VALUES (1, 'BALCAO', 'Balcão', TRUE)
ON CONFLICT (id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('mesa', 'id'), COALESCE((SELECT MAX(id) FROM mesa), 0));

-- Criação da tabela usuarios
CREATE TABLE IF NOT EXISTS usuarios (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    email VARCHAR(150) UNIQUE NOT NULL,
    senha VARCHAR(255),
    telefone VARCHAR(20),
    ativo BOOLEAN DEFAULT TRUE,
    email_verificado BOOLEAN DEFAULT FALSE,
    id_grupo_usuario BIGINT,
    provider VARCHAR(20) DEFAULT 'LOCAL',
    provider_id VARCHAR(100),
    email_verification_token VARCHAR(255),
    email_verification_expires_at TIMESTAMP,
    password_reset_token VARCHAR(255),
    password_reset_expires_at TIMESTAMP,
    foto_perfil VARCHAR(500),
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ultimo_login TIMESTAMP,
    cliente_online BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (id_grupo_usuario) REFERENCES grupo_usuario(id)
);

-- Criação da tabela usuario_roles
CREATE TABLE IF NOT EXISTS usuario_roles (
    usuario_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    PRIMARY KEY (usuario_id, role),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

-- Criação da tabela subcategoria
CREATE TABLE IF NOT EXISTS subcategoria (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255),
    categoria_id BIGINT,
    cover VARCHAR(255),
    FOREIGN KEY (categoria_id) REFERENCES categoria(id)
);

-- Criação da tabela produto
CREATE TABLE IF NOT EXISTS produto (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(200) NOT NULL,
    setor VARCHAR(100),
    descricao TEXT,
    codigo_interno VARCHAR(50) UNIQUE,
    codigo_barras VARCHAR(50),
    codigo_fornecedor VARCHAR(50),
    tipo VARCHAR(30),
    unidade_medida VARCHAR(20),
    unidade_base VARCHAR(12),
    categoria_id BIGINT,
    subcategoria_id BIGINT,
    fornecedor_id BIGINT,
    preco_custo DECIMAL(10,2),
    preco_venda DECIMAL(10,2),
    tipo_precificacao VARCHAR(20) DEFAULT 'SIMPLES',
    controla_estoque BOOLEAN NOT NULL DEFAULT TRUE,
    estoque_atual DECIMAL(10,3) DEFAULT 0.000,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    vendavel BOOLEAN NOT NULL DEFAULT TRUE,
    insumo BOOLEAN DEFAULT FALSE,
    tem_ficha_tecnica BOOLEAN DEFAULT FALSE,
    exibir_no_cardapio BOOLEAN NOT NULL DEFAULT FALSE,
    promocao BOOLEAN NOT NULL DEFAULT FALSE,
    destaque BOOLEAN NOT NULL DEFAULT FALSE,
    necessita_preparacao BOOLEAN NOT NULL DEFAULT TRUE,
    local_preparacao VARCHAR(20),
    ordem INTEGER DEFAULT 0,
    imagem_principal VARCHAR(255),
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (categoria_id) REFERENCES categoria(id),
    FOREIGN KEY (subcategoria_id) REFERENCES subcategoria(id),
    FOREIGN KEY (fornecedor_id) REFERENCES fornecedor(id)
);

-- Criação da tabela estoque
CREATE TABLE IF NOT EXISTS estoque (
    id BIGSERIAL PRIMARY KEY,
    quantidade INTEGER,
    estoque_minimo INTEGER,
    reservado INTEGER
);

-- Criação da tabela estoque_produto
CREATE TABLE IF NOT EXISTS estoque_produto (
    id BIGSERIAL PRIMARY KEY,
    produto_id BIGINT NOT NULL UNIQUE,
    quantidade_base INTEGER NOT NULL DEFAULT 0,
    reservado_base INTEGER NOT NULL DEFAULT 0,
    estoque_minimo_base INTEGER NOT NULL DEFAULT 0,
    version BIGINT,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (produto_id) REFERENCES produto(id)
);

-- Criação da tabela embalagem
CREATE TABLE IF NOT EXISTS embalagem (
    id BIGSERIAL PRIMARY KEY,
    produto_id BIGINT NOT NULL,
    nome VARCHAR(60) NOT NULL,
    fator_base INTEGER NOT NULL,
    codigo_barras VARCHAR(50),
    permite_venda BOOLEAN NOT NULL DEFAULT TRUE,
    principal BOOLEAN NOT NULL DEFAULT FALSE,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (produto_id) REFERENCES produto(id)
);

-- Criação da tabela produto_sku
CREATE TABLE IF NOT EXISTS produto_sku (
    id BIGSERIAL PRIMARY KEY,
    produto_id BIGINT NOT NULL,
    sku VARCHAR(50) UNIQUE NOT NULL,
    variacao VARCHAR(100),
    codigo_barras VARCHAR(50),
    preco_custo DECIMAL(10,2),
    preco_venda DECIMAL(10,2),
    embalagem_id BIGINT,
    estoque_id BIGINT,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    principal BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (produto_id) REFERENCES produto(id),
    FOREIGN KEY (embalagem_id) REFERENCES embalagem(id),
    FOREIGN KEY (estoque_id) REFERENCES estoque(id)
);

CREATE TABLE IF NOT EXISTS produto_harmonizacao (
    id BIGSERIAL PRIMARY KEY,

    produto_principal_id BIGINT NOT NULL,
    produto_harmonizado_id BIGINT NOT NULL,
    sku_harmonizado_id BIGINT,

    tipo VARCHAR(50),
    descricao TEXT,
    ordem INTEGER DEFAULT 0,

    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP,

    CONSTRAINT fk_prod_harm_principal
        FOREIGN KEY (produto_principal_id)
        REFERENCES produto(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_prod_harm_harmonizado
        FOREIGN KEY (produto_harmonizado_id)
        REFERENCES produto(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_prod_harm_sku
        FOREIGN KEY (sku_harmonizado_id)
        REFERENCES produto_sku(id)
        ON DELETE SET NULL
);

-- Criação da tabela produto_fiscal
CREATE TABLE IF NOT EXISTS produto_fiscal (
    produto_id BIGINT PRIMARY KEY,
    ncm VARCHAR(8),
    cest VARCHAR(7),
    origem VARCHAR(1),
    unidade_tributavel VARCHAR(2),
    cfop VARCHAR(4),
    quantidade_tributavel DECIMAL(10,4),
    valor_unitario_tributavel DECIMAL(10,4),
    cst_icms VARCHAR(3),
    csosn VARCHAR(4),
    aliquota_icms DECIMAL(5,2),
    aliquota_icmsst DECIMAL(5,2),
    mva DECIMAL(5,2),
    mvast DECIMAL(5,2),
    reducao_base_icms DECIMAL(5,2),
    reducao_base_icmsst DECIMAL(5,2),
    modalidade_bc_icms INTEGER,
    modalidade_bc_icms_st INTEGER,
    cst_pis VARCHAR(2),
    aliquota_pis DECIMAL(5,2),
    aliquota_pis_reais DECIMAL(5,4),
    cst_cofins VARCHAR(2),
    aliquota_cofins DECIMAL(5,2),
    aliquota_cofins_reais DECIMAL(5,4),
    cst_ipi VARCHAR(2),
    aliquota_ipi DECIMAL(5,2),
    codigo_enquadramento_ipi VARCHAR(3),
    tipo_calculo_ipi VARCHAR(1),
    aliquota_fcp DECIMAL(5,2),
    aliquota_fcpst DECIMAL(5,2),
    informacoes_adicionais_fisco TEXT,
    codigo_beneficio_fiscal VARCHAR(10),
    sujeitost BOOLEAN NOT NULL DEFAULT FALSE,
    possui_beneficio BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (produto_id) REFERENCES produto(id)
);

-- Criação da tabela sessao_mesa
CREATE TABLE IF NOT EXISTS sessao_mesa (
    id BIGSERIAL PRIMARY KEY,
    mesa_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    aberta_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fechada_em TIMESTAMP,
    observacoes TEXT,
    FOREIGN KEY (mesa_id) REFERENCES mesa(id)
);

-- Criação da tabela sessao_convidado
CREATE TABLE IF NOT EXISTS sessao_convidado (
    id BIGSERIAL PRIMARY KEY,
    sessao_mesa_id BIGINT NOT NULL,
    usuario_id BIGINT,
    nome_exibicao VARCHAR(100),
    guest_token VARCHAR(255) UNIQUE NOT NULL,
    device_fingerprint VARCHAR(255),
    entrou_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    saiu_em TIMESTAMP,
    host BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (sessao_mesa_id) REFERENCES sessao_mesa(id),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

-- Criação da tabela pedido
CREATE TABLE IF NOT EXISTS pedido (
    id BIGSERIAL PRIMARY KEY,
    sessao_mesa_id BIGINT NOT NULL,
    sessao_convidado_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    origem VARCHAR(20),
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aceito_em TIMESTAMP,
    entregue_em TIMESTAMP,
    cancelado_em TIMESTAMP,
    motivo_cancelamento VARCHAR(255),
    FOREIGN KEY (sessao_mesa_id) REFERENCES sessao_mesa(id),
    FOREIGN KEY (sessao_convidado_id) REFERENCES sessao_convidado(id)
);

-- Criação da tabela item_pedido
CREATE TABLE IF NOT EXISTS item_pedido (
    id BIGSERIAL PRIMARY KEY,
    pedido_id BIGINT NOT NULL,
    produto_id BIGINT NOT NULL,
    sku_id BIGINT,
    quantidade INTEGER NOT NULL,
    preco_unitario DECIMAL(10,2) NOT NULL,
    observacoes TEXT,
    status VARCHAR(20) NOT NULL,
    motivo_cancelamento_codigo VARCHAR(40),
    motivo_cancelamento VARCHAR(255),
    estacao VARCHAR(20) DEFAULT 'kitchen',
    atualizado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (pedido_id) REFERENCES pedido(id),
    FOREIGN KEY (produto_id) REFERENCES produto(id),
    FOREIGN KEY (sku_id) REFERENCES produto_sku(id)
);

-- Criação da tabela conta_pagar
CREATE TABLE IF NOT EXISTS conta_pagar (
    id BIGSERIAL PRIMARY KEY,
    fornecedor_id BIGINT NOT NULL,
    categoria_despesa_id BIGINT NOT NULL,
    descricao VARCHAR(255) NOT NULL,
    valor_total DECIMAL(10,2) NOT NULL,
    numero_parcelas INTEGER NOT NULL,
    recorrente BOOLEAN DEFAULT FALSE,
    data_cadastro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (fornecedor_id) REFERENCES fornecedor(id),
    FOREIGN KEY (categoria_despesa_id) REFERENCES categoria_despesa(id)
);

-- Criação da tabela conta_receber
CREATE TABLE IF NOT EXISTS conta_receber (
    id BIGSERIAL PRIMARY KEY,
    cliente_id BIGINT NOT NULL,
    tipo_receita_id BIGINT NOT NULL,
    numero_documento VARCHAR(255),
    descricao VARCHAR(255) NOT NULL,
    valor_total DECIMAL(10,2) NOT NULL,
    numero_parcelas INTEGER NOT NULL,
    observacoes TEXT,
    recorrente BOOLEAN DEFAULT FALSE,
    data_cadastro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (cliente_id) REFERENCES usuarios(id),
    FOREIGN KEY (tipo_receita_id) REFERENCES tipo_receita(id)
);

-- Criação da tabela conta_pagar_parcela
CREATE TABLE IF NOT EXISTS conta_pagar_parcela (
    id BIGSERIAL PRIMARY KEY,
    conta_pagar_id BIGINT NOT NULL,
    numero_parcela INTEGER NOT NULL,
    valor DECIMAL(10,2) NOT NULL,
    data_vencimento DATE NOT NULL,
    paga BOOLEAN DEFAULT FALSE,
    forma_pagamento VARCHAR(50),
    data_pagamento DATE,
    observacoes TEXT,
    FOREIGN KEY (conta_pagar_id) REFERENCES conta_pagar(id)
);

-- Criação da tabela conta_receber_parcela
CREATE TABLE IF NOT EXISTS conta_receber_parcela (
    id BIGSERIAL PRIMARY KEY,
    conta_receber_id BIGINT NOT NULL,
    numero_parcela INTEGER NOT NULL,
    valor_bruto DECIMAL(10,2) NOT NULL,
    valor_desconto DECIMAL(10,2) DEFAULT 0.00,
    valor_acrescimo DECIMAL(10,2) DEFAULT 0.00,
    valor_liquido DECIMAL(10,2) NOT NULL,
    vencimento DATE NOT NULL,
    valor DECIMAL(10,2) DEFAULT 0.00,
    data_vencimento DATE,
    data_recebimento DATE,
    valor_multa DECIMAL(10,2) DEFAULT 0.00,
    valor_juros DECIMAL(10,2) DEFAULT 0.00,
    valor_recebido DECIMAL(10,2),
    forma_recebimento VARCHAR(50),
    usuario_recebimento_id BIGINT,
    recebida BOOLEAN DEFAULT FALSE,
    cobranca_enviada BOOLEAN DEFAULT FALSE,
    data_envio_cobranca DATE,
    observacoes TEXT,
    FOREIGN KEY (conta_receber_id) REFERENCES conta_receber(id),
    FOREIGN KEY (usuario_recebimento_id) REFERENCES usuarios(id)
);

-- Criação da tabela configuracoes
CREATE TABLE IF NOT EXISTS configuracoes (
    id BIGSERIAL PRIMARY KEY,
    chave VARCHAR(255) UNIQUE NOT NULL,
    valor TEXT,
    nome VARCHAR(255) NOT NULL,
    descricao TEXT
);

-- Criação da tabela dynamic_form_definitions
CREATE TABLE IF NOT EXISTS dynamic_form_definitions (
    id VARCHAR(36) PRIMARY KEY,
    entity_type VARCHAR(100) NOT NULL UNIQUE,
    program_name VARCHAR(200) NOT NULL,
    program_icon VARCHAR(50),
    table_order VARCHAR(100) NOT NULL DEFAULT 'id',
    complexity VARCHAR(20) NOT NULL DEFAULT 'SIMPLE',
    form_structure JSONB NOT NULL DEFAULT '{}'::jsonb,
    java_extension_class VARCHAR(500),
    custom_slots JSONB NOT NULL DEFAULT '{}'::jsonb,
    table_columns JSONB NOT NULL DEFAULT '{}'::jsonb,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

-- Criação das tabelas restantes que estavam faltando

-- Criação da tabela grupo_cliente (sem dependências)
CREATE TABLE IF NOT EXISTS grupo_cliente (
    id BIGSERIAL PRIMARY KEY,
    descricao VARCHAR(255) UNIQUE NOT NULL
);

-- Criação da tabela tipo_recompensa (sem dependências)
CREATE TABLE IF NOT EXISTS tipo_recompensa (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255),
    descricao VARCHAR(255)
);

-- Criação da tabela gamificacao_evento_tipo (sem dependências)
CREATE TABLE IF NOT EXISTS gamificacao_evento_tipo (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    descricao TEXT
);

-- Criação da tabela permissoes (sem dependências)
CREATE TABLE IF NOT EXISTS permissoes (
    id BIGSERIAL PRIMARY KEY,
    permissao VARCHAR(255) UNIQUE,
    descricao VARCHAR(255)
);

-- Criação das tabelas relacionadas a tarefas
CREATE TABLE IF NOT EXISTS task_lists (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_by BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES usuarios(id)
);

 -- Criação da tabela list_authorizations (sem dependências diretas)
 DROP TABLE IF EXISTS list_authorization;
 CREATE TABLE IF NOT EXISTS list_authorizations (
     id BIGSERIAL PRIMARY KEY,
     list_id BIGINT NOT NULL,
     user_id BIGINT NOT NULL,
     granted_by BIGINT NOT NULL,
     granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
     FOREIGN KEY (list_id) REFERENCES task_lists(id) ON DELETE CASCADE,
     FOREIGN KEY (user_id) REFERENCES usuarios(id) ON DELETE CASCADE,
     FOREIGN KEY (granted_by) REFERENCES usuarios(id) ON DELETE SET NULL
 );
 CREATE UNIQUE INDEX IF NOT EXISTS ux_list_authorizations_list_user ON list_authorizations (list_id, user_id);
 CREATE INDEX IF NOT EXISTS ix_list_authorizations_list_id ON list_authorizations (list_id);
 CREATE INDEX IF NOT EXISTS ix_list_authorizations_user_id ON list_authorizations (user_id);
 CREATE INDEX IF NOT EXISTS ix_list_authorizations_granted_by ON list_authorizations (granted_by);
 CREATE INDEX IF NOT EXISTS ix_list_authorizations_granted_at ON list_authorizations (granted_at);

-- Criação da tabela perfil_cliente (depois de criar grupo_cliente)
CREATE TABLE IF NOT EXISTS perfil_cliente (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT UNIQUE NOT NULL,
    tipo_pessoa VARCHAR(2) DEFAULT 'PF',
    cpf VARCHAR(11),
    cnpj VARCHAR(14),
    inscricao_estadual VARCHAR(20),
    endereco VARCHAR(255),
    logradouro VARCHAR(60),
    numero VARCHAR(10),
    bairro VARCHAR(60),
    complemento VARCHAR(60),
    cidade VARCHAR(100),
    estado VARCHAR(2),
    cep VARCHAR(9),
    data_nascimento DATE,
    codigo_municipio_ibge VARCHAR(7),
    grupo_cliente_id BIGINT,
    origem_cadastro VARCHAR(20) NOT NULL DEFAULT 'LOJA_FISICA',
    mensalista BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    FOREIGN KEY (grupo_cliente_id) REFERENCES grupo_cliente(id)
);

-- Criação da tabela perfil_funcionario
CREATE TABLE IF NOT EXISTS perfil_funcionario (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL UNIQUE,
    voucher_vr DECIMAL(10,2),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_perfil_funcionario_usuario_id ON perfil_funcionario(usuario_id);

-- Criação da tabela grupo_cliente_desconto (depois de criar grupo_cliente e categoria)
CREATE TABLE IF NOT EXISTS grupo_cliente_desconto (
    id BIGSERIAL PRIMARY KEY,
    grupo_cliente_id BIGINT NOT NULL,
    categoria_id BIGINT NOT NULL,
    subcategoria_id BIGINT,
    desconto_percentual DECIMAL(5,2) NOT NULL, -- Ex: 10.00 para 10%
    ativo BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (grupo_cliente_id) REFERENCES grupo_cliente(id),
    FOREIGN KEY (categoria_id) REFERENCES categoria(id),
    FOREIGN KEY (subcategoria_id) REFERENCES subcategoria(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_grupo_cliente_desconto_categoria
    ON grupo_cliente_desconto (grupo_cliente_id, categoria_id)
    WHERE subcategoria_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_grupo_cliente_desconto_subcategoria
    ON grupo_cliente_desconto (grupo_cliente_id, categoria_id, subcategoria_id)
    WHERE subcategoria_id IS NOT NULL;

-- Criação da tabela status_recebimento (sem dependências)
CREATE TABLE IF NOT EXISTS status_recebimento (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255),
    descricao VARCHAR(255)
);

-- Criação da tabela ficha_tecnica
CREATE TABLE IF NOT EXISTS ficha_tecnica (
    id BIGSERIAL PRIMARY KEY,
    produto_id BIGINT UNIQUE NOT NULL,
    custo_total DECIMAL(10,2),
    rendimento INTEGER, -- Quantas porções a ficha técnica produz (padrão: 1)
    observacoes TEXT,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (produto_id) REFERENCES produto(id)
);

-- Criação da tabela ficha_tecnica_item
CREATE TABLE IF NOT EXISTS ficha_tecnica_item (
    id BIGSERIAL PRIMARY KEY,
    ficha_tecnica_id BIGINT NOT NULL,
    insumo_sku_id BIGINT NOT NULL,
    quantidade DECIMAL(10,3) NOT NULL,
    ordem INTEGER NOT NULL DEFAULT 0,
    observacao TEXT,
    FOREIGN KEY (ficha_tecnica_id) REFERENCES ficha_tecnica(id),
    FOREIGN KEY (insumo_sku_id) REFERENCES produto_sku(id)
);

-- Criação da tabela movimento_caixa
CREATE TABLE IF NOT EXISTS movimentos_caixa (
    id BIGSERIAL PRIMARY KEY,
    data_hora TIMESTAMP NOT NULL,
    tipo VARCHAR(20) NOT NULL, -- TipoMovimentoCaixa
    valor DECIMAL(10,2) NOT NULL,
    meio_pagamento VARCHAR(20) NOT NULL, -- TipoFormaPagamento
    afeta_caixa BOOLEAN NOT NULL,
    operacao VARCHAR(20) NOT NULL, -- TipoOperacao
    observacao TEXT,
    referencia_id BIGINT,
    referencia_tipo VARCHAR(50), -- Ex: "PAGAMENTO", "CONTA_PAGAR", etc.
    responsavel_id BIGINT,
    FOREIGN KEY (responsavel_id) REFERENCES usuarios(id)
);

-- Criação da tabela movimento_estoque
CREATE TABLE IF NOT EXISTS movimento_estoque (
    id BIGSERIAL PRIMARY KEY,
    sku_id BIGINT NOT NULL,
    produto_id BIGINT,
    tipo_movimento INTEGER NOT NULL,
    quantidade DECIMAL(10,2) NOT NULL,
    estoque_anterior DECIMAL(10,2) NOT NULL,
    estoque_atual DECIMAL(10,2) NOT NULL,
    quantidade_base INTEGER,
    estoque_anterior_base INTEGER,
    estoque_atual_base INTEGER,
    observacao VARCHAR(500),
    venda_id BIGINT,
    recebimento_id BIGINT,
    consignacao_id BIGINT,
    documento_referencia VARCHAR(100), -- NF, Cupom, etc
    usuario_id BIGINT,
    data_movimento TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    local_origem_id BIGINT,
    local_destino_id BIGINT,
    FOREIGN KEY (sku_id) REFERENCES produto_sku(id),
    FOREIGN KEY (produto_id) REFERENCES produto(id),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

-- Criação da tabela pagamento
CREATE TABLE IF NOT EXISTS pagamento (
    id BIGSERIAL PRIMARY KEY,
    sessao_mesa_id BIGINT NOT NULL,
    sessao_convidado_id BIGINT, -- Beneficiário: null=mesa toda, preenchido=pessoa específica
    pagante_id BIGINT, -- Quem está pagando (pode ser diferente do beneficiário)
    metodo VARCHAR(20) NOT NULL, -- pix | card | cash
    cartao_tipo VARCHAR(20), -- credito | debito
    status VARCHAR(20) NOT NULL, -- StatusPagamento
    valor DECIMAL(10,2) NOT NULL,
    valor_base DECIMAL(10,2),
    valor_taxa_servico DECIMAL(10,2),
    percentual_taxa_servico DECIMAL(5,2),
    inclui_taxa_servico BOOLEAN,
    qr_payload TEXT,
    provider_ref VARCHAR(100),
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    pago_em TIMESTAMP,
    FOREIGN KEY (sessao_mesa_id) REFERENCES sessao_mesa(id),
    FOREIGN KEY (sessao_convidado_id) REFERENCES sessao_convidado(id),
    FOREIGN KEY (pagante_id) REFERENCES sessao_convidado(id)
);

-- Criação da tabela pagamento_alocacao
CREATE TABLE IF NOT EXISTS pagamento_alocacao (
    id BIGSERIAL PRIMARY KEY,
    pagamento_id BIGINT NOT NULL,
    sessao_convidado_id BIGINT NOT NULL,
    valor DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (pagamento_id) REFERENCES pagamento(id),
    FOREIGN KEY (sessao_convidado_id) REFERENCES sessao_convidado(id)
);

-- Criação da tabela produto_disponibilidade
CREATE TABLE IF NOT EXISTS produto_disponibilidade (
    id BIGSERIAL PRIMARY KEY,
    produto_id BIGINT NOT NULL,
    dia_semana VARCHAR(20) NOT NULL, -- DiaSemana
    horario_inicio TIME NOT NULL,
    horario_fim TIME NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (produto_id) REFERENCES produto(id)
);

-- Criação da tabela produto_midia
CREATE TABLE IF NOT EXISTS produto_midia (
    id BIGSERIAL PRIMARY KEY,
    produto_id BIGINT NOT NULL,
    tipo VARCHAR(20) NOT NULL, -- TipoMidia
    url VARCHAR(255) NOT NULL,
    titulo VARCHAR(200),
    descricao TEXT,
    ordem INTEGER DEFAULT 0,
    principal BOOLEAN DEFAULT FALSE,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (produto_id) REFERENCES produto(id)
);

-- Criação da tabela produto_promocao
CREATE TABLE IF NOT EXISTS produto_promocao (
    id BIGSERIAL PRIMARY KEY,
    produto_id BIGINT NOT NULL,
    dia_semana VARCHAR(20) NOT NULL, -- DiaSemana
    horario_inicio TIME NOT NULL,
    horario_fim TIME NOT NULL,
    tipo_promocao VARCHAR(20) NOT NULL, -- TipoPromocao
    percentual_desconto DECIMAL(10,2),
    valor_promocional DECIMAL(10,2),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (produto_id) REFERENCES produto(id)
);

-- Criação da tabela subcategoria_disponibilidade
CREATE TABLE IF NOT EXISTS subcategoria_disponibilidade (
    id BIGSERIAL PRIMARY KEY,
    subcategoria_id BIGINT NOT NULL,
    dia_semana VARCHAR(20) NOT NULL, -- DiaSemana
    horario_inicio TIME NOT NULL,
    horario_fim TIME NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (subcategoria_id) REFERENCES subcategoria(id)
);

CREATE TABLE IF NOT EXISTS tasks (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    due_date DATE,
    priority VARCHAR(50) NOT NULL,
    assignee_id BIGINT,
    created_by BIGINT NOT NULL,
    list_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    status VARCHAR(20), -- TaskStatus
    FOREIGN KEY (assignee_id) REFERENCES usuarios(id),
    FOREIGN KEY (created_by) REFERENCES usuarios(id),
    FOREIGN KEY (list_id) REFERENCES task_lists(id)
);

CREATE TABLE IF NOT EXISTS comments (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    text VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (task_id) REFERENCES tasks(id),
    FOREIGN KEY (user_id) REFERENCES usuarios(id)
);

CREATE TABLE IF NOT EXISTS attachments (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT,
    comment_id BIGINT,
    filename VARCHAR(255) NOT NULL,
    file_type VARCHAR(100),
    file_size BIGINT,
    file_path VARCHAR(255) NOT NULL,
    uploaded_by BIGINT NOT NULL,
    uploaded_at TIMESTAMP NOT NULL,
    FOREIGN KEY (task_id) REFERENCES tasks(id),
    FOREIGN KEY (comment_id) REFERENCES comments(id),
    FOREIGN KEY (uploaded_by) REFERENCES usuarios(id)
);

CREATE TABLE IF NOT EXISTS task_history (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    action VARCHAR(255) NOT NULL,
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_task_history_task
        FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_history_user
        FOREIGN KEY (user_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS task_watchers (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_task_watchers_task
        FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_watchers_user
        FOREIGN KEY (user_id) REFERENCES usuarios(id) ON DELETE CASCADE
);


CREATE TABLE IF NOT EXISTS task_time_entries (
    id VARCHAR(255) PRIMARY KEY,

    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,

    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,

    duration BIGINT,
    description TEXT,

    CONSTRAINT fk_task_time_entries_task
        FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,

    CONSTRAINT fk_task_time_entries_user
        FOREIGN KEY (user_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

-- Criação da tabela chamado
CREATE TABLE IF NOT EXISTS chamado (
    id BIGSERIAL PRIMARY KEY,
    sessao_mesa_id BIGINT NOT NULL,
    sessao_convidado_id BIGINT, -- Quem chamou (opcional)
    tipo VARCHAR(20) NOT NULL, -- TipoChamado
    status VARCHAR(20) NOT NULL, -- StatusChamado
    observacao VARCHAR(500),
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atendido_por VARCHAR(100), -- Nome do staff que atendeu
    atendido_em TIMESTAMP,
    FOREIGN KEY (sessao_mesa_id) REFERENCES sessao_mesa(id),
    FOREIGN KEY (sessao_convidado_id) REFERENCES sessao_convidado(id)
);

-- Criação da tabela nfe
CREATE TABLE IF NOT EXISTS nfe (
    id BIGSERIAL PRIMARY KEY,
    id_venda BIGINT,
    id_cliente BIGINT,
    numero VARCHAR(255),
    protocolo VARCHAR(255),
    motivo_rejeicao VARCHAR(255),
    serie VARCHAR(255),
    chave_acesso VARCHAR(255),
    status VARCHAR(255),
    xml_assinado TEXT,
    xml_retorno TEXT,
    data_emissao TIMESTAMP,
    valor_total DOUBLE PRECISION,
    ambiente INTEGER,
    modelo INTEGER DEFAULT 55
);

-- Criação da tabela notificacao
CREATE TABLE IF NOT EXISTS notificacao (
    id BIGSERIAL PRIMARY KEY,
    sessao_mesa_id BIGINT NOT NULL,
    sessao_convidado_id BIGINT NOT NULL,
    tipo VARCHAR(50) NOT NULL, -- guest_joined, order_created
    titulo VARCHAR(255) NOT NULL,
    mensagem VARCHAR(500) NOT NULL,
    lida BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lida_em TIMESTAMP,
    payload_json TEXT,
    FOREIGN KEY (sessao_mesa_id) REFERENCES sessao_mesa(id),
    FOREIGN KEY (sessao_convidado_id) REFERENCES sessao_convidado(id)
);

-- Criação da tabela movimentos_pontos
CREATE TABLE IF NOT EXISTS movimentos_pontos (
    id BIGSERIAL PRIMARY KEY,
    cliente_id BIGINT NOT NULL,
    tipo VARCHAR(10) NOT NULL, -- TipoMovimentoPontos
    origem VARCHAR(10) NOT NULL, -- OrigemMovimentoPontos
    referencia_tipo VARCHAR(30),
    referencia_id BIGINT,
    pontos INTEGER NOT NULL,
    saldo_apos INTEGER NOT NULL,
    data_hora TIMESTAMP NOT NULL,
    observacao VARCHAR(500),
    FOREIGN KEY (cliente_id) REFERENCES usuarios(id)
);

-- Criação da tabela pedido_compra
CREATE TABLE IF NOT EXISTS pedido_compra (
    id BIGSERIAL PRIMARY KEY,
    fornecedor_id BIGINT,
    usuario_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'RASCUNHO',
    data_prevista DATE,
    observacao TEXT,
    criado_em TIMESTAMP,
    atualizado_em TIMESTAMP,
    FOREIGN KEY (fornecedor_id) REFERENCES fornecedor(id),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

-- Criação da tabela pedido_compra_item
CREATE TABLE IF NOT EXISTS pedido_compra_item (
    id BIGSERIAL PRIMARY KEY,
    pedido_compra_id BIGINT NOT NULL,
    produto_id BIGINT NOT NULL,
    sku_id BIGINT,
    embalagem_id BIGINT,
    quantidade DECIMAL(15,3) NOT NULL,
    quantidade_base INTEGER,
    custo_unitario DECIMAL(15,4),
    subtotal DECIMAL(15,2),
    recebido_base INTEGER DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    FOREIGN KEY (pedido_compra_id) REFERENCES pedido_compra(id),
    FOREIGN KEY (produto_id) REFERENCES produto(id),
    FOREIGN KEY (sku_id) REFERENCES produto_sku(id),
    FOREIGN KEY (embalagem_id) REFERENCES embalagem(id)
);

-- Criação da tabela recebimento_mercadoria
CREATE TABLE IF NOT EXISTS recebimento_mercadoria (
    id BIGSERIAL PRIMARY KEY,
    numero_nf VARCHAR(20) NOT NULL,
    chave_nfe VARCHAR(44),
    fornecedor_id BIGINT NOT NULL,
    data_recebimento TIMESTAMP NOT NULL,
    data_emissao_nf DATE,
    valor_total DECIMAL(15,2) DEFAULT 0.00,
    quantidade_itens INTEGER DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    observacao TEXT,
    xml_nfe TEXT,
    usuario_id BIGINT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (fornecedor_id) REFERENCES fornecedor(id),
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

-- Criação da tabela recebimento_item
CREATE TABLE IF NOT EXISTS recebimento_item (
    id BIGSERIAL PRIMARY KEY,
    recebimento_id BIGINT NOT NULL,
    produto_id BIGINT NOT NULL,
    sku_id BIGINT,
    quantidade DECIMAL(15,3) NOT NULL,
    custo_unitario DECIMAL(15,4) NOT NULL,
    valor_total DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    lote VARCHAR(50),
    data_validade DATE,
    codigo_produto_fornecedor VARCHAR(50),
    descricao_nfe VARCHAR(255),
    ncm VARCHAR(8),
    cfop VARCHAR(4),
    unidade VARCHAR(10),
    FOREIGN KEY (recebimento_id) REFERENCES recebimento_mercadoria(id),
    FOREIGN KEY (produto_id) REFERENCES produto(id),
    FOREIGN KEY (sku_id) REFERENCES produto_sku(id)
);

-- Criação da tabela recompensas
CREATE TABLE IF NOT EXISTS recompensas (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    descricao VARCHAR(500),
    pontos_necessarios INTEGER NOT NULL,
    tipo VARCHAR(30) NOT NULL, -- TipoRecompensa
    desconto_percentual DECIMAL(5,2),
    desconto_valor DECIMAL(12,2),
    desconto_valor_maximo DECIMAL(12,2),
    produto_id BIGINT,
    estoque INTEGER,
    ativo BOOLEAN DEFAULT TRUE,
    validade_inicio DATE,
    validade_fim DATE,
    imagem_url VARCHAR(500),
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP
);

-- Tabela para grupos de permissões (já que permissoes já foi criada antes)
CREATE TABLE IF NOT EXISTS permissoes_grupos (
    id BIGSERIAL PRIMARY KEY,
    id_grupo BIGINT,
    permissao VARCHAR(255),
    FOREIGN KEY (id_grupo) REFERENCES grupo_usuario(id),
    UNIQUE (id_grupo, permissao)
);

-- Tabela para notificações (separada da entidade Notificacao)
CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    titulo VARCHAR(255) NOT NULL,
    mensagem TEXT NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    user_id BIGINT,
    role VARCHAR(50),
    importancia VARCHAR(20) NOT NULL DEFAULT 'MEDIA',
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_expiracao VARCHAR(255),
    link VARCHAR(255),
    created_by BIGINT NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (user_id) REFERENCES usuarios(id),
    FOREIGN KEY (created_by) REFERENCES usuarios(id)
);

-- Tabela para destinatários de notificações
CREATE TABLE IF NOT EXISTS notification_recipients (
    id BIGSERIAL PRIMARY KEY,
    notification_id BIGINT NOT NULL,
    recipient_id BIGINT,
    user_id BIGINT NOT NULL,
    role VARCHAR(255),
    read_at TIMESTAMP,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_status BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (notification_id) REFERENCES notifications(id),
    FOREIGN KEY (user_id) REFERENCES usuarios(id)
);

-- Índices para melhorar desempenho
CREATE INDEX IF NOT EXISTS idx_produto_categoria ON produto(categoria_id);
CREATE INDEX IF NOT EXISTS idx_produto_subcategoria ON produto(subcategoria_id);
CREATE INDEX IF NOT EXISTS idx_produto_fornecedor ON produto(fornecedor_id);
CREATE INDEX IF NOT EXISTS idx_item_pedido_pedido ON item_pedido(pedido_id);
CREATE INDEX IF NOT EXISTS idx_item_pedido_produto ON item_pedido(produto_id);
CREATE INDEX IF NOT EXISTS idx_pedido_sessao_mesa ON pedido(sessao_mesa_id);
CREATE INDEX IF NOT EXISTS idx_conta_pagar_fornecedor ON conta_pagar(fornecedor_id);
CREATE INDEX IF NOT EXISTS idx_conta_receber_cliente ON conta_receber(cliente_id);
CREATE INDEX IF NOT EXISTS idx_pagamento_sessao_mesa ON pagamento(sessao_mesa_id);
CREATE INDEX IF NOT EXISTS idx_movimento_estoque_sku ON movimento_estoque(sku_id);
CREATE INDEX IF NOT EXISTS idx_movimento_estoque_produto ON movimento_estoque(produto_id);
CREATE INDEX IF NOT EXISTS idx_notificacao_convidado_lida ON notificacao(sessao_convidado_id, lida);
CREATE INDEX IF NOT EXISTS idx_notificacao_criado_em ON notificacao(criado_em);
