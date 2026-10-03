package com.sheout.driververification.internal;

import com.sheout.auth.AccountRole;
import com.sheout.driververification.PoliceVerificationMethod;
import com.sheout.driververification.VerificationStatus;
import com.sheout.driververification.internal.PoliceVerificationService.PoliceDecision;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The police check is recorded on a partner whose ID has passed, and not before. */
class PoliceCheckOrderTest {

    private final VerificationRecordRepository repository = mock(VerificationRecordRepository.class);
    private final UUID partner = UUID.randomUUID();
    private VerificationRecordEntity record;
    private PoliceVerificationService service;

    @BeforeEach
    void setUp() {
        service = new PoliceVerificationService(repository, mock(PoliceVerificationRepository.class),
                mock(PartnerDocumentService.class), mock(VerificationConsent.class),
                new PoliceVerificationRules(12, false, "30,7", "v1"), mock(VerificationAudit.class),
                mock(DomainEventPublisher.class));
        record = new VerificationRecordEntity(partner, AccountRole.DRIVER);
        when(repository.findByAccountId(partner)).thenReturn(Optional.of(record));
    }

    @ParameterizedTest
    @EnumSource(value = VerificationStatus.class, names = {"PENDING", "UNDER_REVIEW", "REJECTED"})
    void refusedUntilTheIdCheckHasPassed(VerificationStatus idCheck) {
        record.setGenderVerificationStatus(idCheck);

        var result = service.review(partner, UUID.randomUUID(), new PoliceDecision(VerificationStatus.VERIFIED,
                PoliceVerificationMethod.TS_POLICE_PVC, "PVC-1", null, LocalDate.now().minusDays(3), null,
                UUID.randomUUID(), null, null));

        assertThat(result.error()).isEqualTo(VerificationError.ID_CHECK_NOT_PASSED);
        assertThat(record.getPoliceVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        verify(repository, never()).save(any());
    }

    @Test
    void aRejectionIsRecordedOnceTheIdCheckHasPassed() {
        record.setGenderVerificationStatus(VerificationStatus.VERIFIED);

        var result = service.review(partner, UUID.randomUUID(),
                new PoliceDecision(VerificationStatus.REJECTED, null, null, null, null, null, null, null, "Record found"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(record.getPoliceVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
    }
}
