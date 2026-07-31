ALTER TABLE produto ADD COLUMN controla_validade BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN produto.controla_validade IS 'Indica se o produto exige controle de validade por lote';