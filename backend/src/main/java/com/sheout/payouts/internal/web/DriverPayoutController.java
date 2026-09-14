package com.sheout.payouts.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.payouts.PayoutAccount;
import com.sheout.payouts.PayoutApi;
import com.sheout.payouts.PayoutError;
import com.sheout.payouts.PayoutRequestSummary;
import com.sheout.payouts.PayoutStatus;
import com.sheout.payouts.WalletSummary;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A partner's own wallet, payout details and requests. Always the caller's
 * own, from the token - there is no driver id to pass.
 */
@RestController
@RequestMapping("/api/v1/payouts/me")
public class DriverPayoutController {

    private final PayoutApi payoutApi;

    DriverPayoutController(PayoutApi payoutApi) {
        this.payoutApi = payoutApi;
    }

    @GetMapping
    public ResponseEntity<PayoutOverview> overview() {
        CurrentAccount driver = requireDriver();
        return ResponseEntity.ok(new PayoutOverview(
                payoutApi.getWallet(driver.accountId()),
                payoutApi.getPayoutAccount(driver.accountId()).map(AccountView::from).orElse(null),
                payoutApi.listForDriver(driver.accountId()).stream().map(RequestView::from).toList()));
    }

    @PutMapping("/account")
    public ResponseEntity<AccountView> saveAccount(@Valid @RequestBody SaveAccountRequest request) {
        CurrentAccount driver = requireDriver();
        // The app only ever sees the account number masked, so it cannot send
        // the saved one back. Changing just the UPI ID says so explicitly,
        // and the saved bank account is carried over from here.
        Optional<PayoutAccount> saved = Boolean.TRUE.equals(request.keepSavedBankAccount())
                ? payoutApi.getPayoutAccount(driver.accountId()).filter(PayoutAccount::hasBankAccount)
                : Optional.empty();
        PayoutAccount details = saved
                .map(bank -> new PayoutAccount(bank.accountHolderName(), bank.accountNumber(), bank.ifsc(), request.upiVpa(), null))
                .orElseGet(() -> new PayoutAccount(request.accountHolderName(), request.accountNumber(), request.ifsc(),
                        request.upiVpa(), null));
        Result<PayoutAccount, PayoutError> result = payoutApi.savePayoutAccount(driver.accountId(), details);
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.ok(AccountView.from(result.value()));
    }

    @PostMapping("/requests")
    public ResponseEntity<RequestView> requestPayout(@Valid @RequestBody PayoutRequestBody request) {
        CurrentAccount driver = requireDriver();
        Result<PayoutRequestSummary, PayoutError> result = payoutApi.requestPayout(driver.accountId(), request.amount());
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(RequestView.from(result.value()));
    }

    /** A role gate, so 403: riders have no wallet. */
    private CurrentAccount requireDriver() {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (caller.role() != AccountRole.DRIVER) {
            throw ApiException.forbidden("Only partners have a wallet");
        }
        return caller;
    }

    static ApiException toApiException(PayoutError error) {
        return switch (error) {
            case NO_PAYOUT_DETAILS -> new ApiException(HttpStatus.CONFLICT, "NO_PAYOUT_DETAILS",
                    "Add a bank account or a UPI ID before requesting a payout.");
            case INVALID_DETAILS -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DETAILS",
                    "Check the details: a bank account needs the holder's name, a 9-18 digit account number and an IFSC like HDFC0001234; a UPI ID looks like name@bank.");
            case INVALID_AMOUNT -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT",
                    "Enter an amount above zero, in rupees and paise.");
            case INSUFFICIENT_BALANCE -> new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_BALANCE",
                    "That is more than your available balance.");
            case REQUEST_NOT_FOUND -> ApiException.notFound("No such payout request");
            case ALREADY_PAID -> new ApiException(HttpStatus.CONFLICT, "ALREADY_PAID", "This payout is already marked paid.");
            case REFERENCE_REQUIRED -> new ApiException(HttpStatus.BAD_REQUEST, "REFERENCE_REQUIRED",
                    "Enter the bank or UPI transaction reference.");
        };
    }

    /**
     * Replaces what is on file. keepSavedBankAccount keeps the stored bank
     * account and ignores the three bank fields - for changing only the UPI ID.
     */
    public record SaveAccountRequest(
            @Size(max = 100) String accountHolderName,
            @Size(max = 18) String accountNumber,
            @Size(max = 11) String ifsc,
            @Size(max = 100) String upiVpa,
            Boolean keepSavedBankAccount) {
    }

    public record PayoutRequestBody(@NotNull @DecimalMin("1.00") @Digits(integer = 10, fraction = 2) BigDecimal amount) {
    }

    public record PayoutOverview(WalletSummary wallet, AccountView account, List<RequestView> requests) {
    }

    /** The account number masked: this is read on a phone other people can see. */
    public record AccountView(String accountHolderName, String accountNumberMasked, String ifsc, String upiVpa, Instant updatedAt) {
        static AccountView from(PayoutAccount a) {
            return new AccountView(a.accountHolderName(), a.maskedAccountNumber(), a.ifsc(), a.upiVpa(), a.updatedAt());
        }
    }

    public record RequestView(UUID id, BigDecimal amount, PayoutStatus status, String destination, Instant requestedAt,
                              Instant paidAt, String paymentReference) {
        static RequestView from(PayoutRequestSummary r) {
            String destination = r.upiVpa() != null && r.accountNumber() == null
                    ? "UPI " + r.upiVpa()
                    : r.accountNumber() != null
                            ? "Bank a/c ending " + r.accountNumber().substring(Math.max(0, r.accountNumber().length() - 4))
                                    + (r.upiVpa() != null ? " or UPI " + r.upiVpa() : "")
                            : "-";
            return new RequestView(r.id(), r.amount(), r.status(), destination, r.requestedAt(), r.paidAt(), r.paymentReference());
        }
    }
}
