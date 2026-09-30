package com.sheout.driververification;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * A partner's selfie failed to match the one she was verified with, several
 * times in a row. She cannot go online until an operator has looked; the
 * users module also flags the account, so it shows in the review queue.
 */
public class ShiftCheckNeedsReview extends DomainEvent {

    private final UUID accountId;

    public ShiftCheckNeedsReview(UUID accountId) {
        this.accountId = accountId;
    }

    public UUID accountId() {
        return accountId;
    }
}
