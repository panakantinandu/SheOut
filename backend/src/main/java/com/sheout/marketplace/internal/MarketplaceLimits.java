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

    private final BigDecimal listingFee;
    private final int maxImagesPerSeller;
    private final int maxImagesPerProduct;
    private final int maxProducts;

    MarketplaceLimits(@Value("${sheout.marketplace.listing-fee:299}") BigDecimal listingFee,
                      @Value("${sheout.marketplace.max-images-per-seller:40}") int maxImagesPerSeller,
                      @Value("${sheout.marketplace.max-images-per-product:8}") int maxImagesPerProduct,
                      @Value("${sheout.marketplace.max-products:100}") int maxProducts) {
        this.listingFee = listingFee;
        this.maxImagesPerSeller = maxImagesPerSeller;
        this.maxImagesPerProduct = maxImagesPerProduct;
        this.maxProducts = maxProducts;
    }

    BigDecimal listingFee() { return listingFee; }
    int maxImagesPerSeller() { return maxImagesPerSeller; }
    int maxImagesPerProduct() { return maxImagesPerProduct; }
    int maxProducts() { return maxProducts; }
}
