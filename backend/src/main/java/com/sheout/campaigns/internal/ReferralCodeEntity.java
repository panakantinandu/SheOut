package com.sheout.campaigns.internal;

import com.sheout.auth.AccountRole;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** An account's own referral code. See V38 and ReferralService. */
@Entity
@Table(name = "referral_codes")
public class ReferralCodeEntity extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID accountId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountRole role;
    @Column(nullable = false, unique = true, length = 12)
    private String code;

    protected ReferralCodeEntity() {
    }

    ReferralCodeEntity(UUID accountId, AccountRole role, String code) {
        this.accountId = accountId;
        this.role = role;
        this.code = code;
    }

    UUID getAccountId() { return accountId; }
    AccountRole getRole() { return role; }
    String getCode() { return code; }
}
