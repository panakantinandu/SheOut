package com.sheout.booking;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * Published on any pre-IN_PROGRESS state -&gt; CANCELLED. {@code driverId}
 * is null if cancelled before a driver was ever assigned.
 * <p>
 * cancelledBy and reason were the gap this class previously flagged as
 * something a real system would want. They are here now because the module
 * that counts cancellations against an account cannot work out whose fault
 * one was from a booking id alone - and counting a partner's cancellation
 * against the rider would be worse than not counting at all.
 */
public class BookingCancelled extends DomainEvent {

    private final UUID bookingId;
    private final UUID customerId;
    private final UUID driverId;
    private final UUID cancelledBy;
    private final CancellationReason reason;
    private final UUID countsAgainst;
    private final UUID reported;

    public BookingCancelled(UUID bookingId, UUID customerId, UUID driverId,
                            UUID cancelledBy, CancellationReason reason) {
        this(bookingId, customerId, driverId, cancelledBy, reason, cancelledBy, null);
    }

    public BookingCancelled(UUID bookingId, UUID customerId, UUID driverId,
                            UUID cancelledBy, CancellationReason reason, UUID countsAgainst, UUID reported) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.driverId = driverId;
        this.cancelledBy = cancelledBy;
        this.reason = reason;
        this.countsAgainst = countsAgainst;
        this.reported = reported;
    }

    /**
     * Whose cancellation record this goes on - decided by booking from the
     * evidence it holds (where the partner was, how long she had been
     * coming), not simply whoever pressed cancel. Null for nobody: a search
     * that never sent anybody out, a safety report, a partner who never came.
     */
    public UUID countsAgainst() {
        return countsAgainst;
    }

    /** The account reported as not the right person or vehicle, or not safe; null when nobody was. */
    public UUID reported() {
        return reported;
    }

    /** The account that cancelled. Null only for a system-initiated cancellation, which nothing does today. */
    public UUID cancelledBy() {
        return cancelledBy;
    }

    public CancellationReason reason() {
        return reason;
    }

    public UUID bookingId() {
        return bookingId;
    }

    public UUID customerId() {
        return customerId;
    }

    public UUID driverId() {
        return driverId;
    }
}
