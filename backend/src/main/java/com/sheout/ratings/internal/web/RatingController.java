package com.sheout.ratings.internal.web;

import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.ratings.AggregateRating;
import com.sheout.ratings.Rating;
import com.sheout.ratings.RatingError;
import com.sheout.ratings.RatingTag;
import com.sheout.ratings.RatingsApi;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Rating a trip, and reading what an account's ratings come to.
 * <p>
 * Who the caller is always comes from the token. There is no endpoint here
 * that lets a client say whose rating this is, or whom it is about.
 */
@RestController
@RequestMapping("/api/v1/ratings")
public class RatingController {

    /** A page of history is 20 rows; this leaves room without letting one request ask about everything. */
    private static final int MAX_BOOKING_LOOKUP = 100;

    private final RatingsApi ratingsApi;

    RatingController(RatingsApi ratingsApi) {
        this.ratingsApi = ratingsApi;
    }

    /**
     * Trips the caller can still rate, soonest to close first.
     * <p>
     * What both apps prompt from, so neither has to work out for itself
     * which completed trips are still open - the window lives on the server
     * and this is how a client learns about it.
     */
    @GetMapping("/pending")
    public ResponseEntity<List<Rating>> pending() {
        CurrentAccount caller = requireAuthenticated();
        return ResponseEntity.ok(ratingsApi.findOpenFor(caller.accountId()));
    }

    /**
     * The caller's own slots across several bookings at once.
     * <p>
     * Exists so a history list can mark a page of trips in one request
     * rather than one per row - the same reason the aggregate query takes a
     * set. Bookings the caller has no slot for are simply absent, which is
     * what "there is nothing to rate here" looks like.
     */
    @GetMapping("/bookings")
    public ResponseEntity<List<Rating>> forBookings(@RequestParam(name = "bookingId") List<UUID> bookingIds) {
        CurrentAccount caller = requireAuthenticated();
        if (bookingIds.size() > MAX_BOOKING_LOOKUP) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request",
                    "Ask about at most " + MAX_BOOKING_LOOKUP + " bookings at a time");
        }
        return ResponseEntity.ok(List.copyOf(
                ratingsApi.findForBookings(bookingIds, caller.accountId()).values()));
    }

    /**
     * The quick reasons this caller may be offered, by star rating.
     * <p>
     * Served rather than built into each app, so the words a rider taps, the
     * words a partner taps and the words an operator reads in the console are
     * one list in one place. A rider is never sent the partner's list: they
     * are different questions, and the wrong one reads as nonsense.
     * <p>
     * An app that cannot reach this simply shows no tags. Rating is a tap on
     * a star and must never depend on a second request succeeding.
     */
    @GetMapping("/tags")
    public ResponseEntity<TagCatalogue> tags() {
        CurrentAccount caller = requireAuthenticated();
        return ResponseEntity.ok(new TagCatalogue(
                toOptions(RatingTag.offeredTo(caller.role(), 5)),
                toOptions(RatingTag.offeredTo(caller.role(), 1))));
    }

    /** The caller's own slot for one booking: whether they can rate it, and what they said if they have. */
    @GetMapping("/bookings/{bookingId}")
    public ResponseEntity<Rating> forBooking(@PathVariable UUID bookingId) {
        CurrentAccount caller = requireAuthenticated();
        return ratingsApi.findForBooking(bookingId, caller.accountId())
                .map(ResponseEntity::ok)
                .orElseThrow(() -> ApiException.notFound("There is nothing to rate for this booking"));
    }

    @PostMapping("/bookings/{bookingId}")
    public ResponseEntity<Rating> submit(@PathVariable UUID bookingId,
                                          @Valid @RequestBody SubmitRatingRequest request) {
        CurrentAccount caller = requireAuthenticated();
        Result<Rating, RatingError> result = ratingsApi.submitRating(
                bookingId, caller.accountId(), request.stars(), request.comment(), request.tags());
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(result.value());
    }

    /**
     * A partner's public score, for the card a rider sees while the trip is
     * running.
     * <p>
     * Only the average and the count are returned - never the individual
     * ratings, and never who gave them. A partner able to work out which
     * rider left which score is the whole problem with two-way rating, and
     * this is the endpoint that would leak it.
     */
    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<AggregateRating> forAccount(@PathVariable UUID accountId) {
        requireAuthenticated();
        return ResponseEntity.ok(ratingsApi.getAggregateRating(accountId));
    }

    /** The caller's own score, for their dashboard. */
    @GetMapping("/me")
    public ResponseEntity<AggregateRating> mine() {
        CurrentAccount caller = requireAuthenticated();
        return ResponseEntity.ok(ratingsApi.getAggregateRating(caller.accountId()));
    }

    private CurrentAccount requireAuthenticated() {
        return CurrentAccountContext.get().orElseThrow(() -> ApiException.unauthorized("Authentication required"));
    }

    private ApiException toApiException(RatingError error) {
        return switch (error) {
            // 404 for "no such booking", "not completed" and "not yours"
            // alike - see RatingError.RATING_NOT_FOUND.
            case RATING_NOT_FOUND -> ApiException.notFound("There is nothing to rate for this booking");
            case ALREADY_RATED -> new ApiException(HttpStatus.CONFLICT, "ALREADY_RATED",
                    "You have already rated this trip.");
            case RATING_WINDOW_CLOSED -> new ApiException(HttpStatus.CONFLICT, "RATING_WINDOW_CLOSED",
                    "This trip is too old to rate now.");
            case INVALID_RATING -> new ApiException(HttpStatus.BAD_REQUEST, "Bad Request",
                    "Choose between 1 and 5 stars.");
            // Only reachable from a client out of step with the catalogue, or
            // a request made by hand - see RatingError.INVALID_RATING_TAG.
            case INVALID_RATING_TAG -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RATING_TAG",
                    "Those quick reasons do not go with this rating.");
        };
    }

    private static List<TagOption> toOptions(List<RatingTag> tags) {
        return tags.stream().map(tag -> new TagOption(tag, tag.label())).toList();
    }

    /** What to offer for a high rating, and what to offer for a low one. */
    public record TagCatalogue(List<TagOption> positive, List<TagOption> negative) {
    }

    /** The stored code and the words shown beside it. */
    public record TagOption(RatingTag code, String label) {
    }

    /**
     * Stars are required and the comment is not. Rating without writing
     * anything has to stay a single tap, or the only people who rate are the
     * furious ones and the average stops meaning anything.
     */
    public record SubmitRatingRequest(
            @NotNull @Min(1) @Max(5) Integer stars,
            @Size(max = 500) String comment,
            /** Optional, and absent for most ratings. An unknown name here is a 400 from Jackson, not a silent drop. */
            Set<RatingTag> tags
    ) {
    }
}
