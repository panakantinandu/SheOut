package com.sheout.driververification.internal;

import com.sheout.auth.AccountRegistered;
import com.sheout.auth.AccountRole;
import com.sheout.driververification.AccountVerified;
import com.sheout.driververification.PartnerDocumentExpiring;
import com.sheout.driververification.PartnerDocumentSource;
import com.sheout.driververification.PartnerDocumentStatus;
import com.sheout.driververification.PartnerDocumentSummary;
import com.sheout.driververification.PartnerDocumentType;
import com.sheout.driververification.PartnerReadiness.BlockerCode;
import com.sheout.driververification.PoliceVerificationMethod;
import com.sheout.driververification.VerificationApi;
import com.sheout.driververification.VerificationLapsed;
import com.sheout.driververification.VerificationStatus;
import com.sheout.driververification.internal.PartnerDocumentService.DocumentFacts;
import com.sheout.driververification.internal.PoliceVerificationService.PoliceDecision;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Police verification on the real database: consent first, an evidenced
 * approval with its row in police_verifications, and the re-verification
 * calendar putting her back to PENDING and offline. Needs the local
 * Postgres and Redis.
 */
@SpringBootTest(properties = {"sheout.warm-up.enabled=false", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
@RecordApplicationEvents
class PoliceReverificationFlowTest {

    @Autowired VerificationService verificationService;
    @Autowired PoliceVerificationService police;
    @Autowired PartnerDocumentService documents;
    @Autowired VerificationConsent consent;
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
        publisher.publishEvent(new AccountRegistered(partner, AccountRole.DRIVER));
        jdbc.update("update verification_records set gender_verification_status = 'VERIFIED' where account_id = ?", partner);
        jdbc.update("update driver_profiles set vehicle_type = 'BIKE' where account_id = ?", partner);
    }

    @AfterEach
    void cleanUp() {
        documents.eraseFor(partner);
        jdbc.update("delete from police_verifications where account_id = ?", partner);
        jdbc.update("delete from partner_documents where account_id = ?", partner);
        jdbc.update("delete from verification_audit_events where account_id = ?", partner);
        jdbc.update("delete from verification_records where account_id = ?", partner);
        jdbc.update("delete from driver_profiles where account_id = ?", partner);
        jdbc.update("delete from referral_codes where account_id = ?", partner);
    }

    @Test
    void consentComesFirstAndEvidenceMakesTheCheck() {
        // ---- nothing is taken before she agrees, and only to the current wording
        assertThat(verification.partnerReadiness(partner, "BIKE").firstBlocker().code()).isEqualTo(BlockerCode.CONSENT_REQUIRED);
        assertThat(documents.submit(partner, PartnerDocumentType.POLICE_CERTIFICATE, pdf(),
                new DocumentFacts("PVC-1", today.minusDays(10), null, null), PartnerDocumentSource.PARTNER_UPLOAD,
                partner, false).error()).isEqualTo(VerificationError.CONSENT_REQUIRED);
        assertThat(consent.accept(partner, "an-old-version").error()).isEqualTo(VerificationError.CONSENT_VERSION_OUTDATED);
        assertThat(consent.accept(partner, consent.currentVersion()).isSuccess()).isTrue();
        assertThat(verification.consentStatus(partner).current()).isTrue();

        // ---- she uploads her Telangana Police certificate
        PartnerDocumentSummary certificate = documents.submit(partner, PartnerDocumentType.POLICE_CERTIFICATE, pdf(),
                new DocumentFacts("TSPVC/2026/77", today.minusDays(10), null, null), PartnerDocumentSource.PARTNER_UPLOAD,
                partner, false).value();

        // ---- a bare approval is refused; an evidenced one is recorded
        assertThat(verificationService.reviewPoliceVerification(partner, operator,
                PoliceDecision.bare(VerificationStatus.VERIFIED)).error()).isEqualTo(VerificationError.POLICE_EVIDENCE_MISSING);
        var approved = verificationService.reviewPoliceVerification(partner, operator, new PoliceDecision(
                VerificationStatus.VERIFIED, PoliceVerificationMethod.TS_POLICE_PVC, "TSPVC/2026/77", "Cyberabad Police",
                today.minusDays(10), null, certificate.id(), null, null));
        assertThat(approved.isSuccess()).isTrue();
        assertThat(approved.value().policeVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(verification.policeReverifyDueOn(partner)).contains(today.minusDays(10).plusMonths(12));
        assertThat(verification.policeHistory(partner)).singleElement().satisfies(p -> {
            assertThat(p.evidenceDocumentId()).isEqualTo(certificate.id());
            assertThat(p.consentTextVersion()).isEqualTo(consent.currentVersion());
            assertThat(p.decidedBy()).isEqualTo(operator);
        });
        assertThat(documents.find(certificate.id()).orElseThrow().status())
                .as("the certificate was approved by the same reading").isEqualTo(PartnerDocumentStatus.VERIFIED);
        assertThat(events.stream(AccountVerified.class)).anyMatch(e -> e.accountId().equals(partner));
        assertThat(verification.auditTrail(partner)).extracting(e -> e.action())
                .contains("CONSENT_ACCEPTED", "DOCUMENT_UPLOADED", "POLICE_VERIFIED");
    }

    @Test
    void aPoliceCheckThatComesDueGoesBackToPendingAndTakesHerOffline() {
        jdbc.update("update verification_records set police_verification_status = 'VERIFIED', police_reverify_due_on = ?"
                + " where account_id = ?", today, partner);
        jdbc.update("update driver_profiles set online_status = 'ONLINE', verified = true where account_id = ?", partner);

        assertThat(verification.partnerReadiness(partner, "BIKE").blockers())
                .as("due today: the date decides, before the sweep runs")
                .anyMatch(b -> b.code() == BlockerCode.POLICE_REVERIFY_DUE && today.equals(b.date()));

        police.reverifyDue();

        assertThat(verification.findByAccountId(partner).orElseThrow().policeVerificationStatus())
                .isEqualTo(VerificationStatus.PENDING);
        assertThat(events.stream(VerificationLapsed.class)).anyMatch(e -> e.accountId().equals(partner)
                && e.cause() == VerificationLapsed.Cause.POLICE_REVERIFICATION_DUE);
        var profile = driverProfiles.findByAccountId(partner).orElseThrow();
        assertThat(profile.onlineStatus()).isEqualTo(OnlineStatus.OFFLINE);
        assertThat(profile.verified()).as("users' cached flag is cleared").isFalse();
        assertThat(verification.auditTrail(partner)).anyMatch(e -> e.action().equals("POLICE_REVERIFY_DUE"));
    }

    @Test
    void sheIsRemindedThirtyAndSevenDaysBefore() {
        jdbc.update("update verification_records set police_verification_status = 'VERIFIED', police_reverify_due_on = ?"
                + " where account_id = ?", today.plusDays(20), partner);
        police.reverifyDue();
        police.reverifyDue();
        jdbc.update("update verification_records set police_reverify_due_on = ? where account_id = ?", today.plusDays(6), partner);
        police.reverifyDue();

        assertThat(events.stream(PartnerDocumentExpiring.class)
                .filter(e -> e.accountId().equals(partner) && e.type() == PartnerDocumentType.POLICE_CERTIFICATE)
                .map(PartnerDocumentExpiring::daysLeft)).containsExactly(20, 6);
        assertThat(verification.findPoliceReverificationDueWithin(30)).anyMatch(s -> s.accountId().equals(partner));
    }

    private static DocumentUpload pdf() {
        byte[] bytes = new byte[24 * 1024];
        Arrays.fill(bytes, (byte) ' ');
        byte[] head = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(head, 0, bytes, 0, head.length);
        return new DocumentUpload("certificate.pdf", "application/pdf", bytes);
    }
}
