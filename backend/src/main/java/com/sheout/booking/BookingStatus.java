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
    CANCELLED,

    /**
     * Dispatch searched and found nobody, and has stopped searching.
     * <p>
     * Deliberately its own status rather than reusing either neighbour.
     * <p>
     * Not CANCELLED, because nobody cancelled. Folding the two together
     * would tell a rider she called this off when the platform failed her,
     * and it would put a cancellation on her record - which now feeds the
     * rate that flags accounts for review. Charging somebody's trust score
     * for our inability to find a driver would be exactly backwards.
     * <p>
     * Not REQUESTED either. A booking sitting in REQUESTED means the search
     * is still running, and leaving one there forever is what this status
     * exists to stop: a spinner with no end, on a screen that had no way to
     * tell "still looking" from "gave up an hour ago".
     * <p>
     * A dead end. Retrying means a fresh booking, not reviving this record,
     * because the pickup and drop on it were accurate ninety seconds ago and
     * may not be now - the rider may have walked on, or changed her mind
     * about where she is going.
     */
    NO_DRIVERS_AVAILABLE
}
