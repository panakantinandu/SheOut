package com.sheout.admin.internal.web;

import com.sheout.admin.internal.AdminApprovalExecutors;
import com.sheout.admin.internal.SupportOpsService;
import com.sheout.auth.AccountRole;
import com.sheout.auth.AuthApi;
import com.sheout.booking.BookingApi;
import com.sheout.payments.PaymentApi;
import com.sheout.payments.PaymentError;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.staff.Approvals;
import com.sheout.staff.Permission;
import com.sheout.staff.RefundLimits;
import com.sheout.staff.RequiresPermission;
import com.sheout.staff.RequiresStepUp;
import com.sheout.staff.StaffAudit;
import com.sheout.staff.StaffContext;
import com.sheout.staff.StaffPrincipal;
import com.sheout.users.CustomerProfileApi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Refunds and goodwill credit, into the rider's SheOut wallet.
 * <p>
 * UP TO HER OWN LIMIT, AT ONCE; ABOVE IT, AN OWNER DECIDES. The founder's
 * limits (2026-10-03): support agents ₹200, the operations manager
 * ₹1,000 (staff.RefundLimits). Above that the refund becomes an approval
 * request an owner must approve. Owners have no limit.
 * <p>
 * WITHIN REACH. Whoever may read every account (users.view) may refund any
 * rider. A support agent refunds only the rider of a ticket she holds - the
 * reason a support agent has for touching anyone's money.
 * <p>
 * A refund for a trip is never more, in all, than she paid for it
 * (PaymentApi.refundToWallet). Refunds go to her wallet, not back to the card
 * or UPI she paid with: that needs the payment provider's refund API, which
 * is not built (README).
 */
@RestController
@RequestMapping("/api/v1/admin/refunds")
public class AdminRefundController {

    private final PaymentApi payments;
    private final RefundLimits limits;
    private final Approvals approvals;
    private final StaffAudit audit;
    private final AuthApi authApi;
    private final BookingApi bookings;
    private final CustomerProfileApi customers;
    private final SupportOpsService support;

    public AdminRefundController(PaymentApi payments, RefundLimits limits, Approvals approvals, StaffAudit audit,
                                 AuthApi authApi, BookingApi bookings, CustomerProfileApi customers, SupportOpsService support) {
        this.payments = payments;
        this.limits = limits;
        this.approvals = approvals;
        this.audit = audit;
        this.authApi = authApi;
        this.bookings = bookings;
        this.customers = customers;
        this.support = support;
    }

    public record RefundRequest(@NotNull UUID customerAccountId, UUID bookingId, UUID ticketId,
                                @NotNull @DecimalMin("1.00") @DecimalMax("10000.00") BigDecimal amount,
                                @NotBlank @Size(min = 5, max = 300) String reason) {
    }

    public record Issued(String status, UUID refundId, BigDecimal amount, BigDecimal walletBalance) {
    }

    @RequiresPermission(Permission.REFUNDS_ISSUE)
    @RequiresStepUp
    @PostMapping
    public ResponseEntity<?> refund(@Valid @RequestBody RefundRequest body) {
        StaffPrincipal me = StaffContext.requireSignedIn();
        BigDecimal amount = body.amount().setScale(2, RoundingMode.HALF_UP);
        boolean rider = authApi.findAccount(body.customerAccountId()).map(a -> a.role() == AccountRole.CUSTOMER).orElse(false);
        if (!rider) {
            throw ApiException.notFound("No such rider");
        }
        if (body.bookingId() != null && !bookings.findById(body.bookingId())
                .map(b -> body.customerAccountId().equals(b.customerId())).orElse(false)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_HER_TRIP", "That trip is not hers.");
        }
        requireReach(me, body);

        UUID refundId = UUID.randomUUID();
        String name = customers.findByAccountId(body.customerAccountId()).map(p -> p.name()).orElse("her");
        if (limits.withinOwnLimit(me, amount)) {
            Result<BigDecimal, PaymentError> result = payments.refundToWallet(body.customerAccountId(), refundId,
                    body.bookingId(), amount);
            if (result.isFailure()) {
                throw new ApiException(HttpStatus.CONFLICT, result.error().name(), AdminApprovalExecutors.refundRefusal(result.error()));
            }
            audit.record(new StaffAudit.Entry("refund.issue", Permission.REFUNDS_ISSUE, StaffAudit.Result.OK, "ACCOUNT",
                    body.customerAccountId().toString(), body.reason().trim(), "{\"amount\":\"" + amount + "\",\"refundId\":\""
                    + refundId + "\",\"bookingId\":" + (body.bookingId() == null ? "null" : "\"" + body.bookingId() + "\"") + "}"));
            return ResponseEntity.ok(new Issued("ISSUED", refundId, amount, result.value()));
        }
        String limit = limits.limitFor(me.role()).map(l -> "₹" + l.stripTrailingZeros().toPlainString()).orElse("no limit");
        return ResponseEntity.accepted().body(approvals.submit(new Approvals.Request(Approvals.Kind.REFUND,
                "Refund ₹" + amount.toPlainString() + " to " + name + "'s SheOut wallet (above " + limit + ")",
                Approvals.payload(new AdminApprovalExecutors.Refund(refundId, body.customerAccountId(), body.bookingId(), amount,
                        body.reason().trim())),
                null, Permission.REFUNDS_APPROVE, "ACCOUNT", body.customerAccountId().toString(), body.reason().trim())));
    }

    /** A support agent: only the rider of a ticket she holds. */
    private void requireReach(StaffPrincipal me, RefundRequest body) {
        if (me.has(Permission.USERS_VIEW)) {
            return;
        }
        if (body.ticketId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "OUT_OF_REACH", "Refund from the ticket she raised.");
        }
        boolean holds = support.holder(body.ticketId(), me.accountId()).flatMap(h -> h).map(me.accountId()::equals).orElse(false);
        boolean hers = support.ticket(body.ticketId(), me.accountId())
                .map(t -> body.customerAccountId().equals(t.ticket().raisedByAccountId())).orElse(false);
        if (!holds || !hers) {
            throw new ApiException(HttpStatus.FORBIDDEN, "OUT_OF_REACH", "You can refund only the rider of a ticket you hold.");
        }
    }
}
