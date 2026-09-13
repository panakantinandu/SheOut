package com.sheout.ratings.internal;

import com.sheout.auth.AccountRole;
import com.sheout.ratings.AggregateRating;
import com.sheout.ratings.Rating;
import com.sheout.ratings.RatingError;
import com.sheout.ratings.RatingSubmitted;
import com.sheout.ratings.RatingsApi;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Every rule about who may rate whom, when, and how often.
 * <p>
 * Entitlement is not checked against the booking at submit time. It is
 * decided once, when the trip completes, by writing a slot for each of the
 * two people who were on it - so this module never has to re-derive who was
 * on a trip, and a caller can never talk it into rating a booking they were
 * not part of. The absence of a slot is the refusal.
 */
@Service
public class RatingService implements RatingsApi {

    private static final Logger log = LoggerFactory.getLogger(RatingService.class);

    private static final int MIN_STARS = 1;
    private static final int MAX_STARS = 5;
    private static final int MAX_COMMENT = 500;

    private final RatingRepository repository;
    private final RatingWindow window;
    private final DomainEventPublisher eventPublisher;

    RatingService(RatingRepository repository, RatingWindow window, DomainEventPublisher eventPublisher) {
        this.repository = repository;
        this.window = window;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Opens the two slots for a completed trip.
     * <p>
     * Both sides, always, even though most will go unused - a slot is what
     * tells each app there is something to ask about, and deciding later who
     * "deserves" to be asked would mean re-reading the booking every time.
     * <p>
     * Silently does nothing if slots already exist. A redelivered event must
     * not reopen a window that has closed or wipe a rating already given.
     */
    @Transactional
    public void openSlotsFor(UUID bookingId, UUID customerId, UUID driverId, Instant completedAt) {
        if (driverId == null) {
            // A booking cannot reach COMPLETED without a driver, so this is
            // a corruption rather than a case - worth a line in the log
            // rather than a half-built pair of slots.
            log.warn("Booking {} completed with no driver - no rating slots opened", bookingId);
            return;
        }
        if (!repository.findByBookingId(bookingId).isEmpty()) {
            return;
        }

        Instant closesAt = window.closesAt(completedAt);
        try {
            repository.save(new RatingEntity(bookingId, customerId, driverId, AccountRole.CUSTOMER, closesAt));
            repository.save(new RatingEntity(bookingId, driverId, customerId, AccountRole.DRIVER, closesAt));
        } catch (DataIntegrityViolationException e) {
            // Two deliveries of the same event raced past the check above.
            // The unique index is the real guard; losing this race is fine.
            log.debug("Rating slots for booking {} already existed", bookingId);
        }
    }

    @Override
    @Transactional
    public Result<Rating, RatingError> submitRating(UUID bookingId, UUID raterAccountId, int stars, String comment) {
        if (stars < MIN_STARS || stars > MAX_STARS) {
            return Result.failure(RatingError.INVALID_RATING);
        }
        String trimmed = comment == null ? null : comment.trim();
        if (trimmed != null && trimmed.length() > MAX_COMMENT) {
            return Result.failure(RatingError.INVALID_RATING);
        }
        if (trimmed != null && trimmed.isEmpty()) {
            // An empty comment is no comment. Storing "" would make every
            // read have to know the difference.
            trimmed = null;
        }

        Optional<RatingEntity> found = repository.findByBookingIdAndRaterAccountId(bookingId, raterAccountId);
        if (found.isEmpty()) {
            // Covers all of: no such booking, the trip never completed, and
            // the caller was not on it. See RatingError.RATING_NOT_FOUND.
            return Result.failure(RatingError.RATING_NOT_FOUND);
        }
        RatingEntity slot = found.get();

        if (slot.isSubmitted()) {
            return Result.failure(RatingError.ALREADY_RATED);
        }
        Instant now = Instant.now();
        if (!now.isBefore(slot.getRateableUntil())) {
            return Result.failure(RatingError.RATING_WINDOW_CLOSED);
        }

        slot.submit(stars, trimmed, now);
        repository.save(slot);

        // Read back after the write so the average includes what was just
        // given. A listener told the old figure would flag, or fail to flag,
        // on a number that no longer exists.
        AggregateRating aggregate = getAggregateRating(slot.getRatedAccountId());
        if (aggregate.hasRatings()) {
            eventPublisher.publish(new RatingSubmitted(
                    bookingId,
                    slot.getRatedAccountId(),
                    // The rated account is on the other side from the rater.
                    slot.getRaterRole() == AccountRole.CUSTOMER ? AccountRole.DRIVER : AccountRole.CUSTOMER,
                    stars,
                    aggregate.averageStars(),
                    aggregate.totalRatings()));
        }
        return Result.success(toRating(slot));
    }

    @Override
    public AggregateRating getAggregateRating(UUID accountId) {
        return getAggregateRatings(List.of(accountId))
                .getOrDefault(accountId, AggregateRating.none());
    }

    @Override
    public Map<UUID, AggregateRating> getAggregateRatings(Collection<UUID> accountIds) {
        Map<UUID, AggregateRating> result = new HashMap<>();
        if (accountIds == null || accountIds.isEmpty()) {
            // An empty IN list is a SQL error on Postgres, not an empty
            // result - so it never reaches the query.
            return result;
        }
        for (UUID id : accountIds) {
            result.put(id, AggregateRating.none());
        }
        for (RatingRepository.AggregateRow row : repository.aggregateFor(accountIds)) {
            result.put(row.getAccountId(),
                    new AggregateRating(round(row.getAverageStars()), (int) row.getTotalRatings()));
        }
        return result;
    }

    @Override
    public List<Rating> findOpenFor(UUID accountId) {
        Instant now = Instant.now();
        return repository.findByRaterAccountIdAndStarsIsNullOrderByRateableUntilAsc(accountId).stream()
                .filter(slot -> now.isBefore(slot.getRateableUntil()))
                .map(RatingService::toRating)
                .toList();
    }

    @Override
    public Optional<Rating> findForBooking(UUID bookingId, UUID raterAccountId) {
        return repository.findByBookingIdAndRaterAccountId(bookingId, raterAccountId).map(RatingService::toRating);
    }

    @Override
    public Map<UUID, Rating> findForBookings(Collection<UUID> bookingIds, UUID raterAccountId) {
        if (bookingIds == null || bookingIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByRaterAccountIdAndBookingIdIn(raterAccountId, bookingIds).stream()
                .map(RatingService::toRating)
                .collect(Collectors.toMap(Rating::bookingId, Function.identity(), (a, b) -> a));
    }

    /**
     * Two decimal places, which is as much precision as a star average can
     * honestly claim. Anything longer reads as a measurement rather than an
     * average of a handful of opinions.
     */
    private static Double round(Double average) {
        return average == null ? null : Math.round(average * 100) / 100.0;
    }

    private static Rating toRating(RatingEntity entity) {
        return new Rating(
                entity.getId(),
                entity.getBookingId(),
                entity.getRaterAccountId(),
                entity.getRatedAccountId(),
                entity.getRaterRole(),
                entity.getStars(),
                entity.getComment(),
                entity.getSubmittedAt(),
                entity.getRateableUntil());
    }
}
