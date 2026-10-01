package com.sheout.booking.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface TripAlertRepository extends JpaRepository<TripAlertEntity, UUID> {

    List<TripAlertEntity> findByResolvedAtIsNullOrderByRaisedAtAsc();

    List<TripAlertEntity> findByBookingIdOrderByRaisedAtDesc(UUID bookingId);

    /** The latest alert of this kind on this trip that a person marked checked. */
    java.util.Optional<TripAlertEntity> findFirstByBookingIdAndKindAndResolvedByIsNotNullOrderByResolvedAtDesc(
            UUID bookingId, com.sheout.booking.TripAlertsApi.Kind kind);
}
