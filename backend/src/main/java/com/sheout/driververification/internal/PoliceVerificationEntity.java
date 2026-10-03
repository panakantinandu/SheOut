package com.sheout.driververification.internal;

import com.sheout.driververification.PoliceVerificationMethod;
import com.sheout.driververification.VerificationStatus;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One police-check decision - see V56__police_verification_details.sql for why this is its own table. */
@Entity
@Table(name = "police_verifications")
public class PoliceVerificationEntity extends BaseEntity {

    @Column(nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VerificationStatus outcome;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PoliceVerificationMethod policeMethod;

    @Column(length = 64)
    private String policeCertificateNumber;

    private LocalDate policeIssuedOn;

    @Column(length = 200)
    private String policeIssuingAuthority;

    private LocalDate policeReverifyDueOn;
    private UUID policeDocumentId;
    private UUID extraDocumentId;
    private Instant policeConsentAt;

    @Column(length = 40)
    private String policeConsentTextVersion;

    @Column(length = 1000)
    private String rejectionReason;

    @Column(nullable = false)
    private UUID decidedBy;

    @Column(nullable = false)
    private Instant decidedAt;

    protected PoliceVerificationEntity() {
        // JPA
    }

    static PoliceVerificationEntity verified(UUID accountId, PoliceVerificationMethod method, String certificateNumber,
                                             LocalDate issuedOn, String issuingAuthority, LocalDate reverifyDueOn,
                                             UUID evidenceDocumentId, UUID extraDocumentId, Instant consentAt,
                                             String consentVersion, UUID decidedBy, Instant decidedAt) {
        PoliceVerificationEntity e = new PoliceVerificationEntity();
        e.accountId = accountId;
        e.outcome = VerificationStatus.VERIFIED;
        e.policeMethod = method;
        e.policeCertificateNumber = certificateNumber;
        e.policeIssuedOn = issuedOn;
        e.policeIssuingAuthority = issuingAuthority;
        e.policeReverifyDueOn = reverifyDueOn;
        e.policeDocumentId = evidenceDocumentId;
        e.extraDocumentId = extraDocumentId;
        e.policeConsentAt = consentAt;
        e.policeConsentTextVersion = consentVersion;
        e.decidedBy = decidedBy;
        e.decidedAt = decidedAt;
        return e;
    }

    static PoliceVerificationEntity rejected(UUID accountId, String reason, UUID evidenceDocumentId, Instant consentAt,
                                             String consentVersion, UUID decidedBy, Instant decidedAt) {
        PoliceVerificationEntity e = new PoliceVerificationEntity();
        e.accountId = accountId;
        e.outcome = VerificationStatus.REJECTED;
        e.rejectionReason = reason;
        e.policeDocumentId = evidenceDocumentId;
        e.policeConsentAt = consentAt;
        e.policeConsentTextVersion = consentVersion;
        e.decidedBy = decidedBy;
        e.decidedAt = decidedAt;
        return e;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public VerificationStatus getOutcome() {
        return outcome;
    }

    public PoliceVerificationMethod getPoliceMethod() {
        return policeMethod;
    }

    public String getPoliceCertificateNumber() {
        return policeCertificateNumber;
    }

    public LocalDate getPoliceIssuedOn() {
        return policeIssuedOn;
    }

    public String getPoliceIssuingAuthority() {
        return policeIssuingAuthority;
    }

    public LocalDate getPoliceReverifyDueOn() {
        return policeReverifyDueOn;
    }

    public UUID getPoliceDocumentId() {
        return policeDocumentId;
    }

    public UUID getExtraDocumentId() {
        return extraDocumentId;
    }

    public Instant getPoliceConsentAt() {
        return policeConsentAt;
    }

    public String getPoliceConsentTextVersion() {
        return policeConsentTextVersion;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    /** Account deletion: the certificate number and the note are about her; the decision and its date stay. */
    void eraseForDeletion() {
        this.policeCertificateNumber = null;
        this.rejectionReason = null;
    }
}
