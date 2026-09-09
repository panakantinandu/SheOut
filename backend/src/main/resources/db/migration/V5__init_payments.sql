-- booking_id is unique - one payment row per booking, the idempotency
-- backstop for a retried/duplicate BookingCompleted delivery (see
-- PaymentService.createPendingPayment). razorpay_order_id is unique too
-- (but nullable - always null for a CASH payment, and Postgres allows
-- multiple NULLs under a unique constraint) since it's how a webhook
-- looks the row back up.
create table payments (
    id                   uuid primary key,
    booking_id           uuid            not null unique,
    amount               numeric(10, 2)  not null,
    method               varchar(20)     not null,
    status               varchar(20)     not null,
    razorpay_order_id    varchar(255)    unique,
    razorpay_payment_id  varchar(255),
    failure_reason       varchar(500),
    captured_at          timestamp,

    created_at           timestamp       not null,
    updated_at           timestamp       not null
);

create index idx_payments_status on payments (status);
