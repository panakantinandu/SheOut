package com.sheout.notifications;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * A rider pressed SOS and the alert is recorded. Published for every press,
 * throttled or not - an operator is told about every one, even when her
 * contacts were texted moments ago and are not texted again.
 */
public class SosAlertRaised extends DomainEvent {

    private final UUID alertId;
    private final UUID customerAccountId;
    private final UUID bookingId;

    public SosAlertRaised(UUID alertId, UUID customerAccountId, UUID bookingId) {
        this.alertId = alertId;
        this.customerAccountId = customerAccountId;
        this.bookingId = bookingId;
    }

    public UUID alertId() {
        return alertId;
    }

    public UUID customerAccountId() {
        return customerAccountId;
    }

    /** Null when the alert was raised with no trip under way. */
    public UUID bookingId() {
        return bookingId;
    }
}
