/**
 * Ratings module - how a rider and a partner say how the trip went.
 * <p>
 * Its own module rather than part of booking, because a rating is not a
 * stage of a trip. It happens after the trip is over, it may never happen
 * at all, it is written by each side about the other, and its lifetime and
 * its rules have nothing to do with the booking state machine. Putting it
 * inside booking would have given that module a second, unrelated set of
 * reasons to change.
 * <p>
 * It learns about completed trips the same way payments and notifications
 * do: by listening to BookingCompleted. Booking does not know this module
 * exists.
 * <p>
 * What it deliberately does NOT do is act on a bad rating. A low average
 * raises a flag for a person, in the same single review queue the
 * cancellation rate feeds, and stops there - a partner with three bad
 * scores may be careless or may have had three bad nights, and nothing here
 * can tell those apart.
 */
package com.sheout.ratings;
