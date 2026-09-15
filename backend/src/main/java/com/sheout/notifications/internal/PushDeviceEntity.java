package com.sheout.notifications.internal;

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
 * A browser or installed app that has turned push on for an account, by its
 * FCM registration token. One account, many devices; one device, one account
 * at a time.
 */
@Entity
@Table(name = "push_devices")
public class PushDeviceEntity extends BaseEntity {

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_role", nullable = false, length = 20)
    private AccountRole accountRole;

    @Column(nullable = false, unique = true, length = 512)
    private String token;

    /** Only so a person could tell "my phone" from "the office laptop", were a device list ever shown. */
    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected PushDeviceEntity() {
        // JPA
    }

    public PushDeviceEntity(UUID accountId, AccountRole accountRole, String token, String userAgent, Instant now) {
        this.accountId = accountId;
        this.accountRole = accountRole;
        this.token = token;
        this.userAgent = userAgent;
        this.lastSeenAt = now;
    }

    /**
     * The same device registering again. If a different account is now signed
     * in on it, the device moves to that account - a shared or handed-down
     * phone must never keep showing the previous owner's trips.
     */
    void refresh(UUID accountId, AccountRole accountRole, String userAgent, Instant now) {
        this.accountId = accountId;
        this.accountRole = accountRole;
        this.userAgent = userAgent;
        this.lastSeenAt = now;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public AccountRole getAccountRole() {
        return accountRole;
    }

    public String getToken() {
        return token;
    }
}
