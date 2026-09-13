package com.sheout.ratings;

/**
 * What an account's ratings add up to.
 * <p>
 * averageStars is null when the account has never been rated, and that is
 * not the same as a low score. Collapsing the two into 0.0 would put a brand
 * new partner below everyone who has ever been rated badly, on the strength
 * of no evidence at all.
 */
public record AggregateRating(Double averageStars, int totalRatings) {

    public static AggregateRating none() {
        return new AggregateRating(null, 0);
    }

    public boolean hasRatings() {
        return totalRatings > 0 && averageStars != null;
    }
}
