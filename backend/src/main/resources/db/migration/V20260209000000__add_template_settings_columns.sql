-- Migration: Adiciona colunas de template settings para product_signage e signage_templates
-- Data: 2026-02-09
-- Task: TASK-003

-- ============================================
-- TABELA: product_signage
-- ============================================

-- Adicionar coluna template_settings (JSONB) com default '{}'
ALTER TABLE product_signage
    ADD COLUMN IF NOT EXISTS template_settings JSONB NOT NULL DEFAULT '{}';

-- Adicionar coluna opcional template_settings_version (string)
ALTER TABLE product_signage
    ADD COLUMN IF NOT EXISTS template_settings_version VARCHAR(20);

-- Criar indice para buscas eficientes em template_settings
CREATE INDEX IF NOT EXISTS idx_product_signage_template_settings
    ON product_signage USING GIN (template_settings);

-- Comentarios documentando as novas colunas
COMMENT ON COLUMN product_signage.template_settings IS 'Configuracoes customizadas do template em formato JSON (ex: {fontSize: "large", position: "center"})';
COMMENT ON COLUMN product_signage.template_settings_version IS 'Versao das configuracoes de template para controle de compatibilidade';

-- ============================================
-- TABELA: signage_templates
-- ============================================

-- Adicionar coluna settings_schema (JSONB) com default '{}'
ALTER TABLE signage_templates
    ADD COLUMN IF NOT EXISTS settings_schema JSONB NOT NULL DEFAULT '{}';

-- Adicionar coluna opcional settings_schema_version (string)
ALTER TABLE signage_templates
    ADD COLUMN IF NOT EXISTS settings_schema_version VARCHAR(20);

-- Criar indice para buscas eficientes em settings_schema
CREATE INDEX IF NOT EXISTS idx_signage_templates_settings_schema
    ON signage_templates USING GIN (settings_schema);

-- Comentarios documentando as novas colunas
COMMENT ON COLUMN signage_templates.settings_schema IS 'Schema JSON que define a estrutura e validacao das configuracoes de template disponiveis';
COMMENT ON COLUMN signage_templates.settings_schema_version IS 'Versao do schema de configuracoes para evolucao controlada';

-- ============================================
-- BACKFILL: Garantir compatibilidade com registros existentes
-- ============================================

-- Garantir que registros antigos sem template_settings tenham valor padrao
UPDATE product_signage
    SET template_settings = '{}'
    WHERE template_settings IS NULL;

-- Garantir que templates antigos sem settings_schema tenham valor padrao
UPDATE signage_templates
    SET settings_schema = '{}'
    WHERE settings_schema IS NULL;

-- Verificacao opcional (descomente para debug local)
-- SELECT
--     (SELECT COUNT(*) FROM product_signage WHERE template_settings = '{}') AS product_signage_with_default,
--     (SELECT COUNT(*) FROM signage_templates WHERE settings_schema = '{}') AS signage_templates_with_default;
