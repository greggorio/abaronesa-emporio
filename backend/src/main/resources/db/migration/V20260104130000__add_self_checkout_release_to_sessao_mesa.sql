ALTER TABLE sessao_mesa
    ADD COLUMN self_checkout_liberado BOOLEAN DEFAULT FALSE;

ALTER TABLE sessao_mesa
    ADD COLUMN self_checkout_liberado_em TIMESTAMP NULL;
