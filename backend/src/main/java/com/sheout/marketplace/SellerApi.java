package com.sheout.marketplace;

import com.sheout.marketplace.MarketplaceViews.SellerDetails;
import com.sheout.marketplace.MarketplaceViews.SellerView;
import com.sheout.payments.ListingFeeCheckout;
import com.sheout.sharedkernel.Result;

import java.util.Optional;
import java.util.UUID;

/**
 * A seller's own shop. Every call takes the caller's account id from her
 * token, never from a request: the only shop she can touch is her own.
 */
public interface SellerApi {

    /** Her shop, if she has applied. */
    Optional<SellerView> mySeller(UUID accountId);

    /** Starts her shop as a DRAFT. One per account. */
    Result<SellerView, MarketplaceError> applyAsSeller(UUID accountId, SellerDetails details);

    Result<SellerView, MarketplaceError> updateProfile(UUID accountId, SellerDetails details);

    /** Sets, or with null clears, where her parcels are collected. */
    Result<SellerView, MarketplaceError> setParcelPickup(UUID accountId, MarketplaceViews.PickupPoint pickup);

    /**
     * Where a parcel for this product is collected, for a buyer booking one:
     * only for a live, real shop whose seller has set a pickup point.
     */
    java.util.Optional<MarketplaceViews.PickupPoint> parcelPickupFor(UUID productId);

    /** From DRAFT or REJECTED, once she has a product with a photo and her account is verified. */
    Result<SellerView, MarketplaceError> submitForReview(UUID accountId);

    /**
     * Stops her monthly plan: listed until the month she has ends, then
     * hidden with nothing deleted, and no reminders. reason is optional.
     */
    Result<SellerView, MarketplaceError> stopPlan(UUID accountId, PlanStopReason reason);

    /** Takes back a stop while her month is still running. */
    Result<SellerView, MarketplaceError> resumePlan(UUID accountId);

    /** Once approved: the Razorpay order for her listing fee. */
    Result<ListingFeeCheckout, MarketplaceError> startListingFeePayment(UUID accountId);

    /** Pays the fee from her SheOut wallet, and she goes live at once. */
    Result<SellerView, MarketplaceError> payListingFeeFromWallet(UUID accountId);

    /** Checkout said it went through; verified with Razorpay before she goes live. */
    Result<SellerView, MarketplaceError> confirmListingFeePayment(UUID accountId, String orderId, String razorpayPaymentId,
                                                                  String signature);
}
