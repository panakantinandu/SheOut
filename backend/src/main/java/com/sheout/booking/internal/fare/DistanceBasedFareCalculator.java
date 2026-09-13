package com.sheout.booking.internal.fare;

import com.sheout.booking.BookingCategory;
import com.sheout.booking.GeoAddress;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * The fare formula.
 * <p>
 * <pre>
 *   subtotal = baseFare + (perKmRate x km) + (perMinuteRate x minutes)
 *   fare     = max(subtotal x surge x night, minimumFare)
 * </pre>
 * <p>
 * Distance and duration are real road figures from a router, not a straight
 * line - which is what this class used to price on, and what made it a
 * placeholder. A crow-flies fare systematically underpays a partner on
 * every trip where the road bends, which in a city is every trip.
 * <p>
 * ORDER MATTERS, and each step is deliberate.
 * <p>
 * Surge and the night multiplier apply to the whole subtotal, including the
 * base fare, because what they are compensating for is the trip being
 * harder to serve - and that applies to the whole trip, not only the part
 * measured in kilometres.
 * <p>
 * The minimum fare is applied LAST, as a floor on what the rider actually
 * pays. Applying it before the multipliers would multiply the floor, so a
 * two-minute trip at night would cost the minimum times 1.3 - a surcharge
 * on a fare that was never earned in the first place. A floor is a floor.
 * <p>
 * Every constant comes from configuration. There is not a rupee value in
 * this file, and there should never be one: pricing is the thing most
 * likely to need changing at short notice, and a redeploy is not an
 * acceptable cost for answering a driver who says a rate does not cover
 * fuel.
 */
@Component
public class DistanceBasedFareCalculator implements FareCalculator {

    private static final BigDecimal ONE = BigDecimal.ONE.setScale(2, RoundingMode.UNNECESSARY);

    private final RouteProvider routeProvider;
    private final Map<BookingCategory, FareRates> rates;
    private final ZoneId zoneId;
    private final Clock clock;

    // Annotated because there are two constructors and Spring cannot guess.
    // The other one takes a Clock and exists so the night-window boundaries
    // can be tested without waiting for 22:00.
    @Autowired
    DistanceBasedFareCalculator(RouteProvider routeProvider,
                                 Map<BookingCategory, FareRates> rates,
                                 ZoneId fareZoneId) {
        // Clock.systemUTC() rather than a captured "now": the zone is applied
        // explicitly below, so the clock only has to be a source of instants.
        this(routeProvider, rates, fareZoneId, Clock.systemUTC());
    }

    /** Test seam - lets the night-window boundaries be exercised without waiting for 22:00. */
    DistanceBasedFareCalculator(RouteProvider routeProvider,
                                 Map<BookingCategory, FareRates> rates,
                                 ZoneId fareZoneId,
                                 Clock clock) {
        this.routeProvider = routeProvider;
        this.rates = rates;
        this.zoneId = fareZoneId;
        this.clock = clock;
    }

    @Override
    public FareQuote quote(BookingCategory category, GeoAddress pickup, GeoAddress drop) {
        FareRates rate = rates.get(category);
        if (rate == null) {
            // Every category in the enum is configured, so this is a new
            // category added without a price. Failing loudly is right:
            // silently falling back to some other category's rates would
            // ship a wrong price rather than an obvious error.
            throw new IllegalStateException("No fare rates configured for category " + category);
        }

        RouteEstimate route = routeProvider.route(pickup, drop);
        BigDecimal distanceCharge = rate.perKmRate()
                .multiply(BigDecimal.valueOf(route.distanceKm()))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal timeCharge = rate.perMinuteRate()
                .multiply(BigDecimal.valueOf(route.durationMinutes()))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal subtotal = rate.baseFare().add(distanceCharge).add(timeCharge);

        BigDecimal surge = rate.surgeMultiplier();
        LocalTime localNow = LocalTime.now(clock.withZone(zoneId));
        boolean night = rate.isNight(localNow);
        BigDecimal nightMultiplier = night ? rate.nightMultiplier() : ONE;

        BigDecimal beforeFloor = subtotal
                .multiply(surge)
                .multiply(nightMultiplier)
                .setScale(2, RoundingMode.HALF_UP);

        boolean floored = beforeFloor.compareTo(rate.minimumFare()) < 0;
        BigDecimal amount = floored ? rate.minimumFare().setScale(2, RoundingMode.HALF_UP) : beforeFloor;

        return new FareQuote(
                amount,
                round(route.distanceKm()),
                round(route.durationMinutes()),
                route.isRouted(),
                rate.baseFare().setScale(2, RoundingMode.HALF_UP),
                distanceCharge,
                timeCharge,
                surge,
                nightMultiplier,
                floored);
    }

    /** One decimal place is as much precision as a routed estimate honestly carries. */
    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
