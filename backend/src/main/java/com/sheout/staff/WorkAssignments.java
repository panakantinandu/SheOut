package com.sheout.staff;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Who has taken a piece of work, so an agent sees the work she holds and the
 * work nobody holds - not everything.
 * <p>
 * Support tickets already carry their own assignee (the support module); this
 * is for work with no other place to record it, starting with a partner's
 * verification.
 */
public interface WorkAssignments {

    enum Kind {
        /** subjectId is the partner's account id. A hold: an agent opens only what she holds. */
        VERIFICATION,
        /** subjectId is the SOS alert id. A soft lock: shows who is on it; any responder may still act. */
        SOS,
        /** subjectId is the trip alert id. A soft lock, as SOS. */
        TRIP_ALERT,
        /** subjectId is the seller id. A soft lock, so two moderators do not review the same shop. */
        SELLER_REVIEW
    }

    /** Who holds it and since when - the "taken by" and the clock beside a row. */
    record Held(UUID staffAccountId, java.time.Instant since) {
    }

    /** Everything held of a kind, with since when. */
    java.util.Map<UUID, Held> held(Kind kind);

    /**
     * A manager hands a piece of work to a named member of staff, taking it
     * from whoever held it. Whether the caller may, and whether the new holder
     * can do the work, is the endpoint's check.
     */
    void assign(Kind kind, UUID subjectId, UUID staffAccountId);

    /** Who holds it, by staff account id; empty when nobody does. */
    Optional<UUID> holder(Kind kind, UUID subjectId);

    /** Everything this member of staff holds of a kind. */
    Set<UUID> heldBy(Kind kind, UUID staffAccountId);

    /** Everything anybody holds of a kind, mapped to who holds it. */
    java.util.Map<UUID, UUID> holders(Kind kind);

    /**
     * Takes it, if nobody holds it (or she already does). False when someone
     * else has it: two people must not review the same partner at once.
     */
    boolean take(Kind kind, UUID subjectId, UUID staffAccountId);

    /** Lets it go back to the queue. Only the holder - or someone with the "all" permission - may. */
    void release(Kind kind, UUID subjectId);

    /**
     * Whether the signed-in member of staff may open this item: she holds the
     * "see everything" permission for it, or she holds the item.
     */
    default boolean mayOpen(StaffPrincipal staff, Kind kind, UUID subjectId, Permission seeEverything) {
        return staff.has(seeEverything) || holder(kind, subjectId).map(staff.accountId()::equals).orElse(false);
    }

    /**
     * For an endpoint about one item: 403 NOT_YOURS unless the signed-in
     * member of staff holds it or may see everything of its kind.
     */
    default void requireMayOpen(Kind kind, UUID subjectId, Permission seeEverything) {
        StaffPrincipal staff = StaffContext.requireSignedIn();
        if (!mayOpen(staff, kind, subjectId, seeEverything)) {
            throw new com.sheout.sharedkernel.web.ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "NOT_YOURS",
                    "Take this from the queue before opening it.");
        }
    }

    /**
     * A queue as the signed-in member of staff may see it: everything, if she
     * may see everything of its kind; otherwise what nobody holds and what she
     * holds.
     */
    default <T> java.util.List<T> visibleQueue(java.util.List<T> rows, java.util.function.Function<T, UUID> subject,
                                              Kind kind, Permission seeEverything) {
        StaffPrincipal staff = StaffContext.requireSignedIn();
        if (staff.has(seeEverything)) {
            return rows;
        }
        java.util.Map<UUID, UUID> held = holders(kind);
        return rows.stream().filter(row -> {
            UUID holder = held.get(subject.apply(row));
            return holder == null || holder.equals(staff.accountId());
        }).toList();
    }
}
