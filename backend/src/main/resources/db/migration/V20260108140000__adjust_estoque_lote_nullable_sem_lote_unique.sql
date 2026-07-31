-- Ajusta estoque_lote para suportar SEM_LOTE/SEM_VALIDADE e chave lógica
-- 1) Permitir valores nulos e defaults
ALTER TABLE estoque_lote ALTER COLUMN lote DROP NOT NULL;
ALTER TABLE estoque_lote ALTER COLUMN lote SET DEFAULT 'SEM_LOTE';
ALTER TABLE estoque_lote ALTER COLUMN data_validade DROP NOT NULL;
ALTER TABLE estoque_lote ALTER COLUMN data_validade SET DEFAULT DATE '1900-01-01';

-- 2) Mesclar duplicatas por chave lógica (sku, lote, validade) somando quantidade
WITH duplicados AS (
    SELECT produto_sku_id,
           COALESCE(lote, '') AS lote_normalizado,
           COALESCE(data_validade, '1900-01-01') AS validade_normalizada,
           SUM(quantidade) AS quantidade_total,
           MIN(id) AS id_manter,
           COUNT(*) AS qtd_registros
    FROM estoque_lote
    GROUP BY produto_sku_id,
             COALESCE(lote, ''),
             COALESCE(data_validade, '1900-01-01')
    HAVING COUNT(*) > 1
)
UPDATE estoque_lote el
SET quantidade = d.quantidade_total,
    updated_at = NOW()
FROM duplicados d
WHERE el.id = d.id_manter;

WITH duplicados AS (
    SELECT produto_sku_id,
           COALESCE(lote, '') AS lote_normalizado,
           COALESCE(data_validade, '1900-01-01') AS validade_normalizada,
           MIN(id) AS id_manter
    FROM estoque_lote
    GROUP BY produto_sku_id,
             COALESCE(lote, ''),
             COALESCE(data_validade, '1900-01-01')
    HAVING COUNT(*) > 1
)
DELETE FROM estoque_lote el
USING duplicados d
WHERE el.produto_sku_id = d.produto_sku_id
  AND COALESCE(el.lote, '') = d.lote_normalizado
  AND COALESCE(el.data_validade, '1900-01-01') = d.validade_normalizada
  AND el.id <> d.id_manter;

-- 3) Criar índice único lógico
CREATE UNIQUE INDEX IF NOT EXISTS uk_estoque_lote_logico
    ON estoque_lote (produto_sku_id, COALESCE(lote, ''), COALESCE(data_validade, '1900-01-01'));

-- 4) Índice para FEFO (nulls last)
CREATE INDEX IF NOT EXISTS idx_estoque_lote_fefo
    ON estoque_lote (produto_sku_id, data_validade NULLS LAST, id);
