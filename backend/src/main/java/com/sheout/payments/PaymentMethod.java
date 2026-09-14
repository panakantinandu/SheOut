package com.sheout.payments;

/**
 * How a payment was actually settled.
 * <p>
 * CASH is handed to the partner at the end of any trip - ride or delivery -
 * and confirmed by her. The rest come back from Razorpay Checkout, which
 * offers every method itself; the value recorded is the one the rider chose
 * there, read from Razorpay at capture. ONLINE covers a method Razorpay
 * reports that is not listed here.
 * <p>
 * A payment row is created PENDING with UPI as a placeholder before anyone
 * has paid; the method is overwritten when it is captured.
 */
public enum PaymentMethod {
    UPI,
    CASH,
    CARD,
    NETBANKING,
    WALLET,
    ONLINE
}
