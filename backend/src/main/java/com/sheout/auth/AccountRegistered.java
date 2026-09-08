package com.sheout.auth;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * Published the first time a phone number completes OTP verification and a
 * new account is created. driver-verification listens for this to create
 * the account's verification record - auth does not call into
 * driver-verification directly.
 */
public class AccountRegistered extends DomainEvent {

    private final UUID accountId;
    private final AccountRole role;

    public AccountRegistered(UUID accountId, AccountRole role) {
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
