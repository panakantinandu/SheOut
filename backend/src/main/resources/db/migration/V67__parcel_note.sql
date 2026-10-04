-- What is in a parcel, in the sender's words - "Marketplace order 7K9M2XQ:
-- silk saree" - shown to the partner who carries it. Deliveries only; null
-- for every ride and every parcel sent without one.
alter table bookings add column parcel_note varchar(300);
