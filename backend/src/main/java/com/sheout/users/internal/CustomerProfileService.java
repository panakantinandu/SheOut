package com.sheout.users.internal;

import com.sheout.auth.AccountSummary;
import com.sheout.auth.AuthApi;
import com.sheout.sharedkernel.Result;
import com.sheout.users.CustomerProfileApi;
import com.sheout.users.CustomerProfileSummary;
import com.sheout.users.EmergencyContact;
import com.sheout.users.EmergencyContactsApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sheout.sharedkernel.storage.DocumentStorage;
import com.sheout.sharedkernel.storage.DocumentUpload;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CustomerProfileService implements CustomerProfileApi, EmergencyContactsApi {

    private final CustomerProfileRepository customerProfileRepository;
    private final EmergencyContactRepository emergencyContactRepository;
    private final AuthApi authApi;
    private final DocumentStorage documentStorage;

    public CustomerProfileService(CustomerProfileRepository customerProfileRepository,
                                   EmergencyContactRepository emergencyContactRepository,
                                   AuthApi authApi,
                                   DocumentStorage documentStorage) {
        this.documentStorage = documentStorage;
        this.customerProfileRepository = customerProfileRepository;
        this.emergencyContactRepository = emergencyContactRepository;
        this.authApi = authApi;
    }

    @Override
    public Optional<CustomerProfileSummary> findByAccountId(UUID accountId) {
        return customerProfileRepository.findByAccountId(accountId).map(this::toSummary);
    }

    @Override
    public List<CustomerProfileSummary> findFlaggedForReview() {
        return customerProfileRepository.findByFlaggedAtIsNotNullOrderByFlaggedAtAsc().stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    @Transactional
    public void clearReviewFlag(UUID accountId) {
        customerProfileRepository.findByAccountId(accountId).ifPresent(profile -> {
            profile.clearReviewFlag();
            customerProfileRepository.save(profile);
        });
    }

    /**
     * Saves the whole profile. A profile is only ever saved complete: a date
     * of birth showing she is 18 or over, and a photo already on file -
     * uploaded first, through updateProfilePhoto. The Terms have always said
     * SheOut is not for anyone under 18; this is the first thing that checks.
     */
    @Transactional
    public Result<CustomerProfileSummary, CustomerProfileError> updateProfile(
            UUID accountId, String name, String homeAddress, String workAddress, LocalDate dateOfBirth, String email) {
        Optional<CustomerProfileEntity> found = customerProfileRepository.findByAccountId(accountId);
        if (found.isEmpty()) {
            return Result.failure(CustomerProfileError.PROFILE_NOT_FOUND);
        }
        Optional<ProfileRules.BirthDateProblem> birthDateProblem =
                ProfileRules.checkDateOfBirth(dateOfBirth, ProfileRules.todayInIndia());
        if (birthDateProblem.isPresent()) {
            return Result.failure(switch (birthDateProblem.get()) {
                case MISSING -> CustomerProfileError.DATE_OF_BIRTH_REQUIRED;
                case IMPLAUSIBLE -> CustomerProfileError.INVALID_DATE_OF_BIRTH;
                case UNDER_MINIMUM_AGE -> CustomerProfileError.UNDER_MINIMUM_AGE;
            });
        }
        Optional<String> normalisedEmail = ProfileRules.normaliseEmail(email);
        if (normalisedEmail.isEmpty()) {
            return Result.failure(CustomerProfileError.INVALID_EMAIL);
        }
        CustomerProfileEntity profile = found.get();
        if (!profile.hasProfilePhoto()) {
            return Result.failure(CustomerProfileError.PROFILE_PHOTO_REQUIRED);
        }
        profile.setName(name);
        profile.setHomeAddress(homeAddress);
        profile.setWorkAddress(workAddress);
        profile.setDateOfBirth(dateOfBirth);
        profile.setEmail(normalisedEmail.get().isEmpty() ? null : normalisedEmail.get());
        customerProfileRepository.save(profile);
        return Result.success(toSummary(profile));
    }

    /**
     * Her profile photo. Same DocumentStorage as the partner photo and the
     * identity documents - no second storage mechanism - and required, like a
     * partner's, before her profile counts as complete.
     */
    @Transactional
    public Result<CustomerProfileSummary, CustomerProfileError> updateProfilePhoto(UUID accountId, DocumentUpload upload) {
        Optional<CustomerProfileEntity> found = customerProfileRepository.findByAccountId(accountId);
        if (found.isEmpty()) {
            return Result.failure(CustomerProfileError.PROFILE_NOT_FOUND);
        }
        CustomerProfileEntity profile = found.get();
        String previousKey = profile.getProfilePhotoKey();
        String key;
        try {
            key = documentStorage.store(accountId, "profile-photo", upload);
        } catch (RuntimeException ex) {
            return Result.failure(CustomerProfileError.PHOTO_STORAGE_FAILED);
        }
        profile.setProfilePhotoKey(key);
        customerProfileRepository.save(profile);
        if (previousKey != null && !previousKey.equals(key)) {
            // The replaced photo: once nothing points at it, it could never
            // be found again to delete.
            try {
                documentStorage.delete(previousKey);
            } catch (RuntimeException ignored) {
                // The new photo is saved; a stale file is not worth failing the upload over.
            }
        }
        return Result.success(toSummary(profile));
    }

    @Override
    public List<EmergencyContact> findContactsByAccountId(UUID customerAccountId) {
        return customerProfileRepository.findByAccountId(customerAccountId)
                .map(profile -> emergencyContactRepository.findByCustomerProfileId(profile.getId()))
                .orElseGet(List::of)
                .stream()
                .map(contact -> toContact(contact, customerAccountId))
                .toList();
    }

    @Transactional
    public Result<EmergencyContact, CustomerProfileError> addEmergencyContact(
            UUID accountId, String name, String phoneNumber, String relationship) {
        Optional<CustomerProfileEntity> found = customerProfileRepository.findByAccountId(accountId);
        if (found.isEmpty()) {
            return Result.failure(CustomerProfileError.PROFILE_NOT_FOUND);
        }
        EmergencyContactEntity contact = emergencyContactRepository.save(
                new EmergencyContactEntity(found.get(), name, phoneNumber, relationship));
        return Result.success(toContact(contact, accountId));
    }

    @Transactional
    public Result<Void, CustomerProfileError> removeEmergencyContact(UUID accountId, UUID contactId) {
        Optional<CustomerProfileEntity> profile = customerProfileRepository.findByAccountId(accountId);
        if (profile.isEmpty()) {
            return Result.failure(CustomerProfileError.PROFILE_NOT_FOUND);
        }
        Optional<EmergencyContactEntity> contact =
                emergencyContactRepository.findByIdAndCustomerProfileId(contactId, profile.get().getId());
        if (contact.isEmpty()) {
            return Result.failure(CustomerProfileError.CONTACT_NOT_FOUND);
        }
        emergencyContactRepository.delete(contact.get());
        return Result.success(null);
    }

    private CustomerProfileSummary toSummary(CustomerProfileEntity profile) {
        String phoneNumber = authApi.findAccount(profile.getAccountId())
                .map(AccountSummary::phoneNumber)
                .orElse(null);
        return new CustomerProfileSummary(
                profile.getAccountId(),
                profile.getName(),
                phoneNumber,
                profile.getHomeAddress(),
                profile.getWorkAddress(),
                profile.getDateOfBirth(),
                profile.getEmail(),
                displayablePhotoUrl(profile.getProfilePhotoKey()),
                profile.hasProfilePhoto(),
                profile.isProfileComplete(),
                profile.isVerified(),
                profile.getTrustStats(),
                profile.getUpdatedAt()
        );
    }

    /** As DriverProfileService.displayablePhotoUrl: only a URL a browser could load, never a file path. */
    private String displayablePhotoUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String url = documentStorage.resolveUrl(key);
        return url != null && (url.startsWith("http://") || url.startsWith("https://")) ? url : null;
    }

    /**
     * Takes accountId explicitly rather than reading contact.getCustomerProfile().getAccountId()
     * - that association is lazy, and this method is called from read paths
     * that aren't guaranteed to still have an open Hibernate session by the
     * time this runs. The caller always already knows the accountId anyway.
     */
    private EmergencyContact toContact(EmergencyContactEntity contact, UUID customerAccountId) {
        return new EmergencyContact(
                contact.getId(),
                customerAccountId,
                contact.getName(),
                contact.getPhoneNumber(),
                contact.getRelationship()
        );
    }
}
