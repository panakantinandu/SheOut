package com.sheout.campaigns.internal;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * What one rider holds from one promotion: a credit balance (the signup
 * credit) or a number of uses (a code she redeemed).
 */
@Entity
@Table(name = "promotion_grants")
public class PromotionGrantEntity extends BaseEntity {

    @Column(nullable = false)
    private UUID promotionId;
    @Column(nullable = false)
    private UUID accountId;
    @Column(nullable = false)
    private Instant grantedAt;
    private Instant expiresAt;
    @Column(precision = 10, scale = 2)
    private BigDecimal creditTotal;
    @Column(precision = 10, scale = 2)
    private BigDecimal creditRemaining;
    private Integer usesRemaining;
    private Instant exhaustedAt;

    protected PromotionGrantEntity() {
    }

    static PromotionGrantEntity credit(UUID promotionId, UUID accountId, BigDecimal amount, Instant now, Instant expiresAt) {
        PromotionGrantEntity g = new PromotionGrantEntity();
        g.promotionId = promotionId;
        g.accountId = accountId;
        g.grantedAt = now;
        g.expiresAt = expiresAt;
        g.creditTotal = amount;
        g.creditRemaining = amount;
        return g;
    }

    static PromotionGrantEntity uses(UUID promotionId, UUID accountId, int uses, Instant now, Instant expiresAt) {
        PromotionGrantEntity g = new PromotionGrantEntity();
        g.promotionId = promotionId;
        g.accountId = accountId;
        g.grantedAt = now;
        g.expiresAt = expiresAt;
        g.usesRemaining = uses;
        return g;
    }

    boolean usableAt(Instant now) {
        if (expiresAt != null && !now.isBefore(expiresAt)) return false;
        if (creditRemaining != null) return creditRemaining.signum() > 0;
        return usesRemaining != null && usesRemaining > 0;
    }

    void hold(BigDecimal discount) {
        if (creditRemaining != null) {
            creditRemaining = creditRemaining.subtract(discount).max(BigDecimal.ZERO);
        } else if (usesRemaining != null) {
            usesRemaining = Math.max(0, usesRemaining - 1);
        }
    }

    void release(BigDecimal discount) {
        if (creditRemaining != null) {
            creditRemaining = creditRemaining.add(discount).min(creditTotal);
        } else if (usesRemaining != null) {
            usesRemaining = usesRemaining + 1;
        }
        exhaustedAt = null;
    }

    /** A held discount is now spent for good; if nothing is left, this is the moment it ran out. */
    void settle(Instant now, boolean nothingElseHeld) {
        boolean empty = creditRemaining != null ? creditRemaining.signum() == 0 : usesRemaining != null && usesRemaining == 0;
        if (empty && nothingElseHeld && exhaustedAt == null) {
            exhaustedAt = now;
        }
    }

    UUID getPromotionId() { return promotionId; }
    UUID getAccountId() { return accountId; }
    Instant getGrantedAt() { return grantedAt; }
    Instant getExpiresAt() { return expiresAt; }
    BigDecimal getCreditTotal() { return creditTotal; }
    BigDecimal getCreditRemaining() { return creditRemaining; }
    Integer getUsesRemaining() { return usesRemaining; }
    Instant getExhaustedAt() { return exhaustedAt; }
}
