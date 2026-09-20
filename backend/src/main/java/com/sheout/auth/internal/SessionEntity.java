package com.sheout.auth.internal;

import com.sheout.auth.AccountRole;
import com.sheout.auth.SessionRevocation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One sign-in on one device.
 * <p>
 * Deliberately NOT a BaseEntity: this table has no updatedAt, because the
 * only thing that changes after it is written is lastActiveAt, and that is
 * the audit trail, not a side note about it.
 * <p>
 * The token is not here in any form. The row holds the id the token carries,
 * so a session can be looked up and ended; it can never be turned back into
 * a credential.
 */
@Entity
@Table(name = "account_sessions")
class SessionEntity {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    @Column(name = "device_label", length = 120)
    private String deviceLabel;

    @Column(name = "user_agent", length = 400)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason", length = 40)
    private SessionRevocation revokedReason;

    protected SessionEntity() {
    }

    SessionEntity(UUID accountId, AccountRole role, String deviceLabel, String userAgent) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.role = role;
        this.status = SessionStatus.ACTIVE;
        this.deviceLabel = deviceLabel;
        this.userAgent = userAgent;
        this.createdAt = Instant.now();
        this.lastActiveAt = this.createdAt;
    }

    void revoke(SessionRevocation reason) {
        if (status == SessionStatus.REVOKED) {
            return;
        }
        this.status = SessionStatus.REVOKED;
        this.revokedAt = Instant.now();
        this.revokedReason = reason;
    }

    void touch(Instant at) {
        this.lastActiveAt = at;
    }

    UUID getId() {
        return id;
    }

    UUID getAccountId() {
        return accountId;
    }

    AccountRole getRole() {
        return role;
    }

    SessionStatus getStatus() {
        return status;
    }

    String getDeviceLabel() {
        return deviceLabel;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getLastActiveAt() {
        return lastActiveAt;
    }

    SessionRevocation getRevokedReason() {
        return revokedReason;
    }
}
