package com.sheout.campaigns;

public enum PromotionType {
    /** A credit balance every new rider is given on signup, spent across her trips. */
    SIGNUP_CREDIT,
    /** A percentage off each qualifying trip, optionally bounded per trip. */
    PERCENTAGE_DISCOUNT,
    /** A fixed amount off each qualifying trip. */
    FLAT_DISCOUNT
}
