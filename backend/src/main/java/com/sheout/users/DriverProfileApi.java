package com.sheout.users;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverProfileApi {

    Optional<DriverProfileSummary> findByAccountId(UUID accountId);

    /**
     * Every partner whose cancellation rate has crossed the configured
     * threshold, oldest crossing first. See
     * {@link CustomerProfileApi#findFlaggedForReview()} for why this raises
     * a flag rather than acting on it.
     */
    List<DriverProfileSummary> findFlaggedForReview();

    /** Email addresses of partners, a page at a time - see CustomerProfileApi.findEmailAddresses. */
    List<String> findEmailAddresses(int page, int size);

    /** An operator has reviewed this account and is satisfied. Idempotent. */
    void clearReviewFlag(UUID accountId);

    /**
     * Whether this driver's verification is VERIFIED on both checks right
     * now, read live from driver-verification at the moment of the call.
     * <p>
     * This exists so dispatch can re-check verification without taking on a
     * VerificationApi dependency of its own - DispatchService is
     * deliberately limited to BookingApi and DriverProfileApi. It also
     * keeps the rule itself in one place: the same module that gates going
     * ONLINE decides who stays eligible, so the two cannot drift apart.
     * <p>
     * Deliberately stricter than the ONLINE gate: it does NOT honour
     * sheout.testing.verified-driver-bypass-phone. That bypass exists to
     * let a QA account toggle itself online without an admin review, and
     * that is as far as it should reach. A driver who is not actually
     * verified must never be offered a real customer's booking, which is
     * the entire point of the check. Do not use DriverProfileSummary's
     * `verified` field for this - it is a cached projection of an
     * AccountVerified event and can be stale, which is exactly the bug
     * this method was added to close.
     */
    boolean isCurrentlyVerified(UUID accountId);

    /** Verified partners' changes to their identity details, waiting for an operator, oldest first. */
    List<ProfileChangeReview> findPendingProfileChanges();

    /**
     * An operator's decision on one. Approving applies it; turning it down
     * needs a note, which she is shown. Failure names why (no such pending
     * change, a vehicle change without its RC, a refusal without a note).
     */
    com.sheout.sharedkernel.Result<Void, ProfileChangeDecisionError> decideProfileChange(UUID changeId, boolean approve, UUID adminAccountId, String note);
}
