package com.sheout.driververification.internal;

import com.sheout.auth.AccountRole;
import com.sheout.driververification.PartnerDocumentSource;
import com.sheout.driververification.PartnerDocumentStatus;
import com.sheout.driververification.PartnerDocumentType;
import com.sheout.driververification.PoliceVerificationMethod;
import com.sheout.driververification.VerificationStatus;
import com.sheout.driververification.internal.PoliceVerificationService.PoliceDecision;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A police check is approved on evidence: how it was done, the certificate
 * number, its issue date and the certificate itself - and a private
 * background check alone is not enough unless configuration says so.
 */
class PoliceEvidenceTest {

    private static final Instant NOW = Instant.parse("2026-10-03T04:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 3);

    private final VerificationRecordRepository records = mock(VerificationRecordRepository.class);
    private final PoliceVerificationRepository checks = mock(PoliceVerificationRepository.class);
    private final PartnerDocumentService documents = mock(PartnerDocumentService.class);
    private final VerificationConsent consent = mock(VerificationConsent.class);
    private final UUID partner = UUID.randomUUID();
    private final UUID operator = UUID.randomUUID();
    private VerificationRecordEntity record;
    private PartnerDocumentEntity certificate;
    private PartnerDocumentEntity report;

    @BeforeEach
    void setUp() {
        record = new VerificationRecordEntity(partner, AccountRole.DRIVER);
        record.setGenderVerificationStatus(VerificationStatus.VERIFIED);
        when(records.findByAccountId(partner)).thenReturn(Optional.of(record));
        when(consent.isCurrent(record)).thenReturn(true);
        certificate = evidence(PartnerDocumentType.POLICE_CERTIFICATE);
        report = evidence(PartnerDocumentType.BACKGROUND_CHECK_REPORT);
    }

    private PartnerDocumentEntity evidence(PartnerDocumentType type) {
        PartnerDocumentEntity doc = new PartnerDocumentEntity(partner, type, PartnerDocumentSource.PARTNER_UPLOAD,
                PartnerDocumentStatus.UNDER_REVIEW, NOW);
        doc.setDocumentKey("key-" + type);
        UUID id = UUID.randomUUID();
        when(documents.entity(id)).thenReturn(Optional.of(doc));
        idOf.put(doc, id);
        return doc;
    }

    private final java.util.Map<PartnerDocumentEntity, UUID> idOf = new java.util.IdentityHashMap<>();

    private PoliceVerificationService service(boolean bgvAlone) {
        return new PoliceVerificationService(records, checks, documents, consent,
                new PoliceVerificationRules(12, bgvAlone, "30,7", "v1"), mock(VerificationAudit.class),
                mock(DomainEventPublisher.class), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private PoliceDecision approve(PoliceVerificationMethod method, String number, LocalDate issued, PartnerDocumentEntity doc,
                                   PartnerDocumentEntity extra) {
        return new PoliceDecision(VerificationStatus.VERIFIED, method, number, "Cyberabad Police", issued, null,
                doc == null ? null : idOf.get(doc), extra == null ? null : idOf.get(extra), null);
    }

    @Test
    void aBareApprovalIsRefused() {
        var result = service(false).review(partner, operator, PoliceDecision.bare(VerificationStatus.VERIFIED));

        assertThat(result.error()).isEqualTo(VerificationError.POLICE_EVIDENCE_MISSING);
        assertThat(record.getPoliceVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        verify(checks, never()).save(any());
    }

    @Test
    void eachPieceOfEvidenceIsRequired() {
        LocalDate issued = TODAY.minusMonths(1);
        PoliceVerificationService s = service(false);
        assertThat(s.review(partner, operator, approve(null, "PVC-1", issued, certificate, null)).error())
                .as("method").isEqualTo(VerificationError.POLICE_EVIDENCE_MISSING);
        assertThat(s.review(partner, operator, approve(PoliceVerificationMethod.TS_POLICE_PVC, " ", issued, certificate, null)).error())
                .as("number").isEqualTo(VerificationError.POLICE_EVIDENCE_MISSING);
        assertThat(s.review(partner, operator, approve(PoliceVerificationMethod.TS_POLICE_PVC, "PVC-1", null, certificate, null)).error())
                .as("issue date").isEqualTo(VerificationError.POLICE_EVIDENCE_MISSING);
        assertThat(s.review(partner, operator, approve(PoliceVerificationMethod.TS_POLICE_PVC, "PVC-1", issued, null, null)).error())
                .as("document").isEqualTo(VerificationError.POLICE_EVIDENCE_MISSING);
    }

    @Test
    void approvedWithEvidenceAndDueAgainTwelveMonthsAfterIssue() {
        LocalDate issued = TODAY.minusMonths(2);

        var result = service(false).review(partner, operator,
                approve(PoliceVerificationMethod.TS_POLICE_PVC, "TSPVC/2026/123", issued, certificate, null));

        assertThat(result.isSuccess()).isTrue();
        assertThat(record.getPoliceVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(record.getPoliceReverifyDueOn()).isEqualTo(issued.plusMonths(12));
        ArgumentCaptor<PoliceVerificationEntity> saved = ArgumentCaptor.forClass(PoliceVerificationEntity.class);
        verify(checks).save(saved.capture());
        assertThat(saved.getValue().getPoliceMethod()).isEqualTo(PoliceVerificationMethod.TS_POLICE_PVC);
        assertThat(saved.getValue().getPoliceCertificateNumber()).isEqualTo("TSPVC/2026/123");
        assertThat(saved.getValue().getPoliceDocumentId()).isEqualTo(idOf.get(certificate));
        assertThat(saved.getValue().getDecidedBy()).isEqualTo(operator);
        // The certificate itself is approved by the same reading.
        verify(documents).acceptAsPoliceEvidence(eq(certificate), eq(operator), eq("TSPVC/2026/123"), eq(issued));
    }

    @Test
    void aBackgroundCheckAloneIsNotEnoughByDefault() {
        var byMethod = service(false).review(partner, operator,
                approve(PoliceVerificationMethod.THIRD_PARTY_BGV, "BGV-9", TODAY.minusDays(5), report, null));
        var byDocument = service(false).review(partner, operator,
                approve(PoliceVerificationMethod.TS_POLICE_PVC, "BGV-9", TODAY.minusDays(5), report, null));

        assertThat(byMethod.error()).isEqualTo(VerificationError.BGV_NOT_SUFFICIENT_ALONE);
        assertThat(byDocument.error()).isEqualTo(VerificationError.BGV_NOT_SUFFICIENT_ALONE);
        assertThat(record.getPoliceVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
    }

    @Test
    void butItCanStandBesideAPoliceCertificate() {
        var result = service(false).review(partner, operator,
                approve(PoliceVerificationMethod.TS_POLICE_PVC, "PVC-1", TODAY.minusDays(5), certificate, report));

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void andAloneOnlyWhenConfigurationAllowsIt() {
        var result = service(true).review(partner, operator,
                approve(PoliceVerificationMethod.THIRD_PARTY_BGV, "BGV-9", TODAY.minusDays(5), report, null));

        assertThat(result.isSuccess()).isTrue();
        assertThat(record.getPoliceVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
    }

    @Test
    void evidenceThatIsNotHersOrWasTurnedDownIsRefused() {
        PartnerDocumentEntity someoneElses = new PartnerDocumentEntity(UUID.randomUUID(), PartnerDocumentType.POLICE_CERTIFICATE,
                PartnerDocumentSource.PARTNER_UPLOAD, PartnerDocumentStatus.UNDER_REVIEW, NOW);
        someoneElses.setDocumentKey("k");
        UUID otherId = UUID.randomUUID();
        when(documents.entity(otherId)).thenReturn(Optional.of(someoneElses));
        var notHers = service(false).review(partner, operator, new PoliceDecision(VerificationStatus.VERIFIED,
                PoliceVerificationMethod.TS_POLICE_PVC, "PVC-1", null, TODAY.minusDays(5), null, otherId, null, null));
        assertThat(notHers.error()).isEqualTo(VerificationError.POLICE_EVIDENCE_INVALID);

        certificate.markRejected(operator, NOW, "Unreadable");
        var rejected = service(false).review(partner, operator,
                approve(PoliceVerificationMethod.TS_POLICE_PVC, "PVC-1", TODAY.minusDays(5), certificate, null));
        assertThat(rejected.error()).isEqualTo(VerificationError.POLICE_EVIDENCE_INVALID);
    }

    @Test
    void aCertificateAlreadyDueAgainIsTooOld() {
        var result = service(false).review(partner, operator,
                approve(PoliceVerificationMethod.TS_POLICE_PVC, "PVC-1", TODAY.minusMonths(12), certificate, null));

        assertThat(result.error()).isEqualTo(VerificationError.POLICE_CERTIFICATE_TOO_OLD);
    }

    @Test
    void notWithoutHerConsent() {
        when(consent.isCurrent(record)).thenReturn(false);

        var result = service(false).review(partner, operator,
                approve(PoliceVerificationMethod.TS_POLICE_PVC, "PVC-1", TODAY.minusDays(5), certificate, null));

        assertThat(result.error()).isEqualTo(VerificationError.CONSENT_REQUIRED);
    }

    @Test
    void aRejectionNeedsAReason() {
        var result = service(false).review(partner, operator,
                new PoliceDecision(VerificationStatus.REJECTED, null, null, null, null, null, null, null, ""));

        assertThat(result.error()).isEqualTo(VerificationError.REASON_REQUIRED);
        assertThat(record.getPoliceVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
    }
}
