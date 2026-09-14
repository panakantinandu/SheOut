package com.sheout.privacy;

import com.sheout.auth.AccountRole;
import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * An account holder has asked for their account to be deleted, and the
 * privacy module has accepted the request.
 * <p>
 * Every module that holds personal data about the account listens for this
 * and anonymises its own records - privacy never touches another module's
 * tables. Listeners run synchronously inside the publishing transaction, so
 * the database changes across every module commit together or not at all:
 * there is no half-deleted account where the name is gone but the phone
 * number is still linked.
 * <p>
 * What a listener must NOT do is delete a booking, a payment, or anything
 * else retained for tax or dispute purposes. It removes the person from
 * those records, not the records.
 */
public class AccountDeletionRequested extends DomainEvent {

    /** What a deleted account's name reads as, wherever a name used to be shown. */
    public static final String DELETED_NAME = "Deleted User";

    /** What a deleted person's free text is replaced with where the record itself must stay. */
    public static final String REDACTED_TEXT = "[deleted]";

    private final UUID accountId;
    private final AccountRole role;

    public AccountDeletionRequested(UUID accountId, AccountRole role) {
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
