package com.sheout.driververification;

import com.sheout.sharedkernel.event.DomainEvent;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One of her documents runs out soon. Published once at each reminder
 * threshold (30, 7 and 1 days by default) so she renews it before it stops
 * her working. Notifications tells her, in her language.
 */
public class PartnerDocumentExpiring extends DomainEvent {

    private final UUID accountId;
    private final PartnerDocumentType type;
    private final LocalDate validUntil;
    private final int daysLeft;

    public PartnerDocumentExpiring(UUID accountId, PartnerDocumentType type, LocalDate validUntil, int daysLeft) {
        this.accountId = accountId;
        this.type = type;
        this.validUntil = validUntil;
        this.daysLeft = daysLeft;
    }

    public UUID accountId() {
        return accountId;
    }

    public PartnerDocumentType type() {
        return type;
    }

    public LocalDate validUntil() {
        return validUntil;
    }

    public int daysLeft() {
        return daysLeft;
    }
}
