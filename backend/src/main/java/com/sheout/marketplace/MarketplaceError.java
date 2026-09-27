package com.sheout.marketplace;

public enum MarketplaceError {
    /** She has not applied as a seller. */
    NOT_A_SELLER,
    ALREADY_A_SELLER,
    /** A contact number that is not a 10-digit Indian mobile number. */
    INVALID_PHONE,
    /** Her shop cannot be changed while it is under review, awaiting payment or suspended. */
    NOT_EDITABLE,
    /** Only a draft or a rejected application can be sent for review. */
    NOT_SUBMITTABLE,
    /** Sending for review needs at least one product with a photo. */
    NOTHING_TO_REVIEW,
    /** A seller's account must be ID-verified, like every rider's, before her shop is reviewed. */
    NOT_VERIFIED,
    PRODUCT_NOT_FOUND,
    IMAGE_NOT_FOUND,
    PRODUCT_LIMIT_REACHED,
    PRODUCT_IMAGE_LIMIT_REACHED,
    SELLER_IMAGE_LIMIT_REACHED,
    IMAGE_STORAGE_FAILED,
    /** The listing fee is asked for only once she has been approved. */
    NOT_AWAITING_PAYMENT,
    ALREADY_PAID,
    /** Her SheOut wallet is short of the fee. */
    INSUFFICIENT_BALANCE,
    PAYMENT_FAILED,
    PAYMENT_NOT_VERIFIED,
    /** Console: no such seller, or not in a state that action applies to. */
    SELLER_NOT_FOUND,
    INVALID_TRANSITION,
    REASON_REQUIRED
}
