package com.sheout.marketplace;

import com.sheout.marketplace.MarketplaceViews.SellerAdminDetail;
import com.sheout.marketplace.MarketplaceViews.SellerAdminRow;
import com.sheout.sharedkernel.Result;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

/**
 * The console's Sellers section: the review queue and what can be done
 * about a seller. Every decision records who made it.
 */
public interface MarketplaceAdminApi {

    /** Sellers, oldest submission first; status narrows it (SUBMITTED_FOR_REVIEW is the queue), keyword matches the shop name. */
    Page<SellerAdminRow> listSellers(SellerStatus status, String keyword, Pageable pageable);

    long countAwaitingReview();

    /** Her shop in full - every product and photo - for a decision. */
    Optional<SellerAdminDetail> sellerDetail(UUID sellerId);

    /** From review: she is asked to pay the listing fee in force now. */
    Result<SellerAdminRow, MarketplaceError> approve(UUID sellerId, UUID adminId);

    /** From review, with the reason she is shown. */
    Result<SellerAdminRow, MarketplaceError> reject(UUID sellerId, UUID adminId, String reason);

    /** A live seller comes out of the directory at once, for complaints or misuse. */
    Result<SellerAdminRow, MarketplaceError> suspend(UUID sellerId, UUID adminId, String reason);

    /** A suspended seller goes back into the directory - she has already paid. */
    Result<SellerAdminRow, MarketplaceError> reinstate(UUID sellerId, UUID adminId);
}
