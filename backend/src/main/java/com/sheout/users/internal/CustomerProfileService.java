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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CustomerProfileService implements CustomerProfileApi, EmergencyContactsApi {

    private final CustomerProfileRepository customerProfileRepository;
    private final EmergencyContactRepository emergencyContactRepository;
    private final AuthApi authApi;

    public CustomerProfileService(CustomerProfileRepository customerProfileRepository,
                                   EmergencyContactRepository emergencyContactRepository,
                                   AuthApi authApi) {
        this.customerProfileRepository = customerProfileRepository;
        this.emergencyContactRepository = emergencyContactRepository;
        this.authApi = authApi;
    }

    @Override
    public Optional<CustomerProfileSummary> findByAccountId(UUID accountId) {
        return customerProfileRepository.findByAccountId(accountId).map(this::toSummary);
    }

    @Transactional
    public Result<CustomerProfileSummary, CustomerProfileError> updateProfile(
            UUID accountId, String name, String homeAddress, String workAddress) {
        Optional<CustomerProfileEntity> found = customerProfileRepository.findByAccountId(accountId);
        if (found.isEmpty()) {
            return Result.failure(CustomerProfileError.PROFILE_NOT_FOUND);
        }
        CustomerProfileEntity profile = found.get();
        profile.setName(name);
        profile.setHomeAddress(homeAddress);
        profile.setWorkAddress(workAddress);
        customerProfileRepository.save(profile);
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
                profile.isVerified(),
                profile.getUpdatedAt()
        );
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
