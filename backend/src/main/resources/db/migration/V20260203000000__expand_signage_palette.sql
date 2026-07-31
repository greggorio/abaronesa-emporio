-- Migration: Expandir estrutura de signage para suportar paleta completa (6 cores) e metadata source
-- Data: 2026-02-03
-- Autor: Sistema

-- Adicionar campo metadata_source
ALTER TABLE product_signage ADD COLUMN IF NOT EXISTS metadata_source VARCHAR(20) DEFAULT 'MANUAL';

-- Adicionar constraint para validar valores de metadata_source
ALTER TABLE product_signage 
    ADD CONSTRAINT chk_product_signage_metadata_source 
    CHECK (metadata_source IN ('AUTO_AI', 'AUTO_VIBRANT', 'MANUAL'));

-- Adicionar campo para imagem gerada (caminho/URL da imagem otimizada)
ALTER TABLE product_signage ADD COLUMN IF NOT EXISTS generated_image_path VARCHAR(500);

-- Adicionar campo para URL do vídeo MP4
ALTER TABLE product_signage ADD COLUMN IF NOT EXISTS mp4_url VARCHAR(500);

-- Comentários documentando a estrutura esperada do JSONB 'palette'
COMMENT ON COLUMN product_signage.palette IS 'JSON com paleta de 6 cores: {vibrant, muted, lightVibrant, darkVibrant, lightMuted, darkMuted, background, text, accent, accent2, isDark}';

COMMENT ON COLUMN product_signage.phrases IS 'JSON com textos do signage: {headline, subtitle, cta, badge, price}';

COMMENT ON COLUMN product_signage.metadata_source IS 'Fonte dos metadados: AUTO_AI (gerado por IA), AUTO_VIBRANT (extrato automaticamente da imagem), MANUAL (editado manualmente)';

-- Atualizar registro se necessário (opcional - apenas para desenvolvimento)
-- UPDATE product_signage SET metadata_source = 'MANUAL' WHERE metadata_source IS NULL;

-- Criar tabela de templates de signage (opcional - se quiser armazenar templates no banco)
CREATE TABLE IF NOT EXISTS signage_templates (
    id SERIAL PRIMARY KEY,
    template_id VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    image_mode VARCHAR(20) NOT NULL CHECK (image_mode IN ('ISOLATED', 'CLIPPED', 'FULL_BLEED')),
    required_texts JSONB NOT NULL DEFAULT '["headline", "subtitle"]',
    color_slots JSONB,
    html_template TEXT,
    css_template TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Inserir templates iniciais (baseados nos templates Vue.js)
INSERT INTO signage_templates (template_id, name, description, image_mode, required_texts, color_slots) VALUES
('clean-elegance', 
 'Clean Elegance', 
 'Layout minimalista e elegante com tipografia sofisticada e separador refinado. Ideal para produtos premium.', 
 'ISOLATED', 
 '["badge", "headline", "subtitle", "price"]', 
 '{
    "bg": "LightMuted",
    "headline": "DarkMuted", 
    "subtitle": "Muted",
    "badge": "Muted",
    "price": "DarkMuted",
    "separator": "Muted"
 }'
)
ON CONFLICT (template_id) DO NOTHING;

-- Criar índice para busca rápida por template
CREATE INDEX IF NOT EXISTS idx_signage_templates_active ON signage_templates(is_active);
