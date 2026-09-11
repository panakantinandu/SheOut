package com.sheout.dispatch.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.booking.BookingApi;
import com.sheout.booking.BookingCategory;
import com.sheout.booking.BookingError;
import com.sheout.booking.BookingParticipants;
import com.sheout.booking.BookingSummary;
import com.sheout.booking.GeoAddress;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
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
    private final BookingApi bookingApi;

    public DispatchController(DispatchService dispatchService, BookingApi bookingApi) {
        this.dispatchService = dispatchService;
        this.bookingApi = bookingApi;
    }

    @PostMapping("/api/v1/dispatch/location")
    public ResponseEntity<Void> recordLocation(@Valid @RequestBody LocationRequest request) {
        CurrentAccount caller = requireDriver();
        dispatchService.recordLocation(caller.accountId(), request.lat(), request.lng());
        return ResponseEntity.accepted().build();
    }

    /**
     * Enriched with pickup/drop/fare/category from the booking itself
     * (via BookingApi.findById - a driver who's only been offered this
     * booking, not yet accepted it, isn't a participant yet, so
     * GET /api/v1/bookings/{id} would 403 them - see BookingApi's
     * Javadoc on findById). If the booking has since vanished somehow,
     * falls back to the bare bookingId rather than hiding the offer
     * entirely.
     */
    @GetMapping("/api/v1/dispatch/offers/me")
    public ResponseEntity<OfferResponse> getMyOffer() {
        CurrentAccount caller = requireDriver();
        return dispatchService.findActiveOffer(caller.accountId())
                .map(this::toOfferResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private OfferResponse toOfferResponse(UUID bookingId) {
        Optional<BookingSummary> booking = bookingApi.findById(bookingId);
        return booking
                .map(b -> new OfferResponse(bookingId, b.pickup(), b.drop(), b.fareEstimate(), b.category()))
                .orElseGet(() -> new OfferResponse(bookingId, null, null, null, null));
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
                    // Covers both reasons this can now fail - going offline, and
                    // verification no longer being current - without saying which,
                    // since the driver's own screens already show both states.
                    HttpStatus.CONFLICT, "Conflict",
                    "You're no longer eligible for this offer - check you're online and your verification is up to date");
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

    /** pickup/drop/fareEstimate/category are null only if the booking has vanished between the offer being made and this being read - see toOfferResponse. */
    public record OfferResponse(UUID bookingId, GeoAddress pickup, GeoAddress drop, BigDecimal fareEstimate, BookingCategory category) {
    }

    /**
     * The assigned driver's last known position for this booking, polled by
     * the customer's tracking map. Read-only and deliberately dumb: it
     * returns the last position dispatch actually received, with the moment
     * it arrived, and never interpolates or extrapolates - the marker must
     * only ever sit where the driver really reported being.
     * <p>
     * Authorization follows this codebase's per-resource rule: anyone who
     * is not this booking's customer gets the same 404, with the same
     * message, as a bookingId that does not exist, so the two cannot be
     * told apart. Only the customer is allowed - a driver watching their
     * own position uses the browser's geolocation instead, and nobody else
     * has any business reading where a driver is.
     * <p>
     * 404 also covers "no driver assigned yet" and "driver has not reported
     * a position yet". Those are normal states while a customer waits, not
     * errors - the tracking screen polls until one arrives. They carry
     * distinct messages, which leaks nothing: only the booking's own
     * customer can ever reach them.
     */
    @GetMapping("/api/v1/dispatch/bookings/{bookingId}/driver-location")
    public ResponseEntity<DriverLocationResponse> driverLocation(@PathVariable UUID bookingId) {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));

        Result<BookingParticipants, BookingError> participants = bookingApi.getParticipants(bookingId);
        if (participants.isFailure() || !caller.accountId().equals(participants.value().customerId())) {
            throw ApiException.notFound("No such booking");
        }
        UUID driverId = participants.value().driverId();
        if (driverId == null) {
            throw ApiException.notFound("No driver assigned to this booking yet");
        }
        return dispatchService.findDriverLocation(driverId)
                .map(location -> ResponseEntity.ok(new DriverLocationResponse(
                        location.lat(), location.lng(), location.recordedAt())))
                .orElseThrow(() -> ApiException.notFound("No location reported for this driver yet"));
    }

    /** recordedAt lets the client show staleness instead of implying a stale point is live. */
    public record DriverLocationResponse(double lat, double lng, Instant recordedAt) {
    }
}
