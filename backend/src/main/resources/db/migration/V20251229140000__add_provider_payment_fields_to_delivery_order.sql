ALTER TABLE delivery_order
  ADD COLUMN provider_gateway VARCHAR(50),
  ADD COLUMN provider_payment_id VARCHAR(100);

CREATE INDEX IF NOT EXISTS ix_delivery_order_provider_payment
  ON delivery_order (provider_gateway, provider_payment_id);

CREATE INDEX IF NOT EXISTS ix_delivery_order_external_reference
  ON delivery_order (external_reference);
