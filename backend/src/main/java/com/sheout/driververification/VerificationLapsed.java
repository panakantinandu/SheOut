package com.sheout.driververification;

import com.sheout.sharedkernel.event.DomainEvent;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Something she was cleared on has stopped being true: a required document
 * expired, or her police check came due again. The opposite of
 * AccountVerified, published by the expiry sweep.
 * <p>
 * users takes her offline (a trip already under way carries on - going
 * offline never touches a live trip) and, for a police check, clears its
 * cached verified flag. Dispatch needs no event: its availability check asks
 * for readiness live before every offer. Notifications tells her what to do.
 */
public class VerificationLapsed extends DomainEvent {

    public enum Cause {
        DOCUMENT_EXPIRED,
        POLICE_REVERIFICATION_DUE
    }

    private final UUID accountId;
    private final Cause cause;
    private final PartnerDocumentType documentType;
    private final LocalDate on;

    public VerificationLapsed(UUID accountId, Cause cause, PartnerDocumentType documentType, LocalDate on) {
        this.accountId = accountId;
        this.cause = cause;
        this.documentType = documentType;
        this.on = on;
    }

    public UUID accountId() {
        return accountId;
    }

    public Cause cause() {
        return cause;
    }

    /** Null for a police check. */
    public PartnerDocumentType documentType() {
        return documentType;
    }

    /** The date it lapsed on. */
    public LocalDate on() {
        return on;
    }
}
