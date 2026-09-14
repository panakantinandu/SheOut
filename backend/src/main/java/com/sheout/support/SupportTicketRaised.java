package com.sheout.support;

import com.sheout.auth.AccountRole;
import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * Published when a ticket is created. Carries the few facts an alert needs -
 * category and priority, so a subscriber can treat a safety concern
 * differently - and not the subject or description, which stay in this
 * module.
 */
public class SupportTicketRaised extends DomainEvent {

    private final UUID ticketId;
    private final UUID raisedByAccountId;
    private final AccountRole role;
    private final SupportTicketCategory category;
    private final SupportTicketPriority priority;

    public SupportTicketRaised(UUID ticketId, UUID raisedByAccountId, AccountRole role,
                               SupportTicketCategory category, SupportTicketPriority priority) {
        this.ticketId = ticketId;
        this.raisedByAccountId = raisedByAccountId;
        this.role = role;
        this.category = category;
        this.priority = priority;
    }

    public UUID ticketId() {
        return ticketId;
    }

    public UUID raisedByAccountId() {
        return raisedByAccountId;
    }

    public AccountRole role() {
        return role;
    }

    public SupportTicketCategory category() {
        return category;
    }

    public SupportTicketPriority priority() {
        return priority;
    }
}
