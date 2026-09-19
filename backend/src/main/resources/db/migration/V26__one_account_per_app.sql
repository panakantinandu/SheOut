-- One account per phone number PER APP, not per phone number.
--
-- A number used to own exactly one account, of one role. Entering a
-- partner's number in the rider app sent a code, took it, and only then
-- refused with "registered under a different role" - and going back to try
-- again ran into the resend cooldown. The rider and partner apps collided
-- over a single identity.
--
-- Now a number can hold a rider account and a partner account side by side,
-- each signed into only by its own app, which is how ride-hailing apps
-- generally work. Nothing about either account is visible to the other.
--
-- Partial indexes, because a deleted account's phone and email are set to
-- null (see AccountEntity.markDeleted), and Google-created accounts have no
-- phone at all.
alter table accounts drop constraint if exists accounts_phone_number_key;
alter table accounts drop constraint if exists accounts_email_key;

create unique index uq_accounts_phone_role on accounts (phone_number, role) where phone_number is not null;
create unique index uq_accounts_email_role on accounts (email, role) where email is not null;
