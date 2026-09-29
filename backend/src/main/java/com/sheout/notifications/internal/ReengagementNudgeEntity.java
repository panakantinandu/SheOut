package com.sheout.notifications.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** The last "we miss you" reminder an account was sent - see ReengagementNudger. */
@Entity
@Table(name = "reengagement_nudges")
public class ReengagementNudgeEntity {

    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @Column(nullable = false)
    private int stage;

    @Column(name = "last_nudged_at", nullable = false)
    private Instant lastNudgedAt;

    @Column(name = "seen_at_when_nudged", nullable = false)
    private Instant seenAtWhenNudged;

    protected ReengagementNudgeEntity() {
        // JPA
    }

    ReengagementNudgeEntity(UUID accountId) {
        this.accountId = accountId;
    }

    void record(int stage, Instant nudgedAt, Instant seenAt) {
        this.stage = stage;
        this.lastNudgedAt = nudgedAt;
        this.seenAtWhenNudged = seenAt;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public int getStage() {
        return stage;
    }

    public Instant getLastNudgedAt() {
        return lastNudgedAt;
    }

    public Instant getSeenAtWhenNudged() {
        return seenAtWhenNudged;
    }
}
