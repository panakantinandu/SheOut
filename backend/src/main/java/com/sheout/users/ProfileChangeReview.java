package com.sheout.users;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One change waiting for an operator: what riders see now beside what the
 * partner asked for, and the new registration certificate when the vehicle
 * changed. Requested fields are null where she changed nothing.
 */
public record ProfileChangeReview(
        UUID changeId,
        UUID accountId,
        String phoneNumber,
        String currentName,
        LocalDate currentDateOfBirth,
        VehicleType currentVehicleType,
        String currentVehicleRegistrationNumber,
        String currentPhotoUrl,
        String requestedName,
        LocalDate requestedDateOfBirth,
        VehicleType requestedVehicleType,
        String requestedVehicleRegistrationNumber,
        String requestedPhotoUrl,
        String rcDocumentUrl,
        Instant requestedAt
) {

    public boolean vehicleChanged() {
        return requestedVehicleType != null || requestedVehicleRegistrationNumber != null;
    }
}
