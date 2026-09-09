package com.sheout.payments.internal.web;

import com.sheout.payments.internal.PaymentService;
import com.sheout.payments.internal.gateway.PaymentGateway;
import com.sheout.sharedkernel.web.ApiException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public (no auth) - Razorpay calls this directly, not a logged-in user.
 * Trust comes entirely from the X-Razorpay-Signature check below, which
 * must run before the raw payload is parsed or acted on at all.
 * <p>
 * ASSUMPTION FLAGGED on the payload shape: Razorpay's documented webhook
 * envelope is {@code {event, payload: {payment: {entity: {...}}}}} with
 * event names like "payment.captured"/"payment.failed" and the payment
 * entity carrying "id"/"order_id"/"error_description" - this matches
 * Razorpay's published webhook docs, but wasn't handed to this module
 * explicitly, so treat the exact field names as worth double-checking
 * against a real captured payload before going live. Event types other
 * than payment.captured/payment.failed (order.paid, refund.*, etc.) are
 * deliberately ignored, not modeled by this payments module yet.
 */
@RestController
@RequestMapping("/api/v1/payments/webhooks")
public class RazorpayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookController.class);

    private final PaymentGateway paymentGateway;
    private final PaymentService paymentService;

    public RazorpayWebhookController(PaymentGateway paymentGateway, PaymentService paymentService) {
        this.paymentGateway = paymentGateway;
        this.paymentService = paymentService;
    }

    @PostMapping("/razorpay")
    public ResponseEntity<Void> handle(
            @RequestBody String payload,
            @RequestHeader("X-Razorpay-Signature") String signature) {
        if (!paymentGateway.verifyWebhookSignature(payload, signature)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Invalid webhook signature");
        }

        JSONObject event = new JSONObject(payload);
        String eventType = event.optString("event", "");
        JSONObject paymentJson = event.optJSONObject("payload") != null
                ? event.getJSONObject("payload").optJSONObject("payment")
                : null;
        JSONObject entity = paymentJson != null ? paymentJson.optJSONObject("entity") : null;

        if (entity == null) {
            log.warn("Razorpay webhook event {} carried no payment entity - ignoring", eventType);
            return ResponseEntity.ok().build();
        }

        String orderId = entity.optString("order_id", null);
        String razorpayPaymentId = entity.optString("id", null);

        switch (eventType) {
            case "payment.captured" -> paymentService.applyWebhookUpdate(orderId, razorpayPaymentId, true, null);
            case "payment.failed" -> {
                String reason = entity.optString("error_description", "Payment failed");
                paymentService.applyWebhookUpdate(orderId, razorpayPaymentId, false, reason);
            }
            default -> log.debug("Ignoring unhandled Razorpay webhook event {}", eventType);
        }

        return ResponseEntity.ok().build();
    }
}
