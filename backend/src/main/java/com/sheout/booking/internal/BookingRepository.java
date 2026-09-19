package com.sheout.booking.internal;

import com.sheout.booking.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

    /**
     * The row, locked until the calling transaction ends.
     * <p>
     * For startTrip's pickup-code check, which reads an attempt count,
     * compares, and writes it back. Without the lock that is a lost update:
     * forty parallel wrong guesses against one booking were all read as
     * attempt zero, ten of them were evaluated as fresh guesses against a
     * limit of five, and the count saved was five. The lock makes each guess
     * wait for the previous one's count.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BookingEntity> findLockedById(UUID id);

    /** A rider's oldest ended-and-unpaid trip. Backed by idx_bookings_customer_unsettled. */
    Optional<BookingEntity> findFirstByCustomerIdAndStatusAndPaymentSettledAtIsNullOrderByCompletedAtAsc(
            UUID customerId, BookingStatus status);

    /** A partner's newest ended-and-unpaid trip that ended after the cutoff. Backed by idx_bookings_driver_unsettled. */
    Optional<BookingEntity> findFirstByDriverIdAndStatusAndPaymentSettledAtIsNullAndCompletedAtAfterOrderByCompletedAtDesc(
            UUID driverId, BookingStatus status, Instant completedAfter);
}
