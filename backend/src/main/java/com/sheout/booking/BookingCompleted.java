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

    public BookingCompleted(UUID bookingId, UUID customerId, UUID driverId, BigDecimal finalFare) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.driverId = driverId;
        this.finalFare = finalFare;
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
