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
                                String area, String websiteUrl,
                                /** For OTHER only: what she sells, in her words ("Homemade pickles"). Null otherwise. */
                                String customCategory) {
    }

    /**
     * A product or service. The price is for information only - nothing is
     * ever charged in the app for it. originalPrice is the price before a
     * discount, optional, and above displayPrice when given.
     */
    public record ProductDetails(String title, String description, BigDecimal displayPrice, BigDecimal originalPrice,
                                 boolean active, ProductTerms terms) {

        /** Without the practical terms: in stock, per piece, nothing else said. */
        public ProductDetails(String title, String description, BigDecimal displayPrice, BigDecimal originalPrice,
                              boolean active) {
            this(title, description, displayPrice, originalPrice, active, ProductTerms.defaults());
        }
    }

    // ------------------------------------------------------------ her own shop

    /** A place on the map with what she calls it - her parcel pickup point. */
    public record PickupPoint(String label, double lat, double lng) {
    }

    public record ProductImageView(UUID id, String url) {
    }

    public record ProductView(UUID id, String code, String title, String description, BigDecimal displayPrice,
                              BigDecimal originalPrice, boolean active, List<ProductImageView> images,
                              ProductTerms terms) {
    }

    /**
     * Where she stands on her monthly plan - see the marketplace's SellerPlan.
     * <ul>
     *   <li>NOT_STARTED - not approved yet; nothing to pay.</li>
     *   <li>FREE_MONTH - her first month, free.</li>
     *   <li>PAID - a paid month, with more than the pay window left.</li>
     *   <li>DUE_SOON - her month ends within the pay window: she can pay for the next.</li>
     *   <li>OVERDUE - her month has ended; still listed until hiddenFrom.</li>
     *   <li>LAPSED - hidden from customers until she pays.</li>
     * </ul>
     */
    public enum PlanState { NOT_STARTED, FREE_MONTH, PAID, DUE_SOON, OVERDUE, LAPSED }

    /** One month she paid for. period 1 is her first paid month; 0 is the old one-time fee. */
    public record PaidMonth(int period, BigDecimal amount, PaymentMethod method, Instant paidAt) {
    }

    /**
     * Her plan, as the app and the console show it. monthlyFee is what her
     * next month costs. freeUntil, paidUntil and hiddenFrom are null before
     * approval. canPayNow is the server's answer, so the app never offers a
     * payment that would be refused.
     */
    public record PlanView(BigDecimal monthlyFee, int freeMonths, PlanState state, Instant freeUntil, Instant paidUntil,
                           Instant hiddenFrom, Instant payFrom, boolean canPayNow, List<PaidMonth> history) {
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
            /** What she sells, in her words, when category is OTHER. */
            String customCategory,
            String contactPhone,
            String whatsappNumber,
            String area,
            String websiteUrl,
            SellerStatus status,
            String rejectionReason,
            String suspensionReason,
            Instant submittedAt,
            Instant activatedAt,
            PlanView plan,
            List<ProductView> products,
            int imagesUsed,
            int maxImagesPerSeller,
            int maxImagesPerProduct,
            int maxProducts,
            boolean canEdit,
            boolean canSubmit,
            /** Her account's ID check - a shop is not reviewed before it. */
            boolean accountVerified,
            /** Where her parcels are collected, if she has set it - null until then. */
            PickupPoint parcelPickup
    ) {
    }

    // ------------------------------------------------------------ the directory

    /** One product in the directory list. */
    public record ListingCard(UUID productId, String code, String title, BigDecimal displayPrice, BigDecimal originalPrice,
                              String imageUrl, UUID sellerId, String businessName, SellerCategory category,
                              String customCategory, String area,
                              /** So the grid can say "Out of stock" before anybody opens it. */
                              ProductTerms.Availability availability,
                              /** An example shop: marked as one, and nobody to contact. */
                              boolean sample) {
    }

    /**
     * A directory search "in your own words". mode AI: the model chose and
     * ordered these from real listings. mode EXACT: the model was not asked
     * or did not answer (reason says why), and these are the ordinary
     * keyword results instead, so a search never comes back broken.
     */
    public record SmartSearchResult(Mode mode, FallbackReason reason, List<ListingCard> items, int remainingToday) {
        public enum Mode { AI, EXACT }

        public enum FallbackReason { UNAVAILABLE, LIMIT_REACHED, FAILED }
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
    public record ProductDetail(UUID productId, String code, String title, String description, BigDecimal displayPrice,
                                BigDecimal originalPrice, List<String> imageUrls, UUID sellerId, String businessName,
                                SellerCategory category, String customCategory, String area, String websiteUrl, String contactPhone,
                                String whatsappNumber, List<ListingCard> moreFromSeller,
                                ProductTerms terms,
                                /** When she last changed it - "updated 3 days ago" says the stock line is fresh. */
                                java.time.Instant updatedAt,
                                /** An example shop: contactPhone and whatsappNumber are null, and the app says why. */
                                boolean sample) {
    }

    // ------------------------------------------------------------ the console

    public record SellerAdminRow(
            UUID id,
            UUID accountId,
            String businessName,
            SellerCategory category,
            String customCategory,
            SellerStatus status,
            String contactPhone,
            String whatsappNumber,
            String area,
            String websiteUrl,
            int productCount,
            int imageCount,
            Instant createdAt,
            Instant submittedAt,
            Instant reviewedAt,
            Instant activatedAt,
            Instant editedLiveAt,
            String rejectionReason,
            String suspensionReason,
            PlanView plan,
            /** An example shop, shown while sample shops are on. */
            boolean sample
    ) {
    }

    public record SellerAdminDetail(SellerAdminRow seller, List<ProductView> products) {
    }
}
