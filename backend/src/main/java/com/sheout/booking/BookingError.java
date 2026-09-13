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
    PICKUP_VERIFICATION_LOCKED
}
