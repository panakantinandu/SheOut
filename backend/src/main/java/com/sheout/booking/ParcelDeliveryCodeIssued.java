package com.sheout.booking;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * A parcel's delivery code exists: the partner has accepted it. The sender
 * always has the code in her app; notifications may also text it to the
 * receiver, and only when the receiver SMS is switched on (DLT approved).
 * The code travels in-process only and is never logged.
 */
public class ParcelDeliveryCodeIssued extends DomainEvent {

    private final UUID bookingId;
    private final String receiverPhone;
    private final String code;

    public ParcelDeliveryCodeIssued(UUID bookingId, String receiverPhone, String code) {
        this.bookingId = bookingId;
        this.receiverPhone = receiverPhone;
        this.code = code;
    }

    public UUID bookingId() {
        return bookingId;
    }

    public String receiverPhone() {
        return receiverPhone;
    }

    public String code() {
        return code;
    }

    @Override
    public String toString() {
        return "ParcelDeliveryCodeIssued[bookingId=" + bookingId + "]";
    }
}
