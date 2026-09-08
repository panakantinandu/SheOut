package com.sheout.users;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code phoneNumber} is composed from auth's {@code AuthApi} at read time,
 * not stored here. {@code verified} is a cache from the {@code AccountVerified}
 * event - display-only; the actual online-status gate re-checks live via
 * driver-verification's {@code VerificationApi}. See {@link OnlineStatus}.
 */
public record DriverProfileSummary(
        UUID accountId,
        String name,
        String phoneNumber,
        VehicleType vehicleType,
        String vehicleRegistrationNumber,
        OnlineStatus onlineStatus,
        boolean verified,
        Instant updatedAt
) {
}
