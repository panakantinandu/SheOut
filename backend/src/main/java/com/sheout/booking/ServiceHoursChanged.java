package com.sheout.booking;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * An operator changed when bookings are taken - the schedule, a pause, or a
 * resume. Nothing listens yet; it is published so telling partners "no new
 * trips tonight" can be added without reaching into booking.
 */
public class ServiceHoursChanged extends DomainEvent {

    private final UUID changedBy;
    private final String summary;

    public ServiceHoursChanged(UUID changedBy, String summary) {
        this.changedBy = changedBy;
        this.summary = summary;
    }

    public UUID changedBy() {
        return changedBy;
    }

    public String summary() {
        return summary;
    }
}
