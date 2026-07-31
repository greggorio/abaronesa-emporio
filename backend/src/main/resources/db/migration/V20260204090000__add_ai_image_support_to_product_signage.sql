-- Migration: Adiciona suporte a imagens geradas por IA na tabela product_signage
-- Versão: 20260204090000
-- Autor: Sistema Automatizado
ALTER TABLE product_signage 
ADD COLUMN IF NOT EXISTS use_ai_image BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS ai_image_hash VARCHAR(64),
ADD COLUMN IF NOT EXISTS ai_generated_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS ai_revision INT DEFAULT 0;

-- Cria índice para busca eficiente por hash
CREATE INDEX IF NOT EXISTS idx_product_signage_ai_hash ON product_signage(ai_image_hash) 
WHERE ai_image_hash IS NOT NULL;

-- Comentários nas colunas para documentação
COMMENT ON COLUMN product_signage.use_ai_image IS 'Flag indicando se deve usar imagem IA ao invés da original';
COMMENT ON COLUMN product_signage.ai_image_hash IS 'Hash SHA256 da imagem gerada (referência ao arquivo)';
COMMENT ON COLUMN product_signage.ai_generated_at IS 'Timestamp da última geração da imagem IA';
COMMENT ON COLUMN product_signage.ai_revision IS 'Contador de revisões (incrementado ao regerar)';