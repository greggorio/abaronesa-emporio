ALTER TABLE delivery_order
    ADD COLUMN IF NOT EXISTS uber_dropoff_eta TIMESTAMP,
    ADD COLUMN IF NOT EXISTS uber_pickup_eta TIMESTAMP,
    ADD COLUMN IF NOT EXISTS uber_pickup_address TEXT,
    ADD COLUMN IF NOT EXISTS cliente_id BIGINT;
