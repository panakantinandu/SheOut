package com.sheout.marketplace;

/**
 * What a SheOut Seller sells - the six the app has always shown, each with
 * its own picture, and OTHER for anything else, which she names herself (see
 * SellerDetails.customCategory). Stored by name, so a value is never renamed
 * while rows carry it.
 */
public enum SellerCategory {
    FASHION_SAREE,
    BEAUTY_SERVICES,
    TAILORING,
    MEHANDI,
    GIFTS,
    ORNAMENTS,
    /** Anything else; the seller says what in her own words. */
    OTHER
}
