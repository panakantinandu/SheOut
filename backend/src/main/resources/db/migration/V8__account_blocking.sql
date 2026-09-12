-- Blocking an account.
--
-- Modelled as a nullable timestamp plus who and why, not as a boolean and
-- not as a new value on AccountRole. Two reasons.
--
-- The boolean would hold less than it needs to: blocking a woman off a
-- women's safety platform is a consequential, contestable act, and "who
-- decided this, when, and on what grounds" has to be answerable months
-- later. blocked_at IS the flag, exactly as resolved_at is the flag for an
-- SOS alert (V6), and the audit travels with it in the same row.
--
-- A role value would be worse still: role is the account's identity on the
-- platform and is otherwise fixed for its lifetime (see AccountEntity), so
-- overwriting it to block someone would destroy the fact that they were a
-- driver, and unblocking would have to guess what they used to be.
ALTER TABLE accounts
    ADD COLUMN blocked_at    TIMESTAMPTZ,
    ADD COLUMN blocked_by    UUID,
    ADD COLUMN block_reason  VARCHAR(500);

-- Every admin list of accounts filters on this, and the login path checks it
-- on every sign-in.
CREATE INDEX idx_accounts_blocked_at ON accounts (blocked_at);

COMMENT ON COLUMN accounts.blocked_at IS
    'When this account was blocked. NULL means active. Set/cleared only by an admin - see AdminAccountService.';
COMMENT ON COLUMN accounts.blocked_by IS
    'The admin account that blocked this one. Kept after unblocking is NOT required - unblock clears all three, and the history lives in the block_reason of the next block. See AccountEntity.unblock.';
COMMENT ON COLUMN accounts.block_reason IS
    'Free text the admin had to supply. Operational notes - never shown to the blocked account holder.';
