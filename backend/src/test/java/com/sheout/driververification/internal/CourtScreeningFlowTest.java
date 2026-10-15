package com.sheout.driververification.internal;

import com.sheout.auth.AccountRole;
import com.sheout.driververification.CheckStatus;
import com.sheout.driververification.CheckType;
import com.sheout.driververification.CourtRejectionCategory;
import com.sheout.driververification.CourtScreeningDecision;
import com.sheout.driververification.CourtScreeningStatus;
import com.sheout.driververification.CourtSearchOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The court-record screening rules, end to end against the database: consent
 * first, a note for anything but "No match", a second and different reviewer
 * for a match, nobody deciding their own flagged case, the partner heard
 * before a rejection, every step audited, approvals in the weekly sample, and
 * retention that counts in a dry run and anonymises for real.
 */
@SpringBootTest(properties = {
        "sheout.warm-up.enabled=false",
        "sheout.retention.cron=-",
        "sheout.verification.court-screen.enabled=true",
        "sheout.verification.court-screen.consent-version=test-v1",
        "sheout.verification.court-screen.response-days=7",
        "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
@DirtiesContext
class CourtScreeningFlowTest {

    private static final Instant T0 = Instant.parse("2026-10-14T05:00:00Z");

    @Autowired CourtScreeningService service;
    @Autowired VerificationRecordRepository records;
    @Autowired VerificationCheckRepository checks;
    @Autowired JdbcTemplate jdbc;

    private final UUID partner = UUID.randomUUID();
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final UUID third = UUID.randomUUID();

    @BeforeEach
    void partner() {
        records.save(new VerificationRecordEntity(partner, AccountRole.DRIVER));
        at(T0);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from court_screening_searches where screening_id in (select id from court_screenings where account_id = ?)", partner);
        jdbc.update("delete from court_screenings where account_id = ?", partner);
        jdbc.update("delete from verification_checks where account_id = ?", partner);
        jdbc.update("delete from verification_audit_events where account_id = ?", partner);
        jdbc.update("delete from verification_records where account_id = ?", partner);
    }

    private void at(Instant instant) {
        service.useClock(Clock.fixed(instant, ZoneOffset.UTC));
    }

    private CourtScreeningService.SearchEntry search(CourtSearchOutcome outcome, String note) {
        return new CourtScreeningService.SearchEntry("Hyderabad", "City Civil Court", "Lakshmi Devi", LocalDate.of(2026, 10, 14),
                outcome, note, outcome.isMatch() ? "Sec 420 IPC" : null, outcome.isMatch() ? "Disposed" : null,
                outcome.isMatch() ? 2019 : null);
    }

    private List<String> auditActions() {
        return jdbc.queryForList("select action from verification_audit_events where account_id = ? order by at", String.class, partner);
    }

    @Test
    void nothingIsSearchedBeforeSheAgreesToTheCurrentWording() {
        assertThat(service.request(partner, first, List.of("Warangal")).isSuccess()).isTrue();

        assertThat(service.logSearch(partner, first, search(CourtSearchOutcome.NO_MATCH, null)).error())
                .isEqualTo(CourtScreeningError.CONSENT_REQUIRED);
        assertThat(service.decide(partner, first, new CourtScreeningService.Decision(CourtScreeningDecision.APPROVE, null, null,
                null, null, null, null)).error()).isEqualTo(CourtScreeningError.CONSENT_REQUIRED);
        assertThat(service.consent(partner, "an-older-version").error()).isEqualTo(CourtScreeningError.CONSENT_VERSION_OUTDATED);

        var agreed = service.consent(partner, "test-v1");
        assertThat(agreed.value().getStatus()).isEqualTo(CourtScreeningStatus.SEARCHING);
        assertThat(agreed.value().getDistricts()).contains("Hyderabad", "Rangareddy", "Medchal-Malkajgiri", "Warangal");
        assertThat(service.request(partner, first, null).error()).isEqualTo(CourtScreeningError.ALREADY_OPEN);
    }

    @Test
    void aCleanScreeningIsApprovedAndJoinsTheWeeklySample() {
        service.request(partner, first, null);
        service.consent(partner, "test-v1");
        assertThat(service.logSearch(partner, first, search(CourtSearchOutcome.COULD_NOT_SEARCH, null)).error())
                .as("anything but No match needs a note").isEqualTo(CourtScreeningError.NOTE_REQUIRED);
        service.logSearch(partner, first, search(CourtSearchOutcome.COULD_NOT_SEARCH, "eCourts was down for Rangareddy"));

        var tooEarly = service.decide(partner, first, new CourtScreeningService.Decision(CourtScreeningDecision.APPROVE, null, null,
                null, null, null, null));
        assertThat(tooEarly.error()).as("no completed search yet").isEqualTo(CourtScreeningError.NO_COMPLETED_SEARCH);

        service.logSearch(partner, first, search(CourtSearchOutcome.NO_MATCH, null));
        var approved = service.decide(partner, first, new CourtScreeningService.Decision(CourtScreeningDecision.APPROVE, null, null,
                null, null, null, null));

        assertThat(approved.value().screening().getStatus()).isEqualTo(CourtScreeningStatus.APPROVED);
        var row = checks.findByAccountIdAndType(partner, CheckType.COURT_RECORD_SCREEN).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(CheckStatus.APPROVED_BY_PERSON);
        assertThat(row.getDecidedBy()).isEqualTo(first);
        assertThat(row.getDecidedByRole()).isEqualTo(VerificationAudit.ACTOR_OPERATOR);
        assertThat(auditActions()).contains("COURT_SCREEN_REQUESTED", "COURT_SCREEN_CONSENTED", "COURT_SCREEN_SEARCH_LOGGED",
                "COURT_SCREEN_DECIDED");
    }

    @Test
    void aMatchNeedsASecondReviewerAndAHearingAndNeverItsOwnFlaggerToDecide() {
        service.request(partner, first, null);
        service.consent(partner, "test-v1");
        service.logSearch(partner, first, search(CourtSearchOutcome.POSSIBLE_MATCH, "Same name, father's name matches, age close"));
        assertThat(service.open(partner, first).value().screening().getStatus()).isEqualTo(CourtScreeningStatus.MATCH_TO_CONFIRM);

        Decisions decisions = new Decisions();
        assertThat(service.decide(partner, third, decisions.reject()).error()).isEqualTo(CourtScreeningError.SECOND_SIGNOFF_REQUIRED);
        assertThat(service.secondSignOff(partner, first, true, "I agree").error()).isEqualTo(CourtScreeningError.SAME_REVIEWER);
        assertThat(service.secondSignOff(partner, second, true, "Address and age fit").value().screening().getStatus())
                .isEqualTo(CourtScreeningStatus.MATCH_CONFIRMED);

        assertThat(service.decide(partner, third, decisions.reject()).error())
                .as("not told yet").isEqualTo(CourtScreeningError.PARTNER_NOT_HEARD);
        service.notifyPartner(partner, second);
        assertThat(service.decide(partner, third, decisions.reject()).error())
                .as("told, but her week is not over").isEqualTo(CourtScreeningError.PARTNER_NOT_HEARD);

        at(T0.plus(Duration.ofDays(2)));
        assertThat(service.respond(partner, "That is my aunt, we share a name. I can send a police certificate.").value().getStatus())
                .isEqualTo(CourtScreeningStatus.RESPONSE_RECEIVED);
        assertThat(service.decide(partner, first, decisions.reject()).error()).isEqualTo(CourtScreeningError.OWN_FLAGGED_CASE);

        // A certificate is asked for: her window opens again.
        assertThat(service.decide(partner, third, new CourtScreeningService.Decision(CourtScreeningDecision.ASK_FOR_PCC, null,
                null, null, null, null, null)).value().screening().getStatus()).isEqualTo(CourtScreeningStatus.AWAITING_PCC);
        assertThat(service.decide(partner, third, new CourtScreeningService.Decision(CourtScreeningDecision.APPROVE, null, null,
                null, null, null, null)).error()).isEqualTo(CourtScreeningError.PCC_DETAILS_REQUIRED);

        var approved = service.decide(partner, third, new CourtScreeningService.Decision(CourtScreeningDecision.APPROVE_WITH_NOTE,
                null, "PCC from Hyderabad City Police clears her", LocalDate.of(2026, 10, 10), "Hyderabad City Police",
                LocalDate.of(2027, 10, 10), null));
        assertThat(approved.value().screening().getStatus()).isEqualTo(CourtScreeningStatus.APPROVED_WITH_NOTE);
        assertThat(approved.value().screening().getPccAuthority()).isEqualTo("Hyderabad City Police");
        assertThat(auditActions()).contains("COURT_SCREEN_SECOND_SIGNOFF", "COURT_SCREEN_PARTNER_NOTIFIED",
                "COURT_SCREEN_PARTNER_RESPONDED", "COURT_SCREEN_VIEWED");
    }

    @Test
    void aRejectionAfterHerWindowKeepsACategoryAndIsAnonymisedLater() {
        service.request(partner, first, null);
        service.consent(partner, "test-v1");
        service.logSearch(partner, first, search(CourtSearchOutcome.CONFIRMED_MATCH, "Name, father's name, age and address all match"));
        service.secondSignOff(partner, second, true, "Same person");
        service.notifyPartner(partner, second);
        at(T0.plus(Duration.ofDays(8)));
        assertThat(service.decide(partner, third, new CourtScreeningService.Decision(CourtScreeningDecision.REJECT, null, null,
                null, null, null, null)).error()).isEqualTo(CourtScreeningError.REASON_REQUIRED);

        var rejected = service.decide(partner, third, new Decisions().reject());
        assertThat(rejected.value().screening().getStatus()).isEqualTo(CourtScreeningStatus.REJECTED);
        assertThat(rejected.value().screening().getReasonCategory()).isEqualTo(CourtRejectionCategory.RECORD_CONFIRMED);
        assertThat(checks.findByAccountIdAndType(partner, CheckType.COURT_RECORD_SCREEN).orElseThrow().getStatus())
                .isEqualTo(CheckStatus.PENDING);

        Instant cutoff = T0.plus(Duration.ofDays(40));
        assertThat(service.anonymiseRejectedBefore(cutoff, true, 500)).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select note from court_screening_searches where screening_id = ?", String.class,
                rejected.value().screening().getId())).as("a dry run changes nothing").isNotNull();

        service.anonymiseRejectedBefore(cutoff, false, 500);
        assertThat(jdbc.queryForObject("select note from court_screening_searches where screening_id = ?", String.class,
                rejected.value().screening().getId())).isNull();
        assertThat(jdbc.queryForObject("select reason_category from court_screenings where account_id = ?", String.class, partner))
                .as("the reason category stays").isEqualTo("RECORD_CONFIRMED");
        assertThat(jdbc.queryForObject("select anonymised_at is not null from court_screenings where account_id = ?", Boolean.class,
                partner)).isTrue();
    }

    @Test
    void sheCanWithdrawAndTheSearchStops() {
        service.request(partner, first, null);
        service.consent(partner, "test-v1");

        assertThat(service.withdraw(partner).value().getStatus()).isEqualTo(CourtScreeningStatus.WITHDRAWN);
        assertThat(service.logSearch(partner, first, search(CourtSearchOutcome.NO_MATCH, null)).error())
                .isEqualTo(CourtScreeningError.NOT_FOUND);
        assertThat(auditActions()).contains("COURT_SCREEN_WITHDRAWN");
    }

    private static final class Decisions {
        CourtScreeningService.Decision reject() {
            return new CourtScreeningService.Decision(CourtScreeningDecision.REJECT, CourtRejectionCategory.RECORD_CONFIRMED,
                    null, null, null, null, null);
        }
    }
}
