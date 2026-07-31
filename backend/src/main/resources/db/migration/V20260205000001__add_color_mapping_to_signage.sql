-- Migration: Adicionar mapeamento customizado de cores para signage
-- Data: 2026-02-05
-- Autor: Sistema

-- Adicionar campo color_mapping (JSON) para armazenar mapeamento de elementos do template para cores
ALTER TABLE product_signage ADD COLUMN IF NOT EXISTS color_mapping JSONB;

-- Comentário documentando a estrutura esperada
COMMENT ON COLUMN product_signage.color_mapping IS 
    'JSON com mapeamento de cores customizado: {templateId: "clean-elegance", elementMappings: {background: "palette:lightMuted", headline: "palette:darkMuted", ...}, useCustomMapping: true}';

-- Criar índice para busca rápida por template_id dentro do JSON
CREATE INDEX IF NOT EXISTS idx_product_signage_color_mapping_template 
    ON product_signage((color_mapping->>'templateId'));
