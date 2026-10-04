package com.sheout.marketplace.internal;

import com.sheout.marketplace.MarketplaceViews.ListingCard;
import com.sheout.marketplace.MarketplaceViews.PaidMonth;
import com.sheout.marketplace.MarketplaceViews.PlanView;
import com.sheout.marketplace.MarketplaceViews.ProductImageView;
import com.sheout.marketplace.MarketplaceViews.ProductView;
import com.sheout.marketplace.MarketplaceViews.SellerAdminRow;
import com.sheout.marketplace.MarketplaceViews.SellerView;
import com.sheout.marketplace.SellerStatus;
import com.sheout.payments.PaymentApi;
import com.sheout.sharedkernel.storage.DocumentStorage;
import com.sheout.users.CustomerProfileApi;
import com.sheout.users.CustomerProfileSummary;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Turns the marketplace's rows into what the app and the console are shown. */
@Component
class ShopViews {

    private final ProductRepository products;
    private final ProductImageRepository images;
    private final DocumentStorage documentStorage;
    private final PaymentApi paymentApi;
    private final CustomerProfileApi customerProfiles;
    private final MarketplaceLimits limits;
    private final ProductEnquiries enquiries;

    ShopViews(ProductRepository products, ProductImageRepository images, DocumentStorage documentStorage,
              PaymentApi paymentApi, CustomerProfileApi customerProfiles, MarketplaceLimits limits,
              ProductEnquiries enquiries) {
        this.enquiries = enquiries;
        this.products = products;
        this.images = images;
        this.documentStorage = documentStorage;
        this.paymentApi = paymentApi;
        this.customerProfiles = customerProfiles;
        this.limits = limits;
    }

    boolean accountVerified(UUID accountId) {
        return customerProfiles.findByAccountId(accountId).map(CustomerProfileSummary::verified).orElse(false);
    }

    SellerView sellerView(SellerProfileEntity seller) {
        List<ProductEntity> own = products.findBySellerIdOrderByCreatedAtAsc(seller.getId());
        List<ProductView> productViews = productViews(own, enquiries.countsOn(own.stream().map(ProductEntity::getId).toList(),
                java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"))));
        int imagesUsed = productViews.stream().mapToInt(p -> p.images().size()).sum();
        boolean verified = accountVerified(seller.getAccountId());
        boolean hasPhotographedProduct = productViews.stream().anyMatch(p -> p.active() && !p.images().isEmpty());
        return new SellerView(
                seller.getId(),
                seller.getBusinessName(),
                seller.getCategory(),
                seller.getCustomCategory(),
                seller.getContactPhone(),
                seller.getWhatsappNumber(),
                seller.getArea(),
                seller.getWebsiteUrl(),
                seller.getStatus(),
                seller.getRejectionReason(),
                seller.getSuspensionReason(),
                seller.getSubmittedAt(),
                seller.getActivatedAt(),
                plan(seller),
                productViews,
                imagesUsed,
                limits.maxImagesPerSeller(),
                limits.maxImagesPerProduct(),
                limits.maxProducts(),
                seller.editable(),
                seller.submittable() && verified && hasPhotographedProduct,
                verified,
                pickup(seller));
    }

    SellerAdminRow adminRow(SellerProfileEntity seller) {
        return new SellerAdminRow(
                seller.getId(),
                seller.getAccountId(),
                seller.getBusinessName(),
                seller.getCategory(),
                seller.getCustomCategory(),
                seller.getStatus(),
                seller.getContactPhone(),
                seller.getWhatsappNumber(),
                seller.getArea(),
                seller.getWebsiteUrl(),
                (int) products.countBySellerId(seller.getId()),
                (int) images.countBySellerId(seller.getId()),
                seller.getCreatedAt(),
                seller.getSubmittedAt(),
                seller.getReviewedAt(),
                seller.getActivatedAt(),
                seller.getEditedLiveAt(),
                seller.getRejectionReason(),
                seller.getSuspensionReason(),
                plan(seller),
                seller.isSample());
    }

    /** Her parcel pickup point, or null when she has not set one. */
    static com.sheout.marketplace.MarketplaceViews.PickupPoint pickup(SellerProfileEntity seller) {
        if (seller.getParcelPickupLat() == null || seller.getParcelPickupLng() == null) {
            return null;
        }
        return new com.sheout.marketplace.MarketplaceViews.PickupPoint(seller.getParcelPickupLabel(),
                seller.getParcelPickupLat(), seller.getParcelPickupLng());
    }

    /** Her monthly plan as it stands now, with every month she has paid for. */
    PlanView plan(SellerProfileEntity seller) {
        Instant now = Instant.now();
        Instant paidUntil = seller.getPaidUntil();
        List<PaidMonth> history = paymentApi.paidListingFees(seller.getId()).stream()
                .map(p -> new PaidMonth(p.period(), p.amount(), p.method(), p.paidAt()))
                .toList();
        return new PlanView(
                limits.monthlyFee(),
                limits.freeMonths(),
                SellerPlan.state(seller, limits, now),
                seller.getFreeUntil(),
                paidUntil,
                paidUntil == null ? null : paidUntil.plus(limits.grace()),
                paidUntil == null ? null : paidUntil.minus(limits.payWindow()),
                SellerPlan.canPay(seller, limits, now),
                history);
    }

    List<ProductView> productViews(List<ProductEntity> list) {
        return productViews(list, Map.of());
    }

    List<ProductView> productViews(List<ProductEntity> list, Map<UUID, Integer> enquiriesToday) {
        Map<UUID, List<ProductImageEntity>> byProduct = imagesOf(list.stream().map(ProductEntity::getId).toList());
        return list.stream().map(p -> new ProductView(
                p.getId(), p.getCode(), p.getTitle(), p.getDescription(), p.getDisplayPrice(), p.getOriginalPrice(), p.isActive(),
                byProduct.getOrDefault(p.getId(), List.of()).stream()
                        .map(i -> new ProductImageView(i.getId(), url(i.getStorageKey())))
                        .toList(),
                p.getTerms(), enquiriesToday.getOrDefault(p.getId(), 0))).toList();
    }

    List<ListingCard> cards(List<ProductEntity> list, Map<UUID, SellerProfileEntity> sellers) {
        Map<UUID, List<ProductImageEntity>> byProduct = imagesOf(list.stream().map(ProductEntity::getId).toList());
        return list.stream()
                .filter(p -> sellers.containsKey(p.getSellerId()))
                .map(p -> {
                    SellerProfileEntity s = sellers.get(p.getSellerId());
                    List<ProductImageEntity> photos = byProduct.getOrDefault(p.getId(), List.of());
                    return new ListingCard(p.getId(), p.getCode(), p.getTitle(), p.getDisplayPrice(), p.getOriginalPrice(),
                            photos.isEmpty() ? null : url(photos.get(0).getStorageKey()),
                            s.getId(), s.getBusinessName(), s.getCategory(), s.getCustomCategory(), s.getArea(),
                            p.getTerms().availability(), s.isSample());
                }).toList();
    }

    Map<UUID, List<ProductImageEntity>> imagesOf(Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return images.findByProductIdInOrderByPositionAsc(productIds).stream()
                .collect(Collectors.groupingBy(ProductImageEntity::getProductId));
    }

    /** As the profile photos do: only a link a browser can load, never a file path. */
    String url(String key) {
        String url = documentStorage.resolveUrl(key);
        return url != null && (url.startsWith("http://") || url.startsWith("https://")) ? url : null;
    }

    static boolean live(SellerProfileEntity seller) {
        return seller.getStatus() == SellerStatus.ACTIVE;
    }
}
