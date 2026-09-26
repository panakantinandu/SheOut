package com.sheout.admin.internal;

import com.sheout.auth.AccountRole;
import com.sheout.auth.AccountSummary;
import com.sheout.auth.AccountBlock;
import com.sheout.auth.AuthApi;
import com.sheout.booking.BookingApi;
import com.sheout.booking.BookingSummary;
import com.sheout.driververification.VerificationApi;
import com.sheout.driververification.VerificationDropOff;
import com.sheout.driververification.VerificationSummary;
import com.sheout.notifications.SosAlertSummary;
import com.sheout.notifications.SosApi;
import com.sheout.notifications.SosError;
import com.sheout.payments.PaymentApi;
import com.sheout.payments.PaymentStatus;
import com.sheout.sharedkernel.Result;
import com.sheout.users.TrustStats;
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

    /**
     * How many people reached the identity screen lately and never sent
     * anything - see VerificationApi.dropOff. Passed straight through: the
     * counting belongs to the module that owns the records.
     */
    public VerificationDropOff verificationDropOff(AccountRole role, int windowDays) {
        return verificationApi.dropOff(role, windowDays);
    }

    public List<ReviewQueueRow> reviewQueue() {
        return verificationApi.findAwaitingReview().stream()
                .map(this::toReviewRow)
                .toList();
    }

    public Optional<String> documentUrl(UUID accountId) {
        return verificationApi.findDocumentUrl(accountId);
    }

    /** The vehicle registration certificate, read beside the identity document during one review. */
    public Optional<String> rcDocumentUrl(UUID accountId) {
        return verificationApi.findRcDocumentUrl(accountId);
    }

    public Optional<com.sheout.driververification.LiveSelfie> liveSelfie(UUID accountId) {
        return verificationApi.findLiveSelfie(accountId);
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
                // Her name from her own kind of profile. This read the partner
                // profile for everyone, so every rider in the queue was "Name
                // not set" - the one name a reviewer has to check the ID against.
                summary.role() == AccountRole.DRIVER
                        ? driverProfileApi.findByAccountId(summary.accountId()).map(p -> p.name()).orElse(null)
                        : customerProfileApi.findByAccountId(summary.accountId()).map(p -> p.name()).orElse(null),
                phoneFor(summary.accountId()),
                summary.role(),
                summary.genderVerificationStatus(),
                summary.policeVerificationStatus(),
                summary.documentSubmitted(),
                summary.role() == AccountRole.DRIVER
                        ? driverProfileApi.findByAccountId(summary.accountId())
                                .map(p -> p.vehicleRegistrationNumber()).orElse(null)
                        : null,
                summary.updatedAt(),
                summary.documentSubmittedAt(),
                isBlocked(summary.accountId())
        );
    }

    private SosAlertRow toAlertRow(SosAlertSummary alert) {
        // Partners can raise SOS too. The column keeps its old name; who
        // raised it is read from the account, and her name from her profile.
        boolean partner = authApi.findAccount(alert.customerAccountId())
                .map(a -> a.role() == com.sheout.auth.AccountRole.DRIVER).orElse(false);
        return new SosAlertRow(
                alert.id(),
                alert.customerAccountId(),
                partner
                        ? driverProfileApi.findByAccountId(alert.customerAccountId()).map(p -> p.name()).orElse(null)
                        : customerProfileApi.findByAccountId(alert.customerAccountId()).map(p -> p.name()).orElse(null),
                partner ? "PARTNER" : "RIDER",
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

    /** The console's bookings table, paged and filtered - see BookingQuery. */
    public Page<BookingOpsRow> pagedBookings(com.sheout.booking.BookingQuery query, Pageable pageable) {
        return bookingApi.pageBookings(query, pageable).map(this::toBookingRow);
    }

    /**
     * Accounts flagged for review, riders and partners in one queue, oldest
     * flag first.
     * <p>
     * One queue for every trust signal, not one per signal. Cancelling too
     * often and being rated badly are two ways of arriving at the same
     * question, and an account that does both is one case with two facts in
     * it - a second parallel list would show it twice, let half of it be
     * cleared, and leave nobody able to say when the queue was done.
     * <p>
     * A queue, not an action. Being flagged has already done everything it
     * is ever going to do by putting an account on this list; blocking stays
     * a separate, deliberate decision made with the already-built block
     * action, by a person who can see the figures. That is the same rule
     * driver verification follows, and it matters more here - a high
     * cancellation rate can mean somebody dodging fares or somebody
     * repeatedly abandoned by partners who never arrived, and a low average
     * can mean a careless partner or three bad nights. The numbers cannot
     * tell those apart.
     * <p>
     * Merged from both profile modules here rather than in either of them:
     * neither users' customer half nor its driver half should have to know
     * the other exists, and joining is what this module is for.
     */
    /**
     * Trips driven measurably shorter than quoted, for a person to judge -
     * see booking's RouteCheck. Nothing about the fare or either account has
     * changed because a trip is listed here.
     */
    public List<RouteReviewRow> routeReviewQueue() {
        return bookingApi.findAwaitingRouteReview().stream().map(this::toRouteRow).toList();
    }

    public Result<RouteReviewRow, com.sheout.booking.BookingError> recordRouteReview(UUID bookingId, UUID adminAccountId, String note) {
        Result<com.sheout.booking.RouteReviewItem, com.sheout.booking.BookingError> result =
                bookingApi.recordRouteReview(bookingId, adminAccountId, note);
        return result.isFailure() ? Result.failure(result.error()) : Result.success(toRouteRow(result.value()));
    }

    private RouteReviewRow toRouteRow(com.sheout.booking.RouteReviewItem trip) {
        return new RouteReviewRow(
                trip,
                trip.driverId() == null ? null : driverProfileApi.findByAccountId(trip.driverId()).map(p -> p.name()).orElse(null),
                trip.driverId() == null ? null : phoneFor(trip.driverId()),
                customerProfileApi.findByAccountId(trip.customerId()).map(p -> p.name()).orElse(null),
                phoneFor(trip.customerId()));
    }

    public List<TrustReviewRow> trustReviewQueue() {
        List<TrustReviewRow> customers = customerProfileApi.findFlaggedForReview().stream()
                .map(p -> new TrustReviewRow(
                        p.accountId(), p.name(), p.phoneNumber(), AccountRole.CUSTOMER,
                        p.trustStats(), p.trustStats().flaggedAt(),
                        p.trustStats().flaggedReason(), isBlocked(p.accountId())))
                .toList();
        List<TrustReviewRow> drivers = driverProfileApi.findFlaggedForReview().stream()
                .map(p -> new TrustReviewRow(
                        p.accountId(), p.name(), p.phoneNumber(), AccountRole.DRIVER,
                        p.trustStats(), p.trustStats().flaggedAt(),
                        p.trustStats().flaggedReason(), isBlocked(p.accountId())))
                .toList();

        return java.util.stream.Stream.concat(customers.stream(), drivers.stream())
                .sorted(java.util.Comparator.comparing(
                        TrustReviewRow::flaggedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();
    }

    /**
     * Takes an account off the review queue.
     * <p>
     * Clearing the flag does not reset any counter or average, and that is
     * deliberate: they are facts about what happened, and an operator's
     * decision does not change what happened. Clearing only records that
     * somebody has read them. An account that keeps cancelling, or keeps
     * being rated badly, crosses the line again and comes back - which is
     * what a review queue should do.
     */
    public void clearReviewFlag(UUID accountId, AccountRole role) {
        if (role == AccountRole.DRIVER) {
            driverProfileApi.clearReviewFlag(accountId);
        } else {
            customerProfileApi.clearReviewFlag(accountId);
        }
    }

    /**
     * A page of accounts, composed from four modules - see AccountOpsRow.
     * <p>
     * ADMIN accounts are excluded from what the console lists. An operator
     * blocking another operator, or themselves, is not a workflow this
     * console should make one click away; that is a deployment-level
     * decision, and the bootstrap that grants ADMIN is the place it lives.
     * The filter is applied by asking auth for a role rather than
     * discarding rows after the fact, so page sizes stay honest.
     */
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
        ProfileFacts profile = profileFactsFor(account);
        return new AccountOpsRow(
                account.id(),
                profile.name(),
                account.phoneNumber(),
                account.email(),
                account.role(),
                verification.map(VerificationSummary::genderVerificationStatus).orElse(null),
                verification.map(VerificationSummary::policeVerificationStatus).orElse(null),
                account.blocked(),
                block.map(AccountBlock::blockedAt).orElse(null),
                block.map(AccountBlock::blockedByAccountId).map(this::phoneFor).orElse(null),
                block.map(AccountBlock::reason).orElse(null),
                profile.trustStats(),
                account.createdAt());
    }

    /** The two things a row needs from a profile, read together - see profileFactsFor. */
    private record ProfileFacts(String name, TrustStats trustStats) {
    }

    /**
     * A rider's profile lives in users' customer half and a partner's in its
     * driver half; neither knows the other exists, so the role picks which
     * one to ask.
     * <p>
     * One lookup, not two. The name and the cancellation figures come off
     * the same profile row, and asking for them separately would have
     * doubled the queries behind every page of the accounts table - the same
     * reasoning the block detail above is read once for.
     * <p>
     * An account with no profile row at all - an ADMIN, or one caught
     * mid-registration - gets empty stats rather than null, so the console
     * never has to render a missing number differently from a zero one.
     */
    private ProfileFacts profileFactsFor(AccountSummary account) {
        Optional<ProfileFacts> facts = account.role() == AccountRole.DRIVER
                ? driverProfileApi.findByAccountId(account.id())
                        .map(p -> new ProfileFacts(p.name(), p.trustStats()))
                : customerProfileApi.findByAccountId(account.id())
                        .map(p -> new ProfileFacts(p.name(), p.trustStats()));
        return facts.orElseGet(() -> new ProfileFacts(null, TrustStats.empty()));
    }

}
