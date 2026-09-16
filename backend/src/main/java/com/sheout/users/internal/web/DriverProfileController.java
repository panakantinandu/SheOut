package com.sheout.users.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.storage.DocumentUpload;
import com.sheout.sharedkernel.geo.ServiceArea;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.users.DriverProfileSummary;
import com.sheout.users.OnlineStatus;
import com.sheout.users.VehicleType;
import com.sheout.users.internal.DriverProfileError;
import com.sheout.users.internal.VehicleRegistrationNumber;
import com.sheout.users.internal.DriverProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
import java.time.LocalDate;

/** Self-service only - a caller only ever reads/writes their own profile. */
@RestController
public class DriverProfileController {

    private final DriverProfileService driverProfileService;
    /** Only to name the boundary in the refusal message - the check itself is in the service. */
    private final ServiceArea serviceArea;

    public DriverProfileController(DriverProfileService driverProfileService, ServiceArea serviceArea) {
        this.driverProfileService = driverProfileService;
        this.serviceArea = serviceArea;
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
                caller.accountId(), request.name(), request.vehicleType(), request.vehicleRegistrationNumber(),
                request.dateOfBirth(), request.email());
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
     * Her PAN, for payout tax compliance.
     * <p>
     * Collected with her documents rather than on the profile screen, and so
     * saved on its own: a profile save that did not happen to carry it would
     * otherwise clear it. Sending an empty string removes it.
     */
    @PutMapping("/api/v1/users/driver/me/pan")
    public ResponseEntity<DriverProfileSummary> updateMyPan(@Valid @RequestBody UpdatePanRequest request) {
        CurrentAccount caller = requireDriver();
        Result<DriverProfileSummary, DriverProfileError> result =
                driverProfileService.updatePanNumber(caller.accountId(), request.panNumber());
        if (result.isFailure()) {
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
                driverProfileService.updateProfilePhoto(caller.accountId(), ProfilePhotoUploads.toUpload(file));
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.ok(result.value());
    }


    /**
     * The gated write - see DriverProfileService.setOnlineStatus.
     * <p>
     * Going ONLINE now carries where she is. It is required for that
     * direction and ignored for the other: "put me in the queue for trips
     * near me" is unanswerable without a position, while stopping work is
     * something she must be able to do from anywhere, always.
     * <p>
     * The coordinates come from the app rather than from dispatch's location
     * store, which would have meant this module depending on dispatch while
     * dispatch already depends on this one. A modified client could of
     * course send whatever it likes - but the check that actually costs
     * anything to evade is dispatch's own radius search, which will never
     * offer a Hyderabad booking to a phone in Texas no matter what it
     * claims. This one exists to tell an honest partner the truth.
     */
    @PostMapping("/api/v1/users/driver/me/status")
    public ResponseEntity<DriverProfileSummary> setStatus(@Valid @RequestBody SetStatusRequest request) {
        CurrentAccount caller = requireDriver();
        Result<DriverProfileSummary, DriverProfileError> result =
                driverProfileService.setOnlineStatus(caller.accountId(), request.status(), request.lat(), request.lng());
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
            // Names the city rather than saying "outside our service area",
            // which tells somebody nothing about where they would have to be.
            case OUTSIDE_SERVICE_AREA -> new ApiException(
                    HttpStatus.CONFLICT, "OUTSIDE_SERVICE_AREA",
                    "You are outside SheOut's service area. We currently operate only within "
                            + Math.round(serviceArea.radiusKm()) + "km of " + serviceArea.centreName()
                            + ", so there are no trips to send you here.");
            // Only reached from going online - saving a profile without one is
            // already refused by validation. A conflict with her profile, like
            // the missing photo: she completes it, then goes online.
            case DATE_OF_BIRTH_REQUIRED -> new ApiException(HttpStatus.CONFLICT, "DATE_OF_BIRTH_REQUIRED",
                    "Add your date of birth to your profile before going online.");
            case INVALID_DATE_OF_BIRTH -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_OF_BIRTH",
                    "That date of birth does not look right. Check the year.");
            // Said plainly and without a way round it: this is the Terms'
            // age limit, not a formatting problem to retry.
            case UNDER_MINIMUM_AGE -> new ApiException(HttpStatus.BAD_REQUEST, "UNDER_MINIMUM_AGE",
                    "You must be 18 or older to use SheOut.");
            case INVALID_EMAIL -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL",
                    "That email address does not look right. Leave it empty if you would rather not add one.");
            case INVALID_PAN -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAN",
                    "That PAN does not look right. It is ten characters: five letters, four digits, then a letter.");
            case LOCATION_REQUIRED -> new ApiException(
                    HttpStatus.BAD_REQUEST, "LOCATION_REQUIRED",
                    "We need your location to send you nearby trips. Allow location access and try again.");
        };
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 150) String name,
            @NotNull VehicleType vehicleType,
            @NotBlank @Size(max = 20) String vehicleRegistrationNumber,
            @NotNull LocalDate dateOfBirth,
            @Size(max = 254) String email
    ) {
    }

    /** Optional at the wire level too - an empty value removes a PAN already on file. */
    public record UpdatePanRequest(@Size(max = 20) String panNumber) {
    }

    /**
     * lat/lng are optional at the wire level and required by the service for
     * ONLINE only. Left nullable here on purpose: a bean-validation failure
     * would come back as a generic 400 about a missing field, where the
     * service answers with LOCATION_REQUIRED and a sentence a partner can
     * act on.
     */
    public record SetStatusRequest(
            @NotNull OnlineStatus status,
            @DecimalMin("-90") @DecimalMax("90") Double lat,
            @DecimalMin("-180") @DecimalMax("180") Double lng
    ) {
    }
}
