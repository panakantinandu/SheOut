package com.sheout.driververification;

import com.sheout.auth.AccountRole;
import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * Somebody has sent in a document and is now waiting on a person.
 * <p>
 * Published so operations can be told at once rather than discovering it on
 * the next time somebody opens the console. Nothing reacted to a submission
 * before this: the queue simply grew, and the only thing standing between a
 * new rider and her first trip was whether anybody happened to look.
 */
public class VerificationSubmitted extends DomainEvent {

    private final UUID accountId;
    private final AccountRole role;

    public VerificationSubmitted(UUID accountId, AccountRole role) {
        this.accountId = accountId;
        this.role = role;
    }

    public UUID accountId() {
        return accountId;
    }

    public AccountRole role() {
        return role;
    }
}
