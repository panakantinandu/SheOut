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
}
