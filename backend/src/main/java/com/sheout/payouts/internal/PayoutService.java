package com.sheout.payouts.internal;

import com.sheout.booking.BookingApi;
import com.sheout.payouts.PayoutMarkedPaid;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import com.sheout.booking.BookingError;
import com.sheout.booking.BookingParticipants;
import com.sheout.payments.PaymentCaptured;
import com.sheout.payments.PaymentMethod;
import com.sheout.payouts.PayoutAccount;
import com.sheout.payouts.PayoutApi;
import com.sheout.payouts.PayoutError;
import com.sheout.payouts.PayoutRequestSummary;
import com.sheout.payouts.PayoutStatus;
import com.sheout.payouts.WalletSummary;
import com.sheout.privacy.AccountDeletionRequested;
import com.sheout.sharedkernel.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PayoutService implements PayoutApi {

    private static final Logger log = LoggerFactory.getLogger(PayoutService.class);

    /** Four letters, a zero, six letters or digits - the RBI's IFSC format. */
    private static final Pattern IFSC = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");
    /** Indian bank account numbers run from 9 to 18 digits. */
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("^\\d{9,18}$");
    /** handle@provider, as NPCI allows it. */
    private static final Pattern UPI_VPA = Pattern.compile("^[a-zA-Z0-9.\\-_]{2,64}@[a-zA-Z][a-zA-Z0-9]{1,34}$");
    private static final Pattern HOLDER_NAME = Pattern.compile("^[\\p{L} .'-]{2,100}$");

    private final DriverWalletRepository wallets;
    private final WalletEntryRepository entries;
    private final PayoutAccountRepository accounts;
    private final PayoutRequestRepository requests;
    private final BookingApi bookingApi;

    private final DomainEventPublisher eventPublisher;

    PayoutService(DriverWalletRepository wallets, WalletEntryRepository entries, PayoutAccountRepository accounts,
                  PayoutRequestRepository requests, BookingApi bookingApi, DomainEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        this.wallets = wallets;
        this.entries = entries;
        this.accounts = accounts;
        this.requests = requests;
        this.bookingApi = bookingApi;
    }

    /**
     * Credits the partner for a captured trip. A plain @EventListener, on
     * purpose: it runs inside the capture's own transaction, so the credit
     * and the capture commit together - a wallet can neither be credited for
     * a capture that rolled back nor miss one that committed.
     * <p>
     * Every captured trip credits her share (driverPayout). A cash trip also
     * records the whole fare as collected, because the rider handed it to
     * her: her available balance moves by driverPayout - fare, which is minus
     * the commission. Crediting cash trips like online ones would pay her the
     * fare twice.
     */
    @EventListener
    @Transactional
    public void onPaymentCaptured(PaymentCaptured event) {
        if (entries.existsByPaymentIdAndType(event.paymentId(), WalletEntryEntity.Type.EARNING)) {
            return; // already applied
        }
        Result<BookingParticipants, BookingError> participants = bookingApi.getParticipants(event.bookingId());
        if (participants.isFailure() || participants.value().driverId() == null) {
            log.warn("Payment {} captured for booking {} with no partner to credit", event.paymentId(), event.bookingId());
            return;
        }
        UUID driverId = participants.value().driverId();
        DriverWalletEntity wallet = lockedWallet(driverId);

        wallet.creditEarning(event.driverPayout());
        entries.save(WalletEntryEntity.forPayment(driverId, WalletEntryEntity.Type.EARNING, event.driverPayout(),
                event.bookingId(), event.paymentId()));
        if (event.method() == PaymentMethod.CASH) {
            wallet.recordCashCollected(event.amount());
            entries.save(WalletEntryEntity.forPayment(driverId, WalletEntryEntity.Type.CASH_COLLECTED,
                    event.amount().negate(), event.bookingId(), event.paymentId()));
        }
        wallets.save(wallet);
    }

    /**
     * A campaign incentive: added to what she has earned and can withdraw,
     * as its own entry so her statement shows it apart from trip earnings.
     * In the award's own transaction - the award and the credit commit together.
     */
    @EventListener
    @Transactional
    public void onIncentiveAwarded(com.sheout.campaigns.DriverIncentiveAwarded event) {
        if (entries.existsByIncentiveAwardId(event.awardId())) {
            return;
        }
        DriverWalletEntity wallet = lockedWallet(event.driverId());
        wallet.creditEarning(event.amount());
        entries.save(WalletEntryEntity.forIncentive(event.driverId(), event.amount(), event.bookingId(), event.awardId()));
        wallets.save(wallet);
    }

    @Override
    public WalletSummary getWallet(UUID driverAccountId) {
        return wallets.findByDriverAccountId(driverAccountId)
                .map(PayoutService::toWallet)
                .orElseGet(() -> new WalletSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Override
    public Optional<PayoutAccount> getPayoutAccount(UUID driverAccountId) {
        return accounts.findByDriverAccountId(driverAccountId).map(PayoutService::toAccount);
    }

    @Override
    @Transactional
    public Result<PayoutAccount, PayoutError> savePayoutAccount(UUID driverAccountId, PayoutAccount details) {
        String holder = trimToNull(details.accountHolderName());
        String number = trimToNull(details.accountNumber());
        String ifsc = trimToNull(details.ifsc()) == null ? null : details.ifsc().trim().toUpperCase();
        String vpa = trimToNull(details.upiVpa());

        boolean anyBank = holder != null || number != null || ifsc != null;
        boolean fullBank = holder != null && number != null && ifsc != null;
        if (!fullBank && vpa == null) {
            return Result.failure(anyBank ? PayoutError.INVALID_DETAILS : PayoutError.NO_PAYOUT_DETAILS);
        }
        // Partial bank details alongside a VPA are refused rather than
        // quietly dropped: she believes she saved a bank account.
        if (anyBank && !fullBank) {
            return Result.failure(PayoutError.INVALID_DETAILS);
        }
        if (fullBank && (!HOLDER_NAME.matcher(holder).matches() || !ACCOUNT_NUMBER.matcher(number).matches()
                || !IFSC.matcher(ifsc).matches())) {
            return Result.failure(PayoutError.INVALID_DETAILS);
        }
        if (vpa != null && !UPI_VPA.matcher(vpa).matches()) {
            return Result.failure(PayoutError.INVALID_DETAILS);
        }

        PayoutAccountEntity account = accounts.findByDriverAccountId(driverAccountId)
                .orElseGet(() -> new PayoutAccountEntity(driverAccountId));
        account.replace(holder, number, ifsc, vpa);
        return Result.success(toAccount(accounts.save(account)));
    }

    @Override
    @Transactional
    public Result<PayoutRequestSummary, PayoutError> requestPayout(UUID driverAccountId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            return Result.failure(PayoutError.INVALID_AMOUNT);
        }
        Optional<PayoutAccountEntity> destination = accounts.findByDriverAccountId(driverAccountId);
        if (destination.isEmpty() || !toAccount(destination.get()).hasBankAccount() && !toAccount(destination.get()).hasUpi()) {
            return Result.failure(PayoutError.NO_PAYOUT_DETAILS);
        }
        // Under the wallet lock: two requests at the same instant each see
        // the other's hold, so the same balance cannot be requested twice.
        DriverWalletEntity wallet = lockedWallet(driverAccountId);
        if (amount.compareTo(wallet.available()) > 0) {
            return Result.failure(PayoutError.INSUFFICIENT_BALANCE);
        }
        PayoutRequestEntity request = requests.save(new PayoutRequestEntity(driverAccountId, amount, destination.get()));
        wallet.holdForPayout(amount);
        wallets.save(wallet);
        entries.save(WalletEntryEntity.forPayoutRequest(driverAccountId, amount, request.getId()));
        return Result.success(toSummary(request));
    }

    @Override
    public List<PayoutRequestSummary> listForDriver(UUID driverAccountId) {
        return requests.findByDriverAccountIdOrderByCreatedAtDesc(driverAccountId).stream().map(PayoutService::toSummary).toList();
    }

    @Override
    public Page<PayoutRequestSummary> listRequests(PayoutStatus status, Pageable pageable) {
        Pageable oldestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<PayoutRequestEntity> page = status == null ? requests.findAll(oldestFirst) : requests.findByStatus(status, oldestFirst);
        return page.map(PayoutService::toSummary);
    }

    @Override
    @Transactional
    public Result<PayoutRequestSummary, PayoutError> markPaid(UUID requestId, UUID adminAccountId, String paymentReference) {
        String reference = trimToNull(paymentReference);
        if (reference == null) {
            return Result.failure(PayoutError.REFERENCE_REQUIRED);
        }
        Optional<PayoutRequestEntity> found = requests.findLockedById(requestId);
        if (found.isEmpty()) {
            return Result.failure(PayoutError.REQUEST_NOT_FOUND);
        }
        PayoutRequestEntity request = found.get();
        if (request.getStatus() == PayoutStatus.PAID) {
            return Result.failure(PayoutError.ALREADY_PAID);
        }
        DriverWalletEntity wallet = lockedWallet(request.getDriverAccountId());
        request.markPaid(adminAccountId, reference);
        wallet.settlePayout(request.getAmount());
        wallets.save(wallet);
        log.info("Payout request {} of {} marked paid by admin {}", requestId, request.getAmount(), adminAccountId);
        PayoutRequestEntity saved = requests.save(request);
        eventPublisher.publish(new PayoutMarkedPaid(saved.getId(), saved.getDriverAccountId(), saved.getAmount(), reference));
        return Result.success(toSummary(saved));
    }

    @Override
    public boolean hasPendingPayout(UUID driverAccountId) {
        return requests.existsByDriverAccountIdAndStatus(driverAccountId, PayoutStatus.PENDING);
    }

    /**
     * Account deletion: where she was paid is deleted outright; paid and
     * pending requests are financial records and stay, reduced to what shows
     * which account was paid. The wallet's figures hold no personal data and
     * stay with the retained trips they describe.
     */
    @EventListener
    @Transactional
    public void onAccountDeletionRequested(AccountDeletionRequested event) {
        accounts.findByDriverAccountId(event.accountId()).ifPresent(accounts::delete);
        List<PayoutRequestEntity> retained = requests.findByDriverAccountIdOrderByCreatedAtDesc(event.accountId());
        retained.forEach(PayoutRequestEntity::anonymiseDestination);
        requests.saveAll(retained);
    }

    private DriverWalletEntity lockedWallet(UUID driverAccountId) {
        wallets.createIfAbsent(driverAccountId);
        return wallets.findLockedByDriverAccountId(driverAccountId).orElseThrow();
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static WalletSummary toWallet(DriverWalletEntity w) {
        return new WalletSummary(w.getTotalEarned(), w.getCashCollected(), w.getTotalPaidOut(), w.getPendingPayouts(), w.available());
    }

    private static PayoutAccount toAccount(PayoutAccountEntity a) {
        return new PayoutAccount(a.getAccountHolderName(), a.getAccountNumber(), a.getIfsc(), a.getUpiVpa(), a.getUpdatedAt());
    }

    private static PayoutRequestSummary toSummary(PayoutRequestEntity r) {
        return new PayoutRequestSummary(r.getId(), r.getDriverAccountId(), r.getAmount(), r.getStatus(),
                r.getAccountHolderName(), r.getAccountNumber(), r.getIfsc(), r.getUpiVpa(),
                r.getCreatedAt(), r.getPaidAt(), r.getPaidBy(), r.getPaymentReference());
    }
}
