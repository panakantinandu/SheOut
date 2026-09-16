package com.sheout.ratings;

import java.util.UUID;

/**
 * How often one tag has been chosen about one account within a window.
 * <p>
 * Counts only, never the individual ratings or who gave them - the same rule
 * the aggregate average follows. An operator needs to see that twelve people
 * said the same thing; knowing which twelve is what makes two-way rating
 * something to be afraid of.
 */
public record RatingTagCount(UUID ratedAccountId, RatingTag tag, long count) {
}
