ALTER TABLE produto ADD COLUMN vida_util_dias INTEGER;

COMMENT ON COLUMN produto.vida_util_dias IS 'Vida útil total do produto em dias (base para alertas percentuais de validade)';