package com.sheout.driververification.internal;

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
 * One account reaching one step, once.
 * <p>
 * First time only - the unique index is on (account, step). Somebody who
 * opens the screen six times in an afternoon is one person hesitating, and
 * counting her six times would make the drop-off look like a crowd.
 */
@Entity
@Table(name = "verification_funnel_events")
class VerificationFunnelEntity extends BaseEntity {

    @Column(nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private VerificationFunnelStep step;

    @Column(nullable = false)
    private Instant occurredAt;

    protected VerificationFunnelEntity() {
    }

    VerificationFunnelEntity(UUID accountId, AccountRole role, VerificationFunnelStep step) {
        this.accountId = accountId;
        this.role = role;
        this.step = step;
        this.occurredAt = Instant.now();
    }

    UUID getAccountId() {
        return accountId;
    }

    VerificationFunnelStep getStep() {
        return step;
    }

    Instant getOccurredAt() {
        return occurredAt;
    }
}
