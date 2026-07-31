alter table delivery_payment
  add column if not exists fee_cents integer,
  add column if not exists currency varchar(16),
  add column if not exists quote_id varchar(255);
