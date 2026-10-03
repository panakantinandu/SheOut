package com.sheout.insurance.internal;

import com.sheout.insurance.EnrolmentStatus;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/** A partner's membership of one group cover - see V57__insurance.sql. */
@Entity
@Table(name = "partner_insurance_enrolments")
public class PartnerEnrolmentEntity extends BaseEntity {

    @Column(nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private UUID policyId;

    @Column(length = 80)
    private String memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EnrolmentStatus status;

    private LocalDate enrolledOn;
    private LocalDate exitedOn;

    @Column(length = 200)
    private String exitReason;

    protected PartnerEnrolmentEntity() {
        // JPA
    }

    PartnerEnrolmentEntity(UUID accountId, UUID policyId) {
        this.accountId = accountId;
        this.policyId = policyId;
        this.status = EnrolmentStatus.PENDING_ENROLMENT;
    }

    void enrol(String memberId, LocalDate on) {
        this.memberId = memberId;
        this.status = EnrolmentStatus.ENROLLED;
        this.enrolledOn = on;
    }

    void exit(LocalDate on, String reason) {
        this.status = EnrolmentStatus.EXITED;
        this.exitedOn = on;
        this.exitReason = reason;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getPolicyId() {
        return policyId;
    }

    public String getMemberId() {
        return memberId;
    }

    public EnrolmentStatus getStatus() {
        return status;
    }

    public LocalDate getEnrolledOn() {
        return enrolledOn;
    }

    public LocalDate getExitedOn() {
        return exitedOn;
    }

    public String getExitReason() {
        return exitReason;
    }
}
