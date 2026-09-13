package com.sheout.booking.internal;

import com.sheout.booking.BookingError;
import com.sheout.booking.BookingStatus;
import com.sheout.sharedkernel.Result;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.sheout.booking.BookingStatus.ACCEPTED;
import static com.sheout.booking.BookingStatus.CANCELLED;
import static com.sheout.booking.BookingStatus.COMPLETED;
import static com.sheout.booking.BookingStatus.IN_PROGRESS;
import static com.sheout.booking.BookingStatus.MATCHED;
import static com.sheout.booking.BookingStatus.NO_DRIVERS_AVAILABLE;
import static com.sheout.booking.BookingStatus.REQUESTED;

/**
 * The one place valid booking transitions are decided - every service
 * method that changes a booking's status calls {@link #transition} first
 * and acts on the Result, rather than checking status with its own
 * if-statements. This is the piece expected to change most as the product
 * grows (e.g. a future "driver rejects, re-queue for another match" flow),
 * so it stays isolated here.
 */
final class BookingStateMachine {

    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED = new EnumMap<>(BookingStatus.class);

    static {
        // NO_DRIVERS_AVAILABLE is reachable ONLY from REQUESTED. A booking
        // that already has a driver assigned cannot later decide nobody was
        // available, and this is the guard that makes the race safe: if a
        // driver accepts a still-live offer moments after the search timed
        // out, assignDriver asks for NO_DRIVERS_AVAILABLE -> MATCHED, is
        // refused here, and dispatch releases its claim. The rider is not
        // silently handed a partner she was already told did not exist.
        ALLOWED.put(REQUESTED, EnumSet.of(MATCHED, CANCELLED, NO_DRIVERS_AVAILABLE));
        ALLOWED.put(MATCHED, EnumSet.of(ACCEPTED, CANCELLED));
        ALLOWED.put(ACCEPTED, EnumSet.of(IN_PROGRESS, CANCELLED));
        ALLOWED.put(IN_PROGRESS, EnumSet.of(COMPLETED));
        ALLOWED.put(COMPLETED, EnumSet.noneOf(BookingStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(BookingStatus.class));
        // A dead end on purpose - retrying is a new booking, not a revival
        // of this one. See BookingStatus.NO_DRIVERS_AVAILABLE.
        ALLOWED.put(NO_DRIVERS_AVAILABLE, EnumSet.noneOf(BookingStatus.class));
    }

    private BookingStateMachine() {
    }

    static Result<BookingStatus, BookingError> transition(BookingStatus current, BookingStatus target) {
        if (!ALLOWED.getOrDefault(current, Set.of()).contains(target)) {
            return Result.failure(BookingError.INVALID_STATE_TRANSITION);
        }
        return Result.success(target);
    }
}
