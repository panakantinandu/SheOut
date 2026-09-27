package com.sheout.campaigns.internal;

import com.sheout.auth.AccountRole;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Who referred whom, and how it ended. See V38 and ReferralService. */
@Entity
@Table(name = "referrals")
public class ReferralEntity extends BaseEntity {

    public enum Status {
        /** Signed up with the code; her first paid trip has not happened yet. */
        PENDING,
        /** Her first paid trip happened; the rewards (if any) were given. */
        COMPLETED,
        /** The two accounts turned out to be one person. Nothing was given. */
        REJECTED
    }

    @Column(nullable = false)
    private UUID referrerAccountId;
    @Column(nullable = false, unique = true)
    private UUID refereeAccountId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountRole role;
    @Column(nullable = false, length = 12)
    private String code;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;
    private UUID qualifyingBookingId;
    private Instant completedAt;
    @Column(precision = 10, scale = 2)
    private BigDecimal referrerReward;
    @Column(precision = 10, scale = 2)
    private BigDecimal refereeReward;
    @Column(length = 300)
    private String note;

    protected ReferralEntity() {
    }

    ReferralEntity(UUID referrerAccountId, UUID refereeAccountId, AccountRole role, String code) {
        this.referrerAccountId = referrerAccountId;
        this.refereeAccountId = refereeAccountId;
        this.role = role;
        this.code = code;
        this.status = Status.PENDING;
    }

    void complete(UUID bookingId, BigDecimal referrerReward, BigDecimal refereeReward, String note, Instant at) {
        this.status = Status.COMPLETED;
        this.qualifyingBookingId = bookingId;
        this.referrerReward = referrerReward;
        this.refereeReward = refereeReward;
        this.note = note;
        this.completedAt = at;
    }

    void reject(UUID bookingId, String note, Instant at) {
        this.status = Status.REJECTED;
        this.qualifyingBookingId = bookingId;
        this.referrerReward = BigDecimal.ZERO;
        this.refereeReward = BigDecimal.ZERO;
        this.note = note;
        this.completedAt = at;
    }

    UUID getReferrerAccountId() { return referrerAccountId; }
    UUID getRefereeAccountId() { return refereeAccountId; }
    AccountRole getRole() { return role; }
    String getCode() { return code; }
    Status getStatus() { return status; }
    UUID getQualifyingBookingId() { return qualifyingBookingId; }
    Instant getCompletedAt() { return completedAt; }
    BigDecimal getReferrerReward() { return referrerReward; }
    BigDecimal getRefereeReward() { return refereeReward; }
    String getNote() { return note; }
}
