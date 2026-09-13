package com.sheout.booking.internal.fare;

import com.sheout.booking.BookingCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;

/**
 * Reads every pricing number out of configuration and hands the calculator
 * a table it can look categories up in.
 * <p>
 * Written out one @Value at a time rather than bound from a nested
 * structure, which is more lines but buys two things worth having. Each
 * setting gets the exact environment variable name it is meant to have -
 * BIKE_PER_KM_RATE, not SHEOUT_FARE_CATEGORIES_BIKE_PERKMRATE - so whoever
 * is changing a price under pressure can find it. And every default is
 * visible here in one place, next to the thing it prices.
 */
@Configuration
public class FareConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FareConfiguration.class);

    /**
     * The night window is a wall-clock thing, so it needs a wall clock.
     * <p>
     * Explicitly configured rather than taken from the server's default
     * zone. Render runs in UTC; a 22:00-06:00 window read in UTC would apply
     * the night surcharge from 03:30 to 11:30 local, charging extra over
     * breakfast and nothing at midnight. That is not a rounding error, it is
     * the surcharge landing on the wrong half of the day.
     */
    @Bean
    ZoneId fareZoneId(@Value("${sheout.fare.timezone:Asia/Kolkata}") String timezone) {
        ZoneId zone = ZoneId.of(timezone);
        log.info("Fare night windows evaluated in {}", zone);
        return zone;
    }

    /**
     * Every category's rates, defaulted from Rapido's published Hyderabad
     * structure so the starting point is plausible rather than invented.
     * <p>
     * BIKE and PARCEL are the two live categories and are tuned separately.
     * AUTO, CAB and LUNCHBOX are not launched; they carry sensible
     * placeholders so that enabling one is a config change rather than a
     * missing-key crash at the first booking.
     */
    @Bean
    Map<BookingCategory, FareRates> fareRates(
            @Value("${BIKE_BASE_FARE:22.00}") BigDecimal bikeBase,
            @Value("${BIKE_PER_KM_RATE:9.00}") BigDecimal bikePerKm,
            @Value("${BIKE_PER_MINUTE_RATE:1.20}") BigDecimal bikePerMinute,
            @Value("${BIKE_MINIMUM_FARE:37.00}") BigDecimal bikeMinimum,
            @Value("${BIKE_NIGHT_MULTIPLIER:1.30}") BigDecimal bikeNight,
            @Value("${BIKE_NIGHT_WINDOW_START:22:00}") String bikeNightStart,
            @Value("${BIKE_NIGHT_WINDOW_END:06:00}") String bikeNightEnd,
            @Value("${BIKE_SURGE_MULTIPLIER:1.00}") BigDecimal bikeSurge,

            @Value("${PARCEL_BASE_FARE:25.00}") BigDecimal parcelBase,
            @Value("${PARCEL_PER_KM_RATE:10.00}") BigDecimal parcelPerKm,
            // Lower than a ride's: nobody is sitting in the vehicle, so time
            // in traffic costs a partner less than it costs her to carry
            // somebody through it.
            @Value("${PARCEL_PER_MINUTE_RATE:1.00}") BigDecimal parcelPerMinute,
            @Value("${PARCEL_MINIMUM_FARE:40.00}") BigDecimal parcelMinimum,
            @Value("${PARCEL_NIGHT_MULTIPLIER:1.30}") BigDecimal parcelNight,
            @Value("${PARCEL_NIGHT_WINDOW_START:22:00}") String parcelNightStart,
            @Value("${PARCEL_NIGHT_WINDOW_END:06:00}") String parcelNightEnd,
            @Value("${PARCEL_SURGE_MULTIPLIER:1.00}") BigDecimal parcelSurge,

            @Value("${AUTO_BASE_FARE:30.00}") BigDecimal autoBase,
            @Value("${AUTO_PER_KM_RATE:13.00}") BigDecimal autoPerKm,
            @Value("${AUTO_PER_MINUTE_RATE:1.50}") BigDecimal autoPerMinute,
            @Value("${AUTO_MINIMUM_FARE:45.00}") BigDecimal autoMinimum,
            @Value("${AUTO_NIGHT_MULTIPLIER:1.30}") BigDecimal autoNight,
            @Value("${AUTO_SURGE_MULTIPLIER:1.00}") BigDecimal autoSurge,

            @Value("${CAB_BASE_FARE:50.00}") BigDecimal cabBase,
            @Value("${CAB_PER_KM_RATE:18.00}") BigDecimal cabPerKm,
            @Value("${CAB_PER_MINUTE_RATE:2.00}") BigDecimal cabPerMinute,
            @Value("${CAB_MINIMUM_FARE:80.00}") BigDecimal cabMinimum,
            @Value("${CAB_NIGHT_MULTIPLIER:1.30}") BigDecimal cabNight,
            @Value("${CAB_SURGE_MULTIPLIER:1.00}") BigDecimal cabSurge,

            @Value("${LUNCHBOX_BASE_FARE:18.00}") BigDecimal lunchBase,
            @Value("${LUNCHBOX_PER_KM_RATE:7.00}") BigDecimal lunchPerKm,
            @Value("${LUNCHBOX_PER_MINUTE_RATE:0.80}") BigDecimal lunchPerMinute,
            @Value("${LUNCHBOX_MINIMUM_FARE:30.00}") BigDecimal lunchMinimum,
            @Value("${LUNCHBOX_NIGHT_MULTIPLIER:1.30}") BigDecimal lunchNight,
            @Value("${LUNCHBOX_SURGE_MULTIPLIER:1.00}") BigDecimal lunchSurge,

            // The unlaunched categories share one night window - there is no
            // reason for them to differ, and five more variables nobody sets
            // would be noise in the config.
            @Value("${DEFAULT_NIGHT_WINDOW_START:22:00}") String defaultNightStart,
            @Value("${DEFAULT_NIGHT_WINDOW_END:06:00}") String defaultNightEnd) {

        Map<BookingCategory, FareRates> rates = new EnumMap<>(BookingCategory.class);
        rates.put(BookingCategory.BIKE, new FareRates(
                bikeBase, bikePerKm, bikePerMinute, bikeMinimum, bikeNight,
                LocalTime.parse(bikeNightStart), LocalTime.parse(bikeNightEnd), bikeSurge));
        rates.put(BookingCategory.PARCEL, new FareRates(
                parcelBase, parcelPerKm, parcelPerMinute, parcelMinimum, parcelNight,
                LocalTime.parse(parcelNightStart), LocalTime.parse(parcelNightEnd), parcelSurge));

        LocalTime start = LocalTime.parse(defaultNightStart);
        LocalTime end = LocalTime.parse(defaultNightEnd);
        rates.put(BookingCategory.AUTO, new FareRates(
                autoBase, autoPerKm, autoPerMinute, autoMinimum, autoNight, start, end, autoSurge));
        rates.put(BookingCategory.CAB, new FareRates(
                cabBase, cabPerKm, cabPerMinute, cabMinimum, cabNight, start, end, cabSurge));
        rates.put(BookingCategory.LUNCHBOX, new FareRates(
                lunchBase, lunchPerKm, lunchPerMinute, lunchMinimum, lunchNight, start, end, lunchSurge));

        // Logged at startup so the price the service is actually running is
        // visible without reading the environment of a container. A pricing
        // change that did not take effect looks identical to one that did,
        // until somebody checks.
        rates.forEach((category, r) -> log.info(
                "Fare {}: base {} + {}/km + {}/min, min {}, night x{} ({}-{}), surge x{}",
                category, r.baseFare(), r.perKmRate(), r.perMinuteRate(), r.minimumFare(),
                r.nightMultiplier(), r.nightWindowStart(), r.nightWindowEnd(), r.surgeMultiplier()));
        return rates;
    }
}
