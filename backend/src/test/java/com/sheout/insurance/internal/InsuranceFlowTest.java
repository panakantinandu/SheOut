package com.sheout.insurance.internal;

import com.sheout.auth.AccountBlocked;
import com.sheout.auth.AccountRole;
import com.sheout.booking.BookingCategory;
import com.sheout.booking.BookingCompleted;
import com.sheout.booking.BookingStarted;
import com.sheout.booking.GeoAddress;
import com.sheout.driververification.AccountVerified;
import com.sheout.insurance.EnrolmentStatus;
import com.sheout.insurance.InsuranceApi;
import com.sheout.insurance.PolicyKind;
import com.sheout.insurance.PremiumUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Insurance on the real database: a policy entered and switched on, a trip's
 * cover opened and closed by booking's own events, the day's bordereau, the
 * month's premium, and a partner through a group cover from verification to
 * being blocked. Needs the local Postgres and Redis.
 */
@SpringBootTest(properties = {"sheout.warm-up.enabled=false", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class InsuranceFlowTest {

    @Autowired InsuranceService insurance;
    @Autowired InsuranceApi api;
    @Autowired ApplicationEventPublisher publisher;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;

    private final UUID booking = UUID.randomUUID();
    private final UUID rider = UUID.randomUUID();
    private final UUID partner = UUID.randomUUID();
    private final UUID operator = UUID.randomUUID();
    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
    private final List<UUID> createdPolicies = new ArrayList<>();
    private List<UUID> previouslyActive = List.of();

    @BeforeEach
    void setUp() {
        // One active policy per kind: whatever this database had is set aside and put back after.
        previouslyActive = jdbc.queryForList("select id from insurance_policies where active and kind in ('PASSENGER_TRIP','PARTNER_HEALTH')", UUID.class);
        jdbc.update("update insurance_policies set active = false where kind in ('PASSENGER_TRIP','PARTNER_HEALTH')");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from trip_coverages where booking_id = ?", booking);
        jdbc.update("delete from partner_insurance_enrolments where account_id = ?", partner);
        for (UUID id : createdPolicies) {
            jdbc.update("delete from insurance_policies where id = ?", id);
        }
        for (UUID id : previouslyActive) {
            jdbc.update("update insurance_policies set active = true where id = ?", id);
        }
    }

    private UUID policy(PolicyKind kind, PremiumUnit unit, String premium) {
        var created = insurance.createPolicy(new InsuranceService.PolicyInput(kind, "Example General Insurance",
                "TEST-" + kind + "-" + booking.toString().substring(0, 8), new BigDecimal("500000"), new BigDecimal(premium),
                unit, today.minusDays(1), null, "1800-000-000", null, null, "Accidental death and disability",
                "Call the claims line within 48 hours"), operator);
        UUID id = created.value().getId();
        createdPolicies.add(id);
        assertThat(insurance.setActive(id, true).isSuccess()).isTrue();
        return id;
    }

    @Test
    void aTripIsCoveredFromStartToEndAndDeclaredInTheDaysBordereau() {
        assertThat(api.passengerCoverActive()).isFalse();
        insurance.onBookingStarted(new BookingStarted(booking, rider, partner, true, BookingCategory.BIKE,
                new GeoAddress("Road No. 3, Banjara Hills, Hyderabad", 17.41, 78.44), new GeoAddress("Gachibowli, Hyderabad", 17.44, 78.35)));
        assertThat(api.coverForTrip(booking)).as("no policy in force: not covered, nothing to say").isEmpty();

        policy(PolicyKind.PASSENGER_TRIP, PremiumUnit.PER_TRIP, "1.75");
        UUID secondTrip = booking;
        jdbc.update("delete from trip_coverages where booking_id = ?", secondTrip);
        insurance.onBookingStarted(new BookingStarted(secondTrip, rider, partner, true, BookingCategory.BIKE,
                new GeoAddress("Road No. 3, Banjara Hills, Hyderabad", 17.41, 78.44), new GeoAddress("Gachibowli, Hyderabad", 17.44, 78.35)));

        assertThat(api.passengerCoverActive()).isTrue();
        var cover = api.coverForTrip(booking).orElseThrow();
        assertThat(cover.sumInsured()).isEqualByComparingTo("500000");
        assertThat(cover.coverageEndedAt()).isNull();
        assertThat(jdbc.queryForObject("select premium_amount from trip_coverages where booking_id = ?", BigDecimal.class, booking))
                .isEqualByComparingTo("1.75");

        insurance.onBookingCompleted(new BookingCompleted(booking, rider, partner, new BigDecimal("120.00")));
        assertThat(api.coverForTrip(booking).orElseThrow().coverageEndedAt()).isNotNull();

        var file = insurance.bordereau(today);
        assertThat(file.isSuccess()).isTrue();
        String csv = new String(file.value().file(), StandardCharsets.UTF_8);
        assertThat(csv).contains(booking.toString()).contains("\"Banjara Hills, Hyderabad\"").doesNotContain("Road No. 3");
        assertThat(jdbc.queryForObject("select reported_status from trip_coverages where booking_id = ?", String.class, booking))
                .isEqualTo("REPORTED");
        assertThat(insurance.premiumReport(YearMonth.from(today)))
                .anySatisfy(r -> assertThat(r.premium()).isGreaterThanOrEqualTo(new BigDecimal("1.75")));
        assertThat(insurance.status().passengerCoverActive()).isTrue();
    }

    @Test
    void aPartnerIsShownACoverOnlyOnceTheInsurerHasEnrolledHer() {
        policy(PolicyKind.PARTNER_HEALTH, PremiumUnit.PER_MEMBER_PER_YEAR, "1200");

        insurance.onAccountVerified(new AccountVerified(partner, AccountRole.DRIVER));
        List<PartnerEnrolmentEntity> mine = insurance.allEnrolments().stream()
                .filter(e -> e.getAccountId().equals(partner)).toList();
        assertThat(mine).singleElement().satisfies(e -> assertThat(e.getStatus()).isEqualTo(EnrolmentStatus.PENDING_ENROLMENT));
        assertThat(api.enrolledCoversFor(partner)).as("pending is not cover").isEmpty();

        assertThat(insurance.markEnrolled(mine.get(0).getId(), " ").error())
                .isEqualTo(InsuranceService.EnrolmentError.MEMBER_ID_REQUIRED);
        insurance.markEnrolled(mine.get(0).getId(), "MEM-001");
        assertThat(api.enrolledCoversFor(partner)).singleElement()
                .satisfies(c -> assertThat(c.memberId()).isEqualTo("MEM-001"));

        insurance.onAccountBlocked(new AccountBlocked(partner, AccountRole.DRIVER));
        assertThat(api.enrolledCoversFor(partner)).isEmpty();
        var movements = insurance.movements(YearMonth.from(today));
        assertThat(movements.joiners()).anyMatch(e -> e.getAccountId().equals(partner));
        assertThat(movements.leavers()).anyMatch(e -> e.getAccountId().equals(partner));
    }

    private void publishInTransaction(Object event) {
        transactions.executeWithoutResult(status -> publisher.publishEvent(event));
    }
}
