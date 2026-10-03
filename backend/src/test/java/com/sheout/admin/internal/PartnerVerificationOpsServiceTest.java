package com.sheout.admin.internal;

import com.sheout.auth.AccountRole;
import com.sheout.auth.AuthApi;
import com.sheout.driververification.PartnerDocumentSource;
import com.sheout.driververification.PartnerDocumentStatus;
import com.sheout.driververification.PartnerDocumentSummary;
import com.sheout.driververification.PartnerDocumentType;
import com.sheout.driververification.VerificationApi;
import com.sheout.driververification.VerificationStatus;
import com.sheout.driververification.VerificationSummary;
import com.sheout.users.DriverProfileApi;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The documents queues say how many days are left, from today in India, and carry no file link. */
class PartnerVerificationOpsServiceTest {

    private final VerificationApi verification = mock(VerificationApi.class);
    private final DriverProfileApi profiles = mock(DriverProfileApi.class);
    private final AuthApi auth = mock(AuthApi.class);
    private final PartnerVerificationOpsService ops = new PartnerVerificationOpsService(verification, profiles, auth);
    private final UUID partner = UUID.randomUUID();
    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    @Test
    void anExpiringDocumentSaysHowManyDaysAreLeft() {
        when(profiles.findByAccountId(any())).thenReturn(Optional.empty());
        when(auth.findAccount(any())).thenReturn(Optional.empty());
        when(verification.findDocumentsExpiringWithin(30)).thenReturn(List.of(new PartnerDocumentSummary(UUID.randomUUID(),
                partner, PartnerDocumentType.PUC, PartnerDocumentStatus.VERIFIED, "PUC-1", null, today.plusDays(12),
                null, null, PartnerDocumentSource.PARTNER_UPLOAD, null, true, Instant.now(), Instant.now(), null, null, null)));

        DocumentQueueRow row = ops.documentQueue(PartnerVerificationOpsService.Queue.EXPIRING).get(0);

        assertThat(row.daysLeft()).isEqualTo(12);
        assertThat(row.documentType()).isEqualTo(PartnerDocumentType.PUC);
    }

    @Test
    void aPoliceCheckFallingDueIsARowWithItsDueDate() {
        when(profiles.findByAccountId(any())).thenReturn(Optional.empty());
        when(auth.findAccount(any())).thenReturn(Optional.empty());
        when(verification.findPoliceReverificationDueWithin(30)).thenReturn(List.of(new VerificationSummary(partner,
                AccountRole.DRIVER, VerificationStatus.VERIFIED, VerificationStatus.VERIFIED, true, null, Instant.now(), Instant.now())));
        when(verification.policeReverifyDueOn(partner)).thenReturn(Optional.of(today.plusDays(6)));

        DocumentQueueRow row = ops.documentQueue(PartnerVerificationOpsService.Queue.POLICE_DUE).get(0);

        assertThat(row.documentId()).isNull();
        assertThat(row.documentType()).isEqualTo(PartnerDocumentType.POLICE_CERTIFICATE);
        assertThat(row.validUntil()).isEqualTo(today.plusDays(6));
        assertThat(row.daysLeft()).isEqualTo(6);
    }
}
