package com.sheout.booking;

import java.time.Instant;
import java.util.UUID;

/**
 * One line of a trip's audit log: what happened, between which states, who
 * did it (null for the system), and from which request and device.
 */
public record BookingEvent(
        String event,
        String fromStatus,
        String toStatus,
        UUID actorId,
        String actorRole,
        String detail,
        String requestId,
        String device,
        Instant at
) {
}
