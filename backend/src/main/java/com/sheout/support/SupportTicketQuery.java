package com.sheout.support;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * The filters a ticket list can be narrowed by - the same shape BookingQuery
 * gives the booking lists: every field optional, null means "do not narrow",
 * and an empty set means every value.
 * <p>
 * raisedBy is scope, not a filter. The self-service list always passes the
 * caller's own id, so nobody can page through anyone else's tickets; the ops
 * console passes null.
 */
public record SupportTicketQuery(
        UUID raisedBy,
        Set<SupportTicketStatus> statuses,
        Set<SupportTicketCategory> categories,
        Set<SupportTicketPriority> priorities,
        Instant from,
        Instant to
) {

    public static SupportTicketQuery forRaiser(UUID raisedBy) {
        return new SupportTicketQuery(raisedBy, null, null, null, null, null);
    }

    public Set<SupportTicketStatus> statusesOrAll() {
        return statuses == null || statuses.isEmpty() ? EnumSet.allOf(SupportTicketStatus.class) : statuses;
    }

    public Set<SupportTicketCategory> categoriesOrAll() {
        return categories == null || categories.isEmpty() ? EnumSet.allOf(SupportTicketCategory.class) : categories;
    }

    public Set<SupportTicketPriority> prioritiesOrAll() {
        return priorities == null || priorities.isEmpty() ? EnumSet.allOf(SupportTicketPriority.class) : priorities;
    }
}
