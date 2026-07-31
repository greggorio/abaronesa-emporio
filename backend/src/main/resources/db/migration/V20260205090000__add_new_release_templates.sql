-- Migration: Add new signage templates (Template 02 and 03) and AI config
-- Data: 2026-02-05
-- Autor: Sistema

-- Inserir Template 02 (clean-isolated) - agora full-bleed
INSERT INTO signage_templates (template_id, name, description, image_mode, required_texts, color_slots)
VALUES
('clean-isolated',
 'Editorial Overlay',
 'Imagem full-bleed com texto sobreposto e detalhes sofisticados.',
 'FULL_BLEED',
 '["badge", "headline", "subtitle", "price", "cta"]',
 '{
    "bg": "DarkMuted",
    "headline": "LightMuted",
    "subtitle": "Muted",
    "badge": "Vibrant",
    "price": "LightMuted",
    "cta": "Vibrant"
 }'
)
ON CONFLICT (template_id) DO NOTHING;

-- Inserir Template 03 (new-release)
INSERT INTO signage_templates (template_id, name, description, image_mode, required_texts, color_slots)
VALUES
('new-release',
 'New Release',
 'Layout de lançamento com glow, faixa de novidade e CTA destacado.',
 'FULL_BLEED',
 '["badge", "headline", "subtitle", "price", "cta"]',
 '{
    "bg": "DarkMuted",
    "headline": "LightMuted",
    "subtitle": "Muted",
    "badge": "Vibrant",
    "price": "LightMuted",
    "cta": "Vibrant"
 }'
)
ON CONFLICT (template_id) DO NOTHING;

-- Configurar IA para templates novos
UPDATE signage_templates
SET
    ai_enabled = true,
    ai_mode = 'CUTOUT_TRANSPARENT',
    ai_prompt = 'Professional product photography, isolated on transparent background, studio lighting, high detail, clean edges suitable for overlay on any background. Crisp cutout, premium presentation. PNG transparency.',
    ai_prompt_version = '1.0',
    ai_output_size = '1024x1024'
WHERE template_id IN ('clean-isolated', 'new-release');

-- Garantir image_mode coerente caso já existisse registro
UPDATE signage_templates
SET image_mode = 'FULL_BLEED'
WHERE template_id IN ('clean-isolated', 'new-release');

-- Verificação rápida
SELECT template_id, name, image_mode, ai_enabled, ai_prompt_version
FROM signage_templates
WHERE template_id IN ('clean-isolated', 'new-release');
