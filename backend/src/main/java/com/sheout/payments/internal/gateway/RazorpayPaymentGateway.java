package com.sheout.payments.internal.gateway;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import com.razorpay.Payment;
import com.sheout.payments.PaymentError;
import com.sheout.payments.PaymentMethod;
import com.sheout.sharedkernel.Result;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(RazorpayPaymentGateway.class);

    private final RazorpayClient client;
    private final String webhookSecret;
    private final String keyId;
    private final String keySecret;

    public RazorpayPaymentGateway(
            @Value("${sheout.payments.razorpay.key-id}") String keyId,
            @Value("${sheout.payments.razorpay.key-secret}") String keySecret,
            @Value("${sheout.payments.razorpay.webhook-secret}") String webhookSecret) {
        try {
            this.client = new RazorpayClient(keyId, keySecret);
            log.info("Razorpay client initialized successfully (keyId starts with: {})",
                keyId.isEmpty() ? "BLANK" : keyId.substring(0, Math.min(10, keyId.length())));
        } catch (RazorpayException e) {
            throw new IllegalStateException("Failed to initialize Razorpay client", e);
        }
        this.webhookSecret = webhookSecret;
        this.keyId = keyId;
        this.keySecret = keySecret;
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
            String orderId = order.get("id");
            log.info("Razorpay order created successfully - bookingId: {}, orderId: {}, amount: {} paise", bookingId, orderId, amount);
            return Result.success(new GatewayOrder(orderId));
        } catch (RazorpayException e) {
            log.error("Razorpay order creation failed - bookingId: {}, amount: {}", bookingId, amount, e);
            return Result.failure(PaymentError.GATEWAY_ERROR);
        } catch (Exception e) {
            log.error("Unexpected error creating Razorpay order - bookingId: {}, amount: {}", bookingId, amount, e);
            return Result.failure(PaymentError.GATEWAY_ERROR);
        }
    }

    @Override
    public String publicKeyId() {
        return keyId;
    }

    @Override
    public boolean verifyCheckoutSignature(String orderId, String paymentId, String signature) {
        if (keySecret == null || keySecret.isBlank() || orderId == null || paymentId == null || signature == null) {
            return false;
        }
        try {
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", orderId);
            attributes.put("razorpay_payment_id", paymentId);
            attributes.put("razorpay_signature", signature);
            return Utils.verifyPaymentSignature(attributes, keySecret);
        } catch (RazorpayException e) {
            return false;
        }
    }

    /**
     * Reads the payment back from Razorpay and captures it if it is only
     * authorised. With automatic capture on - the default for orders - it
     * arrives already captured; this still asks rather than assuming.
     */
    @Override
    public Result<GatewayPayment, PaymentError> confirmCapture(String paymentId, String expectedOrderId, BigDecimal expectedAmount) {
        long expectedPaise = expectedAmount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
        try {
            Payment payment = client.payments.fetch(paymentId);
            JSONObject json = payment.toJson();
            if (!expectedOrderId.equals(json.optString("order_id")) || json.optLong("amount") != expectedPaise) {
                log.error("Razorpay payment {} does not match order {} / {} paise", paymentId, expectedOrderId, expectedPaise);
                return Result.failure(PaymentError.SIGNATURE_INVALID);
            }
            String status = json.optString("status");
            if ("authorized".equals(status)) {
                JSONObject capture = new JSONObject();
                capture.put("amount", expectedPaise);
                capture.put("currency", "INR");
                status = client.payments.capture(paymentId, capture).toJson().optString("status");
            }
            if (!"captured".equals(status)) {
                log.warn("Razorpay payment {} is {}, not captured", paymentId, status);
                return Result.failure(PaymentError.NOT_CAPTURED);
            }
            return Result.success(new GatewayPayment(paymentId, methodOf(json.optString("method"))));
        } catch (RazorpayException e) {
            log.error("Razorpay payment {} could not be confirmed: {}", paymentId, e.getMessage());
            return Result.failure(PaymentError.GATEWAY_ERROR);
        }
    }

    private static PaymentMethod methodOf(String razorpayMethod) {
        return switch (razorpayMethod == null ? "" : razorpayMethod) {
            case "upi" -> PaymentMethod.UPI;
            case "card" -> PaymentMethod.CARD;
            case "netbanking" -> PaymentMethod.NETBANKING;
            case "wallet" -> PaymentMethod.WALLET;
            default -> PaymentMethod.ONLINE;
        };
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        // No secret, no webhook. An HMAC over a blank key is one anybody can
        // compute, and a forged "payment.captured" marks a trip or a wallet
        // top-up paid. Refused today only because the JDK happens to reject
        // an empty key - this says it on purpose.
        if (webhookSecret == null || webhookSecret.isBlank() || signature == null || signature.isBlank()) {
            return false;
        }
        try {
            return Utils.verifyWebhookSignature(payload, signature, webhookSecret);
        } catch (RazorpayException e) {
            return false;
        }
    }
}
