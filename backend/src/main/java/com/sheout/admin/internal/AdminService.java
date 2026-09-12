package com.sheout.admin.internal;

import com.sheout.auth.AccountRole;
import com.sheout.auth.AccountSummary;
import com.sheout.auth.AccountBlock;
import com.sheout.auth.AuthApi;
import com.sheout.booking.BookingApi;
import com.sheout.booking.BookingSummary;
import com.sheout.driververification.VerificationApi;
import com.sheout.driververification.VerificationSummary;
import com.sheout.notifications.SosAlertSummary;
import com.sheout.notifications.SosApi;
import com.sheout.notifications.SosError;
import com.sheout.payments.PaymentApi;
import com.sheout.payments.PaymentStatus;
import com.sheout.sharedkernel.Result;
import com.sheout.users.CustomerProfileApi;
import com.sheout.users.DriverProfileApi;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Composes other modules' public interfaces into the three views an
 * operator needs. Owns no repository and no entity of its own: every fact
 * here belongs to another module, and this module's job is joining them,
 * not storing them.
 * <p>
 * The enrichment (attaching a name/phone to an account id) is the reason
 * this class exists rather than the controller calling each API directly -
 * verification, SOS and booking all need the same "who is this account"
 * lookup, and each source module deliberately returns only its own data.
 */
@Service
public class AdminService {

    private final VerificationApi verificationApi;
    private final SosApi sosApi;
    private final BookingApi bookingApi;
    private final PaymentApi paymentApi;
    private final AuthApi authApi;
    private final CustomerProfileApi customerProfileApi;
    private final DriverProfileApi driverProfileApi;

    public AdminService(VerificationApi verificationApi, SosApi sosApi, BookingApi bookingApi,
                 PaymentApi paymentApi, AuthApi authApi, CustomerProfileApi customerProfileApi,
                 DriverProfileApi driverProfileApi) {
        this.verificationApi = verificationApi;
        this.sosApi = sosApi;
        this.bookingApi = bookingApi;
        this.paymentApi = paymentApi;
        this.authApi = authApi;
        this.customerProfileApi = customerProfileApi;
        this.driverProfileApi = driverProfileApi;
    }

    public List<ReviewQueueRow> reviewQueue() {
        return verificationApi.findAwaitingReview().stream()
                .map(this::toReviewRow)
                .toList();
    }

    public Optional<String> documentUrl(UUID accountId) {
        return verificationApi.findDocumentUrl(accountId);
    }

    public List<SosAlertRow> activeAlerts() {
        return sosApi.findActiveAlerts().stream()
                .map(this::toAlertRow)
                .toList();
    }

    public Result<SosAlertSummary, SosError> resolveAlert(UUID alertId, UUID adminAccountId) {
        return sosApi.resolve(alertId, adminAccountId);
    }

    /**
     * One payment lookup per booking. That is an N+1 against payments'
     * interface, accepted deliberately: PaymentApi is per-booking by
     * design, the cap here is small, and widening a public interface to
     * suit one internal ops screen is the worse trade. Revisit if this
     * list ever grows past ops-console size.
     */
    public List<BookingOpsRow> recentBookings(int limit) {
        return bookingApi.findRecent(limit).stream()
                .map(this::toBookingRow)
                .toList();
    }

    private ReviewQueueRow toReviewRow(VerificationSummary summary) {
        return new ReviewQueueRow(
                summary.accountId(),
                driverProfileApi.findByAccountId(summary.accountId()).map(p -> p.name()).orElse(null),
                phoneFor(summary.accountId()),
                summary.role(),
                summary.genderVerificationStatus(),
                summary.policeVerificationStatus(),
                summary.documentSubmitted(),
                summary.updatedAt(),
                isBlocked(summary.accountId())
        );
    }

    private SosAlertRow toAlertRow(SosAlertSummary alert) {
        return new SosAlertRow(
                alert.id(),
                alert.customerAccountId(),
                customerProfileApi.findByAccountId(alert.customerAccountId()).map(p -> p.name()).orElse(null),
                phoneFor(alert.customerAccountId()),
                alert.bookingId(),
                alert.lat(),
                alert.lng(),
                alert.contactsNotified(),
                alert.contactsFailed(),
                alert.createdAt(),
                isBlocked(alert.customerAccountId())
        );
    }

    private BookingOpsRow toBookingRow(BookingSummary booking) {
        Result<com.sheout.payments.PaymentSummary, com.sheout.payments.PaymentError> payment =
                paymentApi.getPaymentStatus(booking.id());
        // A booking with no payment row yet is normal, not an error: payments
        // are only created once a trip completes.
        PaymentStatus paymentStatus = payment.isSuccess() ? payment.value().status() : null;
        return new BookingOpsRow(
                booking.id(),
                booking.status(),
                booking.type(),
                booking.category(),
                customerProfileApi.findByAccountId(booking.customerId()).map(p -> p.name()).orElse(null),
                booking.driverId() == null
                        ? null
                        : driverProfileApi.findByAccountId(booking.driverId()).map(p -> p.name()).orElse(null),
                booking.fareEstimate(),
                booking.finalFare(),
                paymentStatus,
                payment.isSuccess() ? payment.value().method() : null,
                booking.requestedAt(),
                booking.completedAt()
        );
    }

    private boolean isBlocked(UUID accountId) {
        return authApi.findAccount(accountId).map(AccountSummary::blocked).orElse(false);
    }

    private String phoneFor(UUID accountId) {
        return authApi.findAccount(accountId).map(a -> a.phoneNumber()).orElse(null);
    }

    /**
     * A page of accounts, composed from three modules - see AccountOpsRow.
     * <p>
     * ADMIN accounts are excluded from what the console lists. An operator
     * blocking another operator, or themselves, is not a workflow this
     * console should make one click away; that is a deployment-level
     * decision, and the bootstrap that grants ADMIN is the place it lives.
     * The filter is applied by asking auth for a role rather than
     * discarding rows after the fact, so page sizes stay honest.
     */
    /** The console's bookings table, paged and filtered - see BookingQuery. */
    public Page<BookingOpsRow> pagedBookings(com.sheout.booking.BookingQuery query, Pageable pageable) {
        return bookingApi.pageBookings(query, pageable).map(this::toBookingRow);
    }

    public Page<AccountOpsRow> accounts(String text, AccountRole role, Boolean blocked, Pageable pageable) {
        java.util.Set<AccountRole> roles = role == null || role == AccountRole.ADMIN
                ? java.util.Set.of(AccountRole.CUSTOMER, AccountRole.DRIVER)
                : java.util.Set.of(role);
        return authApi.searchAccounts(text, roles, blocked, pageable).map(this::toAccountRow);
    }

    /**
     * Blocks an account, recording who did it and why.
     * <p>
     * The reason is required, not optional - see AuthApi.blockAccount. The
     * controller rejects a blank one rather than storing an empty string,
     * because an audit trail that says nothing is worse than none: it looks
     * answered.
     */
    public Optional<AccountOpsRow> block(UUID accountId, UUID adminAccountId, String reason) {
        return authApi.blockAccount(accountId, adminAccountId, reason).map(this::toAccountRow);
    }

    public Optional<AccountOpsRow> unblock(UUID accountId) {
        return authApi.unblockAccount(accountId).map(this::toAccountRow);
    }

    public Optional<AccountOpsRow> findAccountRow(UUID accountId) {
        return authApi.findAccount(accountId).map(this::toAccountRow);
    }

    private AccountOpsRow toAccountRow(AccountSummary account) {
        Optional<VerificationSummary> verification = verificationApi.findByAccountId(account.id());
        // Read once. Asking three times for the three fields it holds would
        // be three queries per row, per page.
        Optional<AccountBlock> block = account.blocked()
                ? authApi.findBlockDetail(account.id())
                : Optional.empty();
        return new AccountOpsRow(
                account.id(),
                nameFor(account),
                account.phoneNumber(),
                account.email(),
                account.role(),
                verification.map(VerificationSummary::genderVerificationStatus).orElse(null),
                verification.map(VerificationSummary::policeVerificationStatus).orElse(null),
                account.blocked(),
                block.map(AccountBlock::blockedAt).orElse(null),
                block.map(AccountBlock::blockedByAccountId).map(this::phoneFor).orElse(null),
                block.map(AccountBlock::reason).orElse(null),
                account.createdAt());
    }

    /**
     * A driver's name lives in driver-verification's sibling module and a
     * customer's in users; neither knows about the other, so the role picks
     * which one to ask.
     */
    private String nameFor(AccountSummary account) {
        if (account.role() == AccountRole.DRIVER) {
            return driverProfileApi.findByAccountId(account.id()).map(p -> p.name()).orElse(null);
        }
        return customerProfileApi.findByAccountId(account.id()).map(p -> p.name()).orElse(null);
    }

}
