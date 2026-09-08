package com.sheout.booking;

/**
 * See {@code BookingStateMachine} (internal) for the single place valid
 * transitions between these are decided. CANCELLED is reachable from
 * REQUESTED, MATCHED, or ACCEPTED - not from IN_PROGRESS or COMPLETED.
 */
public enum BookingStatus {
    REQUESTED,
    MATCHED,
    ACCEPTED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
