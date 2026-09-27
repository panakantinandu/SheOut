package com.sheout.payments;

/**
 * What a payment is for. Every payment row has one, so a trip fare and a
 * seller's listing fee are never mistaken for each other wherever payments
 * are listed.
 * <ul>
 *   <li>RIDE_FARE - a rider paying for a trip. Tied to a booking; the
 *       partner's share is settled from it.</li>
 *   <li>SELLER_LISTING_FEE - the flat, one-time fee a SheOut Seller pays to
 *       appear in the directory. Tied to her seller profile, not a booking;
 *       nobody else is paid out of it.</li>
 * </ul>
 */
public enum PaymentPurpose {
    RIDE_FARE,
    SELLER_LISTING_FEE
}
