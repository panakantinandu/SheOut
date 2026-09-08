package com.sheout.auth;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * Published the first time a phone number completes OTP verification, or a
 * Google sign-in resolves to a new email, and a new account is created.
 * driver-verification listens for this to create the account's
 * verification record; users listens to create the (empty, for phone) or
 * pre-named (for Google, which hands over a real name in its ID token)
 * profile row - auth does not call into either module directly.
 */
public class AccountRegistered extends DomainEvent {

    private final UUID accountId;
    private final AccountRole role;
    private final String name;

    /** Phone signup - no name is known yet, filled in later by the client. */
    public AccountRegistered(UUID accountId, AccountRole role) {
        this(accountId, role, null);
    }

    /** Google signup - name comes from the verified ID token's "name" claim, or null if Google didn't provide one. */
    public AccountRegistered(UUID accountId, AccountRole role, String name) {
        this.accountId = accountId;
        this.role = role;
        this.name = name;
    }

    public UUID accountId() {
        return accountId;
    }

    public AccountRole role() {
        return role;
    }

    public String name() {
        return name;
    }
}
