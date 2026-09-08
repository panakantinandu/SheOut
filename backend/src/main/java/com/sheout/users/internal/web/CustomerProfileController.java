package com.sheout.users.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.users.CustomerProfileSummary;
import com.sheout.users.EmergencyContact;
import com.sheout.users.internal.CustomerProfileError;
import com.sheout.users.internal.CustomerProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Self-service only - a caller only ever reads/writes their own profile. */
@RestController
public class CustomerProfileController {

    private final CustomerProfileService customerProfileService;

    public CustomerProfileController(CustomerProfileService customerProfileService) {
        this.customerProfileService = customerProfileService;
    }

    @GetMapping("/api/v1/users/customer/me")
    public ResponseEntity<CustomerProfileSummary> getMyProfile() {
        CurrentAccount caller = requireCustomer();
        return customerProfileService.findByAccountId(caller.accountId())
                .map(ResponseEntity::ok)
                .orElseThrow(() -> ApiException.notFound("No customer profile found for this account"));
    }

    @PutMapping("/api/v1/users/customer/me")
    public ResponseEntity<CustomerProfileSummary> updateMyProfile(@Valid @RequestBody UpdateProfileRequest request) {
        CurrentAccount caller = requireCustomer();
        Result<CustomerProfileSummary, CustomerProfileError> result = customerProfileService.updateProfile(
                caller.accountId(), request.name(), request.homeAddress(), request.workAddress());
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.ok(result.value());
    }

    @GetMapping("/api/v1/users/customer/me/emergency-contacts")
    public ResponseEntity<List<EmergencyContact>> listMyEmergencyContacts() {
        CurrentAccount caller = requireCustomer();
        return ResponseEntity.ok(customerProfileService.findContactsByAccountId(caller.accountId()));
    }

    @PostMapping("/api/v1/users/customer/me/emergency-contacts")
    public ResponseEntity<EmergencyContact> addEmergencyContact(@Valid @RequestBody AddContactRequest request) {
        CurrentAccount caller = requireCustomer();
        Result<EmergencyContact, CustomerProfileError> result = customerProfileService.addEmergencyContact(
                caller.accountId(), request.name(), request.phoneNumber(), request.relationship());
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(result.value());
    }

    @DeleteMapping("/api/v1/users/customer/me/emergency-contacts/{contactId}")
    public ResponseEntity<Void> removeEmergencyContact(@PathVariable UUID contactId) {
        CurrentAccount caller = requireCustomer();
        Result<Void, CustomerProfileError> result =
                customerProfileService.removeEmergencyContact(caller.accountId(), contactId);
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.noContent().build();
    }

    private CurrentAccount requireCustomer() {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (caller.role() != AccountRole.CUSTOMER) {
            throw ApiException.forbidden("Customer role required");
        }
        return caller;
    }

    private ApiException toApiException(CustomerProfileError error) {
        return switch (error) {
            case PROFILE_NOT_FOUND -> ApiException.notFound("No customer profile found for this account");
            case CONTACT_NOT_FOUND -> ApiException.notFound("No such emergency contact for this account");
        };
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 500) String homeAddress,
            @Size(max = 500) String workAddress
    ) {
    }

    public record AddContactRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 20) String phoneNumber,
            @NotBlank @Size(max = 50) String relationship
    ) {
    }
}
