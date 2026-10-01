package com.sheout.booking.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface BookingEventRepository extends JpaRepository<BookingEventEntity, UUID> {

    List<BookingEventEntity> findByBookingIdOrderByAtAsc(UUID bookingId);
}
