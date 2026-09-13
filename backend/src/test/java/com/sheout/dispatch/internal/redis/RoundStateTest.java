package com.sheout.dispatch.internal.redis;

import com.sheout.booking.BookingCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the one property the whole search timeout rests on: the deadline
 * belongs to the search, not to the round, and nothing that happens between
 * rounds may move it.
 * <p>
 * If a retry ever recomputed the deadline instead of carrying it forward,
 * every search that kept finding rounds to run would run forever - the exact
 * failure this mechanism exists to stop, reintroduced by a one-line change
 * that would look perfectly reasonable in review.
 */
class RoundStateTest {

    private static final Instant STARTED = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant DEADLINE = STARTED.plusSeconds(90);

    private static RoundState firstRound() {
        return new RoundState(
                1, 3.0, 17.4435, 78.3772, BookingCategory.BIKE,
                UUID.randomUUID(), STARTED, DEADLINE);
    }

    @Test
    @DisplayName("a retry inherits the deadline rather than getting a fresh one")
    void deadlineSurvivesRetries() {
        RoundState round = firstRound();
        for (int i = 0; i < 10; i++) {
            round = round.nextAttempt(round.radiusKm() * 2);
            assertEquals(DEADLINE, round.searchDeadline());
            assertEquals(STARTED, round.searchStartedAt());
        }
        assertEquals(11, round.attempt());
    }

    @Test
    @DisplayName("the radius expands while everything about the search stays put")
    void retryExpandsOnlyTheRadius() {
        RoundState first = firstRound();
        RoundState second = first.nextAttempt(6.0);

        assertEquals(6.0, second.radiusKm());
        assertEquals(2, second.attempt());
        assertEquals(first.customerId(), second.customerId());
        assertEquals(first.pickupLat(), second.pickupLat());
        assertEquals(first.category(), second.category());
    }

    @Test
    @DisplayName("a round that ends exactly on the deadline still runs")
    void roundEndingOnTheDeadlineIsAllowed() {
        RoundState round = firstRound();
        // 15s left, 15s round: it fits exactly, and refusing it threw away
        // half a 30s budget in a live run.
        assertFalse(round.noRoomForAnotherRound(DEADLINE.minusSeconds(15), 15));
        // One second less room than the round needs.
        assertTrue(round.noRoomForAnotherRound(DEADLINE.minusSeconds(14), 15));
        assertTrue(round.noRoomForAnotherRound(DEADLINE, 15));
    }

    @Test
    @DisplayName("the deadline is reached, not merely approached")
    void deadlineIsInclusive() {
        RoundState round = firstRound();
        assertFalse(round.deadlinePassed(DEADLINE.minusMillis(1)));
        // Exactly at the deadline ends the search. A search allowed to run
        // "until strictly after" its deadline would hang on an extra round.
        assertTrue(round.deadlinePassed(DEADLINE));
        assertTrue(round.deadlinePassed(DEADLINE.plusSeconds(30)));
    }
}
