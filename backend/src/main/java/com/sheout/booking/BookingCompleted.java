package com.sheout.booking;

import com.sheout.sharedkernel.event.DomainEvent;

import java.math.BigDecimal;
import java.util.UUID;

/** Published on IN_PROGRESS -&gt; COMPLETED. Carries finalFare - payments needs it to actually charge. */
public class BookingCompleted extends DomainEvent {

    private final UUID bookingId;
    private final UUID customerId;
    private final UUID driverId;
    private final BigDecimal finalFare;
    private final BigDecimal amountDue;

    /** No promotion: she owes the whole fare. */
    public BookingCompleted(UUID bookingId, UUID customerId, UUID driverId, BigDecimal finalFare) {
        this(bookingId, customerId, driverId, finalFare, finalFare);
    }

    public BookingCompleted(UUID bookingId, UUID customerId, UUID driverId, BigDecimal finalFare, BigDecimal amountDue) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.driverId = driverId;
        this.finalFare = finalFare;
        this.amountDue = amountDue;
    }

    /**
     * What the rider is charged: the fare less any promotion. The partner's
     * share is still worked out from finalFare - a promotion is paid by
     * SheOut, never out of what she earns.
     */
    public BigDecimal amountDue() {
        return amountDue;
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

    public BigDecimal finalFare() {
        return finalFare;
    }
}
