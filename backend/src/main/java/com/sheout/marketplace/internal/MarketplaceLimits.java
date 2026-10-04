package com.sheout.marketplace.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * The fee and the caps, all environment settings (see application.yml).
 * <p>
 * The photo caps bound what one shop can put in storage. 40 across a shop is
 * generous for a mehandi artist or a tailor - a handful of services, a few
 * photos each - but tight for a saree seller with a catalogue of thirty:
 * she would get barely one photo a saree. Raise SELLER_MAX_IMAGES_PER_SELLER
 * if real sellers run into it; nothing else depends on the number.
 */
@Component
class MarketplaceLimits {

    private final BigDecimal monthlyFee;
    private final int freeMonths;
    private final java.time.Duration payWindow;
    private final java.time.Duration grace;
    private final java.util.List<Integer> reminderDays;
    private final int maxImagesPerSeller;
    private final int maxImagesPerProduct;
    private final int maxProducts;

    MarketplaceLimits(@Value("${sheout.marketplace.monthly-fee:499}") BigDecimal monthlyFee,
                      @Value("${sheout.marketplace.free-months:1}") int freeMonths,
                      @Value("${sheout.marketplace.pay-window-days:7}") int payWindowDays,
                      @Value("${sheout.marketplace.grace-days:3}") int graceDays,
                      @Value("${sheout.marketplace.reminder-days:7,3,1}") java.util.List<Integer> reminderDays,
                      @Value("${sheout.marketplace.max-images-per-seller:40}") int maxImagesPerSeller,
                      @Value("${sheout.marketplace.max-images-per-product:8}") int maxImagesPerProduct,
                      @Value("${sheout.marketplace.max-products:100}") int maxProducts) {
        this.monthlyFee = monthlyFee;
        this.freeMonths = Math.max(0, freeMonths);
        this.payWindow = java.time.Duration.ofDays(Math.max(1, payWindowDays));
        this.grace = java.time.Duration.ofDays(Math.max(0, graceDays));
        // Largest first: 7 days, then 3, then 1.
        this.reminderDays = reminderDays.stream().filter(d -> d > 0).distinct()
                .sorted(java.util.Comparator.reverseOrder()).toList();
        this.maxImagesPerSeller = maxImagesPerSeller;
        this.maxImagesPerProduct = maxImagesPerProduct;
        this.maxProducts = maxProducts;
    }

    /** Rupees a month, after the free months. */
    BigDecimal monthlyFee() { return monthlyFee; }
    /** Free months from the day a person approves her shop. */
    int freeMonths() { return freeMonths; }
    /** How long before her month ends she may pay for the next one. */
    java.time.Duration payWindow() { return payWindow; }
    /** How long after her month ends her shop stays listed while she pays. */
    java.time.Duration grace() { return grace; }
    /** Days before her month ends that she is reminded, largest first. */
    java.util.List<Integer> reminderDays() { return reminderDays; }
    int maxImagesPerSeller() { return maxImagesPerSeller; }
    int maxImagesPerProduct() { return maxImagesPerProduct; }
    int maxProducts() { return maxProducts; }
}
