package com.sheout.insurance.internal;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The cover one trip had. Opened on BookingStarted, closed on
 * BookingCompleted. premiumAmount is SheOut's cost for it - see
 * V57__insurance.sql.
 */
@Entity
@Table(name = "trip_coverages")
public class TripCoverageEntity extends BaseEntity {

    public enum ReportedStatus {
        PENDING,
        REPORTED,
        FAILED
    }

    @Column(nullable = false, unique = true)
    private UUID bookingId;

    @Column(nullable = false)
    private UUID policyId;

    @Column(nullable = false)
    private UUID riderAccountId;

    private UUID partnerAccountId;

    @Column(nullable = false, length = 20)
    private String category;

    @Column(length = 200)
    private String pickupArea;

    @Column(length = 200)
    private String dropArea;

    @Column(nullable = false)
    private Instant coverageStartedAt;

    private Instant coverageEndedAt;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal premiumAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportedStatus reportedStatus = ReportedStatus.PENDING;

    private Instant reportedAt;

    protected TripCoverageEntity() {
        // JPA
    }

    TripCoverageEntity(UUID bookingId, UUID policyId, UUID riderAccountId, UUID partnerAccountId, String category,
                       String pickupArea, String dropArea, Instant startedAt, BigDecimal premiumAmount) {
        this.bookingId = bookingId;
        this.policyId = policyId;
        this.riderAccountId = riderAccountId;
        this.partnerAccountId = partnerAccountId;
        this.category = category;
        this.pickupArea = pickupArea;
        this.dropArea = dropArea;
        this.coverageStartedAt = startedAt;
        this.premiumAmount = premiumAmount;
    }

    void end(Instant at) {
        if (coverageEndedAt == null) {
            this.coverageEndedAt = at;
        }
    }

    void markReported(Instant at) {
        this.reportedStatus = ReportedStatus.REPORTED;
        this.reportedAt = at;
    }

    void markReportFailed() {
        this.reportedStatus = ReportedStatus.FAILED;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getPolicyId() {
        return policyId;
    }

    public UUID getRiderAccountId() {
        return riderAccountId;
    }

    public UUID getPartnerAccountId() {
        return partnerAccountId;
    }

    public String getCategory() {
        return category;
    }

    public String getPickupArea() {
        return pickupArea;
    }

    public String getDropArea() {
        return dropArea;
    }

    public Instant getCoverageStartedAt() {
        return coverageStartedAt;
    }

    public Instant getCoverageEndedAt() {
        return coverageEndedAt;
    }

    public BigDecimal getPremiumAmount() {
        return premiumAmount;
    }

    public ReportedStatus getReportedStatus() {
        return reportedStatus;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }
}
