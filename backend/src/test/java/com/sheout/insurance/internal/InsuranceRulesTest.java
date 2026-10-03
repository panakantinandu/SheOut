package com.sheout.insurance.internal;

import com.sheout.booking.BookingCategory;
import com.sheout.booking.BookingCompleted;
import com.sheout.booking.BookingStarted;
import com.sheout.booking.GeoAddress;
import com.sheout.insurance.PolicyKind;
import com.sheout.insurance.PremiumUnit;
import com.sheout.insurance.internal.reporting.InsurerReporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A trip is covered - and the apps may say so - only when a policy of the
 * right kind is in force; the premium is a platform cost on the coverage row
 * and nowhere else.
 */
class InsuranceRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 3);

    private final InsurancePolicyRepository policies = mock(InsurancePolicyRepository.class);
    private final TripCoverageRepository coverages = mock(TripCoverageRepository.class);
    private final PartnerEnrolmentRepository enrolments = mock(PartnerEnrolmentRepository.class);
    private final UUID booking = UUID.randomUUID();
    private final UUID rider = UUID.randomUUID();
    private final UUID partner = UUID.randomUUID();

    private InsuranceService service(boolean badgeRequiresReported) {
        return new InsuranceService(policies, coverages, enrolments, mock(InsurerReporter.class), badgeRequiresReported,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    static InsurancePolicyEntity policy(PolicyKind kind, boolean active, LocalDate from, LocalDate to) {
        InsurancePolicyEntity p = new InsurancePolicyEntity(UUID.randomUUID());
        p.update(kind, "Example General Insurance", "MP-" + kind, new BigDecimal("500000.00"),
                kind.partnerCover() ? new BigDecimal("1200.00") : new BigDecimal("1.50"),
                kind.partnerCover() ? PremiumUnit.PER_MEMBER_PER_YEAR : PremiumUnit.PER_TRIP, from, to,
                "1800-000-000", null, null, "Accidental death and disability up to the sum insured", "Call the claims line");
        p.setActive(active);
        return p;
    }

    private BookingStarted started(BookingCategory category) {
        return new BookingStarted(booking, rider, partner, true, category,
                new GeoAddress("Flat 302, Road No. 3, Banjara Hills, Hyderabad", 17.41, 78.44),
                new GeoAddress("Gachibowli, Hyderabad, 500032", 17.44, 78.35));
    }

    @BeforeEach
    void noCoverageYet() {
        when(coverages.findByBookingId(booking)).thenReturn(Optional.empty());
    }

    @Test
    void noActivePolicyMeansNoCoverAndNothingToSay() {
        when(policies.findByKindAndActiveTrue(PolicyKind.PASSENGER_TRIP)).thenReturn(List.of());

        InsuranceService s = service(false);
        s.onBookingStarted(started(BookingCategory.BIKE));

        verify(coverages, never()).save(any());
        assertThat(s.passengerCoverActive()).isFalse();
        assertThat(s.coverForTrip(booking)).isEmpty();
        assertThat(s.currentPassengerCover()).isEmpty();
    }

    @Test
    void aSwitchedOffOrNotYetEffectivePolicyIsNotInForce() {
        when(policies.findByKindAndActiveTrue(PolicyKind.PASSENGER_TRIP)).thenReturn(List.of(
                policy(PolicyKind.PASSENGER_TRIP, true, TODAY.plusDays(1), null),
                policy(PolicyKind.PASSENGER_TRIP, true, TODAY.minusYears(1), TODAY.minusDays(1))));

        assertThat(service(false).passengerCoverActive()).isFalse();
    }

    @Test
    void aRideStartedUnderAnActivePolicyIsCoveredWithItsPremiumAndCoarseAreas() {
        InsurancePolicyEntity p = policy(PolicyKind.PASSENGER_TRIP, true, TODAY.minusMonths(1), null);
        when(policies.findByKindAndActiveTrue(PolicyKind.PASSENGER_TRIP)).thenReturn(List.of(p));

        service(false).onBookingStarted(started(BookingCategory.BIKE));

        ArgumentCaptor<TripCoverageEntity> row = ArgumentCaptor.forClass(TripCoverageEntity.class);
        verify(coverages).save(row.capture());
        assertThat(row.getValue().getPremiumAmount()).isEqualByComparingTo("1.50");
        assertThat(row.getValue().getRiderAccountId()).isEqualTo(rider);
        assertThat(row.getValue().getPartnerAccountId()).isEqualTo(partner);
        assertThat(row.getValue().getPickupArea()).as("never her house number").isEqualTo("Banjara Hills, Hyderabad");
        assertThat(row.getValue().getDropArea()).isEqualTo("Gachibowli, Hyderabad");
        assertThat(row.getValue().getReportedStatus()).isEqualTo(TripCoverageEntity.ReportedStatus.PENDING);
    }

    @Test
    void aParcelUsesGoodsInTransitCoverAndOnlyThat() {
        when(policies.findByKindAndActiveTrue(PolicyKind.GOODS_IN_TRANSIT)).thenReturn(List.of());

        service(false).onBookingStarted(started(BookingCategory.PARCEL));

        verify(policies).findByKindAndActiveTrue(PolicyKind.GOODS_IN_TRANSIT);
        verify(policies, never()).findByKindAndActiveTrue(PolicyKind.PASSENGER_TRIP);
        verify(coverages, never()).save(any());
    }

    @Test
    void theBadgeNeedsACoverageRowThatHasNotFailedReporting() {
        InsurancePolicyEntity p = policy(PolicyKind.PASSENGER_TRIP, true, TODAY.minusMonths(1), null);
        TripCoverageEntity row = new TripCoverageEntity(booking, null, rider, partner, "BIKE", null, null, NOW, BigDecimal.ONE);
        when(coverages.findByBookingId(booking)).thenReturn(Optional.of(row));
        when(policies.findById(any())).thenReturn(Optional.of(p));

        assertThat(service(false).coverForTrip(booking)).isPresent()
                .get().satisfies(c -> {
                    assertThat(c.sumInsured()).isEqualByComparingTo("500000");
                    assertThat(c.policyNumber()).isEqualTo("MP-PASSENGER_TRIP");
                });
        assertThat(service(true).coverForTrip(booking)).as("not yet reported, with the strict setting").isEmpty();

        row.markReportFailed();
        assertThat(service(false).coverForTrip(booking)).as("reporting failed: never called insured").isEmpty();
    }

    @Test
    void endingATripClosesItsCoverAndTouchesNothingElse() {
        TripCoverageEntity row = new TripCoverageEntity(booking, UUID.randomUUID(), rider, partner, "BIKE", null, null,
                NOW.minusSeconds(900), new BigDecimal("1.50"));
        when(coverages.findByBookingId(booking)).thenReturn(Optional.of(row));

        service(false).onBookingCompleted(new BookingCompleted(booking, rider, partner, new BigDecimal("120.00")));

        assertThat(row.getCoverageEndedAt()).isEqualTo(NOW);
        assertThat(row.getPremiumAmount()).as("the fare does not move the premium, nor the other way round")
                .isEqualByComparingTo("1.50");
    }

    @Test
    void aPolicyPricedPerMemberAddsNothingPerTrip() {
        InsurancePolicyEntity perMember = policy(PolicyKind.PARTNER_HEALTH, true, TODAY, null);
        assertThat(InsuranceService.premiumPerTrip(perMember)).isEqualByComparingTo("0");
    }

    @Test
    void policiesAreChargedTheWayTheirKindIs() {
        var s = service(false);
        var wrong = new InsuranceService.PolicyInput(PolicyKind.PASSENGER_TRIP, "X", "P1", new BigDecimal("500000"),
                new BigDecimal("1200"), PremiumUnit.PER_MEMBER_PER_YEAR, TODAY, null, null, null, null, null, null);
        assertThat(s.createPolicy(wrong, UUID.randomUUID()).error()).isEqualTo(InsuranceService.PolicyError.PREMIUM_UNIT_MISMATCH);
        var noSum = new InsuranceService.PolicyInput(PolicyKind.PASSENGER_TRIP, "X", "P1", BigDecimal.ZERO,
                new BigDecimal("1"), PremiumUnit.PER_TRIP, TODAY, null, null, null, null, null, null);
        assertThat(s.createPolicy(noSum, UUID.randomUUID()).error()).isEqualTo(InsuranceService.PolicyError.INVALID_POLICY);
    }

    @Test
    void oneActivePolicyPerKind() {
        InsurancePolicyEntity live = policy(PolicyKind.PASSENGER_TRIP, true, TODAY, null);
        InsurancePolicyEntity next = policy(PolicyKind.PASSENGER_TRIP, false, TODAY, null);
        UUID nextId = UUID.randomUUID();
        when(policies.findById(nextId)).thenReturn(Optional.of(next));
        when(policies.findByKindAndActiveTrue(PolicyKind.PASSENGER_TRIP)).thenReturn(List.of(live));

        assertThat(service(false).setActive(nextId, true).error()).isEqualTo(InsuranceService.PolicyError.KIND_ALREADY_ACTIVE);
    }

    @Test
    void areaNamesDropAnythingWithANumber() {
        assertThat(AreaName.coarse("Plot 12, Road No. 3, Jubilee Hills, Hyderabad, Telangana 500033, India"))
                .isEqualTo("Jubilee Hills, Hyderabad");
        assertThat(AreaName.coarse("Kukatpally")).isEqualTo("Kukatpally");
        assertThat(AreaName.coarse("12-3-45")).isNull();
        assertThat(AreaName.coarse(null)).isNull();
    }
}
