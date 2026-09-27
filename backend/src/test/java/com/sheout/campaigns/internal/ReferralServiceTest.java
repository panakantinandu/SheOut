package com.sheout.campaigns.internal;

import com.sheout.auth.AccountRole;
import com.sheout.auth.AccountSummary;
import com.sheout.auth.AuthApi;
import com.sheout.booking.BookingApi;
import com.sheout.booking.BookingParticipants;
import com.sheout.booking.BookingStatus;
import com.sheout.booking.BookingSummary;
import com.sheout.campaigns.IncentiveType;
import com.sheout.campaigns.PromotionType;
import com.sheout.payments.PaymentCaptured;
import com.sheout.payments.PaymentMethod;
import com.sheout.sharedkernel.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReferralServiceTest {

    private final ReferralCodeRepository codes = mock(ReferralCodeRepository.class);
    private final ReferralRepository referrals = mock(ReferralRepository.class);
    private final ReferralDeviceRepository devices = mock(ReferralDeviceRepository.class);
    private final PromotionService promotions = mock(PromotionService.class);
    private final IncentiveService incentives = mock(IncentiveService.class);
    private final AuthApi auth = mock(AuthApi.class);
    private final BookingApi bookings = mock(BookingApi.class);
    private final List<ReferralEntity> rows = new ArrayList<>();
    private final Map<UUID, List<BookingSummary>> trips = new HashMap<>();

    private final UUID alice = UUID.randomUUID(); // has the code
    private final UUID bea = UUID.randomUUID();   // new, joins with it
    private ReferralService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ObjectProvider<BookingApi> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(bookings);
        service = new ReferralService(codes, referrals, devices, promotions, incentives, auth, provider,
                10, 7, "https://app.sheoutride.com/", "https://partner.sheoutride.com/");

        when(codes.findByCode("ALICE234")).thenReturn(Optional.of(new ReferralCodeEntity(alice, AccountRole.CUSTOMER, "ALICE234")));
        account(alice, Instant.now().minus(Duration.ofDays(200)));
        account(bea, Instant.now().minus(Duration.ofHours(1)));
        when(bookings.findAllForAccount(any())).thenAnswer(inv -> trips.getOrDefault(inv.getArgument(0), List.of()));

        when(referrals.save(any())).thenAnswer(inv -> {
            ReferralEntity r = inv.getArgument(0);
            if (!rows.contains(r)) rows.add(r);
            return r;
        });
        when(referrals.findByRefereeAccountId(any())).thenAnswer(inv ->
                rows.stream().filter(r -> r.getRefereeAccountId().equals(inv.getArgument(0))).findFirst());
        when(referrals.findLockedById(any())).thenAnswer(inv -> rows.stream().findFirst());
        when(referrals.countRewardedFor(any())).thenAnswer(inv -> rows.stream()
                .filter(r -> r.getReferrerAccountId().equals(inv.getArgument(0)))
                .filter(r -> r.getStatus() == ReferralEntity.Status.COMPLETED && r.getReferrerReward().signum() > 0).count());

        when(promotions.grantReferralCredit(eq(PromotionType.REFERRAL_WELCOME), any(), any())).thenReturn(new BigDecimal("50"));
        when(promotions.grantReferralCredit(eq(PromotionType.REFERRAL_REWARD), any(), any())).thenReturn(new BigDecimal("50"));
        when(incentives.awardReferral(any(), any(), any(), any())).thenReturn(new BigDecimal("100"));
    }

    private void account(UUID id, Instant createdAt) {
        when(auth.findAccount(id)).thenReturn(Optional.of(new AccountSummary(id, "+91" + id.hashCode(), null,
                AccountRole.CUSTOMER, createdAt, false)));
    }

    private void paid(UUID bookingId, UUID rider, UUID partner, PaymentMethod method, String amount) {
        when(bookings.getParticipants(bookingId)).thenReturn(Result.success(new BookingParticipants(rider, partner, BookingStatus.COMPLETED)));
        service.onPaymentCaptured(new PaymentCaptured(UUID.randomUUID(), bookingId, method, new BigDecimal(amount),
                new BigDecimal("40"), new BigDecimal("18"), Instant.now()));
    }

    @Test
    void aNewRiderJoiningWithACodeIsPendingAndNothingIsGivenYet() {
        assertThat(service.apply(bea, AccountRole.CUSTOMER, " alice234 ", "install-bea-0001"))
                .isEqualTo(ReferralService.ApplyOutcome.APPLIED);
        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.getStatus()).isEqualTo(ReferralEntity.Status.PENDING);
            assertThat(r.getReferrerAccountId()).isEqualTo(alice);
        });
        verify(promotions, never()).grantReferralCredit(any(), any(), any());
    }

    @Test
    void herFirstPaidTripRewardsBothThroughTheReferralPromotions() {
        service.apply(bea, AccountRole.CUSTOMER, "ALICE234", null);
        UUID trip = UUID.randomUUID();

        paid(trip, bea, UUID.randomUUID(), PaymentMethod.WALLET, "72.00");

        ReferralEntity r = rows.get(0);
        assertThat(r.getStatus()).isEqualTo(ReferralEntity.Status.COMPLETED);
        assertThat(r.getQualifyingBookingId()).isEqualTo(trip);
        assertThat(r.getRefereeReward()).isEqualByComparingTo("50");
        assertThat(r.getReferrerReward()).isEqualByComparingTo("50");
        verify(promotions).grantReferralCredit(eq(PromotionType.REFERRAL_WELCOME), eq(bea), any());
        verify(promotions).grantReferralCredit(eq(PromotionType.REFERRAL_REWARD), eq(alice), any());
    }

    @Test
    void aTripHerSignupCreditPaidInFullIsNotYetAPaidTrip() {
        service.apply(bea, AccountRole.CUSTOMER, "ALICE234", null);

        paid(UUID.randomUUID(), bea, UUID.randomUUID(), PaymentMethod.PROMO_CREDIT, "0.00");

        assertThat(rows.get(0).getStatus()).isEqualTo(ReferralEntity.Status.PENDING);
        verify(promotions, never()).grantReferralCredit(any(), any(), any());
    }

    @Test
    void theReferrerIsRewardedForTenFriendsAndTheEleventhIsStillWelcomed() {
        for (int i = 0; i < 10; i++) {
            ReferralEntity done = new ReferralEntity(alice, UUID.randomUUID(), AccountRole.CUSTOMER, "ALICE234");
            done.complete(UUID.randomUUID(), new BigDecimal("50"), new BigDecimal("50"), null, Instant.now());
            rows.add(done);
        }
        ReferralEntity eleventh = new ReferralEntity(alice, bea, AccountRole.CUSTOMER, "ALICE234");
        rows.add(eleventh);
        when(referrals.findLockedById(any())).thenReturn(Optional.of(eleventh));

        paid(UUID.randomUUID(), bea, UUID.randomUUID(), PaymentMethod.ONLINE, "90.00");

        assertThat(eleventh.getStatus()).isEqualTo(ReferralEntity.Status.COMPLETED);
        assertThat(eleventh.getRefereeReward()).isEqualByComparingTo("50");
        assertThat(eleventh.getReferrerReward()).isEqualByComparingTo("0");
        assertThat(eleventh.getNote()).contains("limit of 10");
        verify(promotions, never()).grantReferralCredit(eq(PromotionType.REFERRAL_REWARD), any(), any());
    }

    @Test
    void anAccountCannotUseItsOwnCode() {
        account(alice, Instant.now());
        when(auth.samePerson(alice, alice)).thenReturn(true);
        assertThat(service.apply(alice, AccountRole.CUSTOMER, "ALICE234", null)).isEqualTo(ReferralService.ApplyOutcome.OWN_CODE);
    }

    @Test
    void aSecondAccountOnTheSamePhoneNumberOrInstallIsTheSamePerson() {
        when(auth.samePerson(alice, bea)).thenReturn(true);
        assertThat(service.apply(bea, AccountRole.CUSTOMER, "ALICE234", null)).isEqualTo(ReferralService.ApplyOutcome.OWN_CODE);

        when(auth.samePerson(alice, bea)).thenReturn(false);
        when(devices.shareAnInstall(alice, bea)).thenReturn(true);
        assertThat(service.apply(bea, AccountRole.CUSTOMER, "ALICE234", "install-shared-01")).isEqualTo(ReferralService.ApplyOutcome.OWN_CODE);
        assertThat(rows).isEmpty();
    }

    @Test
    void foundToBeOnePersonAtTheFirstTripNothingIsPaid() {
        service.apply(bea, AccountRole.CUSTOMER, "ALICE234", null);
        when(devices.shareAnInstall(alice, bea)).thenReturn(true);

        paid(UUID.randomUUID(), bea, UUID.randomUUID(), PaymentMethod.WALLET, "72.00");

        assertThat(rows.get(0).getStatus()).isEqualTo(ReferralEntity.Status.REJECTED);
        verify(promotions, never()).grantReferralCredit(any(), any(), any());
    }

    @Test
    void onlyNewAccountsThatHaveNeverFinishedATripCanJoinWithACode() {
        account(bea, Instant.now().minus(Duration.ofDays(30)));
        assertThat(service.apply(bea, AccountRole.CUSTOMER, "ALICE234", null)).isEqualTo(ReferralService.ApplyOutcome.NOT_ELIGIBLE);

        account(bea, Instant.now().minus(Duration.ofHours(1)));
        BookingSummary finished = mock(BookingSummary.class);
        when(finished.status()).thenReturn(BookingStatus.COMPLETED);
        trips.put(bea, List.of(finished));
        assertThat(service.apply(bea, AccountRole.CUSTOMER, "ALICE234", null)).isEqualTo(ReferralService.ApplyOutcome.NOT_ELIGIBLE);
    }

    @Test
    void aRiderCodeDoesNotBringInAPartnerAndUnknownCodesReadTheSame() {
        assertThat(service.apply(bea, AccountRole.DRIVER, "ALICE234", null)).isEqualTo(ReferralService.ApplyOutcome.INVALID_CODE);
        when(codes.findByCode(anyString())).thenReturn(Optional.empty());
        assertThat(service.apply(bea, AccountRole.CUSTOMER, "NOPE2345", null)).isEqualTo(ReferralService.ApplyOutcome.INVALID_CODE);
    }

    @Test
    void oneCodePerNewAccount() {
        service.apply(bea, AccountRole.CUSTOMER, "ALICE234", null);
        assertThat(service.apply(bea, AccountRole.CUSTOMER, "ALICE234", null)).isEqualTo(ReferralService.ApplyOutcome.ALREADY_APPLIED);
    }

    @Test
    void partnersArePaidThroughTheReferralIncentivesOnTheFriendsFirstPaidTrip() {
        UUID priya = UUID.randomUUID();
        UUID meera = UUID.randomUUID();
        when(codes.findByCode("PRIYA234")).thenReturn(Optional.of(new ReferralCodeEntity(priya, AccountRole.DRIVER, "PRIYA234")));
        account(priya, Instant.now().minus(Duration.ofDays(90)));
        account(meera, Instant.now());
        assertThat(service.apply(meera, AccountRole.DRIVER, "PRIYA234", null)).isEqualTo(ReferralService.ApplyOutcome.APPLIED);
        UUID trip = UUID.randomUUID();

        // Whatever the rider paid with: the partner drove it and was paid.
        paid(trip, UUID.randomUUID(), meera, PaymentMethod.PROMO_CREDIT, "0.00");

        assertThat(rows.get(0).getStatus()).isEqualTo(ReferralEntity.Status.COMPLETED);
        verify(incentives).awardReferral(eq(IncentiveType.REFERRAL_WELCOME), eq(meera), eq(trip), any());
        verify(incentives).awardReferral(eq(IncentiveType.REFERRAL_REWARD), eq(priya), eq(trip), any());
        verify(promotions, never()).grantReferralCredit(any(), any(), any());
    }
}
