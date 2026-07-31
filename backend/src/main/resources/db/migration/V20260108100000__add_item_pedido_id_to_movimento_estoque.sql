-- Adiciona coluna item_pedido_id na tabela movimento_estoque (se ainda não existir)
ALTER TABLE movimento_estoque
ADD COLUMN IF NOT EXISTS item_pedido_id BIGINT;

-- Cria índice para item_pedido_id (se ainda não existir)
CREATE INDEX IF NOT EXISTS idx_movimento_estoque_item_pedido_id
ON movimento_estoque(item_pedido_id);

-- Adiciona comentário para documentar a coluna
COMMENT ON COLUMN movimento_estoque.item_pedido_id
IS 'ID do item do pedido associado ao movimento de estoque';