-- Seed: settings_schema do Template 04 (frontend: template-04-minimalist-zen, backend: clean-elegance)
-- Data: 2026-02-09
--
-- Observacao:
-- - So faz seed quando o schema atual estiver vazio ('{}') ou NULL, para nao sobrescrever dados existentes.
-- - O schema segue um shape simples (type/properties/default/min/max/enum/description) para uso no formulario dinamico.

UPDATE signage_templates
SET
    settings_schema_version = COALESCE(settings_schema_version, '1'),
    settings_schema = '{
      "type": "object",
      "title": "Template 04 - Minimalist Zen",
      "description": "Knobs de comportamento e layout para o template 04.",
      "additionalProperties": false,
      "properties": {
        "particlesEnabled": {
          "type": "boolean",
          "default": true,
          "description": "Ativa/desativa partículas no background."
        },
        "headlineShimmer": {
          "type": "boolean",
          "default": true,
          "description": "Ativa/desativa efeito shimmer no título."
        },
        "subtitleTyping": {
          "type": "boolean",
          "default": true,
          "description": "Ativa/desativa efeito de digitação no subtítulo."
        },
        "zenPadding": {
          "type": "number",
          "default": 24,
          "minimum": 0,
          "maximum": 80,
          "description": "Padding base (px) aplicado ao layout."
        },
        "headlineLetterSpacing": {
          "type": "number",
          "default": 0.5,
          "minimum": -1,
          "maximum": 6,
          "description": "Espaçamento entre letras do título (px)."
        },
        "badgeFontFamily": {
          "type": "string",
          "default": "Inter",
          "enum": ["Inter", "Georgia", "Playfair Display", "Montserrat"],
          "description": "Fonte usada no badge."
        }
      }
    }'::jsonb
WHERE template_id = 'clean-elegance'
  AND (settings_schema IS NULL OR settings_schema = '{}'::jsonb);
