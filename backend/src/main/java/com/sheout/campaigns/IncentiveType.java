package com.sheout.campaigns;

public enum IncentiveType {
    /** A fixed bonus on each qualifying trip. */
    PER_TRIP_BONUS,
    /** Each qualifying trip earns her at least this much; the shortfall is topped up. */
    MINIMUM_EARNINGS_GUARANTEE,
    /**
     * Paid once to a partner whose friend, signed up with her referral code,
     * has driven and been paid for a first trip. Not paid per trip - see
     * ReferralService, and IncentiveRules, which has no rule for it.
     */
    REFERRAL_REWARD,
    /** Paid once to the friend, on that first paid trip. */
    REFERRAL_WELCOME
}
