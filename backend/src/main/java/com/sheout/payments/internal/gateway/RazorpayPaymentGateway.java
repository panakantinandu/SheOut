package com.sheout.payments.internal.gateway;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import com.sheout.payments.PaymentError;
import com.sheout.sharedkernel.Result;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * key-id/key-secret/webhook-secret are blank-by-default (see
 * application.yml) rather than fail-fast like JWT_SECRET - same "not wired
 * up in every environment yet" treatment already given to Firebase's
 * config in this codebase. RazorpayClient's constructor does no network
 * call, so blank credentials only surface as a RazorpayException on the
 * first real createOrder call, not at startup.
 */
@Component
public class RazorpayPaymentGateway implements PaymentGateway {

    private final RazorpayClient client;
    private final String webhookSecret;

    public RazorpayPaymentGateway(
            @Value("${sheout.payments.razorpay.key-id}") String keyId,
            @Value("${sheout.payments.razorpay.key-secret}") String keySecret,
            @Value("${sheout.payments.razorpay.webhook-secret}") String webhookSecret) {
        try {
            this.client = new RazorpayClient(keyId, keySecret);
        } catch (RazorpayException e) {
            throw new IllegalStateException("Failed to initialize Razorpay client", e);
        }
        this.webhookSecret = webhookSecret;
    }

    /**
     * amount is rupees with paise as a decimal (e.g. 149.50); Razorpay
     * wants the smallest currency unit (paise) as an integer - see
     * https://razorpay.com/docs/payments/server-integration/java/
     * receipt is the bookingId, so the webhook side (order_id -&gt;
     * payment lookup) never needs to parse it back out of the receipt.
     */
    @Override
    public Result<GatewayOrder, PaymentError> createOrder(UUID bookingId, BigDecimal amount) {
        try {
            JSONObject request = new JSONObject();
            request.put("amount", amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact());
            request.put("currency", "INR");
            request.put("receipt", bookingId.toString());
            Order order = client.orders.create(request);
            return Result.success(new GatewayOrder(order.get("id")));
        } catch (RazorpayException e) {
            return Result.failure(PaymentError.GATEWAY_ERROR);
        }
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        try {
            return Utils.verifyWebhookSignature(payload, signature, webhookSecret);
        } catch (RazorpayException e) {
            return false;
        }
    }
}
