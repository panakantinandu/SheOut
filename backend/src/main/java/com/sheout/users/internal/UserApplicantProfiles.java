package com.sheout.users.internal;

import com.sheout.driververification.ApplicantProfiles;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Hands driver-verification's automatic checks what they need from a
 * profile - see ApplicantProfiles for why the interface lives there. A
 * partner's profile first, then a rider's: an account has one or the other.
 * Only whether a PAN is on file leaves this module, never the PAN.
 */
@Component
class UserApplicantProfiles implements ApplicantProfiles {

    private final DriverProfileRepository drivers;
    private final CustomerProfileRepository customers;

    UserApplicantProfiles(DriverProfileRepository drivers, CustomerProfileRepository customers) {
        this.drivers = drivers;
        this.customers = customers;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ApplicantProfile> find(UUID accountId) {
        return drivers.findByAccountId(accountId)
                .map(d -> new ApplicantProfile(d.getName(), d.getDateOfBirth(),
                        d.getVehicleType() == null ? null : d.getVehicleType().name(),
                        d.getVehicleRegistrationNumber(), d.getPanNumber() != null && !d.getPanNumber().isBlank()))
                .or(() -> customers.findByAccountId(accountId)
                        .map(c -> new ApplicantProfile(c.getName(), c.getDateOfBirth(), null, null, false)));
    }
}
