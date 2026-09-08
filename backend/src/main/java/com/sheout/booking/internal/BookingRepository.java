package com.sheout.booking.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface BookingRepository extends JpaRepository<BookingEntity, UUID> {

    List<BookingEntity> findByCustomerId(UUID customerId);

    List<BookingEntity> findByDriverId(UUID driverId);
}
