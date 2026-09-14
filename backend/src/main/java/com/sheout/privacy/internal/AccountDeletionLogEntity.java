package com.sheout.privacy.internal;

import com.sheout.auth.AccountRole;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One account deletion, for the compliance audit trail: who asked, when, and
 * when it finished. The same who-and-when convention sos_alert uses for
 * resolution. Holds an account id and nothing else about the person - after
 * deletion that id identifies nobody.
 */
@Entity
@Table(name = "account_deletion_log")
public class AccountDeletionLogEntity extends BaseEntity {

    @Column(nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountRole role;

    @Column(nullable = false)
    private UUID requestedBy;

    @Column(nullable = false)
    private Instant requestedAt;

    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    enum Status { REQUESTED, COMPLETED }

    protected AccountDeletionLogEntity() {
        // JPA
    }

    AccountDeletionLogEntity(UUID accountId, AccountRole role, UUID requestedBy) {
        this.accountId = accountId;
        this.role = role;
        this.requestedBy = requestedBy;
        this.requestedAt = Instant.now();
        this.status = Status.REQUESTED;
    }

    void complete() {
        this.completedAt = Instant.now();
        this.status = Status.COMPLETED;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
