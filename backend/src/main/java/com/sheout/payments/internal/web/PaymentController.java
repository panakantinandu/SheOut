package com.sheout.payments.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.booking.BookingApi;
import com.sheout.booking.BookingError;
import com.sheout.booking.BookingParticipants;
import com.sheout.payments.PaymentError;
import com.sheout.payments.PaymentStatus;
import com.sheout.payments.PaymentSummary;
import com.sheout.payments.internal.PaymentService;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.sharedkernel.web.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
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

    /**
     * The caller's own payment history, paged and filtered by date, status
     * and amount range.
     * <p>
     * Scoped by resolving the caller's bookings from their token, so there
     * is no id to tamper with. Customer-only: a driver's money is their
     * earnings, which is a different screen computed from completed trips,
     * not from the customer's payment rows.
     */
    @GetMapping("/me/search")
    public ResponseEntity<PageResponse<PaymentSummary>> searchMine(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount) {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (caller.role() != AccountRole.CUSTOMER) {
            throw ApiException.forbidden("Only a customer has a payment history");
        }
        Pageable pageable = PageRequest.of(
                PageResponse.normalizePage(page), PageResponse.normalizePageSize(pageSize));
        Page<PaymentSummary> result = paymentService.pageForBookings(
                bookingApi.bookingIdsForCustomer(caller.accountId()),
                status, from, to, minAmount, maxAmount, pageable);
        return ResponseEntity.ok(PageResponse.from(result, summary -> summary));
    }

    /**
     * The partner confirms she has the rider's cash in hand.
     * <p>
     * Hers alone to confirm. It used to be open to either person on the
     * trip, which let a rider mark her own fare "paid in cash" and walk off
     * having paid nothing - and it matters more now that confirming cash
     * also records the platform's commission as owed by the partner: a
     * rider could have put that debt on her by tapping a button. Anyone not
     * on the booking still gets the same 404 as a booking that does not
     * exist; the rider, who is on it, gets a 403 that reveals nothing new.
     */
    @PostMapping("/bookings/{bookingId}/cash")
    public ResponseEntity<PaymentSummary> initiateCash(@PathVariable UUID bookingId) {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        BookingParticipants participants = requireParticipant(bookingId);
        if (!caller.accountId().equals(participants.driverId())) {
            throw ApiException.forbidden("Only the partner who received the cash can confirm it");
        }
        return respond(paymentService.initiateCashPayment(bookingId));
    }

    /** What the rider's app opens Razorpay Checkout with. The rider on the booking only. */
    @GetMapping("/bookings/{bookingId}/checkout")
    public ResponseEntity<PaymentService.CheckoutDetails> checkout(@PathVariable UUID bookingId) {
        requireCustomerOf(bookingId);
        Result<PaymentService.CheckoutDetails, PaymentError> result = paymentService.prepareCheckout(bookingId);
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.ok(result.value());
    }

    /**
     * Checkout reported success. The server verifies it with Razorpay before
     * anything is recorded - see PaymentService.confirmCheckout.
     */
    @PostMapping("/bookings/{bookingId}/checkout/verify")
    public ResponseEntity<PaymentSummary> verifyCheckout(@PathVariable UUID bookingId,
                                                         @Valid @RequestBody CheckoutResult request) {
        requireCustomerOf(bookingId);
        return respond(paymentService.confirmCheckout(
                bookingId, request.razorpayOrderId(), request.razorpayPaymentId(), request.razorpaySignature()));
    }

    /** Only the rider pays online. The partner is on the booking, so her refusal is a 403 that reveals nothing. */
    private void requireCustomerOf(UUID bookingId) {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        BookingParticipants participants = requireParticipant(bookingId);
        if (!caller.accountId().equals(participants.customerId())) {
            throw ApiException.forbidden("Only the rider pays for a trip");
        }
    }

    public record CheckoutResult(
            @NotBlank @Size(max = 100) String razorpayOrderId,
            @NotBlank @Size(max = 100) String razorpayPaymentId,
            @NotBlank @Size(max = 256) String razorpaySignature) {
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
    private BookingParticipants requireParticipant(UUID bookingId) {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));

        Result<BookingParticipants, BookingError> result = bookingApi.getParticipants(bookingId);
        if (result.isFailure()) {
            throw ApiException.notFound("No booking found for this id");
        }
        BookingParticipants participants = result.value();
        if (!participants.includes(caller.accountId())) {
            throw ApiException.notFound("No booking found for this id");
        }
        return participants;
    }

    private ApiException toApiException(PaymentError error) {
        return switch (error) {
            case PAYMENT_NOT_FOUND -> ApiException.notFound("No payment found for this booking");
            case ALREADY_CAPTURED -> new ApiException(HttpStatus.CONFLICT, "Conflict", "Payment already captured");
            case GATEWAY_ERROR -> new ApiException(HttpStatus.BAD_GATEWAY, "Bad Gateway", "Payment gateway error");
            case SIGNATURE_INVALID -> new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_NOT_VERIFIED",
                    "This payment could not be verified. If money left your account, contact support with your trip.");
            case NOT_CAPTURED -> new ApiException(HttpStatus.CONFLICT, "PAYMENT_NOT_CAPTURED",
                    "The payment did not go through. You can try again.");
        };
    }
}
