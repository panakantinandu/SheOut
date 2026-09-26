package com.sheout.booking;

/**
 * Why a partner ended a trip away from the drop the rider booked.
 * <p>
 * Asked for, never used to refuse her: a rider asking to be let out early,
 * or a road that is shut, are ordinary, and ending the trip must stay
 * possible. What the reason buys is a record - kept on the booking whichever
 * one she gives, and shown beside the trip if it comes up for review.
 * <p>
 * Names are stored; add new ones rather than renaming.
 */
public enum DropOffDeviationReason {
    CUSTOMER_REQUESTED_DIFFERENT_DROP,
    ROAD_CLOSED_OR_BLOCKED,
    OTHER;

    /** OTHER says nothing on its own, the same rule as a cancellation's OTHER. */
    public boolean requiresNote() {
        return this == OTHER;
    }
}
