package com.sheout.users;

/**
 * A place she saved - Home or Work - as a label with a point on the map.
 * <p>
 * The same shape booking's GeoAddress has, and deliberately not that type:
 * saved addresses belong to users, and a module's own data should not be
 * described by another module's class just because the fields line up. The
 * apps map one to the other when a saved place is tapped into a booking.
 * <p>
 * lat and lng are null for an address saved as free text before this existed.
 * Such a place still shows in her list - nothing she typed is thrown away -
 * but it cannot be tapped into a booking, which is exactly what it was worth
 * before.
 */
public record SavedPlace(String label, Double lat, Double lng) {

    public boolean hasPoint() {
        return lat != null && lng != null;
    }
}
