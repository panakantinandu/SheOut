package com.sheout.users.internal;

import com.sheout.auth.AccountSummary;
import com.sheout.auth.AuthApi;
import com.sheout.driververification.VerificationApi;
import com.sheout.driververification.VerificationStatus;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import com.sheout.sharedkernel.storage.DocumentStorage;
import com.sheout.sharedkernel.storage.DocumentUpload;
import com.sheout.users.DriverProfileChangeDecided;
import com.sheout.users.PendingProfileChange;
import com.sheout.users.ProfileChangeReview;
import com.sheout.users.VehicleType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Changes to what a rider identifies her partner by, once that partner has
 * been verified: her name, date of birth, photo, vehicle and its number.
 * <p>
 * Before verification these are simply her profile and she edits them
 * freely. After it, they are what an operator checked against her ID and
 * registration certificate, and what a rider at the kerb compares with the
 * person and vehicle in front of her - so a change waits for an operator,
 * and riders keep seeing what was checked until it is approved. Without
 * this, a verified account could be handed to somebody else, photo and
 * bike included, with nobody looking.
 * <p>
 * A vehicle change needs a photo of the new registration certificate; it
 * cannot be approved without one.
 */
@Service
class DriverProfileChangeService {

    /** How long a turned-down change stays on her profile screen with its reason. */
    private static final Duration SHOW_REJECTION_FOR = Duration.ofDays(7);

    private final DriverProfileChangeRepository changes;
    private final DriverProfileRepository profiles;
    private final VerificationApi verificationApi;
    private final DocumentStorage documentStorage;
    private final AuthApi authApi;
    private final DomainEventPublisher events;

    DriverProfileChangeService(DriverProfileChangeRepository changes, DriverProfileRepository profiles,
                               VerificationApi verificationApi, DocumentStorage documentStorage,
                               AuthApi authApi, DomainEventPublisher events) {
        this.changes = changes;
        this.profiles = profiles;
        this.verificationApi = verificationApi;
        this.documentStorage = documentStorage;
        this.authApi = authApi;
        this.events = events;
    }

    /** Her identity has been checked against these details, so they no longer change on her word alone. */
    boolean identityLocked(UUID accountId) {
        return verificationApi.findByAccountId(accountId)
                .map(v -> v.genderVerificationStatus() == VerificationStatus.VERIFIED)
                .orElse(false);
    }

    /**
     * Records what differs from the approved profile as the pending change.
     * The form always sends every field, so a field that matches again is a
     * change she took back; a change left with nothing in it is withdrawn.
     */
    void requestDetails(DriverProfileEntity profile, String name, LocalDate dateOfBirth,
                        VehicleType vehicleType, String registration) {
        String requestedName = Objects.equals(clean(name), clean(profile.getName())) ? null : clean(name);
        LocalDate requestedDob = Objects.equals(dateOfBirth, profile.getDateOfBirth()) ? null : dateOfBirth;
        boolean vehicleDiffers = vehicleType != profile.getVehicleType()
                || !Objects.equals(registration, profile.getVehicleRegistrationNumber());
        Optional<DriverProfileChangeEntity> existing = pending(profile.getAccountId());
        if (requestedName == null && requestedDob == null && !vehicleDiffers && existing.isEmpty()) {
            return;
        }
        DriverProfileChangeEntity change = existing.orElseGet(() -> new DriverProfileChangeEntity(profile.getAccountId()));
        change.requestDetails(requestedName, requestedDob,
                vehicleDiffers ? vehicleType : null, vehicleDiffers ? registration : null);
        saveOrWithdraw(change);
    }

    void requestPhoto(UUID accountId, String photoKey) {
        DriverProfileChangeEntity change = pending(accountId).orElseGet(() -> new DriverProfileChangeEntity(accountId));
        String replaced = change.getPhotoKey();
        change.requestPhoto(photoKey);
        changes.save(change);
        deleteQuietly(replaced);
    }

    @Transactional
    public Result<Void, DriverProfileError> attachRcDocument(UUID accountId, DocumentUpload upload) {
        Optional<DriverProfileChangeEntity> found = pending(accountId);
        if (found.isEmpty()) {
            return Result.failure(DriverProfileError.NO_PENDING_CHANGE);
        }
        DriverProfileChangeEntity change = found.get();
        if (!change.vehicleChanged()) {
            return Result.failure(DriverProfileError.VEHICLE_NOT_CHANGED);
        }
        String key;
        try {
            key = documentStorage.store(accountId, "vehicle-rc-change", upload);
        } catch (RuntimeException ex) {
            return Result.failure(DriverProfileError.PHOTO_STORAGE_FAILED);
        }
        String replaced = change.getRcDocumentKey();
        change.attachRcDocument(key);
        changes.save(change);
        deleteQuietly(replaced);
        return Result.success(null);
    }

    @Transactional
    public Result<Void, DriverProfileError> withdraw(UUID accountId) {
        Optional<DriverProfileChangeEntity> found = pending(accountId);
        if (found.isEmpty()) {
            return Result.failure(DriverProfileError.NO_PENDING_CHANGE);
        }
        found.get().decide(DriverProfileChangeEntity.Status.WITHDRAWN, accountId, null);
        changes.save(found.get());
        return Result.success(null);
    }

    /** Her own view: the pending change, or a recent refusal with its reason; empty otherwise. */
    Optional<PendingProfileChange> viewFor(UUID accountId) {
        return changes.findFirstByAccountIdOrderByRequestedAtDesc(accountId)
                .filter(c -> c.getStatus() == DriverProfileChangeEntity.Status.PENDING
                        || (c.getStatus() == DriverProfileChangeEntity.Status.REJECTED && c.getDecidedAt() != null
                                && c.getDecidedAt().isAfter(Instant.now().minus(SHOW_REJECTION_FOR))))
                .map(c -> new PendingProfileChange(c.getStatus().name(), c.getName(), c.getDateOfBirth(),
                        c.getVehicleType(), c.getVehicleRegistrationNumber(), url(c.getPhotoKey()),
                        c.getPhotoKey() != null, c.getRcDocumentKey() != null, c.vehicleChanged(),
                        c.getRequestedAt(), c.getDecisionNote()));
    }

    @Transactional(readOnly = true)
    public List<ProfileChangeReview> pendingReviews() {
        return changes.findByStatusOrderByRequestedAtAsc(DriverProfileChangeEntity.Status.PENDING).stream()
                .map(c -> {
                    Optional<DriverProfileEntity> profile = profiles.findByAccountId(c.getAccountId());
                    return new ProfileChangeReview(c.getId(), c.getAccountId(),
                            authApi.findAccount(c.getAccountId()).map(AccountSummary::phoneNumber).orElse(null),
                            profile.map(DriverProfileEntity::getName).orElse(null),
                            profile.map(DriverProfileEntity::getDateOfBirth).orElse(null),
                            profile.map(DriverProfileEntity::getVehicleType).orElse(null),
                            profile.map(DriverProfileEntity::getVehicleRegistrationNumber).orElse(null),
                            profile.filter(DriverProfileEntity::hasProfilePhoto).map(p -> url(p.getProfilePhotoKey())).orElse(null),
                            c.getName(), c.getDateOfBirth(), c.getVehicleType(), c.getVehicleRegistrationNumber(),
                            url(c.getPhotoKey()), url(c.getRcDocumentKey()), c.getRequestedAt());
                })
                .toList();
    }

    /**
     * Applies an approved change to her profile, or turns it down with a
     * reason she is shown. A vehicle change without its registration
     * certificate cannot be approved - there would be nothing to have
     * checked the new number against.
     */
    @Transactional
    public Result<Void, DriverProfileError> decide(UUID changeId, boolean approve, UUID adminAccountId, String note) {
        Optional<DriverProfileChangeEntity> found = changes.findById(changeId)
                .filter(c -> c.getStatus() == DriverProfileChangeEntity.Status.PENDING);
        if (found.isEmpty()) {
            return Result.failure(DriverProfileError.CHANGE_NOT_FOUND);
        }
        DriverProfileChangeEntity change = found.get();
        String trimmed = note == null ? null : note.trim();
        if (!approve) {
            if (trimmed == null || trimmed.isEmpty()) {
                return Result.failure(DriverProfileError.DECISION_NOTE_REQUIRED);
            }
            change.decide(DriverProfileChangeEntity.Status.REJECTED, adminAccountId, trimmed);
            changes.save(change);
            events.publish(new DriverProfileChangeDecided(change.getAccountId(), false, trimmed));
            return Result.success(null);
        }
        if (change.vehicleChanged() && change.getRcDocumentKey() == null) {
            return Result.failure(DriverProfileError.RC_DOCUMENT_REQUIRED);
        }
        Optional<DriverProfileEntity> profile = profiles.findByAccountId(change.getAccountId());
        if (profile.isEmpty()) {
            return Result.failure(DriverProfileError.PROFILE_NOT_FOUND);
        }
        DriverProfileEntity p = profile.get();
        if (change.getName() != null) p.setName(change.getName());
        if (change.getDateOfBirth() != null) p.setDateOfBirth(change.getDateOfBirth());
        if (change.getVehicleType() != null) p.setVehicleType(change.getVehicleType());
        if (change.getVehicleRegistrationNumber() != null) p.setVehicleRegistrationNumber(change.getVehicleRegistrationNumber());
        String oldPhoto = null;
        if (change.getPhotoKey() != null) {
            oldPhoto = p.getProfilePhotoKey();
            p.setProfilePhotoKey(change.getPhotoKey());
        }
        profiles.save(p);
        change.decide(DriverProfileChangeEntity.Status.APPROVED, adminAccountId, trimmed == null || trimmed.isEmpty() ? null : trimmed);
        changes.save(change);
        deleteQuietly(oldPhoto);
        events.publish(new DriverProfileChangeDecided(change.getAccountId(), true, null));
        return Result.success(null);
    }

    private Optional<DriverProfileChangeEntity> pending(UUID accountId) {
        return changes.findFirstByAccountIdAndStatus(accountId, DriverProfileChangeEntity.Status.PENDING);
    }

    private void saveOrWithdraw(DriverProfileChangeEntity change) {
        if (change.isEmpty()) {
            if (changes.existsById(change.getId() == null ? new UUID(0, 0) : change.getId())) {
                change.decide(DriverProfileChangeEntity.Status.WITHDRAWN, change.getAccountId(), null);
                changes.save(change);
            }
            return;
        }
        changes.save(change);
    }

    private String url(String key) {
        if (key == null) {
            return null;
        }
        String url = documentStorage.resolveUrl(key);
        return url != null && (url.startsWith("http://") || url.startsWith("https://")) ? url : null;
    }

    private void deleteQuietly(String key) {
        if (key == null) {
            return;
        }
        try {
            documentStorage.delete(key);
        } catch (RuntimeException ignored) {
            // A leftover file is harmless; failing the change over it is not.
        }
    }

    private static String clean(String s) {
        return s == null ? null : s.trim();
    }
}
