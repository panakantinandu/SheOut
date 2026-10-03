package com.sheout.admin.internal;

import com.sheout.payments.PaymentApi;
import com.sheout.payments.PaymentError;
import com.sheout.payouts.PayoutApi;
import com.sheout.payouts.PayoutError;
import com.sheout.payouts.PayoutRequestSummary;
import com.sheout.sharedkernel.Result;
import com.sheout.staff.ApprovalExecutor;
import com.sheout.staff.Approvals;
import com.sheout.staff.StaffContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The money actions that need two people, carried out once the second
 * approves - as the approver, who is the one recorded as having done it.
 */
@Configuration
public class AdminApprovalExecutors {

    /** Finance's preparation: which payout, and the bank or UPI reference of the transfer already made. */
    public record PayoutPaid(UUID payoutRequestId, String paymentReference) {
    }

    /** A refund above the issuer's own limit. refundId makes it happen once, however often it is retried. */
    public record Refund(UUID refundId, UUID customerAccountId, UUID bookingId, BigDecimal amount, String reason) {
    }

    @Bean
    ApprovalExecutor payoutMarkPaid(PayoutApi payouts) {
        return new ApprovalExecutor() {
            @Override
            public Approvals.Kind kind() {
                return Approvals.Kind.PAYOUT_MARK_PAID;
            }

            @Override
            public Outcome execute(String payload) {
                PayoutPaid paid = Approvals.read(payload, PayoutPaid.class);
                Result<PayoutRequestSummary, PayoutError> result = payouts.markPaid(paid.payoutRequestId(),
                        StaffContext.requireSignedIn().accountId(), paid.paymentReference());
                if (result.isFailure()) {
                    return Outcome.failed(switch (result.error()) {
                        case ALREADY_PAID -> "It was already marked paid.";
                        case REQUEST_NOT_FOUND -> "That payout request no longer exists.";
                        case REFERENCE_REQUIRED -> "There was no transfer reference.";
                        default -> "It could not be marked paid.";
                    });
                }
                return Outcome.done("Marked paid: ₹" + result.value().amount() + ", reference " + paid.paymentReference() + ".");
            }
        };
    }

    @Bean
    ApprovalExecutor refundAboveLimit(PaymentApi payments) {
        return new ApprovalExecutor() {
            @Override
            public Approvals.Kind kind() {
                return Approvals.Kind.REFUND;
            }

            @Override
            public Outcome execute(String payload) {
                Refund refund = Approvals.read(payload, Refund.class);
                Result<BigDecimal, PaymentError> result = payments.refundToWallet(refund.customerAccountId(),
                        refund.refundId(), refund.bookingId(), refund.amount());
                if (result.isFailure()) {
                    return Outcome.failed(refundRefusal(result.error()));
                }
                return Outcome.done("Refunded ₹" + refund.amount() + " to her SheOut wallet.");
            }
        };
    }

    public static String refundRefusal(PaymentError error) {
        return switch (error) {
            case REFUND_EXCEEDS_PAYMENT -> "That is more than she paid for the trip, with earlier refunds counted.";
            case NOT_CAPTURED -> "There is no completed payment for that trip to refund.";
            case INVALID_AMOUNT -> "That would take her wallet past its limit, or the amount is not valid.";
            default -> "The refund could not be made.";
        };
    }
}
