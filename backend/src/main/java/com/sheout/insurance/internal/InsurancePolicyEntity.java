package com.sheout.insurance.internal;

import com.sheout.insurance.PolicyKind;
import com.sheout.insurance.PremiumUnit;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One master policy, as an operator entered it from the insurer's schedule. See V57__insurance.sql. */
@Entity
@Table(name = "insurance_policies")
public class InsurancePolicyEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PolicyKind kind;

    @Column(nullable = false, length = 150)
    private String insurerName;

    @Column(nullable = false, length = 80)
    private String masterPolicyNumber;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal sumInsured;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal premiumPerUnit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PremiumUnit premiumUnit;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    @Column(length = 30)
    private String claimsPhone;
    @Column(length = 500)
    private String claimsUrl;
    @Column(length = 500)
    private String policySummaryUrl;
    @Column(length = 2000)
    private String coverageSummary;
    @Column(length = 2000)
    private String claimSteps;

    @Column(nullable = false)
    private boolean active;

    private UUID createdBy;

    protected InsurancePolicyEntity() {
        // JPA
    }

    InsurancePolicyEntity(UUID createdBy) {
        this.createdBy = createdBy;
    }

    void update(PolicyKind kind, String insurerName, String masterPolicyNumber, BigDecimal sumInsured,
                BigDecimal premiumPerUnit, PremiumUnit premiumUnit, LocalDate effectiveFrom, LocalDate effectiveTo,
                String claimsPhone, String claimsUrl, String policySummaryUrl, String coverageSummary, String claimSteps) {
        this.kind = kind;
        this.insurerName = insurerName;
        this.masterPolicyNumber = masterPolicyNumber;
        this.sumInsured = sumInsured;
        this.premiumPerUnit = premiumPerUnit;
        this.premiumUnit = premiumUnit;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.claimsPhone = claimsPhone;
        this.claimsUrl = claimsUrl;
        this.policySummaryUrl = policySummaryUrl;
        this.coverageSummary = coverageSummary;
        this.claimSteps = claimSteps;
    }

    /** In force on this day: switched on, and the day inside its effective dates. */
    public boolean inForceOn(LocalDate day) {
        return active && !day.isBefore(effectiveFrom) && (effectiveTo == null || !day.isAfter(effectiveTo));
    }

    void setActive(boolean active) {
        this.active = active;
    }

    public PolicyKind getKind() {
        return kind;
    }

    public String getInsurerName() {
        return insurerName;
    }

    public String getMasterPolicyNumber() {
        return masterPolicyNumber;
    }

    public BigDecimal getSumInsured() {
        return sumInsured;
    }

    public BigDecimal getPremiumPerUnit() {
        return premiumPerUnit;
    }

    public PremiumUnit getPremiumUnit() {
        return premiumUnit;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public String getClaimsPhone() {
        return claimsPhone;
    }

    public String getClaimsUrl() {
        return claimsUrl;
    }

    public String getPolicySummaryUrl() {
        return policySummaryUrl;
    }

    public String getCoverageSummary() {
        return coverageSummary;
    }

    public String getClaimSteps() {
        return claimSteps;
    }

    public boolean isActive() {
        return active;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
