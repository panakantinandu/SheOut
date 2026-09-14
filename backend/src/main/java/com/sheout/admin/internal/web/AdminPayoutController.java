package com.sheout.admin.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.AccountSummary;
import com.sheout.auth.AuthApi;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.payouts.PayoutApi;
import com.sheout.payouts.PayoutError;
import com.sheout.payouts.PayoutRequestSummary;
import com.sheout.payouts.PayoutStatus;
import com.sheout.payouts.WalletSummary;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.sharedkernel.web.PageResponse;
import com.sheout.users.DriverProfileApi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The console's Payouts section: partners' requests with the full bank or
 * UPI details an operator needs to send the money by hand, and marking a
 * request paid with the transaction reference. ADMIN only - a 403 role gate
 * on the whole surface, as AdminController records. An unknown request is a
 * 404.
 * <p>
 * Full account numbers are shown here and nowhere else: an operator cannot
 * pay a masked account.
 */
@RestController
@RequestMapping("/api/v1/admin/payouts")
public class AdminPayoutController {

    private final PayoutApi payoutApi;
    private final DriverProfileApi driverProfileApi;
    private final AuthApi authApi;

    public AdminPayoutController(PayoutApi payoutApi, DriverProfileApi driverProfileApi, AuthApi authApi) {
        this.payoutApi = payoutApi;
        this.driverProfileApi = driverProfileApi;
        this.authApi = authApi;
    }

    /** status defaults to PENDING - the queue. Pass status=PAID for history. Oldest first. */
    @GetMapping
    public ResponseEntity<PageResponse<PayoutRow>> requests(
            @RequestParam(required = false, defaultValue = "PENDING") PayoutStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        requireAdmin();
        return ResponseEntity.ok(PageResponse.from(
                payoutApi.listRequests(status, PageRequest.of(PageResponse.normalizePage(page), PageResponse.normalizePageSize(pageSize))),
                this::toRow));
    }

    @PostMapping("/{requestId}/mark-paid")
    public ResponseEntity<PayoutRow> markPaid(@PathVariable UUID requestId, @Valid @RequestBody MarkPaidRequest request) {
        CurrentAccount admin = requireAdmin();
        Result<PayoutRequestSummary, PayoutError> result = payoutApi.markPaid(requestId, admin.accountId(), request.paymentReference());
        if (result.isFailure()) {
            throw switch (result.error()) {
                case REQUEST_NOT_FOUND -> ApiException.notFound("No such payout request");
                case ALREADY_PAID -> new ApiException(HttpStatus.CONFLICT, "ALREADY_PAID", "This payout is already marked paid.");
                case REFERENCE_REQUIRED -> new ApiException(HttpStatus.BAD_REQUEST, "REFERENCE_REQUIRED",
                        "Enter the bank or UPI transaction reference.");
                default -> new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "This payout could not be updated.");
            };
        }
        return ResponseEntity.ok(toRow(result.value()));
    }

    private PayoutRow toRow(PayoutRequestSummary r) {
        WalletSummary wallet = payoutApi.getWallet(r.driverAccountId());
        return new PayoutRow(
                r.id(), r.driverAccountId(),
                driverProfileApi.findByAccountId(r.driverAccountId()).map(p -> p.name()).orElse(null),
                authApi.findAccount(r.driverAccountId()).map(AccountSummary::phoneNumber).orElse(null),
                r.amount(), r.status(), r.accountHolderName(), r.accountNumber(), r.ifsc(), r.upiVpa(),
                r.requestedAt(), r.paidAt(),
                r.paidBy() == null ? null : authApi.findAccount(r.paidBy()).map(AccountSummary::phoneNumber).orElse(null),
                r.paymentReference(), wallet.availableBalance());
    }

    private CurrentAccount requireAdmin() {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (caller.role() != AccountRole.ADMIN) {
            throw ApiException.forbidden("Admin role required");
        }
        return caller;
    }

    public record MarkPaidRequest(@NotBlank @Size(max = 100) String paymentReference) {
    }

    /** driverAvailableBalance is her balance now, after this request's hold - context for the operator, not part of the request. */
    public record PayoutRow(UUID id, UUID driverAccountId, String driverName, String driverPhone, BigDecimal amount,
                            PayoutStatus status, String accountHolderName, String accountNumber, String ifsc, String upiVpa,
                            Instant requestedAt, Instant paidAt, String paidByPhone, String paymentReference,
                            BigDecimal driverAvailableBalance) {
    }
}
