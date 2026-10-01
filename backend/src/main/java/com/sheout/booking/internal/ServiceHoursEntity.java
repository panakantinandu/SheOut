package com.sheout.booking.internal;

import com.sheout.booking.ServiceHoursApi.ServiceHoursMode;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/** The one row of service_hours - see V49 and ServiceHoursService. */
@Entity
@Table(name = "service_hours")
class ServiceHoursEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServiceHoursMode mode;

    @Column(name = "opens_at", nullable = false)
    private LocalTime opensAt;

    @Column(name = "closes_at", nullable = false)
    private LocalTime closesAt;

    @Column(nullable = false)
    private boolean paused;

    @Column(name = "pause_reason", length = 300)
    private String pauseReason;

    @Column(name = "paused_until")
    private Instant pausedUntil;

    @Column(name = "paused_at")
    private Instant pausedAt;

    @Column(name = "paused_by")
    private UUID pausedBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected ServiceHoursEntity() {
    }

    /** Only for a database the migration's seed row never reached (tests). */
    static ServiceHoursEntity alwaysOpen() {
        ServiceHoursEntity e = new ServiceHoursEntity();
        e.mode = ServiceHoursMode.ALWAYS_OPEN;
        e.opensAt = LocalTime.of(6, 0);
        e.closesAt = LocalTime.of(22, 0);
        return e;
    }

    void schedule(ServiceHoursMode mode, LocalTime opensAt, LocalTime closesAt, UUID by) {
        this.mode = mode;
        this.opensAt = opensAt;
        this.closesAt = closesAt;
        this.updatedBy = by;
    }

    void pause(String reason, Instant until, Instant now, UUID by) {
        this.paused = true;
        this.pauseReason = reason;
        this.pausedUntil = until;
        this.pausedAt = now;
        this.pausedBy = by;
        this.updatedBy = by;
    }

    void resume(UUID by) {
        this.paused = false;
        this.pauseReason = null;
        this.pausedUntil = null;
        this.updatedBy = by;
    }

    /** Paused, and not a timed pause whose time has passed. */
    boolean pausedAt(Instant now) {
        return paused && (pausedUntil == null || now.isBefore(pausedUntil));
    }

    ServiceHoursMode getMode() {
        return mode;
    }

    LocalTime getOpensAt() {
        return opensAt;
    }

    LocalTime getClosesAt() {
        return closesAt;
    }

    String getPauseReason() {
        return pauseReason;
    }

    Instant getPausedUntil() {
        return pausedUntil;
    }

    Instant getPausedAt() {
        return pausedAt;
    }

    UUID getPausedBy() {
        return pausedBy;
    }

    UUID getUpdatedBy() {
        return updatedBy;
    }
}
