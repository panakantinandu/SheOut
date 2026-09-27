-- Two ways to pay that did not exist.
--
-- 1. A partner's UPI QR. When a trip ends, the partner can show a QR for
--    exactly the fare; the rider scans it with PhonePe, Google Pay, Paytm or
--    any UPI app. It is a Razorpay single-use QR, so the money still goes
--    through SheOut into the partner's wallet with a record behind it - it
--    is not the partner's personal UPI ID. One live QR per payment at a time.
alter table payments
    add column razorpay_qr_id      varchar(64) unique,
    add column qr_image_url        varchar(500),
    add column qr_expires_at       timestamptz;

-- 2. A seller's listing fee paid from her SheOut wallet. The wallet entry
--    points at the payment it paid, and pays it once however often the
--    request is retried.
alter table rider_wallet_entries add column payment_id uuid;

create unique index uq_rider_wallet_entries_listing_fee
    on rider_wallet_entries (payment_id) where entry_type = 'LISTING_FEE';
