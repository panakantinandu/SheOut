package com.sheout.users.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.storage.DocumentUpload;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.users.DriverProfileSummary;
import com.sheout.users.OnlineStatus;
import com.sheout.users.VehicleType;
import com.sheout.users.internal.DriverProfileError;
import com.sheout.users.internal.VehicleRegistrationNumber;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

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
            // This one error needs the number the caller sent to explain
            // itself, so it is answered here where that is still in scope
            // rather than in the shared mapper below. The mapper takes only
            // an enum, and threading the value through a field on this
            // controller would be shared mutable state across requests.
            if (result.error() == DriverProfileError.INVALID_REGISTRATION_NUMBER) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REGISTRATION_NUMBER",
                        VehicleRegistrationNumber.rejectionReason(request.vehicleRegistrationNumber()));
            }
            throw toApiException(result.error());
        }
        return ResponseEntity.ok(result.value());
    }

    /**
     * The photo a rider sees on the tracking screen.
     * <p>
     * Multipart, so it bypasses the JSON body handling the rest of this
     * controller uses - the browser has to set its own boundary. Same shape
     * as the document upload in driver-verification, and the same storage
     * behind it.
     */
    @PostMapping(value = "/api/v1/users/driver/me/photo", consumes = "multipart/form-data")
    public ResponseEntity<DriverProfileSummary> uploadMyPhoto(@RequestParam("file") MultipartFile file) {
        CurrentAccount caller = requireDriver();
        Result<DriverProfileSummary, DriverProfileError> result =
                driverProfileService.updateProfilePhoto(caller.accountId(), toUpload(file));
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.ok(result.value());
    }

    private static DocumentUpload toUpload(MultipartFile file) {
        try {
            return new DocumentUpload(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Could not read the uploaded photo");
        }
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
            // Its own machine code, so the app can send her straight to the
            // camera instead of showing a generic refusal she cannot act on.
            case PROFILE_PHOTO_REQUIRED -> new ApiException(
                    HttpStatus.CONFLICT,
                    "PROFILE_PHOTO_REQUIRED",
                    "Add a profile photo before going online. Riders use it to check they are getting into the right vehicle."
            );
            // The message comes from the validator, which knows whether the
            // state code or the shape was wrong. A single "invalid
            // registration number" tells somebody who has copied their plate
            // correctly nothing at all.
            // Reached only if some future caller hits this without the raw
            // number to hand; updateMyProfile answers it with the specific
            // reason before getting here.
            case INVALID_REGISTRATION_NUMBER -> new ApiException(
                    HttpStatus.BAD_REQUEST, "INVALID_REGISTRATION_NUMBER",
                    VehicleRegistrationNumber.rejectionReason(null));
            case PHOTO_STORAGE_FAILED -> new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                    "Could not save that photo. Please try again."
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
