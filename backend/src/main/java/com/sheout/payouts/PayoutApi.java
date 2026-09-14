package com.sheout.payouts;

import com.sheout.sharedkernel.Result;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A partner's wallet, where she is paid, and her requests to be paid.
 * <p>
 * The wallet is credited by this module's own reaction to payments'
 * PaymentCaptured event - nothing here is called to add money. Payouts are
 * requested by the partner and marked paid by an operator after sending the
 * money by hand; no payout API is called.
 */
public interface PayoutApi {

    /** A partner with no captured trips yet has a zero wallet, not an empty one. */
    WalletSummary getWallet(UUID driverAccountId);

    Optional<PayoutAccount> getPayoutAccount(UUID driverAccountId);

    /** Replaces the partner's payout details. At least a complete bank account or a UPI VPA. */
    Result<PayoutAccount, PayoutError> savePayoutAccount(UUID driverAccountId, PayoutAccount details);

    /**
     * Asks for amount of the available balance to be paid out. The balance
     * drops immediately, so the same money cannot be requested twice; the
     * request stays PENDING until an operator marks it paid.
     */
    Result<PayoutRequestSummary, PayoutError> requestPayout(UUID driverAccountId, BigDecimal amount);

    /** Newest first. */
    List<PayoutRequestSummary> listForDriver(UUID driverAccountId);

    /** The operator queue; status null for every request. Oldest first, so the longest-waiting is paid first. */
    Page<PayoutRequestSummary> listRequests(PayoutStatus status, Pageable pageable);

    /** PENDING to PAID, recording who, when, and the transaction reference that proves it. */
    Result<PayoutRequestSummary, PayoutError> markPaid(UUID requestId, UUID adminAccountId, String paymentReference);

    /** For account deletion, which is refused while SheOut still owes her a requested payout. */
    boolean hasPendingPayout(UUID driverAccountId);
}
