package com.sheout.payments.internal.web;

import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.booking.BookingApi;
import com.sheout.booking.BookingError;
import com.sheout.booking.BookingParticipants;
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
 * FIXED: these used to only require the caller to be authenticated, not
 * that they're a participant on this specific booking (contrast
 * BookingController.requireParticipant) - any authenticated account could
 * query or cash-settle any bookingId's payment. Closed via BookingApi's
 * new getParticipants read method (see its Javadoc) - same
 * caller.accountId().equals(customerId/driverId) check
 * BookingController already uses, just against the smaller
 * BookingParticipants DTO instead of a full BookingSummary, since that's
 * all an authorization check needs.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final BookingApi bookingApi;

    public PaymentController(PaymentService paymentService, BookingApi bookingApi) {
        this.paymentService = paymentService;
        this.bookingApi = bookingApi;
    }

    @GetMapping("/bookings/{bookingId}")
    public ResponseEntity<PaymentSummary> getStatus(@PathVariable UUID bookingId) {
        requireParticipant(bookingId);
        return respond(paymentService.getPaymentStatus(bookingId));
    }

    @PostMapping("/bookings/{bookingId}/cash")
    public ResponseEntity<PaymentSummary> initiateCash(@PathVariable UUID bookingId) {
        requireParticipant(bookingId);
        return respond(paymentService.initiateCashPayment(bookingId));
    }

    private ResponseEntity<PaymentSummary> respond(Result<PaymentSummary, PaymentError> result) {
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.ok(result.value());
    }

    /**
     * Authenticated is not enough - the caller must actually be the
     * customer or the assigned driver on this specific booking.
     * <p>
     * A non-participant and a nonexistent bookingId deliberately produce
     * the identical 404 and the identical message: a caller must not be
     * able to tell "this booking doesn't exist" apart from "you're not
     * allowed to see it". Answering the authorization failure with 403
     * would confirm that a given id names a real booking, making ids
     * enumerable by anyone with an account, so the authorization failure
     * is reported as not-found rather than forbidden. Keep the two
     * messages below identical - letting them drift reopens the same gap
     * the status codes close.
     */
    private void requireParticipant(UUID bookingId) {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));

        Result<BookingParticipants, BookingError> result = bookingApi.getParticipants(bookingId);
        if (result.isFailure()) {
            throw ApiException.notFound("No booking found for this id");
        }
        BookingParticipants participants = result.value();
        boolean isParticipant = caller.accountId().equals(participants.customerId())
                || caller.accountId().equals(participants.driverId());
        if (!isParticipant) {
            throw ApiException.notFound("No booking found for this id");
        }
    }

    private ApiException toApiException(PaymentError error) {
        return switch (error) {
            case PAYMENT_NOT_FOUND -> ApiException.notFound("No payment found for this booking");
            case ALREADY_CAPTURED -> new ApiException(HttpStatus.CONFLICT, "Conflict", "Payment already captured");
            case GATEWAY_ERROR -> new ApiException(HttpStatus.BAD_GATEWAY, "Bad Gateway", "Payment gateway error");
        };
    }
}
