package com.sheout.driververification.internal;

import com.sheout.auth.AccountRegistered;
import com.sheout.auth.AccountRole;
import com.sheout.driververification.CheckFinding;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The free automatic checks on the real database: age, expiry, the name she
 * typed, duplicates, numbers turned down before, and what her phone said
 * about a photo. Every one of them adds a finding for a person; only
 * certain facts (an expired licence, under 18, a number turned down for
 * fraud) fail a check, and none of them rejects anybody.
 */
@SpringBootTest(properties = {"sheout.warm-up.enabled=false", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class AutomaticChecksFlowTest {

    @Autowired VerificationApi api;
    @Autowired PartnerDocumentService documents;
    @Autowired ApplicationEventPublisher publisher;
    @Autowired JdbcTemplate jdbc;
    @Autowired VerificationConsent consent;

    private final List<UUID> accounts = new ArrayList<>();
    private final UUID operator = UUID.randomUUID();
    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
    /** A plate no real test row uses, so a duplicate can only come from this test. */
    private final String plate = "TS09ZQ" + (1000 + (int) (Math.random() * 8999));

    @AfterEach
    void cleanUp() {
        for (UUID id : accounts) {
            documents.eraseFor(id);
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
    void anExpiredLicenceIsFiledAndFailsItsCheckWithNoOverride() {
        UUID partner = partner("Lakshmi Devi", today.minusYears(30));
        PartnerDocumentSummary licence = licence(partner, "TS0920150001234", today.minusDays(3), "Lakshmi Devi", false, null);
        assertThat(licence.status()).as("filed for a person, not refused").isEqualTo(PartnerDocumentStatus.UNDER_REVIEW);

        VerificationCheck check = check(partner, CheckType.LICENCE);
        assertThat(check.status()).isEqualTo(CheckStatus.FAILED_AUTO);
        assertThat(check.findings()).extracting(VerificationCheck.Finding::code).contains(CheckFinding.DOCUMENT_EXPIRED);
        assertThat(check.overridable()).as("the fix is a renewed licence").isFalse();
        assertThat(documents.review(licence.id(), operator, PartnerDocumentStatus.VERIFIED, null, null, null, null,
                Set.of(CheckFinding.DOCUMENT_EXPIRED)).error()).as("an expired document is never approved")
                .isEqualTo(VerificationError.DOCUMENT_ALREADY_EXPIRED);

        // ---- only a person sends it back
        assertThat(documents.review(licence.id(), operator, PartnerDocumentStatus.REJECTED,
                "Upload your renewed licence.", null).isSuccess()).isTrue();
        assertThat(check(partner, CheckType.LICENCE).status()).isEqualTo(CheckStatus.PENDING);
    }

    @Test
    void theNameSheTypesIsComparedKindlyAndADeclarationIsNeverCompared() {
        UUID partner = partner("Lakshmi Devi", today.minusYears(30));

        licence(partner, "TS0920150001234", today.plusYears(5), "Laxmi Devi", false, null);
        assertThat(codes(partner, CheckType.LICENCE)).as("a transliteration is not a mismatch")
                .doesNotContain(CheckFinding.NAME_MISMATCH);

        licence(partner, "TS0920150001234", today.plusYears(5), "Priya Sharma", false, null);
        VerificationCheck mismatch = check(partner, CheckType.LICENCE);
        assertThat(mismatch.status()).as("a name only ever asks a person").isEqualTo(CheckStatus.NEEDS_REVIEW);
        assertThat(mismatch.findings()).extracting(VerificationCheck.Finding::code).contains(CheckFinding.NAME_MISMATCH);

        licence(partner, "TS0920150001234", today.plusYears(5), null, true, null);
        assertThat(codes(partner, CheckType.LICENCE)).contains(CheckFinding.NAME_DECLARED).doesNotContain(CheckFinding.NAME_MISMATCH);
        assertThat(check(partner, CheckType.LICENCE).status()).isEqualTo(CheckStatus.NEEDS_REVIEW);

        Result<PartnerDocumentSummary, VerificationError> noName = documents.submit(partner, PartnerDocumentType.DRIVING_LICENCE,
                VerificationChecksFlowTest.pdf(), new DocumentFacts("TS0920150001234", null, today.plusYears(5), null),
                PartnerDocumentSource.PARTNER_UPLOAD, partner, false);
        assertThat(noName.error()).isEqualTo(VerificationError.NAME_ON_DOCUMENT_REQUIRED);
    }

    @Test
    void theSameVehicleOnTwoAccountsAsksAPersonOnBoth() {
        UUID first = partner("Anitha Reddy", today.minusYears(40));
        UUID second = partner("Kavya Reddy", today.minusYears(19));
        rc(first);
        rc(second);
        for (UUID id : List.of(first, second)) {
            VerificationCheck vehicle = check(id, CheckType.VEHICLE);
            assertThat(vehicle.status()).as("a shared family scooter is real: never a failure").isEqualTo(CheckStatus.NEEDS_REVIEW);
            assertThat(vehicle.findings()).extracting(VerificationCheck.Finding::code).contains(CheckFinding.DUPLICATE_NUMBER);
        }
    }

    @Test
    void aNumberTurnedDownForFraudFailsAgainButTwoPeopleMayOverride() {
        UUID earlier = partner("Ramya K", today.minusYears(25));
        UUID partner = partner("Divya S", today.minusYears(25));
        jdbc.update("insert into flagged_document_numbers (id, document_type, document_number, reason_code, account_id,"
                        + " flagged_by, created_at, updated_at) values (?, 'DRIVING_LICENCE', 'TS0920150009999', 'DOCUMENT_ALTERED', ?, ?, now(), now())",
                UUID.randomUUID(), earlier, operator);
        licence(partner, "TS0920150009999", today.plusYears(5), "Divya S", false, null);
        VerificationCheck check = check(partner, CheckType.LICENCE);
        assertThat(check.status()).isEqualTo(CheckStatus.FAILED_AUTO);
        assertThat(check.findings()).extracting(VerificationCheck.Finding::code).contains(CheckFinding.PREVIOUSLY_REJECTED);
        assertThat(check.overridable()).isTrue();
    }

    @Test
    void underEighteenFailsHerIdentityCheckAndAPhotoSheSentAnywayAsksAPerson() {
        UUID partner = partner("Meena", today.minusYears(17));
        jdbc.update("update verification_records set aadhaar_document_key = 'test/id', document_submitted_at = now(),"
                + " gender_verification_status = 'UNDER_REVIEW', id_photo_warnings = 'BLURRY' where account_id = ?", partner);
        VerificationCheck identity = check(partner, CheckType.IDENTITY);
        assertThat(identity.status()).isEqualTo(CheckStatus.FAILED_AUTO);
        assertThat(identity.findings()).extracting(VerificationCheck.Finding::code)
                .contains(CheckFinding.UNDER_18, CheckFinding.PHOTO_QUALITY);
        assertThat(identity.overridable()).isFalse();

        licence(partner, "TS0920150001234", today.plusYears(5), "Meena", false, "glare, nonsense");
        VerificationCheck licence = check(partner, CheckType.LICENCE);
        assertThat(licence.findings()).extracting(VerificationCheck.Finding::code).contains(CheckFinding.PHOTO_QUALITY);
        assertThat(licence.findings().stream().filter(f -> f.code() == CheckFinding.PHOTO_QUALITY).findFirst().orElseThrow().detail())
                .contains("glare").doesNotContain("nonsense");
    }

    // ---------------------------------------------------------------- helpers

    private UUID partner(String name, LocalDate born) {
        UUID id = UUID.randomUUID();
        accounts.add(id);
        publisher.publishEvent(new AccountRegistered(id, AccountRole.DRIVER));
        jdbc.update("update verification_records set consent_version = ?, consent_accepted_at = now() where account_id = ?",
                consent.currentVersion(), id);
        jdbc.update("update driver_profiles set vehicle_type = 'BIKE', name = ?, date_of_birth = ? where account_id = ?",
                name, born, id);
        return id;
    }

    private PartnerDocumentSummary licence(UUID partner, String number, LocalDate validUntil, String name, boolean declared,
                                           String warnings) {
        Result<PartnerDocumentSummary, VerificationError> result = documents.submit(partner, PartnerDocumentType.DRIVING_LICENCE,
                VerificationChecksFlowTest.pdf(), new DocumentFacts(number, null, validUntil, null, null, null, name, declared, warnings),
                PartnerDocumentSource.PARTNER_UPLOAD, partner, false);
        assertThat(result.isSuccess()).as(String.valueOf(result.isFailure() ? result.error() : "")).isTrue();
        return result.value();
    }

    private void rc(UUID partner) {
        Result<PartnerDocumentSummary, VerificationError> result = documents.submit(partner, PartnerDocumentType.VEHICLE_RC,
                VerificationChecksFlowTest.pdf(), new DocumentFacts(plate, null, today.plusYears(8), InsuranceUseType.COMMERCIAL,
                        VehicleOwner.SELF, null), PartnerDocumentSource.PARTNER_UPLOAD, partner, false);
        assertThat(result.isSuccess()).as(String.valueOf(result.isFailure() ? result.error() : "")).isTrue();
    }

    private VerificationCheck check(UUID account, CheckType type) {
        return api.checksFor(account).find(type).orElseThrow();
    }

    private List<CheckFinding> codes(UUID account, CheckType type) {
        return check(account, type).findings().stream().map(VerificationCheck.Finding::code).toList();
    }
}
