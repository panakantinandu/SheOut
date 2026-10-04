-- Stock that keeps itself right, as far as SheOut can see a sale.
--
-- A Marketplace parcel names the product it carries and how many: when it
-- is delivered, the seller's stock goes down by that many (an opaque
-- reference here - the booking module knows nothing of products).
alter table bookings
    add column order_product_id uuid,
    add column order_quantity   integer,
    add constraint chk_bookings_order_quantity check (order_quantity is null or order_quantity between 1 and 20);

-- A buyer tapped Call or WhatsApp on a product: an enquiry, once per buyer,
-- product and day. Counted for the seller ("3 enquiries today") and behind
-- the evening "sold any?" reminder; who asked is never shown to her.
create table product_enquiries (
    product_id  uuid        not null,
    account_id  uuid        not null,
    day         date        not null,
    created_at  timestamptz not null default now(),
    primary key (product_id, account_id, day)
);
create index idx_product_enquiries_day on product_enquiries (day, product_id);

-- The day she was last sent the "sold any?" reminder - at most one a day.
alter table seller_profiles add column last_enquiry_nudge date;
