package com.sheout.driververification;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * A partner's selfie failed to match the one she was verified with, several
 * times in a row. She cannot go online until an operator has looked; the
 * users module also flags the account, so it shows in the review queue.
 * <p>
 * Carries what she is told about the wait - how long a person usually takes
 * and the hours people review in - so the message to her says when, not
 * only that, somebody will look.
 */
public class ShiftCheckNeedsReview extends DomainEvent {

    private final UUID accountId;
    private final int reviewTargetMinutes;
    private final String reviewHoursFrom;
    private final String reviewHoursTo;

    public ShiftCheckNeedsReview(UUID accountId) {
        this(accountId, 0, null, null);
    }

    public ShiftCheckNeedsReview(UUID accountId, int reviewTargetMinutes, String reviewHoursFrom, String reviewHoursTo) {
        this.accountId = accountId;
        this.reviewTargetMinutes = reviewTargetMinutes;
        this.reviewHoursFrom = reviewHoursFrom;
        this.reviewHoursTo = reviewHoursTo;
    }

    public UUID accountId() {
        return accountId;
    }

    /** How soon a person aims to look, in minutes; 0 when not known. */
    public int reviewTargetMinutes() {
        return reviewTargetMinutes;
    }

    /** "09:00": when people start reviewing, in India; null when not known. */
    public String reviewHoursFrom() {
        return reviewHoursFrom;
    }

    public String reviewHoursTo() {
        return reviewHoursTo;
    }
}
