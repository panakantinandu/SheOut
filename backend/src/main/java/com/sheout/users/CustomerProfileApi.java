package com.sheout.users;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerProfileApi {

    Optional<CustomerProfileSummary> findByAccountId(UUID accountId);

    /**
     * Every rider whose cancellation rate has crossed the configured
     * threshold, oldest crossing first.
     * <p>
     * A queue for a person to work through, which is the whole design: the
     * threshold raises a flag and stops. Nothing here is blocked, throttled
     * or charged by crossing it, for the same reason driver verification
     * never auto-approves - a rate has two opposite explanations and only a
     * human reading the reasons can tell which one this is.
     */
    List<CustomerProfileSummary> findFlaggedForReview();

    /** An operator has reviewed this account and is satisfied. Idempotent. */
    void clearReviewFlag(UUID accountId);
}
