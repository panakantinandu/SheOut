-- SheOut Seller becomes a monthly plan: the first month free from the day a
-- person approves her shop, then a flat monthly fee (499 by default) that
-- keeps her in the directory. See the marketplace module's SellerPlan.

-- ---------------------------------------------------------------- payments
-- A seller now pays once a month, so one seller has many fee payments. Each
-- says which month it pays for: seller_period 1 is her first paid month, 2
-- her second, and so on. The one-time fee paid before this release is
-- period 0.
alter table payments add column seller_period integer;
update payments set seller_period = 0 where purpose = 'SELLER_LISTING_FEE';

alter table payments drop constraint payments_seller_id_key;
create unique index uq_payments_seller_period on payments (seller_id, seller_period) where seller_id is not null;

alter table payments drop constraint chk_payments_purpose_target;
alter table payments add constraint chk_payments_purpose_target check (
    (purpose = 'RIDE_FARE' and booking_id is not null and seller_id is null and seller_period is null)
    or (purpose = 'SELLER_LISTING_FEE' and booking_id is null and seller_id is not null and payer_account_id is not null
        and seller_period is not null)
);

-- ---------------------------------------------------------------- sellers
alter table seller_profiles
    -- Her shop is in the directory until this moment (plus a few days'
    -- grace). Null until a person has approved her.
    add column paid_until timestamptz,
    -- The end of her free first month - kept so the app can say "free until".
    add column free_until timestamptz,
    -- How many months she has paid for; the next payment is for period
    -- paid_periods + 1.
    add column paid_periods integer not null default 0,
    -- The last reminder sent for the current period, so none is sent twice.
    -- Cleared when she pays.
    add column last_plan_notice varchar(20);

-- Shops already live paid the old one-time fee: they get the free month
-- from today, like every new shop. A shop approved and waiting to pay goes
-- live now on its free month - nobody pays to start any more. A suspended
-- shop that had gone live gets the same month, used if it is reinstated.
update seller_profiles
   set paid_until = now() + interval '1 month',
       free_until = now() + interval '1 month'
 where status in ('ACTIVE', 'SUSPENDED') and activated_at is not null;

update seller_profiles
   set status = 'ACTIVE',
       activated_at = now(),
       paid_until = now() + interval '1 month',
       free_until = now() + interval '1 month'
 where status = 'APPROVED_AWAITING_PAYMENT';

create index idx_seller_profiles_plan on seller_profiles (status, paid_until);
