package com.sheout.booking.internal;

import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.sharedkernel.web.RequestInfo;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Writes a trip's audit log, in the same transaction as the change it
 * records - a transition that rolls back leaves no line behind it. Who did
 * it is read from the signed-in account; with none (a scheduled job) the
 * actor is the system.
 */
@Component
class BookingEventLog {

    /** For BookingService built by hand in unit tests. */
    static final BookingEventLog NONE = new BookingEventLog(null);

    private final BookingEventRepository events;

    BookingEventLog(BookingEventRepository events) {
        this.events = events;
    }

    void record(UUID bookingId, String event, Object from, Object to, String detail) {
        if (events == null) {
            return;
        }
        Optional<CurrentAccount> actor = CurrentAccountContext.get();
        events.save(new BookingEventEntity(bookingId, event,
                from == null ? null : from.toString(), to == null ? null : to.toString(),
                actor.map(CurrentAccount::accountId).orElse(null),
                actor.map(a -> a.role().name()).orElse("SYSTEM"),
                detail, RequestInfo.requestId().orElse(null), RequestInfo.userAgent().orElse(null), Instant.now()));
    }

    List<com.sheout.booking.BookingEvent> forBooking(UUID bookingId) {
        if (events == null) {
            return List.of();
        }
        return events.findByBookingIdOrderByAtAsc(bookingId).stream().map(BookingEventEntity::view).toList();
    }
}
