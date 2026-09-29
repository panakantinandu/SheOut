package com.sheout.marketplace;

import com.sheout.payments.PaymentMethod;
import com.sheout.payments.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What the marketplace hands out. Grouped in one file because they are one
 * vocabulary: what she types in, what she sees of her own shop, what a
 * customer sees in the directory, and what an operator reviews.
 */
public final class MarketplaceViews {

    private MarketplaceViews() {
    }

    // ------------------------------------------------------------ input

    /**
     * Her shop: what it is called, what she sells, how customers reach her,
     * and the area she works from (optional free text, filterable in the
     * directory).
     */
    public record SellerDetails(String businessName, SellerCategory category, String contactPhone, String whatsappNumber,
                                String area) {
    }

    /** A product or service. The price is for information only - nothing is ever charged in the app for it. */
    public record ProductDetails(String title, String description, BigDecimal displayPrice, boolean active) {
    }

    // ------------------------------------------------------------ her own shop

    public record ProductImageView(UUID id, String url) {
    }

    public record ProductView(UUID id, String title, String description, BigDecimal displayPrice, boolean active,
                              List<ProductImageView> images) {
    }

    /** Where her listing fee stands. status, method and paidAt are null until she has started paying. */
    public record ListingFeeView(BigDecimal amount, PaymentStatus status, PaymentMethod method, Instant paidAt) {
    }

    /**
     * Her shop as she manages it. canEdit and canSubmit are the server's
     * answer, so the app never offers what would be refused; the limits are
     * the configured ones, so the app can say "12 of 40 photos".
     */
    public record SellerView(
            UUID id,
            String businessName,
            SellerCategory category,
            String contactPhone,
            String whatsappNumber,
            String area,
            SellerStatus status,
            String rejectionReason,
            String suspensionReason,
            Instant submittedAt,
            Instant activatedAt,
            ListingFeeView listingFee,
            List<ProductView> products,
            int imagesUsed,
            int maxImagesPerSeller,
            int maxImagesPerProduct,
            int maxProducts,
            boolean canEdit,
            boolean canSubmit,
            /** Her account's ID check - a shop is not reviewed before it. */
            boolean accountVerified
    ) {
    }

    // ------------------------------------------------------------ the directory

    /** One product in the directory list. */
    public record ListingCard(UUID productId, String title, BigDecimal displayPrice, String imageUrl,
                              UUID sellerId, String businessName, SellerCategory category, String area) {
    }

    /**
     * What a customer narrowed the directory to. Every part is optional: an
     * empty category set is every category, a null bound is no bound, and a
     * blank keyword or area matches everything. The keyword looks at the
     * product's title and description and the shop's name; the area at the
     * area the seller gave.
     */
    public record DirectoryFilter(Set<SellerCategory> categories, String keyword, BigDecimal minPrice,
                                  BigDecimal maxPrice, String area) {
        public DirectoryFilter {
            categories = categories == null ? Set.of() : Set.copyOf(categories);
        }
    }

    /**
     * One product, as a customer sees it. The contact numbers are the whole
     * point of the directory: they are shown to any signed-in rider, the way
     * a shop's number is painted on its signboard.
     */
    public record ProductDetail(UUID productId, String title, String description, BigDecimal displayPrice,
                                List<String> imageUrls, UUID sellerId, String businessName, SellerCategory category,
                                String area, String contactPhone, String whatsappNumber, List<ListingCard> moreFromSeller) {
    }

    // ------------------------------------------------------------ the console

    public record SellerAdminRow(
            UUID id,
            UUID accountId,
            String businessName,
            SellerCategory category,
            SellerStatus status,
            String contactPhone,
            String whatsappNumber,
            String area,
            int productCount,
            int imageCount,
            Instant createdAt,
            Instant submittedAt,
            Instant reviewedAt,
            Instant activatedAt,
            Instant editedLiveAt,
            String rejectionReason,
            String suspensionReason,
            ListingFeeView listingFee
    ) {
    }

    public record SellerAdminDetail(SellerAdminRow seller, List<ProductView> products) {
    }
}
