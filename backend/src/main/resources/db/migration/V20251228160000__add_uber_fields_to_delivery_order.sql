ALTER TABLE delivery_order
    ADD COLUMN IF NOT EXISTS uber_delivery_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS uber_tracking_url TEXT,
    ADD COLUMN IF NOT EXISTS uber_status VARCHAR(50);
