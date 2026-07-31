-- Migration: Adiciona campos para integração com signage-api
-- Versão: 20260206000000
-- Adiciona campos necessários para sincronização com signage

ALTER TABLE product_signage 
ADD COLUMN IF NOT EXISTS duration_seconds INT DEFAULT 10,
ADD COLUMN IF NOT EXISTS signage_screen_id VARCHAR(255),
ADD COLUMN IF NOT EXISTS last_sync_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS mp4_url VARCHAR(500);

-- Cria índices para buscas eficientes
CREATE INDEX IF NOT EXISTS idx_product_signage_screen_id ON product_signage(signage_screen_id) 
WHERE signage_screen_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_product_signage_enabled_status ON product_signage(enabled, status) 
WHERE enabled = TRUE AND status = 'rendered';

-- Comentários nas colunas para documentação
COMMENT ON COLUMN product_signage.duration_seconds IS 'Duração de exibição do vídeo na playlist (em segundos)';
COMMENT ON COLUMN product_signage.signage_screen_id IS 'ID da screen no signage-api (para rastreamento)';
COMMENT ON COLUMN product_signage.last_sync_at IS 'Timestamp da última sincronização com signage-api';
COMMENT ON COLUMN product_signage.mp4_url IS 'URL pública do vídeo MP4 gerado';
