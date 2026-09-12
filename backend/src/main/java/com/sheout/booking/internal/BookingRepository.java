package com.sheout.booking.internal;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

/**
 * JpaSpecificationExecutor is here for the paged, filtered search - see
 * BookingSpecs for why that is built as a Specification rather than as one
 * JPQL query with a nullable parameter per filter.
 */
interface BookingRepository extends JpaRepository<BookingEntity, UUID>, JpaSpecificationExecutor<BookingEntity> {

    List<BookingEntity> findByCustomerId(UUID customerId);

    List<BookingEntity> findByDriverId(UUID driverId);

    /** createdAt is the requestedAt the summary exposes - see BookingEntity's Javadoc. */
    List<BookingEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
