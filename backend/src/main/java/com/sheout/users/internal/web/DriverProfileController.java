package com.sheout.users.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.users.DriverProfileSummary;
import com.sheout.users.OnlineStatus;
import com.sheout.users.VehicleType;
import com.sheout.users.internal.DriverProfileError;
import com.sheout.users.internal.DriverProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Self-service only - a caller only ever reads/writes their own profile. */
@RestController
public class DriverProfileController {

    private final DriverProfileService driverProfileService;

    public DriverProfileController(DriverProfileService driverProfileService) {
        this.driverProfileService = driverProfileService;
    }

    @GetMapping("/api/v1/users/driver/me")
    public ResponseEntity<DriverProfileSummary> getMyProfile() {
        CurrentAccount caller = requireDriver();
        return driverProfileService.findByAccountId(caller.accountId())
                .map(ResponseEntity::ok)
                .orElseThrow(() -> ApiException.notFound("No driver profile found for this account"));
    }

    @PutMapping("/api/v1/users/driver/me")
    public ResponseEntity<DriverProfileSummary> updateMyProfile(@Valid @RequestBody UpdateProfileRequest request) {
        CurrentAccount caller = requireDriver();
        Result<DriverProfileSummary, DriverProfileError> result = driverProfileService.updateProfile(
                caller.accountId(), request.name(), request.vehicleType(), request.vehicleRegistrationNumber());
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.ok(result.value());
    }

    /** The gated write - see DriverProfileService.setOnlineStatus. */
    @PostMapping("/api/v1/users/driver/me/status")
    public ResponseEntity<DriverProfileSummary> setStatus(@Valid @RequestBody SetStatusRequest request) {
        CurrentAccount caller = requireDriver();
        Result<DriverProfileSummary, DriverProfileError> result =
                driverProfileService.setOnlineStatus(caller.accountId(), request.status());
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.ok(result.value());
    }

    private CurrentAccount requireDriver() {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (caller.role() != AccountRole.DRIVER) {
            throw ApiException.forbidden("Driver role required");
        }
        return caller;
    }

    private ApiException toApiException(DriverProfileError error) {
        return switch (error) {
            case PROFILE_NOT_FOUND -> ApiException.notFound("No driver profile found for this account");
            case NOT_VERIFIED -> new ApiException(
                    HttpStatus.CONFLICT,
                    "Conflict",
                    "Gender and police verification must both be VERIFIED before going online"
            );
        };
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 150) String name,
            @NotNull VehicleType vehicleType,
            @NotBlank @Size(max = 20) String vehicleRegistrationNumber
    ) {
    }

    public record SetStatusRequest(@NotNull OnlineStatus status) {
    }
}
