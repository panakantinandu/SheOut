package com.sheout.driververification;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/** An operator turned one of her documents down, with the reason she is shown. */
public class PartnerDocumentRejected extends DomainEvent {

    private final UUID accountId;
    private final PartnerDocumentType type;
    private final String reason;
    private final String code;

    public PartnerDocumentRejected(UUID accountId, PartnerDocumentType type, String reason) {
        this(accountId, type, reason, null);
    }

    public PartnerDocumentRejected(UUID accountId, PartnerDocumentType type, String reason, String code) {
        this.accountId = accountId;
        this.type = type;
        this.reason = reason;
        this.code = code;
    }

    /** The RejectionReason it was sent back for, so she is told in her own language; null for an older, typed reason. */
    public String code() {
        return code;
    }

    public UUID accountId() {
        return accountId;
    }

    public PartnerDocumentType type() {
        return type;
    }

    public String reason() {
        return reason;
    }
}
