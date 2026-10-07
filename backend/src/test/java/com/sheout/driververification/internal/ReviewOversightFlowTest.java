package com.sheout.driververification.internal;

import com.sheout.auth.AccountRegistered;
import com.sheout.auth.AccountRole;
import com.sheout.driververification.CheckStatus;
import com.sheout.driververification.CheckType;
import com.sheout.driververification.PartnerDocumentSource;
import com.sheout.driververification.PartnerDocumentStatus;
import com.sheout.driververification.PartnerDocumentSummary;
import com.sheout.driververification.PartnerDocumentType;
import com.sheout.driververification.RejectionReason;
import com.sheout.driververification.VerificationApi;
import com.sheout.driververification.VerificationCheck;
import com.sheout.driververification.internal.PartnerDocumentService.DocumentFacts;
import com.sheout.sharedkernel.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reviewer's side on the real database: a reason from the list and the
 * message she is shown; a number remembered for fraud and never for a
 * blurry photo; an override that needs a note and a second person; and the
 * weekly sample and its second look.
 */
@SpringBootTest(properties = {"sheout.warm-up.enabled=false", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class ReviewOversightFlowTest {

    @Autowired VerificationApi api;
    @Autowired PartnerDocumentService documents;
    @Autowired VerificationCheckService checks;
    @Autowired ReviewOversightService oversight;
    @Autowired ApplicationEventPublisher publisher;
    @Autowired JdbcTemplate jdbc;
    @Autowired VerificationConsent consent;

    private final List<UUID> accounts = new ArrayList<>();
    private final UUID reviewer = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
    private final String number = "TS09" + today.getYear() + String.format("%07d", (int) (Math.random() * 9_999_999));

    @AfterEach
    void cleanUp() {
        for (UUID id : accounts) {
            documents.eraseFor(id);
            jdbc.update("delete from verification_sample_reviews where account_id = ?", id);
            jdbc.update("delete from flagged_document_numbers where account_id = ?", id);
            jdbc.update("delete from verification_checks where account_id = ?", id);
            jdbc.update("delete from partner_documents where account_id = ?", id);
            jdbc.update("delete from verification_audit_events where account_id = ?", id);
            jdbc.update("delete from verification_records where account_id = ?", id);
            jdbc.update("delete from driver_profiles where account_id = ?", id);
            jdbc.update("delete from referral_codes where account_id = ?", id);
        }
    }

    @Test
    void sheIsToldTheListedFixAndOnlyFraudIsRemembered() {
        UUID blurry = partner();
        PartnerDocumentSummary first = licence(blurry);
        assertThat(documents.review(first.id(), reviewer, PartnerDocumentStatus.REJECTED, null, null, null, null, null,
                RejectionReason.PHOTO_UNREADABLE, "corner cut off").isSuccess()).isTrue();
        PartnerDocumentSummary sentBack = documents.find(first.id()).orElseThrow();
        assertThat(sentBack.rejectionCode()).isEqualTo("PHOTO_UNREADABLE");
        assertThat(sentBack.rejectionReason()).as("her fix, not the staff note")
                .startsWith("The photo is blurry").doesNotContain("corner cut off");
        assertThat(jdbc.queryForObject("select count(*) from flagged_document_numbers where document_number = ?", Integer.class, number))
                .as("a blurry photo is never remembered").isZero();

        // ---- a reason that does not fit the document is refused
        PartnerDocumentSummary again = licence(blurry);
        assertThat(documents.review(again.id(), reviewer, PartnerDocumentStatus.REJECTED, null, null, null, null, null,
                RejectionReason.NOT_COMMERCIAL, null).error()).isEqualTo(VerificationError.INVALID_REJECTION_REASON);

        // ---- an altered licence is remembered, and fails its check on the next account that sends it
        assertThat(documents.review(again.id(), reviewer, PartnerDocumentStatus.REJECTED, null, null, null, null, null,
                RejectionReason.DOCUMENT_ALTERED, null).isSuccess()).isTrue();
        UUID nextAccount = partner();
        licence(nextAccount);
        VerificationCheck check = api.checksFor(nextAccount).find(CheckType.LICENCE).orElseThrow();
        assertThat(check.status()).isEqualTo(CheckStatus.FAILED_AUTO);
        assertThat(check.overridable()).isTrue();
    }

    @Test
    void anOverrideNeedsANoteAndASecondPersonAndThenLetsThePersonApprove() {
        UUID earlier = partner();
        jdbc.update("insert into flagged_document_numbers (id, document_type, document_number, reason_code, account_id,"
                + " flagged_by, created_at, updated_at) values (?, 'DRIVING_LICENCE', ?, 'NOT_HERS', ?, ?, now(), now())",
                UUID.randomUUID(), number, earlier, reviewer);
        UUID partner = partner();
        PartnerDocumentSummary licence = licence(partner);

        assertThat(checks.prepareOverride(partner, CheckType.LICENCE, "ok", reviewer).error())
                .isEqualTo(VerificationError.OVERRIDE_NOTE_REQUIRED);
        Result<VerificationCheckService.OverrideRequest, VerificationError> asked = checks.prepareOverride(partner,
                CheckType.LICENCE, "The earlier refusal was a typing mistake by staff; Sarathi shows her name.", reviewer);
        assertThat(asked.isSuccess()).isTrue();
        assertThat(api.checksFor(partner).find(CheckType.LICENCE).orElseThrow().status())
                .as("nothing changes until a second person signs off").isEqualTo(CheckStatus.FAILED_AUTO);
        assertThat(checks.applyOverride(asked.value(), reviewer).error()).isEqualTo(VerificationError.NEEDS_SECOND_PERSON);

        assertThat(checks.applyOverride(asked.value(), second).isSuccess()).isTrue();
        VerificationCheck accepted = api.checksFor(partner).find(CheckType.LICENCE).orElseThrow();
        assertThat(accepted.status()).isEqualTo(CheckStatus.APPROVED_BY_PERSON);
        assertThat(accepted.overrideRequestedBy()).isEqualTo(reviewer);
        assertThat(accepted.overrideApprovedBy()).isEqualTo(second);

        // ---- the document itself is still a person's decision
        assertThat(documents.review(licence.id(), reviewer, PartnerDocumentStatus.VERIFIED, null, null, null, null,
                java.util.EnumSet.allOf(com.sheout.driververification.CheckFinding.class)).isSuccess()).isTrue();
        assertThat(oversight.reviewerStats(1)).anySatisfy(s -> {
            assertThat(s.reviewerId()).isEqualTo(reviewer);
            assertThat(s.overridesAsked()).isEqualTo(1);
            assertThat(s.approvals()).isEqualTo(1);
        });
    }

    @Test
    void theWeeklySampleIsFixedForTheWeekAndTheSecondLookIsSomebodyElse() {
        UUID partner = partner();
        PartnerDocumentSummary licence = licence(partner);
        documents.review(licence.id(), reviewer, PartnerDocumentStatus.REJECTED, null, null, null, null, null,
                RejectionReason.NUMBER_MISMATCH, null);
        String week = ReviewOversightService.currentWeek();
        ReviewOversightService.Sample sample = oversight.weeklySample(week).value();
        assertThat(sample.poolSize()).isPositive();
        assertThat(sample.cases()).isNotEmpty();
        assertThat(oversight.weeklySample(week).value().cases()).extracting(ReviewOversightService.SampleCase::accountId)
                .as("the same draw every time it is opened")
                .containsExactlyElementsOf(sample.cases().stream().map(ReviewOversightService.SampleCase::accountId).toList());
        assertThat(oversight.weeklySample("2026-41").error()).isEqualTo(VerificationError.INVALID_WEEK);

        // ---- a second look at this partner, when the draw includes her
        boolean drawn = sample.cases().stream().anyMatch(c -> c.accountId().equals(partner));
        if (drawn) {
            assertThat(oversight.recordSecondLook(partner, week, reviewer, "AGREE", null).error())
                    .isEqualTo(VerificationError.NEEDS_SECOND_PERSON);
            assertThat(oversight.recordSecondLook(partner, week, second, "CONCERN", null).error())
                    .isEqualTo(VerificationError.REASON_REQUIRED);
            assertThat(oversight.recordSecondLook(partner, week, second, "AGREE", null).isSuccess()).isTrue();
            assertThat(oversight.recordSecondLook(partner, week, second, "AGREE", null).error())
                    .isEqualTo(VerificationError.ALREADY_LOOKED);
        }
        assertThat(ReviewOversightService.mondayOf("2026-W41")).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(ReviewOversightService.weekOf(LocalDate.of(2026, 10, 7))).isEqualTo("2026-W41");
    }

    // ---------------------------------------------------------------- helpers

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

    private PartnerDocumentSummary licence(UUID partner) {
        Result<PartnerDocumentSummary, VerificationError> result = documents.submit(partner, PartnerDocumentType.DRIVING_LICENCE,
                VerificationChecksFlowTest.pdf(), new DocumentFacts(number, null, today.plusYears(5), null, null, null,
                        "Lakshmi Devi", false, null), PartnerDocumentSource.PARTNER_UPLOAD, partner, false);
        assertThat(result.isSuccess()).as(String.valueOf(result.isFailure() ? result.error() : "")).isTrue();
        return result.value();
    }
}
