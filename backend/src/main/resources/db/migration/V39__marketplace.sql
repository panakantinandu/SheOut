-- SheOut Seller: a directory of women selling from home. See the marketplace
-- module. SheOut charges a flat one-time listing fee and takes no part in
-- any sale - there is no order, cart or delivery here, only who sells what
-- and how to reach her.

-- ---------------------------------------------------------------- payments
-- A payment is now for a trip (RIDE_FARE) or for a seller's listing fee
-- (SELLER_LISTING_FEE). Every existing row is a trip's.
alter table payments
    add column purpose varchar(30) not null default 'RIDE_FARE',
    -- Who paid a listing fee and which seller profile it is for. Null for a
    -- trip, whose payer is the rider on its booking.
    add column payer_account_id uuid,
    add column seller_id uuid unique;

-- A listing fee is no booking's.
alter table payments alter column booking_id drop not null;

alter table payments add constraint chk_payments_purpose_target check (
    (purpose = 'RIDE_FARE' and booking_id is not null and seller_id is null)
    or (purpose = 'SELLER_LISTING_FEE' and booking_id is null and seller_id is not null and payer_account_id is not null)
);

create index idx_payments_payer on payments (payer_account_id) where payer_account_id is not null;

-- ---------------------------------------------------------------- sellers
-- A seller is still a rider's account (CUSTOMER) with this attached - not a
-- new role. One per account.
create table seller_profiles (
    id                  uuid           primary key,
    account_id          uuid           not null unique,
    business_name       varchar(80)    not null,
    category            varchar(30)    not null,
    -- How customers reach her. WhatsApp when she gave a number for it,
    -- otherwise a phone call.
    contact_phone       varchar(15)    not null,
    whatsapp_number     varchar(15),
    status              varchar(30)    not null,
    -- The last decision's reason, shown to her: why it was rejected, or why
    -- a live listing was suspended.
    rejection_reason    varchar(500),
    suspension_reason   varchar(500),
    submitted_at        timestamptz,
    reviewed_at         timestamptz,
    reviewed_by         uuid,
    -- The fee in force when she was approved - what she is asked to pay even
    -- if the fee changes before she does.
    listing_fee_amount  numeric(10, 2),
    activated_at        timestamptz,
    suspended_at        timestamptz,
    -- The last time a live seller changed her profile or products, so
    -- operations can look at what changed after approval.
    edited_live_at      timestamptz,
    created_at          timestamptz    not null,
    updated_at          timestamptz    not null
);

create index idx_seller_profiles_status on seller_profiles (status, submitted_at);

create table seller_products (
    id              uuid            primary key,
    seller_id       uuid            not null references seller_profiles (id),
    title           varchar(100)    not null,
    description     varchar(2000)   not null,
    -- For information only. Nothing is ever charged in the app for it.
    display_price   numeric(10, 2)  not null,
    active          boolean         not null,
    created_at      timestamptz     not null,
    updated_at      timestamptz     not null
);

create index idx_seller_products_seller on seller_products (seller_id);

-- Photos, stored through DocumentStorage like every other image. seller_id
-- is kept on each row so her total can be counted against the cap without
-- a join.
create table seller_product_images (
    id              uuid            primary key,
    product_id      uuid            not null references seller_products (id) on delete cascade,
    seller_id       uuid            not null references seller_profiles (id),
    storage_key     varchar(500)    not null,
    position        int             not null,
    created_at      timestamptz     not null,
    updated_at      timestamptz     not null
);

create index idx_seller_product_images_product on seller_product_images (product_id, position);
create index idx_seller_product_images_seller on seller_product_images (seller_id);
