alter table delivery_payment add column if not exists fulfillment_mode varchar(32) not null default 'DELIVERY';
alter table delivery_order add column if not exists fulfillment_mode varchar(32) not null default 'DELIVERY';
