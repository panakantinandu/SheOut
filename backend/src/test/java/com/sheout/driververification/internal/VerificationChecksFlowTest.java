package com.sheout.driververification.internal;

import com.sheout.auth.AccountRegistered;
import com.sheout.auth.AccountRole;
import com.sheout.driververification.CheckStatus;
import com.sheout.driververification.CheckType;
import com.sheout.driververification.InsuranceUseType;
import com.sheout.driververification.PartnerDocumentSource;
import com.sheout.driververification.PartnerDocumentStatus;
import com.sheout.driververification.PartnerDocumentSummary;
import com.sheout.driververification.PartnerDocumentType;
import com.sheout.driververification.VehicleOwner;
import com.sheout.driververification.VerificationApi;
import com.sheout.driververification.VerificationCheck;
import com.sheout.driververification.VerificationChecks;
import com.sheout.driververification.VerificationStatus;
import com.sheout.driververification.internal.PartnerDocumentService.DocumentFacts;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.storage.DocumentUpload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The checks engine on the real database: each check follows what she sent
 * and what a person decided, the overall state is worked out from the
 * required checks, and nothing but a person ever approves or sends back.
 * Like PartnerDocumentFlowTest, this needs the local Postgres and Redis.
 */
@SpringBootTest(properties = {"sheout.warm-up.enabled=false", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class VerificationChecksFlowTest {

    @Autowired VerificationService verification;
    @Autowired VerificationApi api;
    @Autowired PartnerDocumentService documents;
    @Autowired ApplicationEventPublisher publisher;
    @Autowired JdbcTemplate jdbc;
    @Autowired VerificationConsent consent;

    private final List<UUID> accounts = new ArrayList<>();
    private final UUID operator = UUID.randomUUID();
    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    @AfterEach
    void cleanUp() {
        for (UUID id : accounts) {
            documents.eraseFor(id);
            jdbc.update("delete from verification_checks where account_id = ?", id);
            jdbc.update("delete from partner_documents where account_id = ?", id);
            jdbc.update("delete from verification_audit_events where account_id = ?", id);
            jdbc.update("delete from verification_records where account_id = ?", id);
            jdbc.update("delete from driver_profiles where account_id = ?", id);
            jdbc.update("delete from customer_profiles where account_id = ?", id);
            jdbc.update("delete from referral_codes where account_id = ?", id);
        }
    }

    @Test
    void aRiderNeedsHerIdAndSelfieCheckedByAPersonAndNothingElse() {
        UUID rider = register(AccountRole.CUSTOMER);

        VerificationChecks before = api.checksFor(rider);
        assertThat(before.checks()).extracting(VerificationCheck::type)
                .containsExactly(CheckType.IDENTITY, CheckType.SELFIE_MATCH);
        assertThat(before.overall()).isEqualTo(VerificationChecks.Overall.WAITING_FOR_APPLICANT);

        sendId(rider);
        VerificationChecks sent = api.checksFor(rider);
        assertThat(sent.find(CheckType.IDENTITY).orElseThrow().status()).isEqualTo(CheckStatus.NEEDS_REVIEW);
        assertThat(sent.find(CheckType.SELFIE_MATCH).orElseThrow().reason()).contains("compares her live selfie");
        assertThat(sent.find(CheckType.SELFIE_MATCH).orElseThrow().provider()).isEqualTo("manual");
        assertThat(sent.overall()).isEqualTo(VerificationChecks.Overall.NEEDS_REVIEW);

        assertThat(verification.reviewGenderVerification(rider, operator, VerificationStatus.VERIFIED, null).isSuccess()).isTrue();
        VerificationChecks approved = api.checksFor(rider);
        assertThat(approved.checks()).allSatisfy(c -> {
            assertThat(c.status()).isEqualTo(CheckStatus.APPROVED_BY_PERSON);
            assertThat(c.decidedBy()).isEqualTo(operator);
            assertThat(c.decidedByRole()).isEqualTo("OPERATOR");
        });
        assertThat(approved.overall()).isEqualTo(VerificationChecks.Overall.READY_TO_APPROVE);
    }

    @Test
    void aPartnersDocumentChecksFollowHerUploadsAndThePersonsDecisions() {
        UUID partner = register(AccountRole.DRIVER);
        jdbc.update("update verification_records set consent_version = ?, consent_accepted_at = now() where account_id = ?",
                consent.currentVersion(), partner);
        jdbc.update("update driver_profiles set vehicle_type = 'BIKE' where account_id = ?", partner);

        VerificationChecks empty = api.checksFor(partner);
        assertThat(empty.checks()).extracting(VerificationCheck::type).containsExactly(CheckType.IDENTITY,
                CheckType.SELFIE_MATCH, CheckType.LICENCE, CheckType.VEHICLE, CheckType.INSURANCE, CheckType.PAN,
                CheckType.BACKGROUND);
        VerificationCheck pan = empty.find(CheckType.PAN).orElseThrow();
        assertThat(pan.required()).as("PAN is optional").isFalse();
        assertThat(empty.find(CheckType.LICENCE).orElseThrow().status()).isEqualTo(CheckStatus.PENDING);

        // ---- her licence arrives: a person must read it
        PartnerDocumentSummary licence = upload(partner, PartnerDocumentType.DRIVING_LICENCE, "TS0920150001234");
        VerificationCheck waiting = api.checksFor(partner).find(CheckType.LICENCE).orElseThrow();
        assertThat(waiting.status()).isEqualTo(CheckStatus.NEEDS_REVIEW);
        assertThat(waiting.subjectId()).isEqualTo(licence.id());

        // ---- a person sends it back: pending again, waiting for her, never "rejected" by software
        assertThat(documents.review(licence.id(), operator, PartnerDocumentStatus.REJECTED,
                "The photo is blurry.", null).isSuccess()).isTrue();
        VerificationCheck sentBack = api.checksFor(partner).find(CheckType.LICENCE).orElseThrow();
        assertThat(sentBack.status()).isEqualTo(CheckStatus.PENDING);
        assertThat(sentBack.reason()).contains("Sent back").contains("blurry");
        assertThat(sentBack.decidedBy()).isEqualTo(operator);

        // ---- she sends it again; a person approves it
        PartnerDocumentSummary again = upload(partner, PartnerDocumentType.DRIVING_LICENCE, "TS0920150001234");
        assertThat(api.checksFor(partner).find(CheckType.LICENCE).orElseThrow().status()).isEqualTo(CheckStatus.NEEDS_REVIEW);
        assertThat(documents.review(again.id(), operator, PartnerDocumentStatus.VERIFIED, null, null).isSuccess()).isTrue();
        VerificationChecks after = api.checksFor(partner);
        assertThat(after.find(CheckType.LICENCE).orElseThrow().status()).isEqualTo(CheckStatus.APPROVED_BY_PERSON);
        assertThat(after.overall()).as("her ID and other papers are still to come")
                .isEqualTo(VerificationChecks.Overall.WAITING_FOR_APPLICANT);
        assertThat(jdbc.queryForObject("select count(*) from verification_audit_events where account_id = ? and action = 'CHECK_UPDATED'",
                Integer.class, partner)).as("the software's moves are on her audit trail").isPositive();
    }

    @Test
    void theOverallStateIsWorstFirstOverRequiredChecksOnly() {
        java.util.function.BiFunction<CheckStatus, Boolean, VerificationCheck> check = (status, required) ->
                new VerificationCheck(CheckType.LICENCE, required, status, null, List.of(), "manual", null, null, "SYSTEM",
                        null, null, null, null);
        assertThat(VerificationChecks.overallOf(List.of(check.apply(CheckStatus.PASSED, true),
                check.apply(CheckStatus.APPROVED_BY_PERSON, true), check.apply(CheckStatus.NEEDS_REVIEW, false))))
                .isEqualTo(VerificationChecks.Overall.READY_TO_APPROVE);
        assertThat(VerificationChecks.overallOf(List.of(check.apply(CheckStatus.NEEDS_REVIEW, true),
                check.apply(CheckStatus.FAILED_AUTO, true)))).isEqualTo(VerificationChecks.Overall.FAILED_CHECK);
        assertThat(VerificationChecks.overallOf(List.of(check.apply(CheckStatus.PENDING, true),
                check.apply(CheckStatus.NEEDS_REVIEW, true)))).isEqualTo(VerificationChecks.Overall.NEEDS_REVIEW);
        assertThat(VerificationChecks.overallOf(List.of(check.apply(CheckStatus.PENDING, true),
                check.apply(CheckStatus.PASSED, true)))).isEqualTo(VerificationChecks.Overall.WAITING_FOR_APPLICANT);
    }

    // ---------------------------------------------------------------- helpers

    private UUID register(AccountRole role) {
        UUID id = UUID.randomUUID();
        accounts.add(id);
        publisher.publishEvent(new AccountRegistered(id, role));
        return id;
    }

    /** Her ID and selfie as the submission leaves them, without the camera. */
    private void sendId(UUID account) {
        jdbc.update("update verification_records set aadhaar_document_key = 'test/id', document_submitted_at = now(),"
                + " gender_verification_status = 'UNDER_REVIEW' where account_id = ?", account);
    }

    private PartnerDocumentSummary upload(UUID partner, PartnerDocumentType type, String number) {
        Result<PartnerDocumentSummary, VerificationError> result = documents.submit(partner, type, pdf(),
                new DocumentFacts(number, null, today.plusYears(3), type == PartnerDocumentType.VEHICLE_INSURANCE
                        ? InsuranceUseType.COMMERCIAL : null, type == PartnerDocumentType.VEHICLE_RC ? VehicleOwner.SELF : null, null),
                PartnerDocumentSource.PARTNER_UPLOAD, partner, false);
        assertThat(result.isSuccess()).as(String.valueOf(result.isFailure() ? result.error() : "")).isTrue();
        return result.value();
    }

    static DocumentUpload pdf() {
        byte[] bytes = new byte[24 * 1024];
        Arrays.fill(bytes, (byte) ' ');
        byte[] head = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(head, 0, bytes, 0, head.length);
        return new DocumentUpload("document.pdf", "application/pdf", bytes);
    }
}
