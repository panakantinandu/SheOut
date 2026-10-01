package com.sheout.booking.internal;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One line of a trip's audit log - see V53 and BookingEventLog. */
@Entity
@Table(name = "booking_events")
class BookingEventEntity extends BaseEntity {

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(nullable = false, length = 30)
    private String event;

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", length = 30)
    private String toStatus;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_role", length = 20)
    private String actorRole;

    @Column(length = 300)
    private String detail;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(length = 120)
    private String device;

    @Column(nullable = false)
    private Instant at;

    protected BookingEventEntity() {
    }

    BookingEventEntity(UUID bookingId, String event, String fromStatus, String toStatus, UUID actorId,
                       String actorRole, String detail, String requestId, String device, Instant at) {
        this.bookingId = bookingId;
        this.event = event;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.detail = detail == null || detail.length() <= 300 ? detail : detail.substring(0, 300);
        this.requestId = requestId;
        this.device = device;
        this.at = at;
    }

    com.sheout.booking.BookingEvent view() {
        return new com.sheout.booking.BookingEvent(event, fromStatus, toStatus, actorId, actorRole, detail, requestId, device, at);
    }
}
