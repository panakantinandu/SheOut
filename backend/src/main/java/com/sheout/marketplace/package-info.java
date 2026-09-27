/**
 * SheOut Seller: a directory of women who sell from home, and nothing more.
 * <p>
 * SheOut charges a seller one flat listing fee and takes no part in any
 * sale. There is no cart, order, checkout or delivery here: a customer finds
 * a product, sees its price as the seller set it, and contacts the seller on
 * WhatsApp or by phone. What happens next is between the two of them.
 * <p>
 * A seller is a rider's own account (CUSTOMER) with a seller profile
 * attached, never a new role. Her profile and products are reviewed by a
 * person before she pays anything; once approved she pays the fee through
 * the same Razorpay flow a trip uses (a payment of purpose
 * SELLER_LISTING_FEE), and the capture puts her in the directory.
 * <p>
 * Public: {@link com.sheout.marketplace.SellerApi} and
 * {@link com.sheout.marketplace.ProductApi} (the app's directory and a
 * seller's own shop), {@link com.sheout.marketplace.MarketplaceAdminApi}
 * (the console), and {@link com.sheout.marketplace.SellerStatusChanged}
 * (notifications tells her). It reacts to payments' ListingFeePaid.
 */
package com.sheout.marketplace;
