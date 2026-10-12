package com.sheout.booking.internal;

import com.sheout.booking.ParcelPhotoKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface ParcelPhotoRepository extends JpaRepository<ParcelPhotoEntity, UUID> {

    List<ParcelPhotoEntity> findByBookingIdOrderByTakenAtAsc(UUID bookingId);

    boolean existsByBookingIdAndKindAndDeletedAtIsNull(UUID bookingId, ParcelPhotoKind kind);

    /** The newest photo of a kind, for "a photo since the exception was raised". */
    java.util.Optional<ParcelPhotoEntity> findFirstByBookingIdAndKindOrderByTakenAtDesc(UUID bookingId, ParcelPhotoKind kind);

    /** The same picture used on another delivery by the same partner - a reused photo, flagged for review. */
    boolean existsByUploadedByAndContentHashAndBookingIdNot(UUID uploadedBy, String contentHash, UUID bookingId);

    /** Photos past the retention cutoff whose file still exists, for the retention sweep. */
    List<ParcelPhotoEntity> findTop500ByTakenAtBeforeAndDeletedAtIsNull(Instant cutoff);
}
