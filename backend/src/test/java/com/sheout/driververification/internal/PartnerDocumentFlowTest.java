package com.sheout.driververification.internal;

import com.sheout.auth.AccountRegistered;
import com.sheout.auth.AccountRole;
import com.sheout.driververification.InsuranceUseType;
import com.sheout.driververification.PartnerDocumentExpiring;
import com.sheout.driververification.PartnerDocumentSource;
import com.sheout.driververification.PartnerDocumentStatus;
import com.sheout.driververification.PartnerDocumentSummary;
import com.sheout.driververification.PartnerDocumentType;
import com.sheout.driververification.PartnerReadiness;
import com.sheout.driververification.PartnerReadiness.BlockerCode;
import com.sheout.driververification.VerificationApi;
import com.sheout.driververification.VerificationLapsed;
import com.sheout.driververification.internal.PartnerDocumentService.DocumentFacts;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.storage.DocumentUpload;
import com.sheout.users.DriverProfileApi;
import com.sheout.users.OnlineStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A partner's documents end to end on the real database: the migration's
 * table and its one-current-row index, uploads and renewals, the operator's
 * decision, the gate users and dispatch ask, and the expiry sweep taking her
 * offline after her trip. Like SheOutApplicationTests, this needs the local
 * Postgres and Redis.
 */
@SpringBootTest(properties = {"sheout.warm-up.enabled=false", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
@RecordApplicationEvents
class PartnerDocumentFlowTest {

    @Autowired PartnerDocumentService documents;
    @Autowired VerificationApi verification;
    @Autowired DriverProfileApi driverProfiles;
    @Autowired ApplicationEventPublisher publisher;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationEvents events;

    private final UUID partner = UUID.randomUUID();
    private final UUID operator = UUID.randomUUID();
    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    @BeforeEach
    void setUp() {
        // Both modules create their rows from the signup event, as in production.
        publisher.publishEvent(new AccountRegistered(partner, AccountRole.DRIVER));
        jdbc.update("update verification_records set gender_verification_status = 'VERIFIED',"
                + " police_verification_status = 'VERIFIED' where account_id = ?", partner);
        jdbc.update("update driver_profiles set vehicle_type = 'BIKE' where account_id = ?", partner);
    }

    @AfterEach
    void cleanUp() {
        documents.eraseFor(partner);
        jdbc.update("delete from partner_documents where account_id = ?", partner);
        jdbc.update("delete from verification_audit_events where account_id = ?", partner);
        jdbc.update("delete from verification_records where account_id = ?", partner);
        jdbc.update("delete from driver_profiles where account_id = ?", partner);
        jdbc.update("delete from referral_codes where account_id = ?", partner);
    }

    @Test
    void sheWorksOnlyWithEveryDocumentApprovedAndInDate() {
        // ---- nothing on file: four documents missing, in checklist order
        PartnerReadiness empty = verification.partnerReadiness(partner, "BIKE");
        assertThat(empty.ready()).isFalse();
        assertThat(empty.blockers()).extracting(PartnerReadiness.Blocker::documentType).containsExactly(
                PartnerDocumentType.DRIVING_LICENCE, PartnerDocumentType.VEHICLE_RC,
                PartnerDocumentType.VEHICLE_INSURANCE, PartnerDocumentType.PUC);
        assertThat(driverProfiles.isCurrentlyVerified(partner)).as("dispatch asks the same question").isFalse();

        // ---- she sends them; what she types is required
        assertThat(documents.submit(partner, PartnerDocumentType.DRIVING_LICENCE, pdf(), new DocumentFacts(null, null,
                today.plusYears(3), null), PartnerDocumentSource.PARTNER_UPLOAD, partner, false).error())
                .isEqualTo(VerificationError.DOCUMENT_NUMBER_REQUIRED);
        PartnerDocumentSummary licence = upload(PartnerDocumentType.DRIVING_LICENCE, "TS0920150001234", today.plusYears(3), null);
        PartnerDocumentSummary rc = upload(PartnerDocumentType.VEHICLE_RC, "TS09AB1234", today.plusYears(10), null);
        PartnerDocumentSummary policy = upload(PartnerDocumentType.VEHICLE_INSURANCE, "POL-77", today.plusMonths(9),
                InsuranceUseType.PRIVATE);
        PartnerDocumentSummary puc = upload(PartnerDocumentType.PUC, "PUC-1", today.plusMonths(6), null);
        assertThat(verification.partnerReadiness(partner, "BIKE").blockers())
                .extracting(PartnerReadiness.Blocker::code).containsOnly(BlockerCode.DOCUMENT_UNDER_REVIEW);
        assertThat(verification.findDocumentsAwaitingReview()).extracting(PartnerDocumentSummary::id)
                .contains(licence.id(), rc.id(), policy.id(), puc.id());

        // ---- the operator: the private policy is refused until she brings a commercial one
        approve(licence.id());
        approve(rc.id());
        approve(puc.id());
        assertThat(documents.review(policy.id(), operator, PartnerDocumentStatus.VERIFIED, null, null).error())
                .isEqualTo(VerificationError.INSURANCE_NOT_COMMERCIAL);
        PartnerDocumentSummary commercial = upload(PartnerDocumentType.VEHICLE_INSURANCE, "POL-78", today.plusMonths(9),
                InsuranceUseType.COMMERCIAL);
        approve(commercial.id());

        PartnerReadiness ready = verification.partnerReadiness(partner, "BIKE");
        assertThat(ready.ready()).isTrue();
        assertThat(ready.blockers()).isEmpty();
        assertThat(driverProfiles.isCurrentlyVerified(partner)).isTrue();
        // An auto would also need a fitness certificate.
        assertThat(verification.partnerReadiness(partner, "AUTO").firstBlocker().documentType())
                .isEqualTo(PartnerDocumentType.FITNESS_CERTIFICATE);

        // ---- a renewal waiting for review does not take her off the road
        PartnerDocumentSummary renewal = upload(PartnerDocumentType.PUC, "PUC-2", today.plusMonths(12), null);
        PartnerReadiness waiting = verification.partnerReadiness(partner, "BIKE");
        assertThat(waiting.ready()).isTrue();
        assertThat(waiting.documents()).filteredOn(d -> d.type() == PartnerDocumentType.PUC)
                .singleElement().satisfies(d -> {
                    assertThat(d.renewalUnderReview()).isTrue();
                    assertThat(d.documentNumber()).isEqualTo("PUC-1");
                });
        // ...nor does it being turned down: the earlier one is current again.
        assertThat(documents.review(renewal.id(), operator, PartnerDocumentStatus.REJECTED, "Blurred photo", null).isSuccess())
                .isTrue();
        assertThat(documents.current(partner, PartnerDocumentType.PUC).orElseThrow().getId()).isEqualTo(puc.id());
        assertThat(verification.partnerReadiness(partner, "BIKE").ready()).isTrue();

        // ---- opening a document is on her record, with who opened it
        assertThat(verification.openPartnerDocument(licence.id(), operator)).isPresent();
        assertThat(verification.auditTrail(partner))
                .anySatisfy(e -> {
                    assertThat(e.action()).isEqualTo("DOCUMENT_VIEWED");
                    assertThat(e.actorId()).isEqualTo(operator);
                    assertThat(e.documentId()).isEqualTo(licence.id());
                });
    }

    @Test
    void anExpiredDocumentTakesHerOfflineAfterHerTripAndSaysWhy() {
        approveAll();
        jdbc.update("update driver_profiles set online_status = 'ONLINE' where account_id = ?", partner);
        LocalDate yesterday = today.minusDays(1);
        jdbc.update("update partner_documents set valid_until = ? where account_id = ? and type = 'VEHICLE_INSURANCE'",
                yesterday, partner);

        documents.expireAndRemind();

        assertThat(documents.current(partner, PartnerDocumentType.VEHICLE_INSURANCE).orElseThrow().getStatus())
                .isEqualTo(PartnerDocumentStatus.EXPIRED);
        assertThat(events.stream(VerificationLapsed.class))
                .anyMatch(e -> e.accountId().equals(partner) && e.documentType() == PartnerDocumentType.VEHICLE_INSURANCE);
        assertThat(driverProfiles.findByAccountId(partner).orElseThrow().onlineStatus()).isEqualTo(OnlineStatus.OFFLINE);
        assertThat(driverProfiles.isCurrentlyVerified(partner)).isFalse();
        PartnerReadiness.Blocker reason = verification.partnerReadiness(partner, "BIKE").firstBlocker();
        assertThat(reason.code()).isEqualTo(BlockerCode.DOCUMENT_EXPIRED);
        assertThat(reason.date()).isEqualTo(yesterday);
        assertThat(reason.message()).startsWith("Your vehicle insurance expired on ")
                .endsWith("Upload the new one to go online.");
        assertThat(verification.findExpiredDocuments()).anyMatch(d -> d.accountId().equals(partner));
        assertThat(verification.auditTrail(partner)).anyMatch(e -> e.action().equals("DOCUMENT_EXPIRED") && e.actorId() == null);
    }

    @Test
    void sheIsRemindedOnceAtEachThreshold() {
        approveAll();
        jdbc.update("update partner_documents set valid_until = ? where account_id = ? and type = 'PUC'",
                today.plusDays(5), partner);

        documents.expireAndRemind();
        documents.expireAndRemind();
        assertThat(reminders()).as("7-day reminder, once").containsExactly(5);

        jdbc.update("update partner_documents set valid_until = ? where account_id = ? and type = 'PUC'",
                today.plusDays(1), partner);
        events.clear();
        documents.expireAndRemind();
        assertThat(reminders()).as("then the 1-day reminder").containsExactly(1);

        assertThat(verification.partnerReadiness(partner, "BIKE").warnings())
                .anyMatch(w -> w.code() == BlockerCode.DOCUMENT_EXPIRING && w.documentType() == PartnerDocumentType.PUC);
        assertThat(verification.findDocumentsExpiringWithin(30)).anyMatch(d -> d.accountId().equals(partner));
    }

    @Test
    void deletingHerAccountDeletesEveryFileAndNumber() {
        approveAll();

        documents.eraseFor(partner);

        List<PartnerDocumentSummary> left = verification.findPartnerDocuments(partner);
        assertThat(left).isNotEmpty().allSatisfy(d -> {
            assertThat(d.fileOnFile()).isFalse();
            assertThat(d.documentNumber()).isNull();
        });
        assertThat(verification.auditTrail(partner)).allSatisfy(e -> assertThat(e.detail()).isNull());
    }

    // ------------------------------------------------------------------ helpers

    private List<Integer> reminders() {
        return events.stream(PartnerDocumentExpiring.class)
                .filter(e -> e.accountId().equals(partner))
                .map(PartnerDocumentExpiring::daysLeft).toList();
    }

    private void approveAll() {
        approve(upload(PartnerDocumentType.DRIVING_LICENCE, "DL-1", today.plusYears(3), null).id());
        approve(upload(PartnerDocumentType.VEHICLE_RC, "RC-1", today.plusYears(10), null).id());
        approve(upload(PartnerDocumentType.VEHICLE_INSURANCE, "POL-1", today.plusMonths(9), InsuranceUseType.COMMERCIAL).id());
        approve(upload(PartnerDocumentType.PUC, "PUC-1", today.plusMonths(6), null).id());
        assertThat(verification.partnerReadiness(partner, "BIKE").ready()).isTrue();
    }

    private PartnerDocumentSummary upload(PartnerDocumentType type, String number, LocalDate validUntil, InsuranceUseType use) {
        Result<PartnerDocumentSummary, VerificationError> result = documents.submit(partner, type, pdf(),
                new DocumentFacts(number, null, validUntil, use), PartnerDocumentSource.PARTNER_UPLOAD, partner, false);
        assertThat(result.isSuccess()).as(String.valueOf(result.isFailure() ? result.error() : "")).isTrue();
        return result.value();
    }

    private void approve(UUID documentId) {
        var result = documents.review(documentId, operator, PartnerDocumentStatus.VERIFIED, null, null);
        assertThat(result.isSuccess()).as(String.valueOf(result.isFailure() ? result.error() : "")).isTrue();
    }

    /** A PDF big enough to pass DocumentRules - what a scanned certificate is. */
    private static DocumentUpload pdf() {
        byte[] bytes = new byte[24 * 1024];
        Arrays.fill(bytes, (byte) ' ');
        byte[] head = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(head, 0, bytes, 0, head.length);
        return new DocumentUpload("document.pdf", "application/pdf", bytes);
    }
}
