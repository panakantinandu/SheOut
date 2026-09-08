-- A Google-created account has no phone number at signup time (the
-- self-service PUT /users/customer/me flow never collects one either) -
-- phone_number's existing table-level UNIQUE constraint already allows
-- multiple NULLs in Postgres, so no extra partial-index handling is needed.
alter table accounts alter column phone_number drop not null;

-- Same reasoning in the other direction for a phone-signup account - email
-- is unknown until/unless it signs in with Google. Case is normalized to
-- lowercase before storage/lookup (see AccountEntity/AuthService), so the
-- unique constraint is effectively case-insensitive.
alter table accounts add column email varchar(255) unique;
