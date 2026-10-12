package com.sheout.payouts.internal;

import com.sheout.booking.ParcelDisputeClosed;
import com.sheout.booking.ParcelDisputeOpened;
import com.sheout.booking.ParcelDisputeOutcome;
import com.sheout.payments.PaymentCaptured;
import com.sheout.payments.PaymentMethod;
import com.sheout.payouts.PayoutApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A partner's parcel earnings are credited at capture but held for the
 * dispute window, then released; a dispute keeps them held, and closing it
 * against her withholds them. A ride is never held. Local Postgres.
 */
@SpringBootTest(properties = {"sheout.warm-up.enabled=false", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
@DirtiesContext
class ParcelEarningHoldTest {

    @Autowired PayoutService payouts;
    @Autowired PayoutApi payoutApi;
    @Autowired EarningHoldService holds;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;

    private final UUID partner = UUID.randomUUID();
    private final List<UUID> trips = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        trips.forEach(id -> {
            jdbc.update("delete from earning_holds where booking_id = ?", id);
            jdbc.update("delete from bookings where id = ?", id);
        });
        jdbc.update("delete from wallet_entries where driver_account_id = ?", partner);
        jdbc.update("delete from driver_wallets where driver_account_id = ?", partner);
    }

    private UUID completedTrip(String type, String category) {
        UUID id = UUID.randomUUID();
        trips.add(id);
        jdbc.update("""
                insert into bookings (id, type, category, status, customer_id, driver_id, pickup_label, pickup_lat, pickup_lng,
                    drop_label, drop_lat, drop_lng, fare_estimate, final_fare, promo_discount, pickup_attempts,
                    completed_at, created_at, updated_at)
                values (?, ?, ?, 'COMPLETED', ?, ?, 'A', 17.41, 78.43, 'B', 17.44, 78.37, 100, 100, 0, 0,
                    now() at time zone 'utc', now() at time zone 'utc', now() at time zone 'utc')""",
                id, type, category, UUID.randomUUID(), partner);
        return id;
    }

    private void captured(UUID trip) {
        transactions.executeWithoutResult(s -> payouts.onPaymentCaptured(new PaymentCaptured(UUID.randomUUID(), trip,
                PaymentMethod.UPI, new BigDecimal("100.00"), new BigDecimal("82.00"), new BigDecimal("18.00"), Instant.now())));
    }

    @Test
    void parcelEarningsAreHeldThenReleasedOnceTheWindowHasPassed() {
        UUID trip = completedTrip("DELIVERY", "PARCEL");
        captured(trip);

        var wallet = payoutApi.getWallet(partner);
        assertThat(wallet.totalEarned()).isEqualByComparingTo("82.00");
        assertThat(wallet.heldEarnings()).isEqualByComparingTo("82.00");
        assertThat(wallet.availableBalance()).as("not withdrawable inside the window").isEqualByComparingTo("0");

        assertThat(holds.releaseDue(Instant.now().plus(Duration.ofHours(23)))).as("not yet").isZero();
        assertThat(holds.releaseDue(Instant.now().plus(Duration.ofHours(25)))).isEqualTo(1);
        wallet = payoutApi.getWallet(partner);
        assertThat(wallet.heldEarnings()).isEqualByComparingTo("0");
        assertThat(wallet.availableBalance()).isEqualByComparingTo("82.00");
        assertThat(holds.find(trip).orElseThrow().status()).isEqualTo("RELEASED");
    }

    @Test
    void aRideIsNeverHeld() {
        UUID trip = completedTrip("RIDE", "BIKE");
        captured(trip);
        assertThat(holds.find(trip)).isEmpty();
        assertThat(payoutApi.getWallet(partner).availableBalance()).isEqualByComparingTo("82.00");
    }

    @Test
    void aDisputeKeepsThemHeldAndClosingItAgainstHerWithholdsThem() {
        UUID trip = completedTrip("DELIVERY", "PARCEL");
        captured(trip);
        holds.onDisputeOpened(new ParcelDisputeOpened(trip, UUID.randomUUID(), partner));

        assertThat(holds.releaseDue(Instant.now().plus(Duration.ofHours(48)))).as("held by the dispute, whatever the window says").isZero();

        holds.onDisputeClosed(new ParcelDisputeClosed(trip, partner, ParcelDisputeOutcome.PARTNER_AT_FAULT, UUID.randomUUID(), "Parcel never reached the receiver"));
        var wallet = payoutApi.getWallet(partner);
        assertThat(holds.find(trip).orElseThrow().status()).isEqualTo("WITHHELD");
        assertThat(wallet.heldEarnings()).isEqualByComparingTo("0");
        assertThat(wallet.totalEarned()).as("withheld leaves what she earned").isEqualByComparingTo("0");
        assertThat(wallet.availableBalance()).isEqualByComparingTo("0");
    }

    @Test
    void anOperatorCanHoldPastTheWindowAndReleaseWithAReason() {
        UUID trip = completedTrip("DELIVERY", "PARCEL");
        captured(trip);
        UUID admin = UUID.randomUUID();

        assertThat(holds.hold(trip, admin, " ").isFailure()).as("a reason is required").isTrue();
        assertThat(holds.hold(trip, admin, "Sender called about a damaged box").isSuccess()).isTrue();
        assertThat(holds.releaseDue(Instant.now().plus(Duration.ofHours(48)))).isZero();

        assertThat(holds.release(trip, admin, "Photos show the box intact at the handover").isSuccess()).isTrue();
        assertThat(payoutApi.getWallet(partner).availableBalance()).isEqualByComparingTo("82.00");
        assertThat(holds.release(trip, admin, "again").isFailure()).as("released is final").isTrue();
    }
}
