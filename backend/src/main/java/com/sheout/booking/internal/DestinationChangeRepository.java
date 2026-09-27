package com.sheout.booking.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DestinationChangeRepository extends JpaRepository<DestinationChangeEntity, UUID> {

    /** The most recent request on a trip - the one both screens show. */
    Optional<DestinationChangeEntity> findFirstByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    List<DestinationChangeEntity> findByBookingIdAndStatus(UUID bookingId, DestinationChangeEntity.Status status);

    long countByBookingIdAndStatusIn(UUID bookingId, Collection<DestinationChangeEntity.Status> statuses);
}
