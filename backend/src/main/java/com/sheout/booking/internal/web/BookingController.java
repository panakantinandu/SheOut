package com.sheout.booking.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.booking.BookingCategory;
import com.sheout.booking.BookingError;
import com.sheout.booking.BookingQuery;
import com.sheout.booking.BookingStatus;
import com.sheout.booking.CancellationReason;
import com.sheout.sharedkernel.geo.ServiceArea;
import com.sheout.booking.internal.fare.FareQuote;
import com.sheout.booking.internal.fare.RoutePath;
import com.sheout.booking.internal.fare.RouteProvider;
import com.sheout.booking.BookingSummary;
import com.sheout.booking.BookingType;
import com.sheout.booking.GeoAddress;
import com.sheout.booking.RequestBookingCommand;
import com.sheout.booking.internal.BookingService;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.ratelimit.RateLimiter;
import com.sheout.sharedkernel.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import com.sheout.sharedkernel.web.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
import java.time.Duration;
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

    private static final Duration PICKUP_WINDOW = Duration.ofMinutes(15);

    private final BookingService bookingService;
    private final ServiceArea serviceArea;
    private final RouteProvider routeProvider;
    private final RateLimiter rateLimiter;
    private final int pickupAttemptLimit;

    public BookingController(BookingService bookingService, ServiceArea serviceArea, RouteProvider routeProvider,
                             RateLimiter rateLimiter,
                             @Value("${sheout.rate-limit.pickup-code-per-driver:10}") int pickupAttemptLimit) {
        this.bookingService = bookingService;
        this.serviceArea = serviceArea;
        this.routeProvider = routeProvider;
        this.rateLimiter = rateLimiter;
        this.pickupAttemptLimit = pickupAttemptLimit;
    }

    /** One decimal is all a "2.4 km away" line can use; more would imply a precision the router does not have. */
    private static BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
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
                BigDecimal.valueOf(quote.durationMinutes()).setScale(0, RoundingMode.HALF_UP),
                quote.routed(),
                quote.baseFare(),
                quote.distanceCharge(),
                quote.timeCharge(),
                quote.surgeMultiplier(),
                quote.nightMultiplier(),
                quote.minimumFareApplied(),
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
            @RequestParam(required = false) Set<BookingStatus> status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Set<BookingCategory> category,
            @RequestParam(required = false) @Size(max = 100) String q) {
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

    /**
     * Starts the trip, and only on proof the partner is at the pickup.
     * <p>
     * The body is required and carries the code the rider read out. This
     * used to be a bare tap with no verification at all, which meant a
     * partner could start and complete a trip for a rider who was never
     * collected, and the rider was charged for it.
     * <p>
     * The check itself lives in the service, in the same transaction as the
     * status change - not here. A guard in a controller is a guard exactly
     * one caller respects.
     */
    @PostMapping("/api/v1/bookings/{bookingId}/start")
    public ResponseEntity<BookingSummary> start(@PathVariable UUID bookingId,
                                                 @Valid @RequestBody StartTripRequest request) {
        CurrentAccount caller = requireRole(AccountRole.DRIVER);
        requireAssignedDriver(caller, bookingId);
        // Per partner, across every trip she holds, and after the assignment
        // check so a refusal can never tell anyone a booking exists. The
        // per-trip lockout in startTrip bounds guesses on one code; this
        // bounds the rate at which anyone can guess at all, including in a
        // parallel burst. See PickupCode.MAX_ATTEMPTS for why honest typing
        // never comes near it.
        rateLimiter.tryConsume("pickup-code:driver:" + caller.accountId(), pickupAttemptLimit, PICKUP_WINDOW)
                .orThrow("Too many pickup code attempts. Please wait a few minutes, or call support.");
        return respond(bookingService.startTrip(bookingId, request.pickupCode()));
    }

    /**
     * The code the rider reads to her partner at the kerb.
     * <p>
     * Hers alone. It is deliberately not a field on the booking, because
     * {@code GET /bookings/{id}} serves both participants and a field there
     * would hand the partner the answer to the question she is being asked -
     * which would leave the check looking like verification while verifying
     * nothing.
     * <p>
     * 404 for every refusal, the enumeration-safe convention this codebase
     * uses throughout: not her booking, no such booking, or a booking not in
     * a state that has a code, are one answer from outside. Her own screen
     * knows which status she is in and says so without being told anything.
     */
    /**
     * The road route from where the partner is now to where she is going
     * next, for drawing on her map.
     * <p>
     * WHICH destination is the booking's business, not the caller's: during
     * ACCEPTED it is the pickup, during IN_PROGRESS it is the drop. Taking a
     * destination as a parameter would let a partner's map show a route to
     * the drop while she is still meant to be collecting somebody, which is
     * exactly the confusion the two-phase flow exists to remove.
     * <p>
     * Her own position comes from the request, not from the dispatch
     * location store. The device knows where it is; reading its own position
     * back out of the server would add a round trip, a staleness window and
     * a failure mode, to tell her something she already knows.
     * <p>
     * Deliberately NOT cached or polled server-side. The apps fetch this
     * about twice per trip - once per phase, plus a refresh if she strays
     * well off the line - because the route is drawn for orientation and the
     * real turn-by-turn happens in Google Maps. Re-routing on every location
     * ping would multiply calls to a volunteer-run OSRM instance by fifty.
     */
    @GetMapping("/api/v1/bookings/{bookingId}/route")
    public ResponseEntity<RouteResponse> route(
            @PathVariable UUID bookingId,
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double fromLat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double fromLng) {
        CurrentAccount caller = requireRole(AccountRole.DRIVER);
        BookingSummary booking = bookingService.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("No such booking"));
        if (!caller.accountId().equals(booking.driverId())) {
            throw ApiException.notFound("No such booking");
        }

        GeoAddress destination = switch (booking.status()) {
            case ACCEPTED -> booking.pickup();
            case IN_PROGRESS -> booking.drop();
            default -> throw ApiException.notFound("No route for this booking's current status");
        };

        RoutePath path = routeProvider.routePath(
                new GeoAddress("You", fromLat, fromLng), destination);

        return ResponseEntity.ok(new RouteResponse(
                booking.status() == BookingStatus.ACCEPTED ? "PICKUP" : "DROP",
                destination.lat(),
                destination.lng(),
                destination.label(),
                path.points().stream().map(p -> new RoutePointResponse(p.lat(), p.lng())).toList(),
                path.isAvailable() ? round(path.distanceKm()) : null,
                path.isAvailable() ? round(path.durationMinutes()) : null));
    }

    @GetMapping("/api/v1/bookings/{bookingId}/pickup-code")
    public ResponseEntity<PickupCodeResponse> pickupCode(@PathVariable UUID bookingId) {
        CurrentAccount caller = requireAuthenticated();
        return bookingService.findPickupCodeForCustomer(bookingId, caller.accountId())
                .map(code -> ResponseEntity.ok(new PickupCodeResponse(code)))
                .orElseThrow(() -> ApiException.notFound("No pickup code for this booking"));
    }

    @PostMapping("/api/v1/bookings/{bookingId}/complete")
    public ResponseEntity<BookingSummary> complete(@PathVariable UUID bookingId) {
        CurrentAccount caller = requireRole(AccountRole.DRIVER);
        requireAssignedDriver(caller, bookingId);
        return respond(bookingService.completeTrip(bookingId));
    }

    /**
     * Cancels a booking. The reason is part of the request, not optional.
     * <p>
     * The body is required, so an old client that posts nothing gets a 400
     * naming the missing reason rather than silently cancelling without one.
     * That is the right failure: a cancellation with no reason is exactly
     * what this change exists to stop recording.
     */
    @PostMapping("/api/v1/bookings/{bookingId}/cancel")
    public ResponseEntity<BookingSummary> cancel(
            @PathVariable UUID bookingId,
            @Valid @RequestBody CancelRequest request) {
        CurrentAccount caller = requireAuthenticated();
        BookingSummary booking = bookingService.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("No such booking"));
        requireParticipant(caller, booking);
        return respond(bookingService.cancelBooking(
                bookingId, caller.accountId(), request.reason(), request.note()));
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
            case CANCELLATION_REASON_REQUIRED -> new ApiException(
                    HttpStatus.BAD_REQUEST, "Bad Request", "Please choose a reason for cancelling");
            case CANCELLATION_NOTE_REQUIRED -> new ApiException(
                    HttpStatus.BAD_REQUEST, "Bad Request", "Please say a little more about why you are cancelling");
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
            // A machine-readable code rather than the reason phrase, for the
            // same reason OUTSIDE_SERVICE_AREA carries one: the driver app
            // has to tell a wrong code apart from a lockout so it can keep
            // the keypad open for one and not the other. The message says
            // what to do next, because a partner reading "invalid" at a kerb
            // needs to know whether to retype or to ask again.
            case INVALID_PICKUP_CODE -> new ApiException(
                    HttpStatus.BAD_REQUEST, "INVALID_PICKUP_CODE",
                    "That code doesn't match. Ask your rider to read it out again from her screen.");
            case PICKUP_CODE_REQUIRED -> new ApiException(
                    HttpStatus.BAD_REQUEST, "PICKUP_CODE_REQUIRED",
                    "Enter the four-digit code your rider reads out to you.");
            case PICKUP_VERIFICATION_LOCKED -> new ApiException(
                    HttpStatus.CONFLICT, "PICKUP_VERIFICATION_LOCKED",
                    "Too many wrong codes for this trip. Call support and they will sort it out with you.");
        };
    }

    /** The reason is required; the note only when the reason is OTHER - checked in the service so every caller gets the same rule. */
    public record CancelRequest(
            @NotNull CancellationReason reason,
            @Size(max = 500) String note
    ) {
    }

    /**
     * The code the partner typed.
     * <p>
     * Validated for shape here so an empty field fails as a validation error
     * naming the field, rather than being counted as a wrong guess against
     * the booking's attempt limit. Whether it is the RIGHT code is the
     * service's question, not this one's.
     */
    public record StartTripRequest(
            @NotBlank @Pattern(regexp = "\\d{4}", message = "must be the four-digit code your rider reads out")
            String pickupCode
    ) {
    }

    /** Shown to the rider, read aloud to her partner. Never served to the driver - see the endpoint above. */
    public record PickupCodeResponse(String pickupCode) {
    }

    /**
     * Where the partner is headed next, and the road that gets her there.
     * <p>
     * {@code phase} is the server's answer to "which leg is this", derived
     * from the booking's own status so the map and the booking cannot
     * disagree about which half of the trip is happening.
     * <p>
     * {@code points} is empty and the two figures are null when the router
     * could not be reached. The app then draws the destination without a
     * line and says the route is unavailable, rather than drawing a straight
     * one that would read as a road.
     */
    public record RouteResponse(
            String phase,
            double destinationLat,
            double destinationLng,
            String destinationLabel,
            List<RoutePointResponse> points,
            BigDecimal distanceKm,
            BigDecimal durationMinutes
    ) {
    }

    public record RoutePointResponse(double lat, double lng) {
    }

    public record GeoAddressRequest(
            // pickup_label/drop_label are varchar(255). Without this a longer
            // label reached the insert and failed there, as a 500.
            @NotBlank @Size(max = 255) String label,
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
     * The price and how it was reached.
     * <p>
     * distanceKm is real road distance now, not the straight line it used to
     * be, rounded to one decimal for display.
     * <p>
     * A single amount, not a range. The mockup shows "₹42 - 58", but this
     * calculator is deterministic: there is no spread to report, and
     * inventing one would imply a variability the pricing does not have and
     * would not match the fare the booking is then created with.
     * <p>
     * The breakdown is returned, not just the total, because a rider asking
     * why a short trip cost what it did deserves an answer, and because the
     * same shape is what a partner's payout breakdown will be built from.
     * A single number invites the suspicion that it was made up.
     * <p>
     * routed says whether the distance is a real road route or a fallback
     * estimate. It is surfaced rather than hidden so a fare built on a guess
     * is never mistaken for one built on a measurement.
     */
    public record FareQuoteResponse(
            BigDecimal fareEstimate,
            BigDecimal distanceKm,
            BigDecimal durationMinutes,
            boolean routed,
            BigDecimal baseFare,
            BigDecimal distanceCharge,
            BigDecimal timeCharge,
            BigDecimal surgeMultiplier,
            BigDecimal nightMultiplier,
            boolean minimumFareApplied,
            BookingCategory category
    ) {
    }
}
