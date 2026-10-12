package com.sheout.booking.internal;

import com.sheout.booking.ParcelPhotoKind;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One parcel photo - see V80. The file goes at retention; this row stays as the record that it existed. */
@Entity
@Table(name = "parcel_photos")
public class ParcelPhotoEntity extends BaseEntity {

    @Column(nullable = false)
    private UUID bookingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParcelPhotoKind kind;

    @Column(length = 500)
    private String storageKey;

    @Column(nullable = false)
    private UUID uploadedBy;

    /** The server's clock at upload, never the phone's. */
    @Column(nullable = false)
    private Instant takenAt;

    /** The partner's last trusted position at upload; null when it was too old to say. */
    private Double lat;
    private Double lng;

    @Column(nullable = false, length = 64)
    private String contentHash;

    private Instant deletedAt;

    protected ParcelPhotoEntity() {
        // JPA
    }

    ParcelPhotoEntity(UUID bookingId, ParcelPhotoKind kind, String storageKey, UUID uploadedBy, Instant takenAt,
                      Double lat, Double lng, String contentHash) {
        this.bookingId = bookingId;
        this.kind = kind;
        this.storageKey = storageKey;
        this.uploadedBy = uploadedBy;
        this.takenAt = takenAt;
        this.lat = lat;
        this.lng = lng;
        this.contentHash = contentHash;
    }

    void markDeleted(Instant at) {
        this.storageKey = null;
        this.deletedAt = at;
    }

    public UUID getBookingId() { return bookingId; }
    public ParcelPhotoKind getKind() { return kind; }
    public String getStorageKey() { return storageKey; }
    public UUID getUploadedBy() { return uploadedBy; }
    public Instant getTakenAt() { return takenAt; }
    public Double getLat() { return lat; }
    public Double getLng() { return lng; }
    public String getContentHash() { return contentHash; }
    public Instant getDeletedAt() { return deletedAt; }
}
