package com.sheout.users.internal;

import com.sheout.auth.AccountSummary;
import com.sheout.auth.AuthApi;
import com.sheout.driververification.VerificationApi;
import com.sheout.driververification.VerificationStatus;
import com.sheout.driververification.VerificationSummary;
import com.sheout.sharedkernel.Result;
import com.sheout.users.DriverProfileApi;
import com.sheout.users.DriverProfileSummary;
import com.sheout.users.OnlineStatus;
import com.sheout.users.VehicleType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class DriverProfileService implements DriverProfileApi {

    private final DriverProfileRepository driverProfileRepository;
    private final AuthApi authApi;
    private final VerificationApi verificationApi;

    public DriverProfileService(DriverProfileRepository driverProfileRepository,
                                 AuthApi authApi,
                                 VerificationApi verificationApi) {
        this.driverProfileRepository = driverProfileRepository;
        this.authApi = authApi;
        this.verificationApi = verificationApi;
    }

    @Override
    public Optional<DriverProfileSummary> findByAccountId(UUID accountId) {
        return driverProfileRepository.findByAccountId(accountId).map(this::toSummary);
    }

    @Transactional
    public Result<DriverProfileSummary, DriverProfileError> updateProfile(
            UUID accountId, String name, VehicleType vehicleType, String vehicleRegistrationNumber) {
        Optional<DriverProfileEntity> found = driverProfileRepository.findByAccountId(accountId);
        if (found.isEmpty()) {
            return Result.failure(DriverProfileError.PROFILE_NOT_FOUND);
        }
        DriverProfileEntity profile = found.get();
        profile.setName(name);
        profile.setVehicleType(vehicleType);
        profile.setVehicleRegistrationNumber(vehicleRegistrationNumber);
        driverProfileRepository.save(profile);
        return Result.success(toSummary(profile));
    }

    /**
     * The live gate: fetches current verification status from
     * driver-verification's public API at the moment of the request,
     * rather than trusting this module's own AccountVerified-derived cache
     * - see DriverProfileSummary's Javadoc for why the cache isn't good
     * enough for this specific decision.
     */
    @Transactional
    public Result<DriverProfileSummary, DriverProfileError> setOnlineStatus(UUID accountId, OnlineStatus requested) {
        Optional<DriverProfileEntity> found = driverProfileRepository.findByAccountId(accountId);
        if (found.isEmpty()) {
            return Result.failure(DriverProfileError.PROFILE_NOT_FOUND);
        }
        DriverProfileEntity profile = found.get();

        if (requested == OnlineStatus.ONLINE && !isFullyVerified(accountId)) {
            return Result.failure(DriverProfileError.NOT_VERIFIED);
        }

        profile.setOnlineStatus(requested);
        driverProfileRepository.save(profile);
        return Result.success(toSummary(profile));
    }

    private boolean isFullyVerified(UUID accountId) {
        Optional<VerificationSummary> verification = verificationApi.findByAccountId(accountId);
        if (verification.isEmpty()) {
            return false;
        }
        VerificationSummary v = verification.get();
        return v.genderVerificationStatus() == VerificationStatus.VERIFIED
                && v.policeVerificationStatus() == VerificationStatus.VERIFIED;
    }

    private DriverProfileSummary toSummary(DriverProfileEntity profile) {
        String phoneNumber = authApi.findAccount(profile.getAccountId())
                .map(AccountSummary::phoneNumber)
                .orElse(null);
        return new DriverProfileSummary(
                profile.getAccountId(),
                profile.getName(),
                phoneNumber,
                profile.getVehicleType(),
                profile.getVehicleRegistrationNumber(),
                profile.getOnlineStatus(),
                profile.isVerified(),
                profile.getUpdatedAt()
        );
    }
}
