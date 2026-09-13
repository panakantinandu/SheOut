package com.sheout.dispatch;

import com.sheout.sharedkernel.event.DomainEvent;

import java.time.Duration;
import java.util.UUID;

/**
 * Published when dispatch stops searching for a booking without a driver
 * having accepted it.
 * <p>
 * This is the end of the search, not a pause. Nothing retries after it, and
 * booking treats it as final - see BookingStatus.NO_DRIVERS_AVAILABLE.
 * <p>
 * It exists because the alternative was silence. A search that ran out of
 * retries simply cleared its own Redis state and left the booking sitting
 * in REQUESTED, which the rider's screen renders as "Searching for a nearby
 * driver..." - forever, for a search that stopped minutes ago. The absence
 * of an event was the bug.
 * <p>
 * DEPENDENCY DIRECTION, FLAGGED: dispatch already depends on booking
 * (BookingApi, BookingRequested). Booking listening to this event adds a
 * dependency back the other way, so the two modules now reference each
 * other and dispatch can no longer be extracted into its own service
 * without this class travelling with it or being restated as a message
 * contract. That is the cost of keeping the notification an event rather
 * than a direct bookingApi call, and it is the right trade here: a direct
 * call would put "what happens to a booking when a search fails" inside
 * dispatch, where it does not belong. Every other event in this codebase
 * lives in the module that publishes it, and this follows that.
 */
public class DispatchExhausted extends DomainEvent {

    private final UUID bookingId;
    private final UUID customerId;
    private final Reason reason;
    private final int roundsRun;
    private final Duration searchedFor;

    public DispatchExhausted(UUID bookingId, UUID customerId, Reason reason, int roundsRun, Duration searchedFor) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.reason = reason;
        this.roundsRun = roundsRun;
        this.searchedFor = searchedFor;
    }

    /**
     * Which limit ran out first.
     * <p>
     * Carried because the two mean different things operationally and the
     * distinction is invisible once both become the same booking status. A
     * city full of RETRIES_EXHAUSTED says there are no drivers where riders
     * are. A city full of SEARCH_TIMED_OUT says rounds are running slower
     * than they should and the search is being cut off mid-flight, which is
     * a tuning problem, not a supply one.
     */
    public enum Reason {
        /** Ran the configured number of rounds, each with a wider radius, and nobody took it. */
        RETRIES_EXHAUSTED,

        /** Hit the total time budget for one search, whatever round it was on. */
        SEARCH_TIMED_OUT
    }

    public UUID bookingId() {
        return bookingId;
    }

    /** Here so a listener can notify her without going back to booking to ask who she was. */
    public UUID customerId() {
        return customerId;
    }

    public Reason reason() {
        return reason;
    }

    /** How many offer rounds actually ran, for the operational picture above. */
    public int roundsRun() {
        return roundsRun;
    }

    /** Wall-clock time from the first round to giving up. */
    public Duration searchedFor() {
        return searchedFor;
    }
}
