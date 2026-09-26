package com.sheout.driververification.internal;

import com.sheout.auth.AccountRole;
import com.sheout.driververification.VerificationStatus;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import com.sheout.sharedkernel.storage.DocumentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
    private VerificationService service;

    @BeforeEach
    void setUp() {
        service = new VerificationService(repository, mock(VerificationFunnelRepository.class),
                mock(DocumentStorage.class), mock(DomainEventPublisher.class), 240);
        record = new VerificationRecordEntity(partner, AccountRole.DRIVER);
        when(repository.findByAccountId(partner)).thenReturn(Optional.of(record));
    }

    @ParameterizedTest
    @EnumSource(value = VerificationStatus.class, names = {"PENDING", "UNDER_REVIEW", "REJECTED"})
    void refusedUntilTheIdCheckHasPassed(VerificationStatus idCheck) {
        record.setGenderVerificationStatus(idCheck);

        var result = service.reviewPoliceVerification(partner, UUID.randomUUID(), VerificationStatus.VERIFIED);

        assertThat(result.error()).isEqualTo(VerificationError.ID_CHECK_NOT_PASSED);
        assertThat(record.getPoliceVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        verify(repository, never()).save(any());
    }

    @Test
    void recordedOnceTheIdCheckHasPassed() {
        record.setGenderVerificationStatus(VerificationStatus.VERIFIED);

        var result = service.reviewPoliceVerification(partner, UUID.randomUUID(), VerificationStatus.VERIFIED);

        assertThat(result.isSuccess()).isTrue();
        assertThat(record.getPoliceVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
    }
}
