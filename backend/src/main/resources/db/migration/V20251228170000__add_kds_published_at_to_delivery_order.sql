-- Adds timestamp to guard KDS publishes
ALTER TABLE delivery_order ADD COLUMN kds_published_at timestamp NULL;
