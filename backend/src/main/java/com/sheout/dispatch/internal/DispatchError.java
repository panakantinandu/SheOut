package com.sheout.dispatch.internal;

public enum DispatchError {
    /** No pending offer exists for this driver/booking pair - never offered, already resolved, or expired. */
    OFFER_NOT_FOUND,

    /** Re-checked at accept time (the race the spec calls out explicitly) - driver went OFFLINE since being selected. */
    DRIVER_NO_LONGER_ELIGIBLE,

    /** Another driver's accept already won the race for this booking. */
    BOOKING_ALREADY_ASSIGNED,

    /** This driver won the race, but BookingApi.assignDriver itself failed (e.g. the booking was cancelled in the meantime). */
    ASSIGNMENT_FAILED
}
