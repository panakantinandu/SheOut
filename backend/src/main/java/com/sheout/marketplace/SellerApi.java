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

    /** From DRAFT or REJECTED, once she has a product with a photo and her account is verified. */
    Result<SellerView, MarketplaceError> submitForReview(UUID accountId);

    /** Once approved: the Razorpay order for her listing fee. */
    Result<ListingFeeCheckout, MarketplaceError> startListingFeePayment(UUID accountId);

    /** Checkout said it went through; verified with Razorpay before she goes live. */
    Result<SellerView, MarketplaceError> confirmListingFeePayment(UUID accountId, String orderId, String razorpayPaymentId,
                                                                  String signature);
}
