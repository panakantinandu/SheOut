package com.sheout.payments.internal.gateway;

import com.sheout.payments.PaymentMethod;

import java.time.Instant;

/**
 * A Razorpay UPI QR for one payment: its id, the image Razorpay renders
 * (a PNG any UPI app can scan) and when it stops accepting payments.
 */
public record GatewayQr(String qrId, String imageUrl, Instant closesAt) {

    /** One payment made against a QR, as Razorpay records it. */
    public record QrPayment(String paymentId, long amountPaise, String status, PaymentMethod method) {

        public boolean captured() {
            return "captured".equals(status);
        }
    }
}
