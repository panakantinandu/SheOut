package com.sheout.campaigns;

public enum PromotionType {
    /** A credit balance every new rider is given on signup, spent across her trips. */
    SIGNUP_CREDIT,
    /** A percentage off each qualifying trip, optionally bounded per trip. */
    PERCENTAGE_DISCOUNT,
    /** A fixed amount off each qualifying trip. */
    FLAT_DISCOUNT,
    /**
     * A credit balance for a rider whose friend, signed up with her referral
     * code, has taken and paid for a first trip. Topped up once per such
     * friend, up to the programme's per-rider limit. See ReferralService.
     */
    REFERRAL_REWARD,
    /** A credit balance for the friend: signed up with a referral code, given once her first paid trip is done. */
    REFERRAL_WELCOME;

    /** Held as a rupee balance and spent across trips, rather than a discount on each. */
    public boolean isCredit() {
        return this == SIGNUP_CREDIT || this == REFERRAL_REWARD || this == REFERRAL_WELCOME;
    }
}
