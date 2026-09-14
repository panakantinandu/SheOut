package com.sheout.support.internal;

import com.sheout.auth.AccountRole;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "support_ticket_messages")
public class SupportTicketMessageEntity extends BaseEntity {

    @Column(nullable = false)
    private UUID ticketId;

    @Column(nullable = false)
    private UUID authorAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountRole authorRole;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(nullable = false)
    private boolean internalOnly;

    protected SupportTicketMessageEntity() {
        // JPA
    }

    public SupportTicketMessageEntity(UUID ticketId, UUID authorAccountId, AccountRole authorRole,
                                      String message, boolean internalOnly) {
        this.ticketId = ticketId;
        this.authorAccountId = authorAccountId;
        this.authorRole = authorRole;
        this.message = message;
        this.internalOnly = internalOnly;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public UUID getAuthorAccountId() {
        return authorAccountId;
    }

    public AccountRole getAuthorRole() {
        return authorRole;
    }

    public String getMessage() {
        return message;
    }

    public boolean isInternalOnly() {
        return internalOnly;
    }

    // No setters. Same reason ChatMessageEntity has none: a thread that can
    // be edited afterwards is worthless as a record of what was said.
}
