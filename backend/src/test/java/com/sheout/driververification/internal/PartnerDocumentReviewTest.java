package com.sheout.driververification.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sheout.driververification.InsuranceUseType;
import com.sheout.driververification.PartnerDocumentRejected;
import com.sheout.driververification.PartnerDocumentSource;
import com.sheout.driververification.PartnerDocumentStatus;
import com.sheout.driververification.PartnerDocumentType;
import com.sheout.driververification.internal.PartnerDocumentService.DocumentFacts;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import com.sheout.sharedkernel.storage.DocumentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An operator approves a document only with the evidence and the facts that
 * make it checkable, and turns one down only with a reason she is shown.
 */
class PartnerDocumentReviewTest {

    /** 10:00 in Hyderabad on 3 Oct 2026. */
    private static final Instant NOW = Instant.parse("2026-10-03T04:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 3);

    private final PartnerDocumentRepository repository = mock(PartnerDocumentRepository.class);
    private final DomainEventPublisher events = mock(DomainEventPublisher.class);
    private final UUID partner = UUID.randomUUID();
    private final UUID operator = UUID.randomUUID();
    private PartnerDocumentService service;

    @BeforeEach
    void setUp() {
        service = new PartnerDocumentService(repository, mock(DocumentStorage.class), DocumentRequirementsTest.defaults(),
                mock(VerificationAudit.class), events, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private PartnerDocumentEntity waiting(PartnerDocumentType type) {
        PartnerDocumentEntity doc = new PartnerDocumentEntity(partner, type, PartnerDocumentSource.PARTNER_UPLOAD,
                PartnerDocumentStatus.UNDER_REVIEW, NOW);
        doc.setDocumentKey("key-" + type);
        when(repository.findLockedById(any())).thenReturn(Optional.of(doc));
        return doc;
    }

    private static DocumentFacts facts(String number, LocalDate validUntil, InsuranceUseType use) {
        return new DocumentFacts(number, null, validUntil, use);
    }

    @Test
    void aLicenceIsApprovedWithItsNumberAndDate() {
        PartnerDocumentEntity licence = waiting(PartnerDocumentType.DRIVING_LICENCE);

        var result = service.review(UUID.randomUUID(), operator, PartnerDocumentStatus.VERIFIED, null,
                facts("ts09 2015 0001234", TODAY.plusYears(5), null));

        assertThat(result.isSuccess()).isTrue();
        assertThat(licence.getStatus()).isEqualTo(PartnerDocumentStatus.VERIFIED);
        assertThat(licence.getDocumentNumber()).isEqualTo("TS09 2015 0001234");
        assertThat(licence.getReviewedBy()).isEqualTo(operator);
    }

    @Test
    void notWithoutTheDateItExpiresOn() {
        PartnerDocumentEntity licence = waiting(PartnerDocumentType.DRIVING_LICENCE);

        var result = service.review(UUID.randomUUID(), operator, PartnerDocumentStatus.VERIFIED, null,
                facts("TS0920150001234", null, null));

        assertThat(result.error()).isEqualTo(VerificationError.VALID_UNTIL_REQUIRED);
        assertThat(licence.getStatus()).isEqualTo(PartnerDocumentStatus.UNDER_REVIEW);
    }

    @Test
    void notWithoutItsNumber() {
        waiting(PartnerDocumentType.PUC);

        var result = service.review(UUID.randomUUID(), operator, PartnerDocumentStatus.VERIFIED, null,
                facts(" ", TODAY.plusMonths(6), null));

        assertThat(result.error()).isEqualTo(VerificationError.DOCUMENT_NUMBER_REQUIRED);
    }

    @Test
    void notOnceItHasExpired() {
        waiting(PartnerDocumentType.PUC);

        var result = service.review(UUID.randomUUID(), operator, PartnerDocumentStatus.VERIFIED, null,
                facts("PUC123", TODAY.minusDays(1), null));

        assertThat(result.error()).isEqualTo(VerificationError.DOCUMENT_ALREADY_EXPIRED);
    }

    @Test
    void aDocumentValidUntilTodayIsStillGoodToday() {
        waiting(PartnerDocumentType.PUC);

        var result = service.review(UUID.randomUUID(), operator, PartnerDocumentStatus.VERIFIED, null,
                facts("PUC123", TODAY, null));

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void notWithoutTheFileItself() {
        PartnerDocumentEntity licence = waiting(PartnerDocumentType.DRIVING_LICENCE);
        licence.setDocumentKey(null);

        var result = service.review(UUID.randomUUID(), operator, PartnerDocumentStatus.VERIFIED, null,
                facts("DL1", TODAY.plusYears(1), null));

        assertThat(result.error()).isEqualTo(VerificationError.DOCUMENT_FILE_MISSING);
    }

    @Test
    void aPrivatePolicyCannotBeApprovedForAPartnerWhoCarriesPassengers() {
        PartnerDocumentEntity policy = waiting(PartnerDocumentType.VEHICLE_INSURANCE);

        var result = service.review(UUID.randomUUID(), operator, PartnerDocumentStatus.VERIFIED, null,
                facts("POL-1", TODAY.plusMonths(8), InsuranceUseType.PRIVATE));

        assertThat(result.error()).isEqualTo(VerificationError.INSURANCE_NOT_COMMERCIAL);
        assertThat(policy.getStatus()).isEqualTo(PartnerDocumentStatus.UNDER_REVIEW);
        // The reason an operator sees says why, in plain words.
        assertThat(com.sheout.driververification.internal.web.VerificationControllerAccess
                .message(VerificationError.INSURANCE_NOT_COMMERCIAL))
                .contains("A private policy can be refused when she carries paying passengers");
    }

    @Test
    void anUnknownUsePolicyCannotBeApprovedEither() {
        waiting(PartnerDocumentType.VEHICLE_INSURANCE);

        var result = service.review(UUID.randomUUID(), operator, PartnerDocumentStatus.VERIFIED, null,
                facts("POL-1", TODAY.plusMonths(8), null));

        assertThat(result.error()).isEqualTo(VerificationError.INSURANCE_NOT_COMMERCIAL);
    }

    @Test
    void aCommercialPolicyIsApproved() {
        PartnerDocumentEntity policy = waiting(PartnerDocumentType.VEHICLE_INSURANCE);

        var result = service.review(UUID.randomUUID(), operator, PartnerDocumentStatus.VERIFIED, null,
                facts("POL-1", TODAY.plusMonths(8), InsuranceUseType.COMMERCIAL));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().insuranceUseType()).isEqualTo(InsuranceUseType.COMMERCIAL);
        assertThat(policy.getStatus()).isEqualTo(PartnerDocumentStatus.VERIFIED);
    }

    @Test
    void turningOneDownNeedsAReasonAndTellsHer() {
        PartnerDocumentEntity licence = waiting(PartnerDocumentType.DRIVING_LICENCE);

        assertThat(service.review(UUID.randomUUID(), operator, PartnerDocumentStatus.REJECTED, "  ", null).error())
                .isEqualTo(VerificationError.REASON_REQUIRED);
        verify(events, never()).publish(any());

        var result = service.review(UUID.randomUUID(), operator, PartnerDocumentStatus.REJECTED,
                "The licence number is not readable.", null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(licence.getStatus()).isEqualTo(PartnerDocumentStatus.REJECTED);
        assertThat(licence.getRejectionReason()).isEqualTo("The licence number is not readable.");
        verify(events).publish(any(PartnerDocumentRejected.class));
    }

    @Test
    void aDecidedDocumentIsNotDecidedAgain() {
        PartnerDocumentEntity licence = waiting(PartnerDocumentType.DRIVING_LICENCE);
        licence.markVerified(operator, NOW);

        var result = service.review(UUID.randomUUID(), operator, PartnerDocumentStatus.REJECTED, "changed my mind", null);

        assertThat(result.error()).isEqualTo(VerificationError.DOCUMENT_NOT_UNDER_REVIEW);
    }

    @Test
    void sheCannotUploadABackgroundReport() {
        var result = service.submit(partner, PartnerDocumentType.BACKGROUND_CHECK_REPORT, null, null,
                PartnerDocumentSource.PARTNER_UPLOAD, partner, false);

        assertThat(result.error()).isEqualTo(VerificationError.DOCUMENT_TYPE_NOT_UPLOADABLE);
    }
}
