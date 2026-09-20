-- Whether she has been shown the introduction, so it is shown once.
--
-- Backfilled for every profile that exists now: they have been using the app
-- for weeks, and an introduction to something you already use is not a
-- welcome, it is an obstacle. Only accounts created from here on start with
-- null and see it.
alter table customer_profiles add column onboarding_seen_at timestamptz;

update customer_profiles set onboarding_seen_at = now();
