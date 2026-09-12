package com.sheout.booking.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.booking.BookingCategory;
import com.sheout.booking.BookingError;
import com.sheout.booking.BookingQuery;
import com.sheout.booking.BookingStatus;
import com.sheout.booking.internal.ServiceArea;
import com.sheout.booking.internal.fare.FareQuote;
import com.sheout.booking.BookingSummary;
import com.sheout.booking.BookingType;
import com.sheout.booking.GeoAddress;
import com.sheout.booking.RequestBookingCommand;
import com.sheout.booking.internal.BookingService;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.sharedkernel.web.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Self-service only. This is deliberately NOT where BookingApi's
 * assignDriver is reached from an app - dispatch calls that directly as a
 * Java method, once dispatch exists.
 */
@RestController
public class BookingController {

    private final BookingService bookingService;
    private final ServiceArea serviceArea;

    public BookingController(BookingService bookingService, ServiceArea serviceArea) {
        this.bookingService = bookingService;
        this.serviceArea = serviceArea;
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

    /**
     * Prices a trip without creating one. Pure calculation: no booking row,
     * no event, nothing persisted, so it is safe to call repeatedly as a
     * customer changes their destination.
     * <p>
     * It runs the same FareCalculator that requestBooking runs, so the
     * quoted amount is the amount the resulting booking is created with,
     * not an approximation of it.
     * <p>
     * Kept off BookingApi on purpose. This codebase's rule is that the
     * public interface carries cross-module Java callers, and nothing
     * outside booking needs to price a trip - the caller is the customer
     * app over HTTP. Same reasoning NotificationLogController's Javadoc
     * records for its own self-service endpoint.
     * <p>
     * Authenticated, but with no role check beyond that: a driver or admin
     * asking what a trip costs is harmless, and pricing is not
     * customer-private data.
     */
    @PostMapping("/api/v1/bookings/quote")
    public ResponseEntity<FareQuoteResponse> quote(@Valid @RequestBody QuoteRequest request) {
        requireAuthenticated();
        if (request.category().expectedType() != request.type()) {
            throw toApiException(BookingError.CATEGORY_TYPE_MISMATCH);
        }
        // Quoting refuses an out-of-area trip too, not only booking. A price
        // for a trip that can never be booked is worse than no price: it
        // reads as a promise, and the customer finds out it was not one only
        // after filling in both ends.
        Result<FareQuote, BookingError> result = bookingService.quoteFare(
                request.category(), request.pickup().toGeoAddress(), request.drop().toGeoAddress());
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        FareQuote quote = result.value();
        return ResponseEntity.ok(new FareQuoteResponse(
                quote.amount(),
                BigDecimal.valueOf(quote.distanceKm()).setScale(1, RoundingMode.HALF_UP),
                request.category()));
    }

    /**
     * Unpaged, and staying that way. The driver dashboard sums today's
     * earnings and the Earnings screen sums a period across every completed
     * trip; paging those would mean adding up pages in the browser and
     * showing a different total depending on how far someone had scrolled.
     * See /search below for the list a person actually reads.
     */
    @GetMapping("/api/v1/bookings/me")
    public ResponseEntity<List<BookingSummary>> listMyBookings() {
        CurrentAccount caller = requireAuthenticated();
        List<BookingSummary> bookings = caller.role() == AccountRole.DRIVER
                ? bookingService.listForDriver(caller.accountId())
                : bookingService.listForCustomer(caller.accountId());
        return ResponseEntity.ok(bookings);
    }

    /**
     * The caller's own trips, paged and filtered. Scoped by the token, not
     * by a parameter: there is no customerId or driverId to pass, so no
     * request can page through anybody else's history.
     * <p>
     * GET with query parameters rather than a POST body, because it is a
     * read - so a filtered list is a link that can be bookmarked, shared
     * with support, or re-opened by the browser's back button.
     */
    @GetMapping("/api/v1/bookings/me/search")
    public ResponseEntity<PageResponse<BookingSummary>> searchMyBookings(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Set<BookingCategory> category,
            @RequestParam(required = false) String q) {
        CurrentAccount caller = requireAuthenticated();
        BookingQuery query = new BookingQuery(status, from, to, category, q);
        Pageable pageable = PageRequest.of(
                PageResponse.normalizePage(page), PageResponse.normalizePageSize(pageSize));
        Page<BookingSummary> result = caller.role() == AccountRole.DRIVER
                ? bookingService.pageForDriver(caller.accountId(), query, pageable)
                : bookingService.pageForCustomer(caller.accountId(), query, pageable);
        return ResponseEntity.ok(PageResponse.from(result, summary -> summary));
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

    /**
     * Not-found rather than forbidden, and deliberately the same message
     * the missing-booking lookups above use: a 403 here would confirm that
     * an id names a real booking, letting any authenticated caller probe
     * ids and learn which exist. The caller cannot distinguish "no such
     * booking" from "not yours". Keep these messages identical - letting
     * them drift reopens the gap the status code closes. Contrast
     * requireRole's 403, which is about the caller's own role and reveals
     * nothing about any particular booking.
     */
    private void requireParticipant(CurrentAccount caller, BookingSummary booking) {
        boolean isParticipant = caller.accountId().equals(booking.customerId())
                || caller.accountId().equals(booking.driverId());
        if (!isParticipant) {
            throw ApiException.notFound("No such booking");
        }
    }

    /** Same not-found-rather-than-forbidden reasoning as requireParticipant. */
    private void requireAssignedDriver(CurrentAccount caller, UUID bookingId) {
        BookingSummary booking = bookingService.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("No such booking"));
        if (!caller.accountId().equals(booking.driverId())) {
            throw ApiException.notFound("No such booking");
        }
    }

    private ApiException toApiException(BookingError error) {
        return switch (error) {
            case CUSTOMER_NOT_VERIFIED -> new ApiException(
                    HttpStatus.CONFLICT, "Conflict", "Customer must be gender-verified before booking");
            // The one case where `error` carries a machine-readable code
            // rather than the HTTP reason phrase. The apps need to tell this
            // refusal apart from every other 409 so they can name the real
            // reason instead of showing a generic failure, and this field is
            // the one the standard error shape already has for the kind of
            // error. The message carries the configured radius so it stays
            // true when the boundary is widened.
            case OUTSIDE_SERVICE_AREA -> new ApiException(
                    HttpStatus.CONFLICT, "OUTSIDE_SERVICE_AREA",
                    "SheOut currently operates only in and around " + serviceArea.centreName()
                            + ". Pickup and drop must both be within "
                            + Math.round(serviceArea.radiusKm()) + "km of the city.");
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

    /** Same shape as CreateBookingRequest - quoting and booking take the same inputs by definition. */
    public record QuoteRequest(
            @NotNull BookingType type,
            @NotNull BookingCategory category,
            @Valid @NotNull GeoAddressRequest pickup,
            @Valid @NotNull GeoAddressRequest drop
    ) {
    }

    /**
     * distanceKm is the straight-line distance the fare was derived from,
     * rounded to one decimal for display. It is not a routed distance - see
     * DistanceBasedFareCalculator.
     * <p>
     * A single amount, not a range. The mockup shows "₹42 - 58", but this
     * calculator is deterministic: there is no spread to report, and
     * inventing one would imply a variability the pricing does not have and
     * would not match the fare the booking is then created with.
     */
    public record FareQuoteResponse(
            BigDecimal fareEstimate,
            BigDecimal distanceKm,
            BookingCategory category
    ) {
    }
}
