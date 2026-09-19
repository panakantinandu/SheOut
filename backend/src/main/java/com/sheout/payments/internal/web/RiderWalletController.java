package com.sheout.payments.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.payments.PaymentError;
import com.sheout.payments.internal.wallet.RiderWalletService;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.sharedkernel.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
import java.util.UUID;

/**
 * The rider's own SheOut wallet. Every endpoint is scoped by the token -
 * there is no account id to pass, so no request can read or fill anybody
 * else's balance. Riders only: a partner's money is her earnings wallet,
 * which is a different thing with different rules (see payouts).
 * <p>
 * There is no transfer endpoint and no withdrawal - see RiderWalletService
 * for why that is a rule, not a gap.
 */
@RestController
@RequestMapping("/api/v1/wallet")
public class RiderWalletController {

    private final RiderWalletService walletService;

    public RiderWalletController(RiderWalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/me")
    public ResponseEntity<RiderWalletService.WalletView> myWallet() {
        return ResponseEntity.ok(walletService.getWallet(requireRider()));
    }

    @GetMapping("/me/transactions")
    public ResponseEntity<PageResponse<RiderWalletService.WalletEntryView>> myTransactions(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        UUID rider = requireRider();
        var result = walletService.statement(rider, PageRequest.of(
                PageResponse.normalizePage(page), PageResponse.normalizePageSize(pageSize)));
        return ResponseEntity.ok(PageResponse.from(result, entry -> entry));
    }

    /** Opens a Razorpay order for money she wants to add. Nothing is credited until it is verified. */
    @PostMapping("/topups")
    public ResponseEntity<RiderWalletService.TopupCheckout> startTopup(@Valid @RequestBody TopupRequest request) {
        return ResponseEntity.ok(unwrap(walletService.startTopup(requireRider(), request.amount())));
    }

    @PostMapping("/topups/{topupId}/verify")
    public ResponseEntity<RiderWalletService.WalletView> verifyTopup(@PathVariable UUID topupId,
                                                                     @Valid @RequestBody TopupResult request) {
        return ResponseEntity.ok(unwrap(walletService.confirmTopup(requireRider(), topupId,
                request.razorpayOrderId(), request.razorpayPaymentId(), request.razorpaySignature())));
    }

    public record TopupRequest(@NotNull BigDecimal amount) {
    }

    public record TopupResult(
            @NotBlank @Size(max = 100) String razorpayOrderId,
            @NotBlank @Size(max = 100) String razorpayPaymentId,
            @NotBlank @Size(max = 256) String razorpaySignature) {
    }

    private static UUID requireRider() {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (caller.role() != AccountRole.CUSTOMER) {
            throw ApiException.forbidden("Only a rider has a SheOut wallet");
        }
        return caller.accountId();
    }

    private static <T> T unwrap(Result<T, PaymentError> result) {
        if (result.isSuccess()) {
            return result.value();
        }
        throw switch (result.error()) {
            case INVALID_AMOUNT -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT",
                    "Add between ₹" + plain(RiderWalletService.MIN_TOPUP) + " and ₹"
                            + plain(RiderWalletService.MAX_TOPUP) + ", keeping your balance at or under ₹"
                            + plain(RiderWalletService.MAX_BALANCE) + ".");
            case TOPUP_NOT_FOUND -> ApiException.notFound("No such top-up");
            case SIGNATURE_INVALID -> new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_NOT_VERIFIED",
                    "This payment could not be verified. If money left your account, contact support.");
            case NOT_CAPTURED -> new ApiException(HttpStatus.CONFLICT, "PAYMENT_NOT_CAPTURED",
                    "The payment did not go through. Nothing was added. You can try again.");
            case GATEWAY_ERROR -> new ApiException(HttpStatus.BAD_GATEWAY, "Bad Gateway",
                    "Online payments are not available right now. Please try again in a minute.");
            default -> new ApiException(HttpStatus.CONFLICT, "Conflict", "This could not be done right now.");
        };
    }

    private static String plain(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }
}
