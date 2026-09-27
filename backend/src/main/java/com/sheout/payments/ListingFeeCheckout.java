package com.sheout.payments;

import java.util.UUID;

/**
 * What the seller's browser opens Razorpay Checkout with to pay her listing
 * fee. keyId is Razorpay's publishable key, not a secret.
 */
public record ListingFeeCheckout(UUID paymentId, String keyId, String orderId, long amountPaise, String currency) {
}
