package com.sheout.payments.internal.gateway;

import com.sheout.payments.PaymentError;
import com.sheout.sharedkernel.Result;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What PaymentService depends on instead of the Razorpay SDK directly -
 * this is what lets the provider be swapped later without touching
 * PaymentService or any other module. RazorpayPaymentGateway is the only
 * implementation today.
 */
public interface PaymentGateway {

    Result<GatewayOrder, PaymentError> createOrder(UUID bookingId, BigDecimal amount);

    /**
     * True only if signature genuinely matches payload under the
     * configured webhook secret - callers must reject the webhook (not
     * just log a warning) when this returns false.
     */
    boolean verifyWebhookSignature(String payload, String signature);

    /**
     * The publishable key id the browser's Checkout needs. Not a secret -
     * Razorpay designs it to be embedded in pages - but served from here so
     * the app never carries its own copy.
     */
    String publicKeyId();

    /**
     * Whether a Checkout success response really came from Razorpay for this
     * order: HMAC of "orderId|paymentId" under the key secret.
     */
    boolean verifyCheckoutSignature(String orderId, String paymentId, String signature);

    /**
     * Asks Razorpay for the payment's real state, and captures it if it is
     * only authorised. The browser saying "success" is never taken as
     * payment; this is. expectedAmount guards against a payment for a
     * different amount being attached to this order.
     */
    Result<GatewayPayment, PaymentError> confirmCapture(String paymentId, String expectedOrderId, BigDecimal expectedAmount);
}
