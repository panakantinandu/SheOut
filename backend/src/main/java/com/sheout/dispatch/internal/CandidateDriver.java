package com.sheout.dispatch.internal;

import java.util.UUID;

/**
 * A driver found nearby, before eligibility filtering (online status,
 * vehicle type) is applied. Public (within this module's internal
 * package tree) because it's shared across internal.redis and
 * internal.matching, both different sub-packages from this one.
 */
public record CandidateDriver(UUID driverId, double distanceKm) {
}
