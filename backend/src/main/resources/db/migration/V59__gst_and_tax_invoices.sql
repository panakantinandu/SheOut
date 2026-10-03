-- GST on captured payments, and tax invoices. Nothing here does anything
-- until GST_ENABLED is true, which must wait for SheOut's GSTIN and its CA's
-- sign-off on the rates (see the README): every column stays null and no
-- invoice is issued.
--
-- Prices are tax-inclusive. The rider pays exactly the price she was shown;
-- at capture it is split into the taxable value and the tax inside it, and
-- the split is stored on the payment with the rate that produced it.
alter table payments add column taxable_value numeric(10, 2);
alter table payments add column tax_amount numeric(10, 2);
alter table payments add column tax_rate_percent numeric(5, 2);
alter table payments add column tax_category varchar(30);

-- Invoice numbers must run without gaps within a financial year
-- (SO/2026-27/000001, 000002, ...). A database sequence would leave a gap on
-- every rolled-back capture; this row is locked and incremented inside the
-- capturing transaction instead, so a number is used only if the capture
-- commits.
create table tax_invoice_sequences (
    financial_year varchar(7)  primary key,
    next_value     bigint      not null,
    created_at     timestamptz not null,
    updated_at     timestamptz not null
);

create table tax_invoices (
    id                uuid primary key,
    invoice_number    varchar(30)    not null unique,
    financial_year    varchar(7)     not null,
    sequence_number   bigint         not null,
    payment_id        uuid           not null unique,
    booking_id        uuid,
    issued_at         timestamptz    not null,
    supplier_gstin    varchar(15)    not null,
    supplier_name     varchar(200),
    place_of_supply   varchar(60)    not null,
    sac_code          varchar(10)    not null,
    description       varchar(200)   not null,
    recipient_account_id uuid,
    recipient_name    varchar(150),
    taxable_value     numeric(10, 2) not null,
    tax_rate_percent  numeric(5, 2)  not null,
    cgst_amount       numeric(10, 2) not null,
    sgst_amount       numeric(10, 2) not null,
    igst_amount       numeric(10, 2) not null,
    total_amount      numeric(10, 2) not null,
    created_at        timestamptz    not null,
    updated_at        timestamptz    not null,
    unique (financial_year, sequence_number)
);

create index idx_tax_invoices_booking on tax_invoices (booking_id);
