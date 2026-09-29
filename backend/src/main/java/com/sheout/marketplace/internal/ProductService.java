package com.sheout.marketplace.internal;

import com.sheout.marketplace.MarketplaceError;
import com.sheout.marketplace.MarketplaceViews.DirectoryFilter;
import com.sheout.marketplace.MarketplaceViews.ListingCard;
import com.sheout.marketplace.MarketplaceViews.ProductDetail;
import com.sheout.marketplace.MarketplaceViews.ProductDetails;
import com.sheout.marketplace.MarketplaceViews.SellerView;
import com.sheout.marketplace.ProductApi;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.storage.DocumentStorage;
import com.sheout.sharedkernel.storage.DocumentUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The directory, and a seller managing her products and their photos.
 * <p>
 * Photos go through DocumentStorage like every other image in SheOut, and
 * are counted against two caps - per product and per shop - before anything
 * is stored, so a refused upload leaves nothing behind.
 */
@Service
public class ProductService implements ProductApi {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private static final int MORE_FROM_SELLER = 6;

    private final SellerProfileRepository sellers;
    private final ProductRepository products;
    private final ProductImageRepository images;
    private final ShopViews views;
    private final DocumentStorage documentStorage;
    private final MarketplaceLimits limits;

    ProductService(SellerProfileRepository sellers, ProductRepository products, ProductImageRepository images,
                   ShopViews views, DocumentStorage documentStorage, MarketplaceLimits limits) {
        this.sellers = sellers;
        this.products = products;
        this.images = images;
        this.views = views;
        this.documentStorage = documentStorage;
        this.limits = limits;
    }

    // ------------------------------------------------------------ the directory

    @Override
    @Transactional(readOnly = true)
    public Page<ListingCard> browseListings(DirectoryFilter filter, Pageable pageable) {
        Page<ProductEntity> page = products.findAll(MarketplaceSpecs.directory(filter),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<UUID, SellerProfileEntity> bySeller = sellersOf(page.getContent());
        return new PageImpl<>(views.cards(page.getContent(), bySeller), page.getPageable(), page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductDetail> getProductDetail(UUID productId) {
        Optional<ProductEntity> found = products.findById(productId).filter(ProductEntity::isActive);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ProductEntity product = found.get();
        Optional<SellerProfileEntity> seller = sellers.findById(product.getSellerId()).filter(ShopViews::live);
        if (seller.isEmpty()) {
            // A suspended or unpaid shop's product reads exactly like one
            // that never existed.
            return Optional.empty();
        }
        SellerProfileEntity s = seller.get();
        List<String> photos = views.imagesOf(List.of(product.getId())).getOrDefault(product.getId(), List.of()).stream()
                .map(i -> views.url(i.getStorageKey()))
                .filter(url -> url != null)
                .toList();
        List<ProductEntity> others = products.findBySellerIdOrderByCreatedAtAsc(s.getId()).stream()
                .filter(p -> p.isActive() && !p.getId().equals(product.getId()))
                .limit(MORE_FROM_SELLER)
                .toList();
        return Optional.of(new ProductDetail(product.getId(), product.getTitle(), product.getDescription(),
                product.getDisplayPrice(), photos, s.getId(), s.getBusinessName(), s.getCategory(),
                s.getArea(), s.getContactPhone(), s.getWhatsappNumber(), views.cards(others, Map.of(s.getId(), s))));
    }

    private Map<UUID, SellerProfileEntity> sellersOf(List<ProductEntity> list) {
        List<UUID> ids = list.stream().map(ProductEntity::getSellerId).distinct().toList();
        return sellers.findAllById(ids).stream().collect(Collectors.toMap(SellerProfileEntity::getId, Function.identity()));
    }

    // ------------------------------------------------------------ her products

    @Override
    @Transactional
    public Result<SellerView, MarketplaceError> addProduct(UUID accountId, ProductDetails details) {
        return withEditableShop(accountId, seller -> {
            if (products.countBySellerId(seller.getId()) >= limits.maxProducts()) {
                return Result.failure(MarketplaceError.PRODUCT_LIMIT_REACHED);
            }
            products.save(new ProductEntity(seller.getId(), details.title().trim(), details.description().trim(),
                    details.displayPrice(), details.active()));
            return Result.success(null);
        });
    }

    @Override
    @Transactional
    public Result<SellerView, MarketplaceError> updateProduct(UUID accountId, UUID productId, ProductDetails details) {
        return withEditableShop(accountId, seller -> ownProduct(seller, productId).map(product -> {
            product.update(details.title().trim(), details.description().trim(), details.displayPrice(), details.active());
            products.save(product);
            return Result.<Void, MarketplaceError>success(null);
        }).orElse(Result.failure(MarketplaceError.PRODUCT_NOT_FOUND)));
    }

    @Override
    @Transactional
    public Result<SellerView, MarketplaceError> deleteProduct(UUID accountId, UUID productId) {
        return withEditableShop(accountId, seller -> ownProduct(seller, productId).map(product -> {
            List<ProductImageEntity> photos = images.findByProductIdOrderByPositionAsc(product.getId());
            images.deleteAll(photos);
            products.delete(product);
            photos.forEach(photo -> deleteStored(photo.getStorageKey()));
            return Result.<Void, MarketplaceError>success(null);
        }).orElse(Result.failure(MarketplaceError.PRODUCT_NOT_FOUND)));
    }

    @Override
    @Transactional
    public Result<SellerView, MarketplaceError> addProductImage(UUID accountId, UUID productId, DocumentUpload upload) {
        return withEditableShop(accountId, seller -> {
            Optional<ProductEntity> product = ownProduct(seller, productId);
            if (product.isEmpty()) {
                return Result.failure(MarketplaceError.PRODUCT_NOT_FOUND);
            }
            // The shop row is locked (withEditableShop), so two uploads at
            // once are counted one after the other and cannot both squeeze
            // in under the cap.
            if (images.countByProductId(productId) >= limits.maxImagesPerProduct()) {
                return Result.failure(MarketplaceError.PRODUCT_IMAGE_LIMIT_REACHED);
            }
            if (images.countBySellerId(seller.getId()) >= limits.maxImagesPerSeller()) {
                return Result.failure(MarketplaceError.SELLER_IMAGE_LIMIT_REACHED);
            }
            String key;
            try {
                key = documentStorage.store(accountId, "product-photo", upload);
            } catch (RuntimeException e) {
                log.warn("Could not store a product photo for seller {}", seller.getId(), e);
                return Result.failure(MarketplaceError.IMAGE_STORAGE_FAILED);
            }
            images.save(new ProductImageEntity(productId, seller.getId(), key, images.maxPosition(productId) + 1));
            return Result.success(null);
        });
    }

    @Override
    @Transactional
    public Result<SellerView, MarketplaceError> deleteProductImage(UUID accountId, UUID productId, UUID imageId) {
        return withEditableShop(accountId, seller -> {
            if (ownProduct(seller, productId).isEmpty()) {
                return Result.failure(MarketplaceError.PRODUCT_NOT_FOUND);
            }
            Optional<ProductImageEntity> image = images.findById(imageId)
                    .filter(i -> i.getProductId().equals(productId));
            if (image.isEmpty()) {
                return Result.failure(MarketplaceError.IMAGE_NOT_FOUND);
            }
            images.delete(image.get());
            deleteStored(image.get().getStorageKey());
            return Result.success(null);
        });
    }

    /**
     * Every change to her products runs here: her own shop, locked, in a
     * state she may change, and marked as changed after approval when it
     * is live. The answer is her whole shop as it now is.
     */
    private Result<SellerView, MarketplaceError> withEditableShop(
            UUID accountId, Function<SellerProfileEntity, Result<Void, MarketplaceError>> change) {
        Optional<SellerProfileEntity> found = sellers.findLockedByAccountId(accountId);
        if (found.isEmpty()) {
            return Result.failure(MarketplaceError.NOT_A_SELLER);
        }
        SellerProfileEntity seller = found.get();
        if (!seller.editable()) {
            return Result.failure(MarketplaceError.NOT_EDITABLE);
        }
        Result<Void, MarketplaceError> outcome = change.apply(seller);
        if (outcome.isFailure()) {
            return Result.failure(outcome.error());
        }
        seller.touchedWhileLive(Instant.now());
        sellers.save(seller);
        return Result.success(views.sellerView(seller));
    }

    /** Only a product of hers: someone else's reads as not found. */
    private Optional<ProductEntity> ownProduct(SellerProfileEntity seller, UUID productId) {
        return products.findById(productId).filter(p -> p.getSellerId().equals(seller.getId()));
    }

    private void deleteStored(String key) {
        try {
            documentStorage.delete(key);
        } catch (RuntimeException e) {
            // The row is gone, so nothing shows it; a stray file is not worth failing her change over.
            log.warn("Could not delete stored product photo {}", key);
        }
    }
}
