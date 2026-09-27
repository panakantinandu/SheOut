package com.sheout.booking.internal.web;

import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.booking.GeoAddress;
import com.sheout.booking.internal.fare.RoutePath;
import com.sheout.booking.internal.fare.RouteProvider;
import com.sheout.sharedkernel.geo.ServiceArea;
import com.sheout.sharedkernel.ratelimit.RateLimiter;
import com.sheout.sharedkernel.web.ApiException;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;

/**
 * The road between two points, for drawing the purple line from pickup to
 * drop - on the booking screens, while a rider watches her trip, and on a
 * partner's offer.
 * <p>
 * From SheOut's own OSRM, through the same RouteProvider.routePath a
 * partner's navigation line already uses. Not Google Directions: that would
 * be a paid call for a shape our own router gives for nothing. Not the fare
 * quote either: pricing asks OSRM for distance only, on purpose (see
 * RoutePath), and this changes nothing about how a fare is worked out.
 * <p>
 * Only inside the service area, and limited per account, so it cannot be
 * used as a free routing service for anywhere else. Signed in, any role.
 * An unreachable router answers with no points - the map then draws no line
 * rather than a straight one that would cross a lake.
 */
@RestController
@Validated
public class RoutePreviewController {

    private final RouteProvider routes;
    private final ServiceArea serviceArea;
    private final RateLimiter rateLimiter;

    public RoutePreviewController(RouteProvider routes, ServiceArea serviceArea, RateLimiter rateLimiter) {
        this.routes = routes;
        this.serviceArea = serviceArea;
        this.rateLimiter = rateLimiter;
    }

    public record RoutePreviewResponse(List<BookingController.RoutePointResponse> points, BigDecimal distanceKm,
                                       BigDecimal durationMinutes) {
    }

    @GetMapping("/api/v1/routes/preview")
    public ResponseEntity<RoutePreviewResponse> preview(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double fromLat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double fromLng,
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double toLat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double toLng) {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        // A booking screen asks again as she moves a pin; a tracking or offer
        // screen once. Thirty a minute is well above either.
        rateLimiter.tryConsume("route-preview:" + caller.accountId(), 30, Duration.ofMinutes(1))
                .orThrow("Too many requests. Please wait a moment.");
        if (!serviceArea.covers(fromLat, fromLng) || !serviceArea.covers(toLat, toLng)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OUTSIDE_SERVICE_AREA", "Both points must be inside the service area.");
        }
        RoutePath path = routes.routePath(new GeoAddress("from", fromLat, fromLng), new GeoAddress("to", toLat, toLng));
        return ResponseEntity.ok(new RoutePreviewResponse(
                path.points().stream().map(p -> new BookingController.RoutePointResponse(p.lat(), p.lng())).toList(),
                path.isAvailable() ? BigDecimal.valueOf(path.distanceKm()).setScale(1, RoundingMode.HALF_UP) : null,
                path.isAvailable() ? BigDecimal.valueOf(path.durationMinutes()).setScale(0, RoundingMode.HALF_UP) : null));
    }
}
