package com.sheout.staff.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * What a console sign-in needs beyond the account_sessions row it shares an
 * id with: the cookie's hash, the CSRF token, and its two clocks. See V61.
 */
@Entity
@Table(name = "staff_sessions")
class StaffSessionEntity {

    @Id
    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "csrf_token", nullable = false, length = 64)
    private String csrfToken;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "idle_timeout_minutes", nullable = false)
    private int idleTimeoutMinutes;

    @Column(name = "absolute_expires_at", nullable = false)
    private Instant absoluteExpiresAt;

    @Column(name = "push_token", length = 500)
    private String pushToken;

    protected StaffSessionEntity() {
    }

    StaffSessionEntity(UUID sessionId, UUID staffId, String tokenHash, String csrfToken, String ipAddress,
                       Instant now, Duration idle, Instant absoluteExpiresAt) {
        this.sessionId = sessionId;
        this.staffId = staffId;
        this.tokenHash = tokenHash;
        this.csrfToken = csrfToken;
        this.ipAddress = ipAddress;
        this.createdAt = now;
        this.lastActivityAt = now;
        this.idleTimeoutMinutes = (int) idle.toMinutes();
        this.absoluteExpiresAt = absoluteExpiresAt;
    }

    Instant idleExpiresAt() {
        return lastActivityAt.plus(Duration.ofMinutes(idleTimeoutMinutes));
    }

    /** True when it was written, which is at most every few seconds. */
    boolean recordActivity(Instant now, Duration writeEvery) {
        if (lastActivityAt.plus(writeEvery).isAfter(now)) {
            return false;
        }
        lastActivityAt = now;
        return true;
    }

    void rememberPushToken(String token) {
        this.pushToken = token;
    }

    UUID getSessionId() {
        return sessionId;
    }

    UUID getStaffId() {
        return staffId;
    }

    String getCsrfToken() {
        return csrfToken;
    }

    String getIpAddress() {
        return ipAddress;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getLastActivityAt() {
        return lastActivityAt;
    }

    Instant getAbsoluteExpiresAt() {
        return absoluteExpiresAt;
    }

    String getPushToken() {
        return pushToken;
    }
}
