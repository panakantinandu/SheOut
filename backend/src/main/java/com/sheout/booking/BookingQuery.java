package com.sheout.booking;

import java.time.Instant;
import java.util.Set;

/**
 * The filters a booking list can be narrowed by. One record rather than a
 * growing parameter list, because the same set is wanted by three callers -
 * a customer's own history, a driver's own history, and the ops console -
 * and a fourth parameter added for one of them would otherwise have to be
 * threaded through all three signatures.
 * <p>
 * Every field is optional and null means "do not narrow on this". The one
 * exception is categories: an empty set would have to mean either "all" or
 * "none", and JPQL cannot express `IN ()` at all, so the caller passes the
 * full set when it does not want to filter. {@link #allCategories()} builds
 * that, so no call site has to remember.
 * <p>
 * `text` matches pickup or drop label. It is deliberately not offered for
 * payments: there is nothing in a payment worth typing at, and a search box
 * that silently matches nothing is worse than no search box.
 */
public record BookingQuery(
        BookingStatus status,
        Instant from,
        Instant to,
        Set<BookingCategory> categories,
        String text
) {

    public static BookingQuery unfiltered() {
        return new BookingQuery(null, null, null, allCategories(), null);
    }

    public static Set<BookingCategory> allCategories() {
        return Set.of(BookingCategory.values());
    }

    /**
     * Normalises what arrives from a request: an absent or empty category
     * set becomes every category, and blank text becomes null so the query
     * does not try to match on an empty string.
     */
    public BookingQuery normalized() {
        return new BookingQuery(
                status,
                from,
                to,
                categories == null || categories.isEmpty() ? allCategories() : categories,
                text == null || text.isBlank() ? null : text.trim().toLowerCase()
        );
    }
}
