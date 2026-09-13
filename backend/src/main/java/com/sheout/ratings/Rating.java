package com.sheout.ratings;

import com.sheout.auth.AccountRole;

import java.time.Instant;
import java.util.UUID;

/**
 * One rating slot: who may rate whom for which trip, and what they said if
 * they have said anything yet.
 * <p>
 * The slot exists from the moment the trip completes, so an unrated trip is
 * a row with stars still null rather than the absence of a row. That is what
 * lets a client ask "is there anything waiting for me" and "did I already
 * rate this" without either app having to reason about booking statuses.
 */
public record Rating(
        UUID id,
        UUID bookingId,
        UUID raterAccountId,
        UUID ratedAccountId,
        AccountRole raterRole,
        Integer stars,
        String comment,
        Instant submittedAt,
        Instant rateableUntil
) {

    public boolean submitted() {
        return stars != null;
    }

    /** Still waiting, and still allowed. What a client should actually prompt for. */
    public boolean open(Instant now) {
        return !submitted() && now.isBefore(rateableUntil);
    }
}
