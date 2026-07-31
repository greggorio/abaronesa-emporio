-- Migration inicial do banco de dados Espresso

-- Criação da tabela categorias_foto
CREATE TABLE IF NOT EXISTS categorias_foto (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP(6) NOT NULL,
    nome VARCHAR(100) NOT NULL,
    ordem INTEGER NOT NULL
);

-- Criação da tabela categories
CREATE TABLE IF NOT EXISTS categories (
    id BIGSERIAL PRIMARY KEY,
    active BOOLEAN NOT NULL,
    color VARCHAR(7),
    created_at TIMESTAMP(6) NOT NULL,
    description VARCHAR(500),
    difficulty_level VARCHAR(20) NOT NULL CHECK (difficulty_level IN ('EASY', 'MEDIUM', 'HARD', 'EXPERT')),
    icon VARCHAR(100),
    name VARCHAR(100) NOT NULL UNIQUE,
    updated_at TIMESTAMP(6)
);

-- Criação da tabela evento
CREATE TABLE IF NOT EXISTS evento (
    id BIGSERIAL PRIMARY KEY,
    titulo VARCHAR(200) NOT NULL,
    descricao TEXT,
    data_evento TIMESTAMP NOT NULL,
    banda VARCHAR(200),
    genero VARCHAR(50) NOT NULL,
    imagem_url VARCHAR(500),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(50) NOT NULL DEFAULT 'AGENDADO',
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Criação da tabela fotos
CREATE TABLE IF NOT EXISTS fotos (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP(6) NOT NULL,
    url VARCHAR(500) NOT NULL,
    categoria_id BIGINT NOT NULL,
    FOREIGN KEY (categoria_id) REFERENCES categorias_foto(id)
);

-- Criação da tabela questions
CREATE TABLE IF NOT EXISTS questions (
    id BIGSERIAL PRIMARY KEY,
    active BOOLEAN NOT NULL,
    correct_answer INTEGER NOT NULL,
    image_url VARCHAR(255),
    points INTEGER NOT NULL,
    question VARCHAR(500) NOT NULL,
    category_id BIGINT NOT NULL,
    FOREIGN KEY (category_id) REFERENCES categories(id)
);

-- Criação da tabela question_options
CREATE TABLE IF NOT EXISTS question_options (
    question_id BIGINT NOT NULL,
    option VARCHAR(200),
    option_order INTEGER NOT NULL,
    PRIMARY KEY (question_id, option_order),
    FOREIGN KEY (question_id) REFERENCES questions(id)
);

-- Criação da tabela quiz_sessions
CREATE TABLE IF NOT EXISTS quiz_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_code VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    current_question_index INTEGER NOT NULL DEFAULT 0,
    question_time_limit INTEGER NOT NULL DEFAULT 30,
    auto_advance BOOLEAN NOT NULL DEFAULT FALSE
);

-- Criação da tabela session_questions
CREATE TABLE IF NOT EXISTS session_questions (
    session_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    question_order INTEGER NOT NULL,
    FOREIGN KEY (session_id) REFERENCES quiz_sessions(id) ON DELETE CASCADE,
    FOREIGN KEY (question_id) REFERENCES questions(id)
);

-- Criação da tabela players
CREATE TABLE IF NOT EXISTS players (
    id BIGSERIAL PRIMARY KEY,
    nickname VARCHAR(100) NOT NULL,
    session_code VARCHAR(255) NOT NULL,
    score INTEGER NOT NULL DEFAULT 0,
    correct_answers INTEGER NOT NULL DEFAULT 0,
    wrong_answers INTEGER NOT NULL DEFAULT 0,
    joined_at TIMESTAMP NOT NULL,
    connection_id VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

-- Criação da tabela tenant_config
CREATE TABLE IF NOT EXISTS tenant_config (
    key VARCHAR(64) PRIMARY KEY,
    value VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Criação da tabela theme
CREATE TABLE IF NOT EXISTS theme (
    id BIGSERIAL PRIMARY KEY,
    assets JSONB,
    base_theme_id BIGINT,
    content JSONB,
    created_at TIMESTAMP(6) NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    tenant_id VARCHAR(255) NOT NULL,
    tokens JSONB,
    updated_at TIMESTAMP(6)
);

-- Criação da tabela theme_assignment
CREATE TABLE IF NOT EXISTS theme_assignment (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL,
    priority INTEGER NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    theme_id BIGINT NOT NULL,
    updated_at TIMESTAMP(6),
    valid_from TIMESTAMP(6),
    valid_to TIMESTAMP(6),
    FOREIGN KEY (theme_id) REFERENCES theme(id)
);

-- Índices
CREATE INDEX IF NOT EXISTS idx_evento_ativo ON evento (ativo);
CREATE INDEX IF NOT EXISTS idx_evento_data ON evento (data_evento);
CREATE INDEX IF NOT EXISTS idx_evento_status ON evento (status);
CREATE INDEX IF NOT EXISTS idx_players_connection_id ON players (connection_id);
CREATE INDEX IF NOT EXISTS idx_players_session_code ON players (session_code);
CREATE INDEX IF NOT EXISTS idx_quiz_sessions_code ON quiz_sessions (session_code);
CREATE INDEX IF NOT EXISTS idx_quiz_sessions_status ON quiz_sessions (status);

-- Notificações push (FCM)
CREATE TABLE IF NOT EXISTS notification_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(500) NOT NULL UNIQUE,
    device_info VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS notification_history (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    body VARCHAR(500) NOT NULL,
    image_url VARCHAR(500),
    sent_at TIMESTAMP NOT NULL,
    recipients_count INTEGER NOT NULL DEFAULT 0
);
