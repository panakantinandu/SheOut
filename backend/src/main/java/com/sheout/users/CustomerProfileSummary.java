package com.sheout.users;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code phoneNumber} is composed from auth's {@code AuthApi} at read time,
 * not stored here - see the module README note on why this module never
 * persists a phone number of its own.
 * <p>
 * {@code verified} is a cache updated from driver-verification's
 * {@code AccountVerified} event (see {@code UserProfileEventListeners}) -
 * convenient for display, but nothing safety-critical should gate on it;
 * see {@code DriverProfileService.setOnlineStatus} for the one place that
 * matters, which re-checks live instead.
 */
public record CustomerProfileSummary(
        UUID accountId,
        String name,
        String phoneNumber,
        String homeAddress,
        String workAddress,
        boolean verified,
        Instant updatedAt
) {
}
