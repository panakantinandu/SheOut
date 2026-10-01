package com.sheout.booking;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/** Trip watch noticed something about a live trip - see TripAlertsApi.Kind. */
public class TripAlertRaised extends DomainEvent {

    private final UUID alertId;
    private final UUID bookingId;
    private final UUID customerId;
    private final TripAlertsApi.Kind kind;
    private final String detail;

    public TripAlertRaised(UUID alertId, UUID bookingId, UUID customerId, TripAlertsApi.Kind kind, String detail) {
        this.alertId = alertId;
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.kind = kind;
        this.detail = detail;
    }

    public UUID alertId() {
        return alertId;
    }

    public UUID bookingId() {
        return bookingId;
    }

    public UUID customerId() {
        return customerId;
    }

    public TripAlertsApi.Kind kind() {
        return kind;
    }

    public String detail() {
        return detail;
    }
}
