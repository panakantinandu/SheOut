package com.sheout.admin.internal;

import com.sheout.auth.AccountRole;
import com.sheout.users.TrustStats;

import java.time.Instant;
import java.util.UUID;

/**
 * One account waiting on a human decision about whether something is wrong
 * with it, as an operator needs to see it.
 * <p>
 * One row per account, not one per reason. An account that cancels too often
 * AND is rated badly is a single case with two facts in it, and listing it
 * twice would make the queue look longer than it is and let half of it be
 * cleared while the other half quietly stayed open.
 * <p>
 * Shaped like ReviewQueueRow on purpose. This is the same kind of screen as
 * the driver-verification queue - a list of accounts waiting on a person -
 * and an operator should not have to learn a second one to work it.
 * <p>
 * The numbers travel with the row rather than being fetched per account,
 * because the whole point of the queue is that the decision is made from
 * them. Showing "flagged" without "cancelled 6 of 9" and "rated 2.4 across
 * 11" would hand an operator a verdict and ask them to rubber-stamp it.
 * <p>
 * blocked is included so an account already blocked for some other reason
 * does not read as an open case.
 */
public record TrustReviewRow(
        UUID accountId,
        String name,
        String phoneNumber,
        AccountRole role,
        TrustStats trustStats,
        Instant flaggedAt,
        String flaggedReason,
        boolean blocked
) {
}
