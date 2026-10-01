package com.sheout.users;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/** An operator approved or turned down a partner's change to her identity details. */
public class DriverProfileChangeDecided extends DomainEvent {

    private final UUID accountId;
    private final boolean approved;
    private final String note;

    public DriverProfileChangeDecided(UUID accountId, boolean approved, String note) {
        this.accountId = accountId;
        this.approved = approved;
        this.note = note;
    }

    public UUID accountId() {
        return accountId;
    }

    public boolean approved() {
        return approved;
    }

    public String note() {
        return note;
    }
}
