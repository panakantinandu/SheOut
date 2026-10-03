package com.sheout.auth;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * An operator blocked an account. Published inside the blocking transaction.
 * <p>
 * Insurance listens: a blocked partner is taken out of group covers (an
 * enrolment the insurer is paid for should not outlive her access). Nothing
 * else needed it before - sessions end inside auth itself.
 */
public class AccountBlocked extends DomainEvent {

    private final UUID accountId;
    private final AccountRole role;

    public AccountBlocked(UUID accountId, AccountRole role) {
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
