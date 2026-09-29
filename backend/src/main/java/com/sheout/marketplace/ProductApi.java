package com.sheout.marketplace;

import com.sheout.marketplace.MarketplaceViews.DirectoryFilter;
import com.sheout.marketplace.MarketplaceViews.ListingCard;
import com.sheout.marketplace.MarketplaceViews.ProductDetail;
import com.sheout.marketplace.MarketplaceViews.ProductDetails;
import com.sheout.marketplace.MarketplaceViews.SellerView;
import com.sheout.marketplace.MarketplaceViews.SmartSearchResult;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.storage.DocumentUpload;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

/**
 * Products: the public directory, and a seller managing her own. The
 * directory shows only live sellers' active products; the seller-side calls
 * act only on the caller's own shop, and each returns her whole shop so the
 * app redraws from one answer.
 */
public interface ProductApi {

    /** The directory: live sellers' active products, newest first, narrowed by whatever the filter holds. */
    Page<ListingCard> browseListings(DirectoryFilter filter, Pageable pageable);

    /**
     * The directory searched "in your own words": the model picks from the
     * newest live listings that match the rest of the filter, and only from
     * those. Falls back to the keyword search, never to an error.
     */
    SmartSearchResult askListings(UUID accountId, DirectoryFilter filter, String query);

    /** One product, if it is in the directory now. */
    Optional<ProductDetail> getProductDetail(UUID productId);

    Result<SellerView, MarketplaceError> addProduct(UUID accountId, ProductDetails details);

    Result<SellerView, MarketplaceError> updateProduct(UUID accountId, UUID productId, ProductDetails details);

    Result<SellerView, MarketplaceError> deleteProduct(UUID accountId, UUID productId);

    Result<SellerView, MarketplaceError> addProductImage(UUID accountId, UUID productId, DocumentUpload upload);

    Result<SellerView, MarketplaceError> deleteProductImage(UUID accountId, UUID productId, UUID imageId);
}
