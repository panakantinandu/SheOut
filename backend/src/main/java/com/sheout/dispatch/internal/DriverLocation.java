package com.sheout.dispatch.internal;

import java.time.Instant;

/**
 * A driver's last known position, with the moment it was recorded.
 * <p>
 * recordedAt exists because Redis GEO stores a position and nothing else -
 * a bare lat/lng cannot tell a caller whether it arrived two seconds ago or
 * two hours ago. Tracking UIs poll this, so they need to be able to say
 * "last seen 40s ago" rather than draw a stale marker as though it were
 * live. This is the whole reason recordLocation writes a second key.
 */
public record DriverLocation(double lat, double lng, Instant recordedAt) {
}
