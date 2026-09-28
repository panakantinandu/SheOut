package com.sheout.booking.internal;

import com.sheout.booking.BookingCategory;
import com.sheout.booking.GeoAddress;
import com.sheout.booking.internal.fare.RouteEstimate;
import com.sheout.booking.internal.fare.RouteProvider;
import com.sheout.dispatch.NearbyPartnerApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * The two times on a fare quote: how soon a partner could reach her, and
 * when she would get there.
 * <p>
 * Pickup ETA is the road time, by OSRM, from the nearest partner who could
 * actually be offered this trip right now (dispatch's own availability gate)
 * to the pickup, plus a minute to accept and set off. Drop-by is now + that
 * ETA + the trip's own OSRM duration - the pickup wait is part of when she
 * arrives, so leaving it out would promise an earlier time than is true.
 * <p>
 * With nobody nearby both are empty and the app says so, rather than
 * guessing a wait. These are estimates shown before booking; nothing about
 * the fare depends on them - the price is fixed at booking.
 * <p>
 * Dispatch is reached lazily: it already depends on booking, so a
 * constructor dependency the other way would be a cycle.
 */
@Component
public class QuoteTimes {

    /** Accepting the offer and setting off. */
    private static final double ACCEPT_MINUTES = 1.0;

    private final ObjectProvider<NearbyPartnerApi> nearbyPartners;
    private final RouteProvider routes;

    QuoteTimes(ObjectProvider<NearbyPartnerApi> nearbyPartners, RouteProvider routes) {
        this.nearbyPartners = nearbyPartners;
        this.routes = routes;
    }

    /** pickupEtaMinutes and dropBy are null when no partner could come now. */
    public record Times(Integer pickupEtaMinutes, Instant dropBy) {
        static final Times UNKNOWN = new Times(null, null);
    }

    public Times estimate(BookingCategory category, GeoAddress pickup, double tripMinutes, Instant now) {
        NearbyPartnerApi api = nearbyPartners.getIfAvailable();
        if (api == null) {
            return Times.UNKNOWN;
        }
        Optional<NearbyPartnerApi.NearbyPartner> nearest;
        try {
            nearest = api.nearestAvailable(pickup.lat(), pickup.lng(), category);
        } catch (RuntimeException e) {
            // A quote is never refused for want of an ETA.
            return Times.UNKNOWN;
        }
        if (nearest.isEmpty()) {
            return Times.UNKNOWN;
        }
        NearbyPartnerApi.NearbyPartner partner = nearest.get();
        RouteEstimate approach = routes.route(new GeoAddress("partner", partner.lat(), partner.lng()), pickup);
        int eta = (int) Math.max(1, Math.ceil(approach.durationMinutes() + ACCEPT_MINUTES));
        Instant dropBy = now.plus(Duration.ofSeconds(Math.round((eta + tripMinutes) * 60)));
        // Up to the next whole minute: "by 4:47" should not be a minute early.
        Instant rounded = dropBy.truncatedTo(ChronoUnit.MINUTES);
        if (rounded.isBefore(dropBy)) {
            rounded = rounded.plus(1, ChronoUnit.MINUTES);
        }
        return new Times(eta, rounded);
    }
}
