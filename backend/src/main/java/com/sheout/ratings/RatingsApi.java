package com.sheout.ratings;

import com.sheout.sharedkernel.Result;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface RatingsApi {

    /**
     * Records what one side of a trip thought of the other.
     * <p>
     * The caller is identified by raterAccountId, which comes from the
     * token, never from the request body. Whether that account is entitled
     * to rate this booking is decided here, from the slot created when the
     * trip completed - so there is no version of this that trusts a client
     * about who it is rating.
     */
    Result<Rating, RatingError> submitRating(UUID bookingId, UUID raterAccountId, int stars, String comment);

    /** What this account's ratings add up to. Never null; see AggregateRating.none(). */
    AggregateRating getAggregateRating(UUID accountId);

    /**
     * The same figure for many accounts at once.
     * <p>
     * Exists for the operator console, which renders a page of accounts and
     * would otherwise ask one query per row. Accounts with no ratings are
     * present in the result with AggregateRating.none() rather than absent,
     * so a caller never has to decide what a missing key meant.
     */
    Map<UUID, AggregateRating> getAggregateRatings(Collection<UUID> accountIds);

    /** Trips this account can still rate, soonest to close first. What a client prompts from. */
    List<Rating> findOpenFor(UUID accountId);

    /** This account's slot for one booking, if it has one. Lets a history row say "rated" without a second call. */
    Optional<Rating> findForBooking(UUID bookingId, UUID raterAccountId);

    /**
     * Every slot this account holds across the given bookings, keyed by
     * booking id. For a history list, which needs to mark several trips at
     * once and should not ask per row.
     */
    Map<UUID, Rating> findForBookings(Collection<UUID> bookingIds, UUID raterAccountId);
}
