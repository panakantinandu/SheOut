package com.sheout.dispatch;

import java.util.Optional;
import java.util.UUID;

/**
 * Where a partner last reported being - the one piece of dispatch state
 * another module may read.
 * <p>
 * Booking needs it to decide whether a partner is really at the pickup
 * before the trip starts and at the drop before it ends. It used to import
 * dispatch's Redis store directly, which is the cross-module reach into
 * {@code internal} this codebase's module rule forbids.
 */
public interface DriverLocationApi {

    /** Last reported position, or empty if she has never reported one (or went offline since). */
    Optional<DriverLocation> findLocation(UUID driverId);
}
