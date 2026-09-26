package com.sheout.privacy.internal;

import com.sheout.auth.AccountRole;
import com.sheout.booking.BookingApi;
import com.sheout.payouts.PayoutApi;
import com.sheout.booking.BookingStatus;
import com.sheout.privacy.AccountDeletionRequested;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Deletes an account: deactivation and anonymisation, never a hard delete.
 * <p>
 * The whole thing is one transaction. The log row is written, the
 * AccountDeletionRequested event runs every module's listener synchronously
 * inside that transaction - auth unlinks the phone and email, users replaces
 * the name and removes contacts and the photo, driver-verification removes
 * the documents, chat, ratings, support and notifications remove the
 * person's own text and numbers, dispatch removes a partner from matching -
 * and the row is marked complete. If any listener fails, all of the
 * database changes roll back together and the caller is told it failed.
 * <p>
 * Refused while a trip is still under way. Deleting the account of a woman
 * who is sitting in a vehicle, or of the partner driving her, would strand
 * the other person mid-trip with nobody on the other end of chat.
 */
@Service
class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    /** A trip in any of these still has a live second person depending on this account. */
    private static final Set<BookingStatus> ACTIVE = EnumSet.of(
            BookingStatus.REQUESTED, BookingStatus.MATCHED, BookingStatus.ACCEPTED, BookingStatus.IN_PROGRESS);

    private final AccountDeletionLogRepository deletionLog;
    private final BookingApi bookingApi;
    private final PayoutApi payoutApi;
    private final DomainEventPublisher eventPublisher;

    AccountDeletionService(AccountDeletionLogRepository deletionLog, BookingApi bookingApi, PayoutApi payoutApi,
                           DomainEventPublisher eventPublisher) {
        this.deletionLog = deletionLog;
        this.bookingApi = bookingApi;
        this.payoutApi = payoutApi;
        this.eventPublisher = eventPublisher;
    }

    public enum Outcome { DELETED, ACTIVE_TRIP, PENDING_PAYOUT, UNPAID_TRIP }

    public record Result(Outcome outcome, Instant requestedAt, Instant completedAt) {
    }

    @Transactional
    public Result delete(UUID accountId, AccountRole role) {
        boolean tripUnderWay = bookingApi.findAllForAccount(accountId).stream()
                .anyMatch(b -> ACTIVE.contains(b.status()));
        if (tripUnderWay) {
            return new Result(Outcome.ACTIVE_TRIP, null, null);
        }
        // A rider who owes a partner for a finished trip settles it first.
        // Deleting used to be a way to walk away from the fare: the account
        // that owed it was gone, and so was the partner's pay.
        if (role == AccountRole.CUSTOMER && bookingApi.findPaymentHoldForCustomer(accountId).isPresent()) {
            return new Result(Outcome.UNPAID_TRIP, null, null);
        }
        // Refused while SheOut still owes her money she asked for: deleting
        // the account would delete where to send it, and the operator paying
        // it would have nowhere to pay.
        if (role == AccountRole.DRIVER && payoutApi.hasPendingPayout(accountId)) {
            return new Result(Outcome.PENDING_PAYOUT, null, null);
        }

        AccountDeletionLogEntity entry = deletionLog.save(new AccountDeletionLogEntity(accountId, role, accountId));
        eventPublisher.publish(new AccountDeletionRequested(accountId, role));
        entry.complete();
        deletionLog.save(entry);

        // The account id only. It identifies nobody any more, and it is what
        // ties this line to the audit row.
        log.warn("Account {} ({}) deleted at the account holder's request", accountId, role);
        return new Result(Outcome.DELETED, entry.getRequestedAt(), entry.getCompletedAt());
    }
}
