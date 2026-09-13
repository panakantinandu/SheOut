package com.sheout.ratings;

import com.sheout.auth.AccountRole;
import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * Published when somebody rates somebody, carrying the rated account's new
 * running average.
 * <p>
 * The average travels on the event rather than being recomputed by the
 * listener, so the figure that decides whether an account is put in front of
 * an operator is the same figure this module just calculated. Two modules
 * independently working out "the average" is how they end up disagreeing.
 * <p>
 * The rated account's role is here because the module that keeps profiles
 * has two of them and cannot tell which one this account has without asking
 * somebody.
 */
public class RatingSubmitted extends DomainEvent {

    private final UUID bookingId;
    private final UUID ratedAccountId;
    private final AccountRole ratedRole;
    private final int stars;
    private final double averageStars;
    private final int totalRatings;

    public RatingSubmitted(UUID bookingId, UUID ratedAccountId, AccountRole ratedRole,
                            int stars, double averageStars, int totalRatings) {
        this.bookingId = bookingId;
        this.ratedAccountId = ratedAccountId;
        this.ratedRole = ratedRole;
        this.stars = stars;
        this.averageStars = averageStars;
        this.totalRatings = totalRatings;
    }

    public UUID bookingId() {
        return bookingId;
    }

    public UUID ratedAccountId() {
        return ratedAccountId;
    }

    public AccountRole ratedRole() {
        return ratedRole;
    }

    /** The single score just given. The average is what matters; this is here for anything that wants the event itself. */
    public int stars() {
        return stars;
    }

    public double averageStars() {
        return averageStars;
    }

    public int totalRatings() {
        return totalRatings;
    }
}
