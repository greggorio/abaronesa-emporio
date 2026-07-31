ALTER TABLE produto
    ALTER COLUMN controla_estoque SET DEFAULT TRUE;

ALTER TABLE produto
    ALTER COLUMN controla_validade SET DEFAULT TRUE;

UPDATE produto
SET controla_estoque = TRUE,
    controla_validade = TRUE
WHERE controla_estoque IS DISTINCT FROM TRUE
   OR controla_validade IS DISTINCT FROM TRUE;
