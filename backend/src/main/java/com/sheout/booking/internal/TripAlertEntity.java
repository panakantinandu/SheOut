package com.sheout.booking.internal;

import com.sheout.booking.TripAlertsApi;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One trip-watch alert - see V51 and TripWatchService. */
@Entity
@Table(name = "trip_alerts")
class TripAlertEntity extends BaseEntity {

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TripAlertsApi.Kind kind;

    @Column(length = 300)
    private String detail;

    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(length = 500)
    private String note;

    protected TripAlertEntity() {
    }

    TripAlertEntity(UUID bookingId, TripAlertsApi.Kind kind, String detail, Instant raisedAt) {
        this.bookingId = bookingId;
        this.kind = kind;
        this.detail = detail;
        this.raisedAt = raisedAt;
    }

    void resolve(Instant at, UUID by, String note) {
        this.resolvedAt = at;
        this.resolvedBy = by;
        this.note = note;
    }

    void updateDetail(String detail) {
        this.detail = detail;
    }

    TripAlertsApi.TripAlert view() {
        return new TripAlertsApi.TripAlert(getId(), bookingId, kind, detail, raisedAt, resolvedAt, resolvedBy, note);
    }

    UUID getBookingId() {
        return bookingId;
    }

    TripAlertsApi.Kind getKind() {
        return kind;
    }

    Instant getResolvedAt() {
        return resolvedAt;
    }
}
