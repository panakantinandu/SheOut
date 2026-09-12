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
 * Every field is optional and null means "do not narrow on this". The two
 * exceptions are the sets: an empty set would have to mean either "all" or
 * "none", and JPQL cannot express `IN ()` at all, so the caller passes the
 * full set when it does not want to filter. {@link #allCategories()} and
 * {@link #allStatuses()} build those, so no call site has to remember.
 * <p>
 * `statuses` is a set rather than a single value because the useful
 * questions are about groups of them. "What is happening right now" means
 * REQUESTED, MATCHED, ACCEPTED and IN_PROGRESS; "what has already happened"
 * means COMPLETED and CANCELLED. With a single status the customer app's
 * Live Track and History entries had nothing to narrow by and both showed
 * the same undifferentiated list.
 * <p>
 * `text` matches pickup or drop label. It is deliberately not offered for
 * payments: there is nothing in a payment worth typing at, and a search box
 * that silently matches nothing is worse than no search box.
 */
public record BookingQuery(
        Set<BookingStatus> statuses,
        Instant from,
        Instant to,
        Set<BookingCategory> categories,
        String text
) {

    public static BookingQuery unfiltered() {
        return new BookingQuery(allStatuses(), null, null, allCategories(), null);
    }

    public static Set<BookingCategory> allCategories() {
        return Set.of(BookingCategory.values());
    }

    public static Set<BookingStatus> allStatuses() {
        return Set.of(BookingStatus.values());
    }

    /**
     * Normalises what arrives from a request: an absent or empty set becomes
     * everything, and blank text becomes null so the query does not try to
     * match on an empty string.
     */
    public BookingQuery normalized() {
        return new BookingQuery(
                statuses == null || statuses.isEmpty() ? allStatuses() : statuses,
                from,
                to,
                categories == null || categories.isEmpty() ? allCategories() : categories,
                text == null || text.isBlank() ? null : text.trim().toLowerCase()
        );
    }
}
