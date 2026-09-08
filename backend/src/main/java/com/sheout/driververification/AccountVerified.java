package com.sheout.driververification;

import com.sheout.auth.AccountRole;
import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * Published when an account's verification is complete and the account is
 * eligible to transact:
 * <ul>
 *     <li>CUSTOMER - when gender_verification_status reaches VERIFIED.</li>
 *     <li>DRIVER - when BOTH gender_verification_status and
 *     police_verification_status reach VERIFIED (a driver isn't fully
 *     verified by gender review alone - see the README's flagged
 *     assumption on what "verification completes" means for drivers).</li>
 * </ul>
 * Not published on rejection - no event is defined for that today (booking/
 * dispatch/notifications aren't built yet to react to either outcome; this
 * one was requested explicitly, rejection was not).
 */
public class AccountVerified extends DomainEvent {

    private final UUID accountId;
    private final AccountRole role;

    public AccountVerified(UUID accountId, AccountRole role) {
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
