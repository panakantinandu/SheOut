package com.sheout.driververification.internal;

import com.sheout.auth.AccountRegistered;
import com.sheout.auth.AccountRole;
import com.sheout.driververification.CheckFinding;
import com.sheout.driververification.InsuranceUseType;
import com.sheout.driververification.PartnerDocumentSource;
import com.sheout.driververification.PartnerDocumentStatus;
import com.sheout.driververification.PartnerDocumentSummary;
import com.sheout.driververification.PartnerDocumentType;
import com.sheout.driververification.VehicleOwner;
import com.sheout.driververification.VerificationApi;
import com.sheout.driververification.VerificationStageReached;
import com.sheout.driververification.VerificationStageReached.Stage;
import com.sheout.driververification.VerificationStatus;
import com.sheout.driververification.internal.PartnerDocumentService.DocumentFacts;
import com.sheout.sharedkernel.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * She is told at each stage: we have her ID, it is approved, we have her
 * papers, they are approved, we have her police certificate. Once each, as
 * it happens, and never for an account that existed before checks did.
 */
@SpringBootTest(properties = {"sheout.warm-up.enabled=false", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
@RecordApplicationEvents
class VerificationStagesFlowTest {

    @Autowired VerificationApi api;
    @Autowired VerificationService verification;
    @Autowired PartnerDocumentService documents;
    @Autowired ApplicationEventPublisher publisher;
    @Autowired JdbcTemplate jdbc;
    @Autowired VerificationConsent consent;
    @Autowired ApplicationEvents events;

    private final List<UUID> accounts = new ArrayList<>();
    private final UUID operator = UUID.randomUUID();
    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
    private final EnumSet<CheckFinding> lookedAtEverything = EnumSet.allOf(CheckFinding.class);

    @AfterEach
    void cleanUp() {
        for (UUID id : accounts) {
            documents.eraseFor(id);
            jdbc.update("delete from verification_checks where account_id = ?", id);
            jdbc.update("delete from partner_documents where account_id = ?", id);
            jdbc.update("delete from verification_audit_events where account_id = ?", id);
            jdbc.update("delete from verification_records where account_id = ?", id);
            jdbc.update("delete from driver_profiles where account_id = ?", id);
            jdbc.update("delete from referral_codes where account_id = ?", id);
        }
    }

    @Test
    void aPartnerHearsAtEveryStageOnce() {
        UUID partner = partner();
        assertThat(stages(partner)).as("signing up is not a stage").isEmpty();

        jdbc.update("update verification_records set aadhaar_document_key = 'test/id', document_submitted_at = now(),"
                + " gender_verification_status = 'UNDER_REVIEW' where account_id = ?", partner);
        api.checksFor(partner);
        api.checksFor(partner);
        assertThat(stages(partner)).as("opened twice, told once").containsExactly(Stage.ID_RECEIVED);

        assertThat(verification.reviewGenderVerification(partner, operator, VerificationStatus.VERIFIED, null,
                lookedAtEverything).isSuccess()).isTrue();
        assertThat(stages(partner)).containsExactly(Stage.ID_RECEIVED, Stage.IDENTITY_APPROVED);

        PartnerDocumentSummary licence = upload(partner, PartnerDocumentType.DRIVING_LICENCE, "TS0920150001234");
        PartnerDocumentSummary rc = upload(partner, PartnerDocumentType.VEHICLE_RC, "TS09AB1234");
        assertThat(stages(partner)).as("not until all three are sent").doesNotContain(Stage.VEHICLE_PAPERS_RECEIVED);
        PartnerDocumentSummary policy = upload(partner, PartnerDocumentType.VEHICLE_INSURANCE, "POL1234567");
        assertThat(stages(partner)).endsWith(Stage.VEHICLE_PAPERS_RECEIVED);

        for (PartnerDocumentSummary doc : List.of(licence, rc, policy)) {
            assertThat(documents.review(doc.id(), operator, PartnerDocumentStatus.VERIFIED, null, null, null, null,
                    lookedAtEverything).isSuccess()).isTrue();
        }
        assertThat(stages(partner)).endsWith(Stage.VEHICLE_APPROVED);
        assertThat(stages(partner).stream().filter(s -> s == Stage.VEHICLE_APPROVED)).hasSize(1);

        Result<PartnerDocumentSummary, VerificationError> certificate = documents.submit(partner, PartnerDocumentType.POLICE_CERTIFICATE,
                VerificationChecksFlowTest.pdf(), new DocumentFacts("TSPVC/2026/77", today.minusDays(10), null, null),
                PartnerDocumentSource.PARTNER_UPLOAD, partner, false);
        assertThat(certificate.isSuccess()).isTrue();
        assertThat(stages(partner)).endsWith(Stage.SAFETY_CHECK_RECEIVED);
    }

    @Test
    void anAccountFromBeforeChecksIsNeverToldAnythingOnItsFirstLook() {
        UUID partner = partner();
        jdbc.update("delete from verification_checks where account_id = ?", partner);
        jdbc.update("update verification_records set aadhaar_document_key = 'test/id', document_submitted_at = now(),"
                + " gender_verification_status = 'VERIFIED' where account_id = ?", partner);
        api.checksFor(partner);
        assertThat(stages(partner)).isEmpty();
    }

    // ---------------------------------------------------------------- helpers

    private List<Stage> stages(UUID account) {
        return events.stream(VerificationStageReached.class).filter(e -> e.accountId().equals(account))
                .map(VerificationStageReached::stage).toList();
    }

    private UUID partner() {
        UUID id = UUID.randomUUID();
        accounts.add(id);
        publisher.publishEvent(new AccountRegistered(id, AccountRole.DRIVER));
        jdbc.update("update verification_records set consent_version = ?, consent_accepted_at = now() where account_id = ?",
                consent.currentVersion(), id);
        jdbc.update("update driver_profiles set vehicle_type = 'BIKE', name = 'Lakshmi Devi', date_of_birth = ? where account_id = ?",
                today.minusYears(30), id);
        return id;
    }

    private PartnerDocumentSummary upload(UUID partner, PartnerDocumentType type, String number) {
        Result<PartnerDocumentSummary, VerificationError> result = documents.submit(partner, type, VerificationChecksFlowTest.pdf(),
                new DocumentFacts(number, null, today.plusYears(3), type == PartnerDocumentType.VEHICLE_INSURANCE
                        ? InsuranceUseType.COMMERCIAL : null, type == PartnerDocumentType.VEHICLE_RC ? VehicleOwner.SELF : null, null,
                        type == PartnerDocumentType.DRIVING_LICENCE ? "Lakshmi Devi" : null, false, null),
                PartnerDocumentSource.PARTNER_UPLOAD, partner, false);
        assertThat(result.isSuccess()).as(String.valueOf(result.isFailure() ? result.error() : "")).isTrue();
        return result.value();
    }
}
