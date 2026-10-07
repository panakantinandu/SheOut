package com.sheout.driververification;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * What the automatic checks need from her profile, which the users module
 * owns: her name, date of birth, vehicle, and whether she gave a PAN.
 * <p>
 * Declared here and implemented by users, because users already depends on
 * this module; calling users' API from here would make each depend on the
 * other. The PAN itself never crosses, only whether one is on file.
 */
public interface ApplicantProfiles {

    Optional<ApplicantProfile> find(UUID accountId);

    /** vehicleType is users' VehicleType by name (BIKE, AUTO, CAB), or null. */
    record ApplicantProfile(String name, LocalDate dateOfBirth, String vehicleType, String vehicleRegistrationNumber,
                            boolean panGiven) {
    }
}
