package com.sheout.users;

import java.util.Optional;
import java.util.UUID;

public interface DriverProfileApi {

    Optional<DriverProfileSummary> findByAccountId(UUID accountId);

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
}
