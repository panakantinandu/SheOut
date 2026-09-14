package com.sheout.support.internal;

import com.sheout.auth.AccountRole;
import com.sheout.sharedkernel.BaseEntity;
import com.sheout.support.SupportTicketCategory;
import com.sheout.support.SupportTicketPriority;
import com.sheout.support.SupportTicketStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "support_tickets")
public class SupportTicketEntity extends BaseEntity {

    @Column(nullable = false)
    private UUID raisedByAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "raised_by_role", nullable = false, length = 20)
    private AccountRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SupportTicketCategory category;

    @Column(nullable = false, length = 150)
    private String subject;

    @Column(nullable = false, length = 2000)
    private String description;

    private UUID linkedBookingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupportTicketStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SupportTicketPriority priority;

    @Column(nullable = false)
    private int queueRank;

    private UUID assignedAdminId;

    /** Both null until RESOLVED; both set together, both cleared on reopening. */
    private Instant resolvedAt;

    private UUID resolvedBy;

    @Column(nullable = false)
    private Instant lastActivityAt;

    protected SupportTicketEntity() {
        // JPA
    }

    public SupportTicketEntity(UUID raisedByAccountId, AccountRole role, SupportTicketCategory category,
                               String subject, String description, UUID linkedBookingId) {
        this.raisedByAccountId = raisedByAccountId;
        this.role = role;
        this.category = category;
        this.subject = subject;
        this.description = description;
        this.linkedBookingId = linkedBookingId;
        this.priority = SupportTicketPriority.forCategory(category);
        this.status = SupportTicketStatus.OPEN;
        this.lastActivityAt = Instant.now();
        refreshQueueRank();
    }

    /**
     * Moves to a new status. Callers check SupportTicketStatus.canMoveTo
     * first; this records the consequences.
     * <p>
     * Leaving RESOLVED clears who resolved it and when. Those fields describe
     * the ticket's current state, and a reopened ticket is not resolved. The
     * earlier resolution is not lost: the service writes it into the thread
     * as an internal note before calling this.
     */
    void moveTo(SupportTicketStatus next, UUID actorAccountId) {
        if (next == SupportTicketStatus.RESOLVED) {
            this.resolvedAt = Instant.now();
            this.resolvedBy = actorAccountId;
        } else if (this.status == SupportTicketStatus.RESOLVED && next != SupportTicketStatus.CLOSED) {
            this.resolvedAt = null;
            this.resolvedBy = null;
        }
        this.status = next;
        touch();
        refreshQueueRank();
    }

    void assignTo(UUID adminAccountId) {
        this.assignedAdminId = adminAccountId;
        touch();
    }

    /** See SupportService.onAccountDeletionRequested. */
    void redactRaiserText() {
        this.subject = com.sheout.privacy.AccountDeletionRequested.REDACTED_TEXT;
        this.description = com.sheout.privacy.AccountDeletionRequested.REDACTED_TEXT;
    }

    void touch() {
        this.lastActivityAt = Instant.now();
    }

    /**
     * The operator queue's sort key: every unresolved ticket before any
     * resolved one, and within each, HIGH before MEDIUM before LOW. Recomputed
     * whenever status changes; priority is fixed at creation.
     */
    private void refreshQueueRank() {
        int statusGroup = switch (status) {
            case OPEN, IN_PROGRESS -> 0;
            case RESOLVED -> 1;
            case CLOSED -> 2;
        };
        int priorityRank = switch (priority) {
            case HIGH -> 0;
            case MEDIUM -> 1;
            case LOW -> 2;
        };
        this.queueRank = statusGroup * 10 + priorityRank;
    }

    public UUID getRaisedByAccountId() {
        return raisedByAccountId;
    }

    public AccountRole getRole() {
        return role;
    }

    public SupportTicketCategory getCategory() {
        return category;
    }

    public String getSubject() {
        return subject;
    }

    public String getDescription() {
        return description;
    }

    public UUID getLinkedBookingId() {
        return linkedBookingId;
    }

    public SupportTicketStatus getStatus() {
        return status;
    }

    public SupportTicketPriority getPriority() {
        return priority;
    }

    public UUID getAssignedAdminId() {
        return assignedAdminId;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public UUID getResolvedBy() {
        return resolvedBy;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }
}
