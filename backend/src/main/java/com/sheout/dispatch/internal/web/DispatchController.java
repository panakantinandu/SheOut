package com.sheout.dispatch.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.dispatch.internal.DispatchError;
import com.sheout.dispatch.internal.DispatchService;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Self-service, driver-only. There is no HTTP path to BookingApi's
 * assignDriver - that's a Java call this module makes internally once a
 * driver's accept wins the race (see DispatchService.acceptOffer).
 * <p>
 * ASSUMPTION FLAGGED on "broadcast": with no notifications module built
 * yet (no FCM/push), "broadcast the offer" is implemented as dispatch
 * opening a short-lived offer record per candidate driver, which the
 * driver app is expected to poll for via GET /offers/me. Real-time push
 * delivery (matching the mockup's "New Request" screen appearing
 * instantly) isn't built - this provides the race/state-management
 * backend and a pollable surface, not live delivery.
 */
@RestController
public class DispatchController {

    private final DispatchService dispatchService;

    public DispatchController(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @PostMapping("/api/v1/dispatch/location")
    public ResponseEntity<Void> recordLocation(@Valid @RequestBody LocationRequest request) {
        CurrentAccount caller = requireDriver();
        dispatchService.recordLocation(caller.accountId(), request.lat(), request.lng());
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/api/v1/dispatch/offers/me")
    public ResponseEntity<OfferResponse> getMyOffer() {
        CurrentAccount caller = requireDriver();
        return dispatchService.findActiveOffer(caller.accountId())
                .map(bookingId -> ResponseEntity.ok(new OfferResponse(bookingId)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/api/v1/dispatch/offers/{bookingId}/accept")
    public ResponseEntity<Void> accept(@PathVariable UUID bookingId) {
        CurrentAccount caller = requireDriver();
        Result<Void, DispatchError> result = dispatchService.acceptOffer(bookingId, caller.accountId());
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/v1/dispatch/offers/{bookingId}/decline")
    public ResponseEntity<Void> decline(@PathVariable UUID bookingId) {
        CurrentAccount caller = requireDriver();
        dispatchService.declineOffer(bookingId, caller.accountId());
        return ResponseEntity.ok().build();
    }

    private CurrentAccount requireDriver() {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (caller.role() != AccountRole.DRIVER) {
            throw ApiException.forbidden("Driver role required");
        }
        return caller;
    }

    private ApiException toApiException(DispatchError error) {
        return switch (error) {
            case OFFER_NOT_FOUND -> ApiException.notFound("No pending offer for this booking");
            case DRIVER_NO_LONGER_ELIGIBLE -> new ApiException(
                    HttpStatus.CONFLICT, "Conflict", "You're no longer eligible for this offer (are you still online?)");
            case BOOKING_ALREADY_ASSIGNED -> new ApiException(
                    HttpStatus.CONFLICT, "Conflict", "Another driver already accepted this booking");
            case ASSIGNMENT_FAILED -> new ApiException(
                    HttpStatus.CONFLICT, "Conflict", "This booking is no longer available");
        };
    }

    public record LocationRequest(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng
    ) {
    }

    public record OfferResponse(UUID bookingId) {
    }
}
