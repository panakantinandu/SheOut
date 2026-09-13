package com.sheout.booking.internal.fare;

import com.sheout.booking.BookingCategory;
import com.sheout.booking.GeoAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the formula and, more importantly, its boundaries.
 * <p>
 * The arithmetic in the middle is easy to get right and easy to check by
 * hand. The two places pricing actually goes wrong are the edges: a night
 * window that wraps past midnight, and the order the minimum fare is
 * applied in. Both are wrong in ways that look fine in daylight on an
 * average trip.
 */
class DistanceBasedFareCalculatorTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final GeoAddress FROM = new GeoAddress("Hitec City", 17.4435, 78.3772);
    private static final GeoAddress TO = new GeoAddress("Gachibowli", 17.4401, 78.3489);

    /** base 20, 10/km, 1/min, floor 40, night x1.5 between 22:00 and 06:00, no surge. */
    private static final FareRates RATES = new FareRates(
            new BigDecimal("20.00"), new BigDecimal("10.00"), new BigDecimal("1.00"),
            new BigDecimal("40.00"), new BigDecimal("1.50"),
            LocalTime.of(22, 0), LocalTime.of(6, 0), BigDecimal.ONE);

    /** A stand-in router, so nothing here depends on a network call. */
    private static RouteProvider fixedRoute(double km, double minutes) {
        return (pickup, drop) -> new RouteEstimate(km, minutes, RouteEstimate.Source.ROUTED);
    }

    private static DistanceBasedFareCalculator calculatorAt(String istTime, RouteProvider router, FareRates rates) {
        // A fixed instant expressed in IST, so a boundary can be tested
        // without waiting for 22:00 - and so the test does not pass or fail
        // depending on where the machine running it happens to be.
        Instant fixed = LocalTime.parse(istTime).atDate(java.time.LocalDate.of(2026, 3, 1))
                .atZone(IST).toInstant();
        return new DistanceBasedFareCalculator(
                router, Map.of(BookingCategory.BIKE, rates), IST, Clock.fixed(fixed, IST));
    }

    @Test
    @DisplayName("the formula is base + distance + time")
    void addsTheThreeComponents() {
        // 5km at 10/km = 50, 12min at 1/min = 12, base 20 -> 82.
        FareQuote quote = calculatorAt("12:00", fixedRoute(5.0, 12.0), RATES)
                .quote(BookingCategory.BIKE, FROM, TO);

        assertEquals(new BigDecimal("20.00"), quote.baseFare());
        assertEquals(new BigDecimal("50.00"), quote.distanceCharge());
        assertEquals(new BigDecimal("12.00"), quote.timeCharge());
        assertEquals(new BigDecimal("82.00"), quote.amount());
        assertFalse(quote.minimumFareApplied());
    }

    @Test
    @DisplayName("the night window wraps past midnight")
    void nightWindowWrapsMidnight() {
        // A window of 22:00-06:00 is not a range you can test with a simple
        // between, and getting it wrong only shows up at night - on exactly
        // the trips the surcharge exists to get somebody out of bed for.
        assertTrue(RATES.isNight(LocalTime.of(23, 30)));
        assertTrue(RATES.isNight(LocalTime.of(2, 0)));
        assertTrue(RATES.isNight(LocalTime.of(5, 59)));
        assertFalse(RATES.isNight(LocalTime.of(12, 0)));
        assertFalse(RATES.isNight(LocalTime.of(21, 59)));
    }

    @Test
    @DisplayName("the window starts inclusive and ends exclusive")
    void nightWindowBoundaries() {
        assertTrue(RATES.isNight(LocalTime.of(22, 0)), "22:00 exactly is night");
        assertFalse(RATES.isNight(LocalTime.of(6, 0)), "06:00 exactly is not");
    }

    @Test
    @DisplayName("the night multiplier applies at 22:00 and not a minute earlier")
    void nightMultiplierAppliesAtTheBoundary() {
        RouteProvider route = fixedRoute(5.0, 12.0);

        FareQuote justBefore = calculatorAt("21:59", route, RATES).quote(BookingCategory.BIKE, FROM, TO);
        assertEquals(new BigDecimal("82.00"), justBefore.amount());
        assertEquals(BigDecimal.ONE.setScale(2), justBefore.nightMultiplier().setScale(2));

        FareQuote atStart = calculatorAt("22:00", route, RATES).quote(BookingCategory.BIKE, FROM, TO);
        assertEquals(new BigDecimal("123.00"), atStart.amount()); // 82 x 1.5
        assertEquals(new BigDecimal("1.50"), atStart.nightMultiplier());

        FareQuote atEnd = calculatorAt("06:00", route, RATES).quote(BookingCategory.BIKE, FROM, TO);
        assertEquals(new BigDecimal("82.00"), atEnd.amount());
    }

    @Test
    @DisplayName("a trip below the floor is charged the floor, and says so")
    void minimumFareFloorsTheResult() {
        // 0.5km and 2min: 20 + 5 + 2 = 27, under the 40 floor.
        FareQuote quote = calculatorAt("12:00", fixedRoute(0.5, 2.0), RATES)
                .quote(BookingCategory.BIKE, FROM, TO);

        assertEquals(new BigDecimal("40.00"), quote.amount());
        assertTrue(quote.minimumFareApplied());
    }

    @Test
    @DisplayName("the floor is a floor, not something the night multiplier lifts")
    void minimumFareIsNotMultiplied() {
        // The ordering bug this guards against: applying the floor before
        // the multipliers would charge 40 x 1.5 = 60 for a one-minute trip
        // at night - a surcharge on a fare that was never earned.
        //
        // The numbers have to be chosen so the NIGHT-MULTIPLIED subtotal is
        // still under the floor, or the test proves nothing. 0.2km and 1min
        // is 20 + 2 + 1 = 23, and 23 x 1.5 = 34.50, still under 40. The
        // first version of this test used 0.5km/2min, where the night fare
        // came to 40.50 and cleared the floor on its own - it would have
        // passed against a broken ordering.
        FareQuote quote = calculatorAt("23:00", fixedRoute(0.2, 1.0), RATES)
                .quote(BookingCategory.BIKE, FROM, TO);

        assertEquals(new BigDecimal("40.00"), quote.amount());
        assertTrue(quote.minimumFareApplied());
    }

    @Test
    @DisplayName("surge multiplies the whole subtotal, base included")
    void surgeAppliesToTheSubtotal() {
        FareRates surging = new FareRates(
                RATES.baseFare(), RATES.perKmRate(), RATES.perMinuteRate(), RATES.minimumFare(),
                RATES.nightMultiplier(), RATES.nightWindowStart(), RATES.nightWindowEnd(),
                new BigDecimal("2.00"));

        FareQuote quote = calculatorAt("12:00", fixedRoute(5.0, 12.0), surging)
                .quote(BookingCategory.BIKE, FROM, TO);

        assertEquals(new BigDecimal("164.00"), quote.amount()); // 82 x 2
        assertEquals(new BigDecimal("2.00"), quote.surgeMultiplier());
    }

    @Test
    @DisplayName("surge and night compound, and the quote shows both")
    void surgeAndNightCompound() {
        FareRates surging = new FareRates(
                RATES.baseFare(), RATES.perKmRate(), RATES.perMinuteRate(), RATES.minimumFare(),
                RATES.nightMultiplier(), RATES.nightWindowStart(), RATES.nightWindowEnd(),
                new BigDecimal("2.00"));

        FareQuote quote = calculatorAt("23:00", fixedRoute(5.0, 12.0), surging)
                .quote(BookingCategory.BIKE, FROM, TO);

        assertEquals(new BigDecimal("246.00"), quote.amount()); // 82 x 2 x 1.5
    }

    @Test
    @DisplayName("a fare built on a fallback estimate is marked as one")
    void estimatedRoutesAreNotPassedOffAsRouted() {
        RouteProvider estimating = (pickup, drop) ->
                new RouteEstimate(5.0, 12.0, RouteEstimate.Source.ESTIMATED);

        FareQuote quote = calculatorAt("12:00", estimating, RATES).quote(BookingCategory.BIKE, FROM, TO);

        assertFalse(quote.routed(), "a guess must never be reported as a measurement");
        assertEquals(new BigDecimal("82.00"), quote.amount());
    }

    @Test
    @DisplayName("a zero-length night window switches the surcharge off rather than making it permanent")
    void zeroLengthWindowMeansNoNight() {
        FareRates noNight = new FareRates(
                RATES.baseFare(), RATES.perKmRate(), RATES.perMinuteRate(), RATES.minimumFare(),
                RATES.nightMultiplier(), LocalTime.of(0, 0), LocalTime.of(0, 0), BigDecimal.ONE);

        assertFalse(noNight.isNight(LocalTime.of(2, 0)));
        assertFalse(noNight.isNight(LocalTime.of(14, 0)));
    }
}
