-- Migration: Popula templates com configurações de IA
-- Versão: 20260204090200
-- Autor: Sistema Automatizado
-- NOTA: Atualiza apenas templates existentes na base

UPDATE signage_templates 
SET 
    ai_enabled = true,
    ai_mode = 'CUTOUT_TRANSPARENT',
    ai_prompt = 'Elegant product photography, isolated on transparent background. Sophisticated lighting, refined details, premium feel. Perfect for luxury product presentation. High-quality edges, studio-grade isolation. PNG transparency. Suitable for elegant minimalist templates.',
    ai_prompt_version = '1.0',
    ai_output_size = '1024x1024'
WHERE template_id = 'clean-elegance';

-- Verificação: Mostra templates atualizados
SELECT template_id, name, ai_enabled, ai_mode, ai_prompt_version 
FROM signage_templates 
WHERE ai_enabled = true;