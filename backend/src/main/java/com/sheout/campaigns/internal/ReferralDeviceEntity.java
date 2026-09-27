package com.sheout.campaigns.internal;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** An app install an account used the referral screens from. See V38. */
@Entity
@Table(name = "referral_devices")
public class ReferralDeviceEntity extends BaseEntity {

    @Column(nullable = false)
    private UUID accountId;
    @Column(nullable = false, length = 64)
    private String installId;
    @Column(nullable = false)
    private Instant lastSeenAt;

    protected ReferralDeviceEntity() {
    }

    ReferralDeviceEntity(UUID accountId, String installId, Instant now) {
        this.accountId = accountId;
        this.installId = installId;
        this.lastSeenAt = now;
    }

    void seen(Instant now) {
        this.lastSeenAt = now;
    }

    UUID getAccountId() { return accountId; }
    String getInstallId() { return installId; }
}
