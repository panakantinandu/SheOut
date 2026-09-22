package com.sheout.booking;

/**
 * Public (unlike most other modules' *Error enums, which stay internal)
 * because {@link BookingApi} is a real cross-module interface - dispatch
 * will call {@code assignDriver} and needs to be able to interpret what
 * comes back, the same way a caller of any public method needs its
 * checked/declared failure type.
 */
public enum BookingError {
    CUSTOMER_NOT_VERIFIED,
    /**
     * A rider account with no verified phone number - one created through
     * Google that has not added one yet. Dispatch, SOS and support all reach
     * a rider on her number, so a trip is never booked without one.
     */
    CUSTOMER_PHONE_REQUIRED,
    /** Pickup or drop is outside the radius SheOut operates in - see ServiceArea. */
    OUTSIDE_SERVICE_AREA,
    CATEGORY_TYPE_MISMATCH,
    BOOKING_NOT_FOUND,

    /** Cancelling without saying why. See CancellationReason. */
    CANCELLATION_REASON_REQUIRED,

    /** Reason OTHER with no note - an answer that answers nothing. */
    CANCELLATION_NOTE_REQUIRED,
    INVALID_STATE_TRANSITION,

    /**
     * The partner typed a code, and it was not this booking's code. Said
     * plainly to her, because the alternative - a trip that silently does
     * not start - leaves her standing at a kerb with no idea why.
     */
    INVALID_PICKUP_CODE,

    /**
     * No code submitted at all. Distinct from a wrong one so an out-of-date
     * client gets told what it is missing rather than being accused of
     * getting it wrong.
     */
    PICKUP_CODE_REQUIRED,

    /** Too many wrong guesses on this booking. See PickupCode.MAX_ATTEMPTS. */
    PICKUP_VERIFICATION_LOCKED,

    /**
     * The rider has a trip that ended and is still unpaid, so she cannot
     * book another. Paying it - from the wallet or online - clears this.
     */
    UNPAID_TRIP,

    /**
     * She already has a live trip of this type - searching, matched, on the
     * way or under way. One at a time: every booking goes out to real
     * partners, and six at once from one account was six partners each
     * driving to a pickup for a trip that could only ever be one.
     */
    ACTIVE_BOOKING_EXISTS
}
