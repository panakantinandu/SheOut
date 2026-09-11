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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class DriverProfileService implements DriverProfileApi {

    private final DriverProfileRepository driverProfileRepository;
    private final AuthApi authApi;
    private final VerificationApi verificationApi;
    private final String verifiedDriverBypassPhone;

    public DriverProfileService(DriverProfileRepository driverProfileRepository,
                                 AuthApi authApi,
                                 VerificationApi verificationApi,
                                 @Value("${sheout.testing.verified-driver-bypass-phone:}") String verifiedDriverBypassPhone) {
        this.driverProfileRepository = driverProfileRepository;
        this.authApi = authApi;
        this.verificationApi = verificationApi;
        this.verifiedDriverBypassPhone = verifiedDriverBypassPhone;
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
     * <p>
     * TESTING AID ONLY, NOT A PRODUCT FEATURE: sheout.testing.verified-
     * driver-bypass-phone (blank/unset by default, same opt-in-via-env-var
     * treatment as sheout.testing.verified-bypass-phone on the booking
     * side) lets exactly one configured driver phone number go ONLINE
     * without real admin verification - there is no self-service path to
     * VERIFIED status (gender verification needs an admin review after
     * document submission; police verification has no driver-facing
     * submission step at all - see AdminVerificationController's Javadoc),
     * so end-to-end testing of the online/dispatch flow was otherwise
     * blocked on manual database access. Every other account still goes
     * through the real, unmodified check below.
     */
    @Transactional
    public Result<DriverProfileSummary, DriverProfileError> setOnlineStatus(UUID accountId, OnlineStatus requested) {
        Optional<DriverProfileEntity> found = driverProfileRepository.findByAccountId(accountId);
        if (found.isEmpty()) {
            return Result.failure(DriverProfileError.PROFILE_NOT_FOUND);
        }
        DriverProfileEntity profile = found.get();

        if (requested == OnlineStatus.ONLINE && !isFullyVerified(accountId) && !isVerifiedBypassAccount(accountId)) {
            return Result.failure(DriverProfileError.NOT_VERIFIED);
        }

        profile.setOnlineStatus(requested);
        driverProfileRepository.save(profile);
        return Result.success(toSummary(profile));
    }

    /**
     * See DriverProfileApi.isCurrentlyVerified. Intentionally the real
     * check with no bypass: the testing bypass may let a QA account go
     * ONLINE, but it must not make that account eligible to be offered a
     * real customer's booking.
     */
    @Override
    public boolean isCurrentlyVerified(UUID accountId) {
        return isFullyVerified(accountId);
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

    private boolean isVerifiedBypassAccount(UUID accountId) {
        if (verifiedDriverBypassPhone.isBlank()) {
            return false;
        }
        return authApi.findAccount(accountId)
                .map(account -> verifiedDriverBypassPhone.equals(account.phoneNumber()))
                .orElse(false);
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
