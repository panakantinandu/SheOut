package com.sheout.support;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * Published when an operator posts a reply the raiser can see. Never for an
 * internal note.
 * <p>
 * The reply text is deliberately not on the event. The notification it
 * drives is an SMS, and an SMS is read on a lock screen by whoever is
 * holding the phone; it says a reply is waiting, and the reply itself is
 * read in the app.
 */
public class SupportReplyPosted extends DomainEvent {

    private final UUID ticketId;
    private final UUID recipientAccountId;
    private final String subject;

    public SupportReplyPosted(UUID ticketId, UUID recipientAccountId, String subject) {
        this.ticketId = ticketId;
        this.recipientAccountId = recipientAccountId;
        this.subject = subject;
    }

    public UUID ticketId() {
        return ticketId;
    }

    public UUID recipientAccountId() {
        return recipientAccountId;
    }

    public String subject() {
        return subject;
    }
}
