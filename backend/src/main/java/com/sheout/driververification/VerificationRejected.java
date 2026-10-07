package com.sheout.driververification;

import com.sheout.auth.AccountRole;
import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * A document was looked at by a person and turned down.
 * <p>
 * Only approval was ever announced (see AccountVerified), so somebody whose
 * document was rejected heard nothing at all: the app went on saying "being
 * reviewed" while the record said otherwise, and the reason an operator had
 * carefully typed stayed in the database. This carries that reason to her.
 */
public class VerificationRejected extends DomainEvent {

    private final UUID accountId;
    private final AccountRole role;
    private final String reason;
    private final String code;

    public VerificationRejected(UUID accountId, AccountRole role, String reason) {
        this(accountId, role, reason, null);
    }

    public VerificationRejected(UUID accountId, AccountRole role, String reason, String code) {
        this.accountId = accountId;
        this.role = role;
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

    public AccountRole role() {
        return role;
    }

    /** What the operator wrote, shown to her as-is. Never null or blank. */
    public String reason() {
        return reason;
    }
}
