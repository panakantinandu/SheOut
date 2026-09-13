package com.sheout.users.internal;

import com.sheout.users.TrustStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * When an account is worth a person's attention.
 * <p>
 * One policy covering both signals rather than one per signal, because
 * there is one queue and one flag. Cancelling too often and being rated
 * badly are two ways of arriving at the same question - is something wrong
 * here? - and an operator should get one row with both numbers on it, not
 * the same account twice on two lists.
 * <p>
 * Every threshold is configurable, because where these lines sit is a
 * business judgement that will move with experience, not a constant.
 * <p>
 * Nothing here ever acts. Crossing a line flags an account for review and
 * does nothing else: no block, no throttle, no fee. That is the same rule
 * driver verification follows, and it matters more here, because both
 * signals have innocent explanations that the number cannot distinguish
 * from the guilty one. A rider who cancels a lot may be dodging fares or
 * may be repeatedly abandoned by partners who never arrive. A partner rated
 * badly three times may be careless or may have had three bad nights.
 * Someone reading the reasons can tell; an arithmetic rule cannot.
 */
@Component
public class TrustPolicy {

    private static final Logger log = LoggerFactory.getLogger(TrustPolicy.class);

    private final double cancellationThreshold;
    private final int minimumBookings;
    private final double ratingThreshold;
    private final int minimumRatings;

    TrustPolicy(
            @Value("${sheout.users.cancellation-rate-warning-threshold:0.4}") double cancellationThreshold,
            @Value("${sheout.users.cancellation-minimum-bookings:5}") int minimumBookings,
            @Value("${sheout.users.rating-review-threshold:3.5}") double ratingThreshold,
            @Value("${sheout.users.rating-minimum-count:5}") int minimumRatings) {
        this.cancellationThreshold = cancellationThreshold;
        this.minimumBookings = minimumBookings;
        this.ratingThreshold = ratingThreshold;
        this.minimumRatings = minimumRatings;
        log.info("Trust review thresholds: cancellation rate above {} after {} bookings; "
                        + "average rating below {} after {} ratings",
                cancellationThreshold, minimumBookings, ratingThreshold, minimumRatings);
    }

    /**
     * Why this account should be reviewed, or empty if it should not.
     * <p>
     * Returns a sentence rather than a boolean because the sentence is what
     * an operator reads, and building it here keeps the numbers and the
     * words that describe them in one place. Both reasons appear when both
     * apply - an account that cancels constantly AND is rated badly is a
     * different case from one that only does one of those, and collapsing
     * them to whichever tripped last would hide that.
     */
    public Optional<String> reviewReason(TrustStats stats) {
        List<String> reasons = new ArrayList<>();
        if (cancelsTooOften(stats)) {
            reasons.add("cancelled %d of %d bookings (%d%%), above the %d%% threshold".formatted(
                    stats.totalCancellations(),
                    stats.totalBookings(),
                    stats.cancellationRatePercent(),
                    Math.round(cancellationThreshold * 100)));
        }
        if (ratedTooLow(stats)) {
            reasons.add("rated %.2f across %d ratings, below %.1f".formatted(
                    stats.averageStars(), stats.totalRatings(), ratingThreshold));
        }
        if (reasons.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(capitalise(String.join("; ", reasons)));
    }

    /**
     * The minimum-bookings floor is not a nicety, it is what stops the
     * feature being unfair on its first day: without it, one cancellation on
     * a first-ever booking is a rate of 100%, and every new user who changed
     * her mind once would be flagged. Nobody's second trip should start with
     * an open case against her.
     */
    private boolean cancelsTooOften(TrustStats stats) {
        if (stats.totalBookings() < minimumBookings) {
            return false;
        }
        return stats.cancellationRate() > cancellationThreshold;
    }

    /**
     * The same floor, for the same reason. One furious rider on somebody's
     * first night out is an anecdote, not a pattern, and a partner should
     * not have a case opened against her over it.
     */
    private boolean ratedTooLow(TrustStats stats) {
        if (!stats.hasRatings() || stats.totalRatings() < minimumRatings) {
            return false;
        }
        return stats.averageStars() < ratingThreshold;
    }

    private static String capitalise(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    public double cancellationThreshold() {
        return cancellationThreshold;
    }

    public double ratingThreshold() {
        return ratingThreshold;
    }
}
