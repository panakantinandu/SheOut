package com.sheout.users;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A partner's own view of a change to her identity details waiting for an
 * operator - or one just turned down, with the reason, so she knows why
 * nothing changed. Only the fields she changed are set.
 *
 * @param status PENDING, or REJECTED for a recent refusal
 */
public record PendingProfileChange(
        String status,
        String name,
        LocalDate dateOfBirth,
        VehicleType vehicleType,
        String vehicleRegistrationNumber,
        String photoUrl,
        boolean photoChanged,
        boolean rcDocumentAttached,
        boolean rcDocumentRequired,
        Instant requestedAt,
        String decisionNote
) {
}
