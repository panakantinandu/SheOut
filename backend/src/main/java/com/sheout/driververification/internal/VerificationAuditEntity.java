package com.sheout.driververification.internal;

import com.sheout.driververification.PartnerDocumentType;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One line of verification_audit_events. Written once, never changed. */
@Entity
@Table(name = "verification_audit_events")
public class VerificationAuditEntity extends BaseEntity {

    @Column(nullable = false)
    private UUID accountId;

    private UUID actorId;

    @Column(nullable = false, length = 20)
    private String actorRole;

    @Column(nullable = false, length = 40)
    private String action;

    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private PartnerDocumentType documentType;

    @Column(length = 1000)
    private String detail;

    @Column(nullable = false)
    private Instant at;

    protected VerificationAuditEntity() {
        // JPA
    }

    VerificationAuditEntity(UUID accountId, UUID actorId, String actorRole, String action, UUID documentId,
                            PartnerDocumentType documentType, String detail, Instant at) {
        this.accountId = accountId;
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.action = action;
        this.documentId = documentId;
        this.documentType = documentType;
        this.detail = detail == null || detail.length() <= 1000 ? detail : detail.substring(0, 1000);
        this.at = at;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public String getAction() {
        return action;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public PartnerDocumentType getDocumentType() {
        return documentType;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getAt() {
        return at;
    }

    /**
     * The one change a line ever gets: account deletion clears the free text,
     * which can hold a document number or an operator's note about her. Who
     * did what, and when, stays.
     */
    void clearDetail() {
        this.detail = null;
    }
}
