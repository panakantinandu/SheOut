package com.sheout.booking;

/**
 * Resolved coordinates + a display label - owned by this module, per your
 * explicit instruction, rather than by {@code users}' saved addresses.
 * Whatever a booking is created from (free text the client geocoded, or
 * one of a customer's saved addresses), what lands here is always
 * resolved lat/lng, never a bare string - dispatch needs real coordinates
 * for geo-radius driver queries.
 * <p>
 * FLAGGED BACK TO YOU, as asked: {@code users}' {@code CustomerProfileEntity}
 * still stores {@code homeAddress}/{@code workAddress} as plain free-text
 * strings (built in the users-module pass, before this constraint
 * existed). Nothing here changes that yet, per your instruction not to
 * require it to change shape in this pass - but it means a saved home/work
 * address can't be used to prefill a booking's pickup/drop with real
 * coordinates until {@code users} is revisited to store the same
 * label+lat+lng shape (or geocode on read). Left as a follow-up, not
 * silently worked around.
 */
public record GeoAddress(String label, double lat, double lng) {
}
