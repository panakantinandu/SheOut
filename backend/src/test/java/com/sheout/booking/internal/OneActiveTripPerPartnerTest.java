package com.sheout.booking.internal;

import com.sheout.booking.BookingApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V81: the database itself refuses a second active trip for one partner -
 * whatever the application checks above it get wrong. A finished trip does
 * not count, and a partner on a trip cannot be assigned another. Local Postgres.
 */
@SpringBootTest(properties = {"sheout.warm-up.enabled=false", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
@DirtiesContext
class OneActiveTripPerPartnerTest {

    @Autowired BookingApi bookings;
    @Autowired JdbcTemplate jdbc;

    private final UUID partner = UUID.randomUUID();
    private final List<UUID> trips = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        trips.forEach(id -> {
            jdbc.update("delete from booking_events where booking_id = ?", id);
            jdbc.update("delete from bookings where id = ?", id);
        });
    }

    private UUID trip(String status, UUID driver) {
        UUID id = UUID.randomUUID();
        trips.add(id);
        jdbc.update("""
                insert into bookings (id, type, category, status, customer_id, driver_id, pickup_label, pickup_lat, pickup_lng,
                    drop_label, drop_lat, drop_lng, fare_estimate, promo_discount, pickup_attempts, created_at, updated_at)
                values (?, 'RIDE', 'BIKE', ?, ?, ?, 'A', 17.41, 78.43, 'B', 17.44, 78.37, 50, 0, 0,
                    now() at time zone 'utc', now() at time zone 'utc')""",
                id, status, UUID.randomUUID(), driver);
        return id;
    }

    @Test
    void theDatabaseRefusesASecondActiveTripForOnePartner() {
        trip("ACCEPTED", partner);
        for (String status : List.of("MATCHED", "ACCEPTED", "IN_PROGRESS")) {
            assertThatThrownBy(() -> trip(status, partner)).as(status).isInstanceOf(DataIntegrityViolationException.class);
        }
        // Finished trips are history, not work: any number of them.
        trip("COMPLETED", partner);
        trip("COMPLETED", partner);
        trip("CANCELLED", partner);
        assertThat(jdbc.queryForObject("select count(*) from bookings where driver_id = ?", Integer.class, partner)).isEqualTo(4);
    }

    @Test
    void aPartnerOnATripCannotBeAssignedAnother() {
        trip("IN_PROGRESS", partner);
        UUID waiting = trip("REQUESTED", null);

        assertThatThrownBy(() -> bookings.assignDriver(waiting, partner)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select status from bookings where id = ?", String.class, waiting))
                .as("rolled back: still waiting for someone else").isEqualTo("REQUESTED");
        assertThat(jdbc.queryForObject("select driver_id is null from bookings where id = ?", Boolean.class, waiting)).isTrue();
    }

    @Test
    void onceHerTripEndsSheCanBeAssignedTheNextOne() {
        UUID current = trip("IN_PROGRESS", partner);
        jdbc.update("update bookings set status = 'COMPLETED' where id = ?", current);
        UUID next = trip("REQUESTED", null);
        assertThat(bookings.assignDriver(next, partner).isSuccess()).isTrue();
    }
}
