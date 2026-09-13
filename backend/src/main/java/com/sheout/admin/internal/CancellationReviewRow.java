package com.sheout.admin.internal;

import com.sheout.auth.AccountRole;
import com.sheout.users.CancellationStats;

import java.time.Instant;
import java.util.UUID;

/**
 * One account whose cancellation rate crossed the review threshold, as an
 * operator needs to see it.
 * <p>
 * Shaped like ReviewQueueRow on purpose. This is the same kind of thing as
 * the driver-verification queue - a list of accounts waiting on a human
 * decision - and an operator should not have to learn a second screen to
 * work it.
 * <p>
 * The numbers travel with the row rather than being fetched per account,
 * because the whole point of the queue is that the decision is made from
 * them. Showing "flagged" without "cancelled 6 of 9" would hand an operator
 * a verdict and ask them to rubber-stamp it.
 * <p>
 * blocked is included so an account already blocked for some other reason
 * does not read as an open case.
 */
public record CancellationReviewRow(
        UUID accountId,
        String name,
        String phoneNumber,
        AccountRole role,
        CancellationStats cancellationStats,
        Instant flaggedAt,
        String flaggedReason,
        boolean blocked
) {
}
