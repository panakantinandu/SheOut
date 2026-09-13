package com.sheout.ratings.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface RatingRepository extends JpaRepository<RatingEntity, UUID> {

    Optional<RatingEntity> findByBookingIdAndRaterAccountId(UUID bookingId, UUID raterAccountId);

    List<RatingEntity> findByBookingId(UUID bookingId);

    List<RatingEntity> findByRaterAccountIdAndBookingIdIn(UUID raterAccountId, Collection<UUID> bookingIds);

    /** Open slots for one person, soonest to close first - so a prompt asks about the one about to expire. */
    List<RatingEntity> findByRaterAccountIdAndStarsIsNullOrderByRateableUntilAsc(UUID raterAccountId);

    /**
     * Averages and counts for a set of accounts, in one query.
     * <p>
     * One method rather than a single-account version beside it, because a
     * second query doing the same arithmetic is a second place for the
     * arithmetic to drift. The single-account call passes a set of one.
     * <p>
     * COUNT is over stars rather than *, so an open slot never inflates
     * somebody's total. An account with no ratings simply has no row here,
     * which the service turns into AggregateRating.none() - the difference
     * between "never rated" and "rated badly" has to survive this far.
     */
    @Query("""
            select r.ratedAccountId as accountId,
                   avg(r.stars) as averageStars,
                   count(r.stars) as totalRatings
            from RatingEntity r
            where r.ratedAccountId in :accountIds and r.stars is not null
            group by r.ratedAccountId
            """)
    List<AggregateRow> aggregateFor(@Param("accountIds") Collection<UUID> accountIds);

    /** Projection for the aggregate query - names must match its aliases. */
    interface AggregateRow {
        UUID getAccountId();

        Double getAverageStars();

        long getTotalRatings();
    }
}
