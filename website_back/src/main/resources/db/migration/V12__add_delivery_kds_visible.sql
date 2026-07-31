alter table delivery_order_item
  add column if not exists kds_visible boolean not null default false;
