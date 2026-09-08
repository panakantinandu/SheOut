package com.sheout.booking.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.booking.BookingCategory;
import com.sheout.booking.BookingError;
import com.sheout.booking.BookingSummary;
import com.sheout.booking.BookingType;
import com.sheout.booking.GeoAddress;
import com.sheout.booking.RequestBookingCommand;
import com.sheout.booking.internal.BookingService;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Self-service only. This is deliberately NOT where BookingApi's
 * assignDriver is reached from an app - dispatch calls that directly as a
 * Java method, once dispatch exists.
 */
@RestController
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/api/v1/bookings")
    public ResponseEntity<BookingSummary> createBooking(@Valid @RequestBody CreateBookingRequest request) {
        CurrentAccount caller = requireRole(AccountRole.CUSTOMER);
        RequestBookingCommand command = new RequestBookingCommand(
                caller.accountId(),
                request.type(),
                request.category(),
                request.pickup().toGeoAddress(),
                request.drop().toGeoAddress()
        );
        Result<BookingSummary, BookingError> result = bookingService.requestBooking(command);
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(result.value());
    }

    @GetMapping("/api/v1/bookings/me")
    public ResponseEntity<List<BookingSummary>> listMyBookings() {
        CurrentAccount caller = requireAuthenticated();
        List<BookingSummary> bookings = caller.role() == AccountRole.DRIVER
                ? bookingService.listForDriver(caller.accountId())
                : bookingService.listForCustomer(caller.accountId());
        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/api/v1/bookings/{bookingId}")
    public ResponseEntity<BookingSummary> getBooking(@PathVariable UUID bookingId) {
        CurrentAccount caller = requireAuthenticated();
        BookingSummary booking = bookingService.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("No such booking"));
        requireParticipant(caller, booking);
        return ResponseEntity.ok(booking);
    }

    @PostMapping("/api/v1/bookings/{bookingId}/accept")
    public ResponseEntity<BookingSummary> accept(@PathVariable UUID bookingId) {
        CurrentAccount caller = requireRole(AccountRole.DRIVER);
        requireAssignedDriver(caller, bookingId);
        return respond(bookingService.acceptBooking(bookingId));
    }

    @PostMapping("/api/v1/bookings/{bookingId}/start")
    public ResponseEntity<BookingSummary> start(@PathVariable UUID bookingId) {
        CurrentAccount caller = requireRole(AccountRole.DRIVER);
        requireAssignedDriver(caller, bookingId);
        return respond(bookingService.startTrip(bookingId));
    }

    @PostMapping("/api/v1/bookings/{bookingId}/complete")
    public ResponseEntity<BookingSummary> complete(@PathVariable UUID bookingId) {
        CurrentAccount caller = requireRole(AccountRole.DRIVER);
        requireAssignedDriver(caller, bookingId);
        return respond(bookingService.completeTrip(bookingId));
    }

    @PostMapping("/api/v1/bookings/{bookingId}/cancel")
    public ResponseEntity<BookingSummary> cancel(@PathVariable UUID bookingId) {
        CurrentAccount caller = requireAuthenticated();
        BookingSummary booking = bookingService.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("No such booking"));
        requireParticipant(caller, booking);
        return respond(bookingService.cancelBooking(bookingId));
    }

    private ResponseEntity<BookingSummary> respond(Result<BookingSummary, BookingError> result) {
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.ok(result.value());
    }

    private CurrentAccount requireAuthenticated() {
        return CurrentAccountContext.get().orElseThrow(() -> ApiException.unauthorized("Authentication required"));
    }

    private CurrentAccount requireRole(AccountRole role) {
        CurrentAccount caller = requireAuthenticated();
        if (caller.role() != role) {
            throw ApiException.forbidden(role + " role required");
        }
        return caller;
    }

    private void requireParticipant(CurrentAccount caller, BookingSummary booking) {
        boolean isParticipant = caller.accountId().equals(booking.customerId())
                || caller.accountId().equals(booking.driverId());
        if (!isParticipant) {
            throw ApiException.forbidden("Not a participant on this booking");
        }
    }

    private void requireAssignedDriver(CurrentAccount caller, UUID bookingId) {
        BookingSummary booking = bookingService.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("No such booking"));
        if (!caller.accountId().equals(booking.driverId())) {
            throw ApiException.forbidden("Not the driver assigned to this booking");
        }
    }

    private ApiException toApiException(BookingError error) {
        return switch (error) {
            case CUSTOMER_NOT_VERIFIED -> new ApiException(
                    HttpStatus.CONFLICT, "Conflict", "Customer must be gender-verified before booking");
            case CATEGORY_TYPE_MISMATCH -> new ApiException(
                    HttpStatus.BAD_REQUEST, "Bad Request", "category does not match the requested type");
            case BOOKING_NOT_FOUND -> ApiException.notFound("No such booking");
            case INVALID_STATE_TRANSITION -> new ApiException(
                    HttpStatus.CONFLICT, "Conflict", "This action isn't valid for the booking's current status");
        };
    }

    public record GeoAddressRequest(
            @NotBlank String label,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng
    ) {
        GeoAddress toGeoAddress() {
            return new GeoAddress(label, lat, lng);
        }
    }

    public record CreateBookingRequest(
            @NotNull BookingType type,
            @NotNull BookingCategory category,
            @Valid @NotNull GeoAddressRequest pickup,
            @Valid @NotNull GeoAddressRequest drop
    ) {
    }
}
