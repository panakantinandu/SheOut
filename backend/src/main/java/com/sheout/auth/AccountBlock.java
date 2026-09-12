package com.sheout.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * The audit behind a block: when, by whom, and on what grounds.
 * <p>
 * Separate from {@link AccountSummary}, which carries only the boolean.
 * That split is deliberate and not tidiness: dispatch re-reads the blocked
 * flag on every offer and every accept, and it has no use for the reason
 * text, so the summary stays small on the hot path. The detail is a second
 * read, paid only by the ops console that actually displays it.
 * <p>
 * reason is free text an admin was required to supply. It is an operational
 * note and is never returned to the blocked account holder - see
 * AuthController's ACCOUNT_BLOCKED mapping.
 */
public record AccountBlock(
        Instant blockedAt,
        UUID blockedByAccountId,
        String reason
) {
}
