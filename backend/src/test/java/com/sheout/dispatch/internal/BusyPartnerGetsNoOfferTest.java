package com.sheout.dispatch.internal;

import com.sheout.auth.AccountRole;
import com.sheout.auth.AccountSummary;
import com.sheout.auth.AuthApi;
import com.sheout.booking.BookingCategory;
import com.sheout.booking.BookingRequested;
import com.sheout.booking.BookingType;
import com.sheout.booking.GeoAddress;
import com.sheout.dispatch.internal.redis.DriverLocationStore;
import com.sheout.dispatch.internal.redis.OfferStore;
import com.sheout.driververification.ShiftCheckApi;
import com.sheout.driververification.ShiftCheckState;
import com.sheout.users.DriverProfileApi;
import com.sheout.users.DriverProfileSummary;
import com.sheout.users.OnlineStatus;
import com.sheout.users.VehicleType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Today, a partner with a trip in hand - in ANY phase - is never offered
 * another one. Evidence for docs/parcel-dispatch-audit.md (B.1), and the
 * baseline a queued-offer change must not break by accident.
 * <p>
 * Real Postgres (the bookings query behind hasActiveTripAsDriver) and real
 * Redis (the geo set and the offer slot). Only the gates that need a whole
 * verified partner behind them are stubbed: her profile is ONLINE on a BIKE,
 * verified, with a valid shift check, not blocked.
 */
@SpringBootTest(properties = {"sheout.warm-up.enabled=false", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
@DirtiesContext
class BusyPartnerGetsNoOfferTest {

    private static final double LAT = 17.4156;
    private static final double LNG = 78.4347;

    @Autowired DispatchService dispatch;
    @Autowired DriverLocationStore locations;
    @Autowired OfferStore offers;
    @Autowired JdbcTemplate jdbc;
    @SpyBean DriverProfileApi profiles;
    @SpyBean ShiftCheckApi shiftChecks;
    @SpyBean AuthApi auth;

    private final UUID partner = UUID.randomUUID();
    private final List<UUID> bookings = new ArrayList<>();

    @BeforeEach
    void onlineVerifiedPartnerNextToThePickup() {
        DriverProfileSummary profile = mock(DriverProfileSummary.class);
        when(profile.onlineStatus()).thenReturn(OnlineStatus.ONLINE);
        when(profile.vehicleType()).thenReturn(VehicleType.BIKE);
        doReturn(Optional.of(profile)).when(profiles).findByAccountId(partner);
        doReturn(true).when(profiles).isCurrentlyVerified(partner);
        ShiftCheckState shift = mock(ShiftCheckState.class);
        when(shift.valid()).thenReturn(true);
        doReturn(shift).when(shiftChecks).stateFor(partner);
        doReturn(Optional.of(new AccountSummary(partner, "+910000000000", null, AccountRole.DRIVER, Instant.now(), false)))
                .when(auth).findAccount(partner);
        locations.recordLocation(partner, LAT, LNG);
    }

    @AfterEach
    void cleanUp() {
        bookings.forEach(id -> {
            offers.removeOffer(id, partner);
            offers.clearRound(id);
            jdbc.update("delete from bookings where id = ?", id);
        });
        locations.remove(partner);
    }

    /** NONE = no trip at all: the control, which must be offered. */
    @ParameterizedTest(name = "partner whose current trip is {0}")
    @ValueSource(strings = {"NONE", "MATCHED", "ACCEPTED", "IN_PROGRESS", "COMPLETED_UNPAID", "COMPLETED_PAID"})
    void offeredOnlyWhenFree(String current) {
        if (!current.equals("NONE")) {
            UUID trip = UUID.randomUUID();
            bookings.add(trip);
            String status = current.startsWith("COMPLETED") ? "COMPLETED" : current;
            jdbc.update("""
                    insert into bookings (id, type, category, status, customer_id, driver_id,
                        pickup_label, pickup_lat, pickup_lng, drop_label, drop_lat, drop_lng,
                        fare_estimate, completed_at, payment_settled_at, created_at, updated_at)
                    values (?, 'RIDE', 'BIKE', ?, ?, ?, 'A', ?, ?, 'B', ?, ?, 50,
                        case when ? then now() at time zone 'utc' end,
                        case when ? then now() at time zone 'utc' end, now(), now())""",
                    // UTC, as the app writes these timestamp-without-zone columns -
                    // a JVM-local Timestamp here read back five hours old in Chicago.
                    trip, status, UUID.randomUUID(), partner, LAT, LNG, LAT + 0.01, LNG,
                    status.equals("COMPLETED"), current.equals("COMPLETED_PAID"));
        }

        UUID next = UUID.randomUUID();
        bookings.add(next);
        dispatch.onBookingRequested(new BookingRequested(next, UUID.randomUUID(), BookingType.RIDE, BookingCategory.BIKE,
                new GeoAddress("Pickup", LAT + 0.001, LNG), new GeoAddress("Drop", LAT + 0.02, LNG)));

        boolean offered = offers.findActiveOfferForDriver(partner).filter(next::equals).isPresent();
        boolean free = current.equals("NONE") || current.equals("COMPLETED_PAID");
        assertThat(offered)
                .as("a partner whose trip is %s %s be offered the next one", current, free ? "should" : "must not")
                .isEqualTo(free);
    }
}
