package com.sheout.payments.internal.web;

import com.sheout.auth.CurrentAccountContext;
import com.sheout.payments.PaymentError;
import com.sheout.payments.PaymentSummary;
import com.sheout.payments.internal.PaymentService;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Self-service endpoints for a Wallet/payment-status screen.
 * <p>
 * ASSUMPTION FLAGGED / known gap: these only require the caller to be
 * authenticated, not that they're a participant on this specific booking
 * (contrast BookingController.requireParticipant) - there is no way to
 * check that here without a BookingApi read method, and BookingApi
 * doesn't have one (see its Javadoc; BookingRequested's Javadoc flags the
 * same gap for dispatch). Any authenticated account can currently query
 * or cash-settle any bookingId's payment. Closing this needs a minimal
 * BookingApi addition (e.g. a participant-check or read method) - out of
 * scope here per this session's instruction not to modify booking's
 * files.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/bookings/{bookingId}")
    public ResponseEntity<PaymentSummary> getStatus(@PathVariable UUID bookingId) {
        requireAuthenticated();
        return respond(paymentService.getPaymentStatus(bookingId));
    }

    @PostMapping("/bookings/{bookingId}/cash")
    public ResponseEntity<PaymentSummary> initiateCash(@PathVariable UUID bookingId) {
        requireAuthenticated();
        return respond(paymentService.initiateCashPayment(bookingId));
    }

    private ResponseEntity<PaymentSummary> respond(Result<PaymentSummary, PaymentError> result) {
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.ok(result.value());
    }

    private void requireAuthenticated() {
        CurrentAccountContext.get().orElseThrow(() -> ApiException.unauthorized("Authentication required"));
    }

    private ApiException toApiException(PaymentError error) {
        return switch (error) {
            case PAYMENT_NOT_FOUND -> ApiException.notFound("No payment found for this booking");
            case ALREADY_CAPTURED -> new ApiException(HttpStatus.CONFLICT, "Conflict", "Payment already captured");
            case GATEWAY_ERROR -> new ApiException(HttpStatus.BAD_GATEWAY, "Bad Gateway", "Payment gateway error");
        };
    }
}
