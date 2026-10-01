package com.sheout.booking;

/**
 * Why a booking was cancelled. A fixed set, because free text alone cannot
 * be counted, compared, or acted on - and the whole point of recording a
 * reason is to tell a rider who changed their mind apart from one whose
 * partner never arrived.
 * <p>
 * Some of these are about the other party rather than the canceller. That
 * is deliberate: DRIVER_TAKING_TOO_LONG from a rider and
 * CUSTOMER_NOT_AT_PICKUP from a partner are the two sides of the same
 * failure, and an operator reading a flagged account needs to see which one
 * is being claimed.
 * <p>
 * OTHER requires a note - see BookingService.cancelBooking. An "Other" with
 * nothing after it is the same as no reason at all, dressed up as an answer.
 */
public enum CancellationReason {

    /** Rider or partner simply no longer needs the trip. */
    CHANGE_OF_PLANS,

    /** Rider waited too long for an assigned partner. */
    DRIVER_TAKING_TOO_LONG,

    /** Rider travelled another way instead. */
    FOUND_ANOTHER_RIDE,

    /** The pickup point was wrong, so the trip could not start. */
    WRONG_PICKUP_LOCATION,

    /** Partner could not find the rider at the pickup point. */
    CUSTOMER_NOT_AT_PICKUP,

    /** Partner cannot complete it - vehicle trouble, an emergency. */
    DRIVER_UNAVAILABLE,

    /**
     * Either side, at the pickup: the person there is not the one the app
     * showed - a different face, a different number plate, a man. Never
     * counted against whoever cancelled; the other account goes to an
     * operator instead. See UserProfileEventListeners.
     */
    IDENTITY_MISMATCH,
    /** The vehicle or its number is not the one the app showed. Handled like IDENTITY_MISMATCH. */
    WRONG_VEHICLE,
    /** She does not feel safe going ahead - either side. Never held against whoever says it. */
    SAFETY_CONCERN,

    /** Anything else. Requires a note. */
    OTHER;

    public boolean requiresNote() {
        return this == OTHER;
    }

    /** Reasons that say the other person or vehicle is not right or not safe. */
    public boolean isSafetyReport() {
        return this == IDENTITY_MISMATCH || this == WRONG_VEHICLE || this == SAFETY_CONCERN;
    }

    /** May a rider give this reason? Never one that is a partner's account of her. */
    public boolean riderMayGive() {
        return this != CUSTOMER_NOT_AT_PICKUP && this != DRIVER_UNAVAILABLE;
    }

    /** May a partner give this reason? Never one that is a rider's account of the partner. */
    public boolean partnerMayGive() {
        return this != DRIVER_TAKING_TOO_LONG && this != FOUND_ANOTHER_RIDE && this != CHANGE_OF_PLANS
                && this != WRONG_VEHICLE;
    }
}
