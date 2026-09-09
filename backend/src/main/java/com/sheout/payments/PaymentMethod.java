package com.sheout.payments;

/** UPI is captured through Razorpay; CASH is settled by hand at pickup/drop-off (parcel/lunchbox). */
public enum PaymentMethod {
    UPI,
    CASH
}
