-- Migration: Add promo-hero signage template to match frontend template id
-- Date: 2026-02-05

INSERT INTO signage_templates (template_id, name, description, image_mode, required_texts, color_slots)
VALUES
(
  'promo-hero',
  'Hero Promo',
  'Full-bleed com overlays e CTA destacado.',
  'FULL_BLEED',
  '["badge", "headline", "subtitle", "price", "cta"]',
  '{
    "bg": "DarkMuted",
    "headline": "LightMuted",
    "subtitle": "Muted",
    "badge": "Vibrant",
    "price": "Vibrant",
    "cta": "Vibrant"
  }'
)
ON CONFLICT (template_id) DO NOTHING;

