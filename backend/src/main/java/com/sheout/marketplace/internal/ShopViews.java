package com.sheout.marketplace.internal;

import com.sheout.marketplace.MarketplaceViews.ListingCard;
import com.sheout.marketplace.MarketplaceViews.ListingFeeView;
import com.sheout.marketplace.MarketplaceViews.ProductImageView;
import com.sheout.marketplace.MarketplaceViews.ProductView;
import com.sheout.marketplace.MarketplaceViews.SellerAdminRow;
import com.sheout.marketplace.MarketplaceViews.SellerView;
import com.sheout.marketplace.SellerStatus;
import com.sheout.payments.PaymentApi;
import com.sheout.payments.PaymentStatus;
import com.sheout.payments.PaymentSummary;
import com.sheout.sharedkernel.storage.DocumentStorage;
import com.sheout.users.CustomerProfileApi;
import com.sheout.users.CustomerProfileSummary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    ShopViews(ProductRepository products, ProductImageRepository images, DocumentStorage documentStorage,
              PaymentApi paymentApi, CustomerProfileApi customerProfiles, MarketplaceLimits limits) {
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
        List<ProductView> productViews = productViews(products.findBySellerIdOrderByCreatedAtAsc(seller.getId()));
        int imagesUsed = productViews.stream().mapToInt(p -> p.images().size()).sum();
        boolean verified = accountVerified(seller.getAccountId());
        boolean hasPhotographedProduct = productViews.stream().anyMatch(p -> p.active() && !p.images().isEmpty());
        return new SellerView(
                seller.getId(),
                seller.getBusinessName(),
                seller.getCategory(),
                seller.getContactPhone(),
                seller.getWhatsappNumber(),
                seller.getArea(),
                seller.getWebsiteUrl(),
                seller.getStatus(),
                seller.getRejectionReason(),
                seller.getSuspensionReason(),
                seller.getSubmittedAt(),
                seller.getActivatedAt(),
                listingFee(seller),
                productViews,
                imagesUsed,
                limits.maxImagesPerSeller(),
                limits.maxImagesPerProduct(),
                limits.maxProducts(),
                seller.editable(),
                seller.submittable() && verified && hasPhotographedProduct,
                verified);
    }

    SellerAdminRow adminRow(SellerProfileEntity seller) {
        return new SellerAdminRow(
                seller.getId(),
                seller.getAccountId(),
                seller.getBusinessName(),
                seller.getCategory(),
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
                listingFee(seller));
    }

    /**
     * What she pays: the amount she was approved at, or - before approval -
     * the fee in force now, so the app can say what is coming.
     */
    ListingFeeView listingFee(SellerProfileEntity seller) {
        Optional<PaymentSummary> payment = paymentApi.listingFeeFor(seller.getId());
        BigDecimal amount = payment.map(PaymentSummary::amount)
                .orElse(seller.getListingFeeAmount() != null ? seller.getListingFeeAmount() : limits.listingFee());
        boolean paid = payment.map(p -> p.status() == PaymentStatus.CAPTURED).orElse(false);
        return new ListingFeeView(
                amount,
                payment.map(PaymentSummary::status).orElse(null),
                paid ? payment.get().method() : null,
                paid ? payment.get().capturedAt() : null);
    }

    boolean listingFeePaid(UUID sellerId) {
        return paymentApi.listingFeeFor(sellerId).map(p -> p.status() == PaymentStatus.CAPTURED).orElse(false);
    }

    List<ProductView> productViews(List<ProductEntity> list) {
        Map<UUID, List<ProductImageEntity>> byProduct = imagesOf(list.stream().map(ProductEntity::getId).toList());
        return list.stream().map(p -> new ProductView(
                p.getId(), p.getCode(), p.getTitle(), p.getDescription(), p.getDisplayPrice(), p.getOriginalPrice(), p.isActive(),
                byProduct.getOrDefault(p.getId(), List.of()).stream()
                        .map(i -> new ProductImageView(i.getId(), url(i.getStorageKey())))
                        .toList())).toList();
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
                            s.getId(), s.getBusinessName(), s.getCategory(), s.getArea());
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
