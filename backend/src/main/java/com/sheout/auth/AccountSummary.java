package com.sheout.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only view of an account for other modules (e.g. driver-verification
 * showing an admin who they're reviewing). Never exposes anything other
 * modules shouldn't see - just enough to identify the account.
 */
public record AccountSummary(
        UUID id,
        String phoneNumber,
        AccountRole role,
        Instant createdAt
) {
}
