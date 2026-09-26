package com.sheout.users.internal;

import com.sheout.auth.AccountSummary;
import com.sheout.auth.AuthApi;
import com.sheout.driververification.VerificationApi;
import com.sheout.driververification.VerificationStatus;
import com.sheout.driververification.VerificationSummary;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import com.sheout.users.DriverWentOffline;
import com.sheout.sharedkernel.geo.ServiceArea;
import com.sheout.sharedkernel.storage.DocumentStorage;
import com.sheout.sharedkernel.storage.DocumentUpload;
import com.sheout.users.DriverProfileApi;
import com.sheout.users.DriverProfileSummary;
import com.sheout.users.OnlineStatus;
import com.sheout.users.VehicleType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DriverProfileService implements DriverProfileApi {

    private final DriverProfileRepository driverProfileRepository;
    private final AuthApi authApi;
    private final VerificationApi verificationApi;
    private final DocumentStorage documentStorage;
    private final ServiceArea serviceArea;
    private final String verifiedDriverBypassPhone;
    private final DomainEventPublisher eventPublisher;

    public DriverProfileService(DriverProfileRepository driverProfileRepository,
                                 AuthApi authApi,
                                 VerificationApi verificationApi,
                                 DocumentStorage documentStorage,
                                 ServiceArea serviceArea,
                                 DomainEventPublisher eventPublisher,
                                 @Value("${sheout.testing.verified-driver-bypass-phone:}") String verifiedDriverBypassPhone) {
        this.eventPublisher = eventPublisher;
        this.driverProfileRepository = driverProfileRepository;
        this.authApi = authApi;
        this.verificationApi = verificationApi;
        this.documentStorage = documentStorage;
        this.serviceArea = serviceArea;
        this.verifiedDriverBypassPhone = verifiedDriverBypassPhone;
    }

    @Override
    public Optional<DriverProfileSummary> findByAccountId(UUID accountId) {
        return driverProfileRepository.findByAccountId(accountId).map(this::toSummary);
    }

    @Override
    public List<String> findEmailAddresses(int page, int size) {
        return driverProfileRepository.findEmailAddresses(PageRequest.of(page, size));
    }

    @Override
    public List<DriverProfileSummary> findFlaggedForReview() {
        return driverProfileRepository.findByFlaggedAtIsNotNullOrderByFlaggedAtAsc().stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    @Transactional
    public void clearReviewFlag(UUID accountId) {
        driverProfileRepository.findByAccountId(accountId).ifPresent(profile -> {
            profile.clearReviewFlag();
            driverProfileRepository.save(profile);
        });
    }

    /**
     * Saves the profile, refusing a registration number that is not a
     * well-formed Indian plate.
     * <p>
     * Checked here rather than in booking or dispatch, because the number
     * is this module's field and this is the only place it is written. A
     * validator anywhere else would be a second opinion to keep in step.
     * <p>
     * Stored normalized, so "ts 06 fh 2653" and "TS-06-FH-2653" become one
     * value. An operator cross-checking the typed number against the RC
     * photo should not have to mentally strip punctuation, and two spellings
     * of one plate would defeat any future lookup by it.
     */
    @Transactional
    public Result<DriverProfileSummary, DriverProfileError> updateProfile(
            UUID accountId, String name, VehicleType vehicleType, String vehicleRegistrationNumber,
            LocalDate dateOfBirth, String email) {
        if (!VehicleRegistrationNumber.isValid(vehicleRegistrationNumber)) {
            return Result.failure(DriverProfileError.INVALID_REGISTRATION_NUMBER);
        }
        Optional<DriverProfileEntity> found = driverProfileRepository.findByAccountId(accountId);
        if (found.isEmpty()) {
            return Result.failure(DriverProfileError.PROFILE_NOT_FOUND);
        }
        // The same rules as a rider's profile - see CustomerProfileService.updateProfile.
        Optional<ProfileRules.BirthDateProblem> birthDateProblem =
                ProfileRules.checkDateOfBirth(dateOfBirth, ProfileRules.todayInIndia());
        if (birthDateProblem.isPresent()) {
            return Result.failure(switch (birthDateProblem.get()) {
                case MISSING -> DriverProfileError.DATE_OF_BIRTH_REQUIRED;
                case IMPLAUSIBLE -> DriverProfileError.INVALID_DATE_OF_BIRTH;
                case UNDER_MINIMUM_AGE -> DriverProfileError.UNDER_MINIMUM_AGE;
            });
        }
        Optional<String> normalisedEmail = ProfileRules.normaliseEmail(email);
        if (normalisedEmail.isEmpty()) {
            return Result.failure(DriverProfileError.INVALID_EMAIL);
        }
        DriverProfileEntity profile = found.get();
        if (!profile.hasProfilePhoto()) {
            return Result.failure(DriverProfileError.PROFILE_PHOTO_REQUIRED);
        }
        profile.setDateOfBirth(dateOfBirth);
        profile.setEmail(normalisedEmail.get().isEmpty() ? null : normalisedEmail.get());
        profile.setName(name);
        profile.setVehicleType(vehicleType);
        profile.setVehicleRegistrationNumber(VehicleRegistrationNumber.normalize(vehicleRegistrationNumber));
        driverProfileRepository.save(profile);
        return Result.success(toSummary(profile));
    }

    /**
     * Records her PAN, for deducting TDS on payouts.
     * <p>
     * Its own call rather than a field on updateProfile, because it is
     * collected on a different screen at a different time - with her
     * documents, when she is already dealing with paperwork - and because a
     * profile save that happened not to include it would otherwise wipe it.
     * <p>
     * Blank clears it. She gave it voluntarily and must be able to take it
     * back; there is nothing here that needs it to stay.
     */
    @Transactional
    public Result<DriverProfileSummary, DriverProfileError> updatePanNumber(UUID accountId, String panNumber) {
        if (!PanNumber.isValidOrBlank(panNumber)) {
            return Result.failure(DriverProfileError.INVALID_PAN);
        }
        Optional<DriverProfileEntity> found = driverProfileRepository.findByAccountId(accountId);
        if (found.isEmpty()) {
            return Result.failure(DriverProfileError.PROFILE_NOT_FOUND);
        }
        DriverProfileEntity profile = found.get();
        profile.setPanNumber(PanNumber.normalize(panNumber));
        driverProfileRepository.save(profile);
        return Result.success(toSummary(profile));
    }

    /**
     * Stores the photo a rider sees, and hands back the updated profile.
     * <p>
     * Goes through the same DocumentStorage the identity documents use.
     * There is no second storage mechanism here, and no public bucket: the
     * column holds an opaque key, and a URL is resolved at read time for
     * whoever is entitled to one.
     */
    @Transactional
    public Result<DriverProfileSummary, DriverProfileError> updateProfilePhoto(
            UUID accountId, DocumentUpload upload) {
        Optional<DriverProfileEntity> found = driverProfileRepository.findByAccountId(accountId);
        if (found.isEmpty()) {
            return Result.failure(DriverProfileError.PROFILE_NOT_FOUND);
        }
        DriverProfileEntity profile = found.get();

        String key;
        try {
            key = documentStorage.store(accountId, "profile-photo", upload);
        } catch (RuntimeException ex) {
            return Result.failure(DriverProfileError.PHOTO_STORAGE_FAILED);
        }

        profile.setProfilePhotoKey(key);
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
    public Result<DriverProfileSummary, DriverProfileError> setOnlineStatus(
            UUID accountId, OnlineStatus requested, Double lat, Double lng) {
        Optional<DriverProfileEntity> found = driverProfileRepository.findByAccountId(accountId);
        if (found.isEmpty()) {
            return Result.failure(DriverProfileError.PROFILE_NOT_FOUND);
        }
        DriverProfileEntity profile = found.get();

        // Where she is, before anything else about who she is.
        //
        // This app operates in one city. Going online used to succeed from
        // anywhere on earth: a partner in Dallas could tap Go Online, be
        // told "Looking for ride requests nearby", and wait forever for
        // requests that could never reach her, because dispatch searches a
        // radius around a Hyderabad pickup and she was thirteen thousand
        // kilometres outside it. Nothing was broken, and nothing said so.
        //
        // Deliberately first, so somebody standing in the wrong country is
        // told the thing that is actually true of her rather than being sent
        // to fix a photo that was never the obstacle.
        //
        // Going OFFLINE is never checked. A partner must always be able to
        // stop working, wherever she is - the same principle the photo gate
        // below follows.
        if (requested == OnlineStatus.ONLINE) {
            if (lat == null || lng == null) {
                return Result.failure(DriverProfileError.LOCATION_REQUIRED);
            }
            if (!serviceArea.covers(lat, lng)) {
                return Result.failure(DriverProfileError.OUTSIDE_SERVICE_AREA);
            }
        }

        if (requested == OnlineStatus.ONLINE && !isFullyVerified(accountId) && !isVerifiedBypassAccount(accountId)) {
            return Result.failure(DriverProfileError.NOT_VERIFIED);
        }

        // A photo is required before a partner can take her first booking,
        // and this gate is not bypassable the way verification is. The
        // testing bypass exists because there is no self-service route to
        // VERIFIED and QA would otherwise be blocked on an operator; taking
        // a photo needs nobody's approval, so there is nothing to bypass.
        //
        // It matters more than it sounds. A rider getting into a stranger's
        // vehicle at night has one way to check she has the right one, and
        // it is the face on her screen. Going offline is always allowed - a
        // missing photo must never be a reason somebody cannot stop working.
        if (requested == OnlineStatus.ONLINE && !profile.hasProfilePhoto()) {
            return Result.failure(DriverProfileError.PROFILE_PHOTO_REQUIRED);
        }
        // An account from before dates of birth were required has never
        // shown she is 18. Not bypassable, for the same reason the photo is
        // not: stating a date needs nobody's approval.
        if (requested == OnlineStatus.ONLINE && profile.getDateOfBirth() == null) {
            return Result.failure(DriverProfileError.DATE_OF_BIRTH_REQUIRED);
        }

        profile.setOnlineStatus(requested);
        driverProfileRepository.save(profile);
        if (requested == OnlineStatus.OFFLINE) {
            eventPublisher.publish(new DriverWentOffline(accountId));
        }
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

    /**
     * The photo URL, but only if a browser could actually load it.
     * <p>
     * With local-disk storage - which is the default, and what this service
     * runs on today - resolveUrl hands back a file:// path. No browser will
     * load one from a web page, and returning it achieved two bad things at
     * once: every client logged a blocked-resource error, and the API was
     * handing out an absolute server filesystem path to anyone who asked.
     * <p>
     * Null instead, which the apps already render as a silhouette. That is
     * the honest answer: there is no photo anyone can see. It is not a fix
     * for the underlying problem - see the note on the storage provider -
     * it just stops the API claiming otherwise.
     */
    private String displayablePhotoUrl(DriverProfileEntity profile) {
        if (!profile.hasProfilePhoto()) {
            return null;
        }
        String url = documentStorage.resolveUrl(profile.getProfilePhotoKey());
        return url != null && (url.startsWith("http://") || url.startsWith("https://")) ? url : null;
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
                profile.getPanNumber(),
                profile.getOnlineStatus(),
                profile.isVerified(),
                // Resolved at read time, never stored. A presigned S3 URL is
                // valid for minutes; a column holding one would be wrong
                // almost immediately.
                displayablePhotoUrl(profile),
                profile.hasProfilePhoto(),
                profile.getDateOfBirth(),
                profile.getEmail(),
                profile.isProfileComplete(),
                profile.getTrustStats(),
                profile.getUpdatedAt()
        );
    }
}
