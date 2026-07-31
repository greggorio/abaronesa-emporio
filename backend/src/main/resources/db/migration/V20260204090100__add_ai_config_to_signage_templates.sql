-- Migration: Adiciona configuração de IA aos templates
-- Versão: 20260204090100
-- Autor: Sistema Automatizado

ALTER TABLE signage_templates 
ADD COLUMN IF NOT EXISTS ai_mode VARCHAR(20),
ADD COLUMN IF NOT EXISTS ai_prompt TEXT,
ADD COLUMN IF NOT EXISTS ai_prompt_version VARCHAR(10) DEFAULT '1.0',
ADD COLUMN IF NOT EXISTS ai_enabled BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS ai_output_size VARCHAR(20) DEFAULT '1024x1024';

-- Cria índice para busca rápida de templates com IA habilitada
CREATE INDEX IF NOT EXISTS idx_signage_templates_ai_enabled ON signage_templates(ai_enabled) 
WHERE ai_enabled = TRUE;

-- Comentários nas colunas
COMMENT ON COLUMN signage_templates.ai_mode IS 'Modo de geração de IA (ex: CUTOUT_TRANSPARENT)';
COMMENT ON COLUMN signage_templates.ai_prompt IS 'Prompt base enviado para a API de IA';
COMMENT ON COLUMN signage_templates.ai_prompt_version IS 'Versão do prompt para controle de cache';
COMMENT ON COLUMN signage_templates.ai_enabled IS 'Flag indicando se template suporta geração de imagem IA';
COMMENT ON COLUMN signage_templates.ai_output_size IS 'Dimensão da imagem gerada (ex: 1024x1024)';