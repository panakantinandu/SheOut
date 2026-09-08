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
    CATEGORY_TYPE_MISMATCH,
    BOOKING_NOT_FOUND,
    INVALID_STATE_TRANSITION
}
