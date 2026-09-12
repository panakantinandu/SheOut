package com.sheout.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only view of an account for other modules (e.g. driver-verification
 * showing an admin who they're reviewing). Never exposes anything other
 * modules shouldn't see - just enough to identify the account.
 * <p>
 * Exactly one of phoneNumber/email is non-null, depending on whether this
 * account was created via phone+OTP or Google sign-in - see AccountEntity.
 * <p>
 * blocked is here rather than left to a separate lookup because the callers
 * that need it need it on every request: dispatch re-checks it live on
 * every offer and every accept, and a second round trip per driver per
 * offer round would be paid on the hot path.
 */
public record AccountSummary(
        UUID id,
        String phoneNumber,
        String email,
        AccountRole role,
        Instant createdAt,
        boolean blocked
) {
}
