package com.sheout.notifications.internal.sos;

import com.sheout.notifications.SosStatus;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sos_alert")
public class SosAlertEntity extends BaseEntity {

    @Column(name = "customer_account_id", nullable = false)
    private UUID customerAccountId;

    /** Nullable - the customer may trigger SOS with no active booking at all. Not validated against BookingApi - see SosService's Javadoc. */
    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SosStatus status;

    @Column(name = "contacts_notified", nullable = false)
    private int contactsNotified;

    @Column(name = "contacts_failed", nullable = false)
    private int contactsFailed;

    /** Both null while ACTIVE; both set together by resolve(). */
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    protected SosAlertEntity() {
        // JPA
    }

    public SosAlertEntity(UUID customerAccountId, UUID bookingId, double lat, double lng) {
        this.customerAccountId = customerAccountId;
        this.bookingId = bookingId;
        this.lat = lat;
        this.lng = lng;
        this.status = SosStatus.ACTIVE;
        this.contactsNotified = 0;
        this.contactsFailed = 0;
    }

    public UUID getCustomerAccountId() {
        return customerAccountId;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public SosStatus getStatus() {
        return status;
    }

    public int getContactsNotified() {
        return contactsNotified;
    }

    public void setContactsNotified(int contactsNotified) {
        this.contactsNotified = contactsNotified;
    }

    public int getContactsFailed() {
        return contactsFailed;
    }

    public void setContactsFailed(int contactsFailed) {
        this.contactsFailed = contactsFailed;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public UUID getResolvedBy() {
        return resolvedBy;
    }

    /**
     * Status and audit fields move together, so there is no way to mark an
     * alert resolved without recording who did it - hence no plain
     * setStatus on this entity.
     */
    public void resolve(UUID resolvedByAccountId) {
        this.status = SosStatus.RESOLVED;
        this.resolvedAt = Instant.now();
        this.resolvedBy = resolvedByAccountId;
    }
}
