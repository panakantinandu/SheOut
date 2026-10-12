package com.sheout.payouts.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** See V80: one parcel's earnings, held. Keyed by the booking, so a trip is held once. */
@Entity
@Table(name = "earning_holds")
class EarningHoldEntity {

    enum Status { HELD, RELEASED, WITHHELD }

    @Id
    private UUID bookingId;
    @Column(nullable = false)
    private UUID driverAccountId;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false)
    private Instant releaseAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;
    @Column(nullable = false)
    private boolean operatorHold;
    @Column(length = 500)
    private String reason;
    private UUID decidedBy;
    private Instant decidedAt;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected EarningHoldEntity() {
        // JPA
    }

    EarningHoldEntity(UUID bookingId, UUID driverAccountId, BigDecimal amount, Instant releaseAt, boolean operatorHold, String reason) {
        this.bookingId = bookingId;
        this.driverAccountId = driverAccountId;
        this.amount = amount;
        this.releaseAt = releaseAt;
        this.status = Status.HELD;
        this.operatorHold = operatorHold;
        this.reason = reason;
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    void holdByOperator(UUID by, String reason, Instant at) {
        operatorHold = true;
        this.reason = reason;
        decidedBy = by;
        decidedAt = at;
    }

    void settle(Status outcome, UUID by, String reason, Instant at) {
        status = outcome;
        operatorHold = false;
        if (reason != null) {
            this.reason = reason;
        }
        decidedBy = by;
        decidedAt = at;
    }

    UUID getBookingId() { return bookingId; }
    UUID getDriverAccountId() { return driverAccountId; }
    BigDecimal getAmount() { return amount; }
    Instant getReleaseAt() { return releaseAt; }
    Status getStatus() { return status; }
    boolean isOperatorHold() { return operatorHold; }
    String getReason() { return reason; }
    Instant getDecidedAt() { return decidedAt; }
}
