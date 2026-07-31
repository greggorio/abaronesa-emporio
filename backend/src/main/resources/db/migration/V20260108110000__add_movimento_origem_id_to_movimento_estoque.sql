-- Adiciona coluna movimento_origem_id na tabela movimento_estoque (se ainda não existir)
ALTER TABLE movimento_estoque
ADD COLUMN IF NOT EXISTS movimento_origem_id BIGINT;

-- Cria índice para movimento_origem_id (se ainda não existir)
CREATE INDEX IF NOT EXISTS idx_movimento_estoque_origem_id
ON movimento_estoque(movimento_origem_id);

-- Adiciona comentário para documentar a coluna
COMMENT ON COLUMN movimento_estoque.movimento_origem_id
IS 'ID do movimento original que este movimento estorna';