package com.sheout.dispatch;

import com.sheout.booking.BookingCategory;

import java.util.Optional;

/**
 * The nearest partner who could be offered a trip from here right now - for
 * the pickup ETA on a fare quote.
 * <p>
 * Same availability gate as matching and the "partners near you" preview:
 * online, verified, not blocked, not mid-trip, not held for an unpaid fare,
 * the right vehicle, and a position reported in the last two minutes. The
 * position is exact and stays inside the backend - it is only used to route
 * her to the pickup; the app is told how many minutes, never where she is.
 */
public interface NearbyPartnerApi {

    Optional<NearbyPartner> nearestAvailable(double lat, double lng, BookingCategory category);

    record NearbyPartner(double lat, double lng, double distanceKm) {
    }
}
