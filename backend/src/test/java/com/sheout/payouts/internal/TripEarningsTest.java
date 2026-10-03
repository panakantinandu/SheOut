package com.sheout.payouts.internal;

import com.sheout.booking.BookingApi;
import com.sheout.booking.BookingParticipants;
import com.sheout.payments.PaymentCaptured;
import com.sheout.payments.PaymentMethod;
import com.sheout.payouts.PayoutApi;
import com.sheout.payouts.TripEarning;
import com.sheout.sharedkernel.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A partner's earnings are her share - the fare less SheOut's commission,
 * as her wallet was credited at capture - never the fare the rider paid.
 * Earnings and Home used to add up fares, so a ₹100 trip showed ₹100 earned
 * when she was paid ₹82. Needs the local Postgres and Redis.
 */
@SpringBootTest(properties = {"sheout.warm-up.enabled=false", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
@DirtiesContext
class TripEarningsTest {

    @Autowired PayoutApi payouts;
    @Autowired PayoutService payoutService;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;
    @SpyBean BookingApi bookings;

    private final UUID partner = UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from wallet_entries where driver_account_id = ?", partner);
        jdbc.update("delete from driver_wallets where driver_account_id = ?", partner);
    }

    private void captured(UUID paymentId, UUID bookingId, String fare, String share) {
        BookingParticipants participants = mock(BookingParticipants.class);
        when(participants.driverId()).thenReturn(partner);
        doReturn(Result.success(participants)).when(bookings).getParticipants(any());
        transactions.executeWithoutResult(s -> payoutService.onPaymentCaptured(new PaymentCaptured(paymentId, bookingId,
                PaymentMethod.UPI, new BigDecimal(fare), new BigDecimal(share), new BigDecimal("18.00"), Instant.now())));
    }

    @Test
    void herEarningsAreHerShareNotTheFare() {
        UUID trip1 = UUID.randomUUID(), trip2 = UUID.randomUUID();
        UUID payment1 = UUID.randomUUID();
        captured(payment1, trip1, "100.00", "82.00");
        captured(UUID.randomUUID(), trip2, "77.09", "63.21");
        // A capture delivered twice is credited once.
        captured(payment1, trip1, "100.00", "82.00");

        var earnings = payouts.earningsByTrip(partner);

        assertThat(earnings).extracting(TripEarning::bookingId).containsExactlyInAnyOrder(trip1, trip2);
        assertThat(earnings).filteredOn(e -> e.bookingId().equals(trip1)).singleElement()
                .satisfies(e -> assertThat(e.share()).isEqualByComparingTo("82.00"));
        BigDecimal total = earnings.stream().map(TripEarning::share).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).as("what she earned, not the 177.09 riders paid").isEqualByComparingTo("145.21");
    }

    @Test
    void nobodyElsesTripsAreHers() {
        captured(UUID.randomUUID(), UUID.randomUUID(), "50.00", "41.00");

        assertThat(payouts.earningsByTrip(UUID.randomUUID())).isEmpty();
    }
}
