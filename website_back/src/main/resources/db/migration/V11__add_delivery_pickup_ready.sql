alter table delivery_order
  add column if not exists pickup_ready_at timestamptz;
