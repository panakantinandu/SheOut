package com.sheout.booking.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * The parcel limits and proof-of-delivery settings, from configuration so
 * none of them is a constant buried in code. Every default is the safe one:
 * photos required, receiver SMS off.
 */
@Component
public class ParcelRules {

    /**
     * What may not be sent, as codes the apps translate. Changing the list
     * means changing {@link #prohibitedItemsVersion} too, so every sender
     * agrees to the list actually in force.
     */
    public static final List<String> PROHIBITED_ITEMS = List.of(
            "CASH", "JEWELLERY", "ALCOHOL", "MEDICINES", "WEAPONS", "FLAMMABLES", "ILLEGAL",
            "LIVE_ANIMALS", "ORIGINAL_IDS", "CHEQUES_CARDS");

    private final double deliveryRadiusMetres;
    private final int maxWeightKg;
    private final BigDecimal maxDeclaredValue;
    private final String prohibitedItemsVersion;
    private final boolean photosRequired;
    private final Duration photoRetention;
    private final long maxPhotoBytes;

    public ParcelRules(
            @Value("${sheout.parcel.delivery-radius-metres:150}") double deliveryRadiusMetres,
            @Value("${sheout.parcel.max-weight-kg:5}") int maxWeightKg,
            @Value("${sheout.parcel.max-declared-value:5000}") BigDecimal maxDeclaredValue,
            @Value("${sheout.parcel.prohibited-items-version:2026-10-11}") String prohibitedItemsVersion,
            @Value("${sheout.parcel.photos-required:true}") boolean photosRequired,
            @Value("${sheout.parcel.photo-retention-days:90}") long photoRetentionDays,
            @Value("${sheout.parcel.max-photo-bytes:3145728}") long maxPhotoBytes) {
        this.deliveryRadiusMetres = deliveryRadiusMetres;
        this.maxWeightKg = maxWeightKg;
        this.maxDeclaredValue = maxDeclaredValue;
        this.prohibitedItemsVersion = prohibitedItemsVersion;
        this.photosRequired = photosRequired;
        this.photoRetention = Duration.ofDays(photoRetentionDays);
        this.maxPhotoBytes = maxPhotoBytes;
    }

    /** For tests and hand-built services: the defaults above. */
    public static ParcelRules defaults() {
        return new ParcelRules(150, 5, new BigDecimal("5000"), "2026-10-11", true, 90, 3_145_728);
    }

    public double deliveryRadiusMetres() { return deliveryRadiusMetres; }
    public int maxWeightKg() { return maxWeightKg; }
    public BigDecimal maxDeclaredValue() { return maxDeclaredValue; }
    public String prohibitedItemsVersion() { return prohibitedItemsVersion; }
    public boolean photosRequired() { return photosRequired; }
    public Duration photoRetention() { return photoRetention; }
    public long maxPhotoBytes() { return maxPhotoBytes; }
}
