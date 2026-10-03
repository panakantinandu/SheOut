package com.sheout.driververification;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/** An operator turned one of her documents down, with the reason she is shown. */
public class PartnerDocumentRejected extends DomainEvent {

    private final UUID accountId;
    private final PartnerDocumentType type;
    private final String reason;

    public PartnerDocumentRejected(UUID accountId, PartnerDocumentType type, String reason) {
        this.accountId = accountId;
        this.type = type;
        this.reason = reason;
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
