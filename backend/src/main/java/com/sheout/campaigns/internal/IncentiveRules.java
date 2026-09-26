package com.sheout.campaigns.internal;

import com.sheout.campaigns.IncentiveType;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * What each kind of partner incentive pays on one trip - one small rule per
 * type, so a new shape of incentive is a new rule here, not a rebuild.
 * <p>
 * Every rule is paid on top of her normal share of the fare, out of
 * SheOut's margin: nothing here comes out of what the rider pays or what
 * the partner already earns.
 */
final class IncentiveRules {

    /** The facts about one paid trip that a rule may use. */
    record Trip(BigDecimal partnerShare, long tripOrdinal) {
    }

    @FunctionalInterface
    interface Rule {
        /** The amount this incentive pays on this trip, before the budget; zero for none. */
        BigDecimal amountFor(DriverIncentiveEntity incentive, Trip trip);
    }

    private static final Map<IncentiveType, Rule> RULES = new EnumMap<>(IncentiveType.class);

    static {
        // A fixed bonus per qualifying trip.
        RULES.put(IncentiveType.PER_TRIP_BONUS, (incentive, trip) -> incentive.getValue());
        // Each qualifying trip earns her at least `value`: the shortfall is topped up.
        RULES.put(IncentiveType.MINIMUM_EARNINGS_GUARANTEE,
                (incentive, trip) -> incentive.getValue().subtract(trip.partnerShare()).max(BigDecimal.ZERO));
    }

    private IncentiveRules() {
    }

    /** Qualifies (within her first N trips, when the incentive has such a limit) and what it pays. */
    static BigDecimal amountFor(DriverIncentiveEntity incentive, Trip trip) {
        if (incentive.getFirstNTrips() != null && trip.tripOrdinal() > incentive.getFirstNTrips()) {
            return BigDecimal.ZERO;
        }
        Rule rule = RULES.get(incentive.getType());
        return rule == null ? BigDecimal.ZERO : rule.amountFor(incentive, trip).max(BigDecimal.ZERO);
    }
}
