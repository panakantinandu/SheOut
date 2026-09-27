package com.sheout.payments;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A trip's UPI QR, for the partner to show and the rider to scan with
 * PhonePe, Google Pay, Paytm or any UPI app. It is a Razorpay QR for exactly
 * the fare, single use: the money reaches SheOut and then the partner's
 * wallet, like every other way of paying.
 */
public record UpiQr(String imageUrl, BigDecimal amount, Instant expiresAt) {
}
