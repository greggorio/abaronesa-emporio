create table if not exists delivery_payment (
    id bigserial primary key,
    status varchar(32) not null,
    amount_cents integer not null,
    qr_payload text,
    provider_reference varchar(255),
    created_at timestamptz default now() not null,
    updated_at timestamptz default now()
);

create table if not exists delivery_order (
    id bigserial primary key,
    external_id varchar(255),
    status varchar(32) not null,
    customer_name varchar(255),
    customer_phone varchar(64),
    customer_email varchar(255),
    dropoff_address text,
    dropoff_notes text,
    uber_delivery_id varchar(255),
    uber_quote_id varchar(255),
    uber_tracking_url text,
    uber_fee_cents integer,
    uber_currency varchar(16),
    payment_id bigint references delivery_payment(id),
    created_at timestamptz default now() not null,
    updated_at timestamptz default now()
);

create table if not exists delivery_order_item (
    id bigserial primary key,
    delivery_order_id bigint references delivery_order(id),
    produto_id bigint,
    sku_id bigint,
    nome varchar(255),
    quantidade integer,
    observacoes text,
    size varchar(16),
    status varchar(32) not null,
    estacao varchar(16),
    created_at timestamptz default now() not null,
    updated_at timestamptz default now()
);

create table if not exists delivery_order_event (
    id bigserial primary key,
    delivery_id varchar(255),
    delivery_order_id bigint references delivery_order(id),
    kind varchar(128),
    status varchar(64),
    payload jsonb,
    created_at timestamptz default now() not null
);

create index if not exists idx_delivery_order_delivery_id on delivery_order(uber_delivery_id);
create index if not exists idx_delivery_order_item_status on delivery_order_item(status);
create index if not exists idx_delivery_order_event_delivery_id on delivery_order_event(delivery_id);
