package com.sheout.booking.internal;

import com.sheout.booking.BookingStatus;
import com.sheout.booking.BookingType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** Every trip in these states - for trip watch, which looks at the live ones. */
    List<BookingEntity> findByStatusIn(java.util.Collection<BookingStatus> statuses);

    List<BookingEntity> findByCustomerIdOrDriverIdOrderByCreatedAtDesc(UUID customerId, UUID driverId, Pageable pageable);

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

    /**
     * Holds this rider's "new booking" lock until the calling transaction
     * ends, so her booking requests are taken one at a time.
     * <p>
     * requestBooking checks that she has no live trip and then inserts one.
     * Two requests at the same moment - a double tap, a retry after a slow
     * answer, or two servers each taking one - both passed the check, and
     * eight parallel requests from one rider made eight live bookings, each
     * sent to a different partner. A Postgres advisory lock (not a row: there
     * is no row yet to lock) makes the second wait for the first to commit,
     * and then see it. It lives in the database, so it holds across servers.
     */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(hashtext('booking-request:' || cast(:customerId as text)))) held",
            nativeQuery = true)
    Integer lockNewBookingsFor(@Param("customerId") UUID customerId);

    /** The route-review queue: flagged by the route check, not yet looked at. */
    List<BookingEntity> findByRouteFlaggedAtIsNotNullAndRouteReviewedAtIsNullOrderByRouteFlaggedAtAsc();

    /** Backed by idx_bookings_driver - see BookingService.hasActiveTripAsDriver. */
    boolean existsByDriverIdAndStatusIn(UUID driverId, java.util.Collection<BookingStatus> statuses);

    /** How many trips she had completed by the time this one completed - this one included. */
    long countByDriverIdAndStatusAndCompletedAtLessThanEqual(UUID driverId, BookingStatus status, java.time.Instant completedAt);

    /** Completed trips a rider paid in full for (no promotion) between two moments. */
    long countByCustomerIdAndStatusAndPromoDiscountAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
            UUID customerId, BookingStatus status, java.math.BigDecimal promoDiscount,
            java.time.Instant from, java.time.Instant to);

    /** A live booking of this type for this rider - see BookingService.requestBooking. */
    boolean existsByCustomerIdAndTypeAndStatusIn(UUID customerId, BookingType type, java.util.Collection<BookingStatus> statuses);

    /** Searches that outlived the search budget - see StaleSearchReaper. */
    List<BookingEntity> findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(BookingStatus status, Instant cutoff);

    /** Searches begun before the cutoff - a re-opened booking's search began when it was re-opened. */
    List<BookingEntity> findTop100ByStatusAndSearchStartedAtBeforeOrderBySearchStartedAtAsc(BookingStatus status, Instant cutoff);

    /** A rider's oldest ended-and-unpaid trip. Backed by idx_bookings_customer_unsettled. */
    Optional<BookingEntity> findFirstByCustomerIdAndStatusAndPaymentSettledAtIsNullOrderByCompletedAtAsc(
            UUID customerId, BookingStatus status);

    /** A partner's newest ended-and-unpaid trip that ended after the cutoff. Backed by idx_bookings_driver_unsettled. */
    Optional<BookingEntity> findFirstByDriverIdAndStatusAndPaymentSettledAtIsNullAndCompletedAtAfterOrderByCompletedAtDesc(
            UUID driverId, BookingStatus status, Instant completedAfter);
}
