package com.sheout.ratings;

/** Expected outcomes of submitting a rating - all normal states, none faults. */
public enum RatingError {

    /**
     * No such booking, OR it has no rating slot for this caller - which
     * covers a trip that never completed, and a caller who was not on it.
     * <p>
     * One value for all three on purpose, and the controller answers 404 to
     * all three, so a caller cannot probe booking ids for existence or
     * learn who was on a trip. The same rule chat and booking already
     * follow.
     */
    RATING_NOT_FOUND,

    /**
     * This side has already rated this trip.
     * <p>
     * Rejected rather than overwritten. A rating is what somebody thought
     * when the trip was fresh; letting it be rewritten later turns the
     * record into a running opinion and opens the obvious pressure - "change
     * your rating and I will change mine".
     */
    ALREADY_RATED,

    /**
     * The window has closed. See RatingWindow for why one exists at all.
     */
    RATING_WINDOW_CLOSED,

    /** Stars outside 1-5, or a comment past the length the column allows. */
    INVALID_RATING
}
