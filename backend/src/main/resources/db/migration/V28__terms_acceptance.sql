-- When she agreed to the Terms and the Privacy Policy, and to which version.
--
-- Until now consent was implied: the sign-in screen said that tapping the
-- button meant agreement. That is a claim about what somebody did, with
-- nothing recorded to show it - no date, no version, nothing to answer
-- "what did she actually agree to, and when" with.
--
-- Both columns stay null for accounts that signed up before this existed.
-- Null means "no record", which is the truth about them, and is not the same
-- as a recorded refusal.
alter table accounts add column terms_accepted_at timestamptz;
alter table accounts add column terms_version varchar(40);
