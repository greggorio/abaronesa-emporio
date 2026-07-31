ALTER TABLE pagamento
    ADD COLUMN IF NOT EXISTS self_checkout_origem VARCHAR(30),
    ADD COLUMN IF NOT EXISTS self_checkout_resolvido BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS self_checkout_resolvido_em TIMESTAMP;
