package com.sheout.marketplace.internal;

import com.sheout.marketplace.MarketplaceAdminApi;
import com.sheout.marketplace.MarketplaceError;
import com.sheout.marketplace.MarketplaceViews.SellerAdminDetail;
import com.sheout.marketplace.MarketplaceViews.SellerAdminRow;
import com.sheout.marketplace.MarketplaceViews.SellerDetails;
import com.sheout.marketplace.MarketplaceViews.SellerView;
import com.sheout.marketplace.SellerApi;
import com.sheout.marketplace.SellerStatus;
import com.sheout.marketplace.SellerStatusChanged;
import com.sheout.payments.ListingFeeCheckout;
import com.sheout.payments.ListingFeePaid;
import com.sheout.payments.PaymentApi;
import com.sheout.payments.PaymentError;
import com.sheout.payments.PaymentSummary;
import com.sheout.privacy.AccountDeletionRequested;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import com.sheout.sharedkernel.storage.DocumentStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A seller's shop from application to the directory, and every decision an
 * operator makes about it.
 * <p>
 * The order is deliberate: she builds her shop, a person reviews it for
 * free, and only then does she pay. The fee is charged through payments'
 * ordinary Razorpay flow as a SELLER_LISTING_FEE; its capture (Checkout or
 * webhook, whichever is first) publishes ListingFeePaid, and that - nothing
 * else - puts her in the directory.
 */
@Service
public class SellerService implements SellerApi, MarketplaceAdminApi {

    private static final Logger log = LoggerFactory.getLogger(SellerService.class);

    private final SellerProfileRepository sellers;
    private final ProductRepository products;
    private final ProductImageRepository images;
    private final ShopViews views;
    private final PaymentApi paymentApi;
    private final DocumentStorage documentStorage;
    private final DomainEventPublisher events;
    private final MarketplaceLimits limits;
    private final TransactionTemplate transactions;

    SellerService(SellerProfileRepository sellers, ProductRepository products, ProductImageRepository images,
                  ShopViews views, PaymentApi paymentApi, DocumentStorage documentStorage,
                  DomainEventPublisher events, MarketplaceLimits limits, PlatformTransactionManager transactionManager) {
        this.sellers = sellers;
        this.products = products;
        this.images = images;
        this.views = views;
        this.paymentApi = paymentApi;
        this.documentStorage = documentStorage;
        this.events = events;
        this.limits = limits;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    // ------------------------------------------------------------ her shop

    @Override
    @Transactional(readOnly = true)
    public Optional<SellerView> mySeller(UUID accountId) {
        return sellers.findByAccountId(accountId).map(views::sellerView);
    }

    @Override
    @Transactional
    public Result<SellerView, MarketplaceError> applyAsSeller(UUID accountId, SellerDetails details) {
        if (sellers.findByAccountId(accountId).isPresent()) {
            return Result.failure(MarketplaceError.ALREADY_A_SELLER);
        }
        Result<SellerDetails, MarketplaceError> clean = cleaned(details);
        if (clean.isFailure()) {
            return Result.failure(clean.error());
        }
        SellerDetails d = clean.value();
        SellerProfileEntity seller = sellers.save(new SellerProfileEntity(accountId, d.businessName(), d.category(),
                d.contactPhone(), d.whatsappNumber()));
        log.info("Seller application started: {} by account {}", seller.getId(), accountId);
        return Result.success(views.sellerView(seller));
    }

    @Override
    @Transactional
    public Result<SellerView, MarketplaceError> updateProfile(UUID accountId, SellerDetails details) {
        Optional<SellerProfileEntity> found = sellers.findLockedByAccountId(accountId);
        if (found.isEmpty()) {
            return Result.failure(MarketplaceError.NOT_A_SELLER);
        }
        SellerProfileEntity seller = found.get();
        if (!seller.editable()) {
            return Result.failure(MarketplaceError.NOT_EDITABLE);
        }
        Result<SellerDetails, MarketplaceError> clean = cleaned(details);
        if (clean.isFailure()) {
            return Result.failure(clean.error());
        }
        SellerDetails d = clean.value();
        seller.setDetails(d.businessName(), d.category(), d.contactPhone(), d.whatsappNumber());
        seller.touchedWhileLive(Instant.now());
        return Result.success(views.sellerView(sellers.save(seller)));
    }

    @Override
    @Transactional
    public Result<SellerView, MarketplaceError> submitForReview(UUID accountId) {
        Optional<SellerProfileEntity> found = sellers.findLockedByAccountId(accountId);
        if (found.isEmpty()) {
            return Result.failure(MarketplaceError.NOT_A_SELLER);
        }
        SellerProfileEntity seller = found.get();
        if (!seller.submittable()) {
            return Result.failure(MarketplaceError.NOT_SUBMITTABLE);
        }
        // The same women-only check every rider passes before booking: a
        // shop is not shown to a reviewer, let alone customers, before it.
        if (!views.accountVerified(accountId)) {
            return Result.failure(MarketplaceError.NOT_VERIFIED);
        }
        SellerView current = views.sellerView(seller);
        if (current.products().stream().noneMatch(p -> p.active() && !p.images().isEmpty())) {
            return Result.failure(MarketplaceError.NOTHING_TO_REVIEW);
        }
        seller.submit(Instant.now());
        log.info("Seller {} submitted for review", seller.getId());
        return Result.success(views.sellerView(sellers.save(seller)));
    }

    /**
     * The Razorpay order for her fee. No transaction is held open: the
     * gateway call can take seconds, and payments opens its own short ones.
     */
    @Override
    public Result<ListingFeeCheckout, MarketplaceError> startListingFeePayment(UUID accountId) {
        Optional<SellerProfileEntity> found = sellers.findByAccountId(accountId);
        if (found.isEmpty()) {
            return Result.failure(MarketplaceError.NOT_A_SELLER);
        }
        SellerProfileEntity seller = found.get();
        if (seller.getStatus() != SellerStatus.APPROVED_AWAITING_PAYMENT) {
            return Result.failure(seller.getStatus() == SellerStatus.ACTIVE
                    ? MarketplaceError.ALREADY_PAID : MarketplaceError.NOT_AWAITING_PAYMENT);
        }
        Result<ListingFeeCheckout, PaymentError> checkout =
                paymentApi.startListingFeeCheckout(accountId, seller.getId(), seller.getListingFeeAmount());
        if (checkout.isFailure()) {
            if (checkout.error() == PaymentError.ALREADY_CAPTURED) {
                // Paid, but she is still waiting: the capture's event was
                // missed. Put that right now rather than ask her twice.
                activateIfPaid(seller.getId());
                return Result.failure(MarketplaceError.ALREADY_PAID);
            }
            log.warn("Listing fee checkout for seller {} failed: {}", seller.getId(), checkout.error());
            return Result.failure(MarketplaceError.PAYMENT_FAILED);
        }
        return Result.success(checkout.value());
    }

    @Override
    public Result<SellerView, MarketplaceError> confirmListingFeePayment(UUID accountId, String orderId,
                                                                         String razorpayPaymentId, String signature) {
        Optional<SellerProfileEntity> found = sellers.findByAccountId(accountId);
        if (found.isEmpty()) {
            return Result.failure(MarketplaceError.NOT_A_SELLER);
        }
        Result<PaymentSummary, PaymentError> confirmed = paymentApi.confirmListingFeeCheckout(
                accountId, found.get().getId(), orderId, razorpayPaymentId, signature);
        if (confirmed.isFailure()) {
            log.warn("Listing fee confirmation for seller {} failed: {}", found.get().getId(), confirmed.error());
            return Result.failure(confirmed.error() == PaymentError.SIGNATURE_INVALID
                    ? MarketplaceError.PAYMENT_NOT_VERIFIED : MarketplaceError.PAYMENT_FAILED);
        }
        // ListingFeePaid has been handled by now (it runs as the capture
        // commits); this also covers the case where it could not be.
        activateIfPaid(found.get().getId());
        return Result.success(mySeller(accountId).orElseThrow());
    }

    /**
     * Her fee was captured. After the payment commits, in a transaction of
     * its own, so a problem here can never undo a payment Razorpay has taken.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onListingFeePaid(ListingFeePaid event) {
        sellers.findLockedById(event.sellerId()).ifPresentOrElse(seller -> {
            if (seller.getStatus() == SellerStatus.APPROVED_AWAITING_PAYMENT) {
                goLive(seller);
            } else {
                // Paid while suspended, say: the money is recorded and she
                // goes live if she is reinstated. Nothing to undo here.
                log.warn("Listing fee {} paid for seller {} in status {} - left as it is",
                        event.paymentId(), seller.getId(), seller.getStatus());
            }
        }, () -> log.error("Listing fee {} paid for unknown seller {}", event.paymentId(), event.sellerId()));
    }

    /** Its own short transaction, from calls that deliberately hold none open. */
    private void activateIfPaid(UUID sellerId) {
        transactions.executeWithoutResult(status -> sellers.findLockedById(sellerId)
                .filter(s -> s.getStatus() == SellerStatus.APPROVED_AWAITING_PAYMENT)
                .filter(s -> views.listingFeePaid(s.getId()))
                .ifPresent(this::goLive));
    }

    private void goLive(SellerProfileEntity seller) {
        seller.activate(Instant.now());
        sellers.save(seller);
        log.info("Seller {} is live", seller.getId());
        events.publish(new SellerStatusChanged(seller.getId(), seller.getAccountId(), SellerStatus.ACTIVE,
                seller.getListingFeeAmount()));
    }

    // ------------------------------------------------------------ the console

    @Override
    @Transactional(readOnly = true)
    public Page<SellerAdminRow> listSellers(SellerStatus status, String keyword, Pageable pageable) {
        // The queue reads oldest first - whoever has waited longest is next.
        Sort order = status == SellerStatus.SUBMITTED_FOR_REVIEW
                ? Sort.by(Sort.Direction.ASC, "submittedAt")
                : Sort.by(Sort.Direction.DESC, "updatedAt");
        return sellers.findAll(MarketplaceSpecs.sellers(status, keyword),
                        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), order))
                .map(views::adminRow);
    }

    @Override
    @Transactional(readOnly = true)
    public long countAwaitingReview() {
        return sellers.countByStatus(SellerStatus.SUBMITTED_FOR_REVIEW);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SellerAdminDetail> sellerDetail(UUID sellerId) {
        return sellers.findById(sellerId).map(s -> new SellerAdminDetail(views.adminRow(s),
                views.productViews(products.findBySellerIdOrderByCreatedAtAsc(s.getId()))));
    }

    @Override
    @Transactional
    public Result<SellerAdminRow, MarketplaceError> approve(UUID sellerId, UUID adminId) {
        return decide(sellerId, SellerStatus.SUBMITTED_FOR_REVIEW,
                s -> s.approve(adminId, limits.listingFee(), Instant.now()));
    }

    @Override
    @Transactional
    public Result<SellerAdminRow, MarketplaceError> reject(UUID sellerId, UUID adminId, String reason) {
        if (reason == null || reason.isBlank()) {
            return Result.failure(MarketplaceError.REASON_REQUIRED);
        }
        return decide(sellerId, SellerStatus.SUBMITTED_FOR_REVIEW, s -> s.reject(adminId, reason.trim(), Instant.now()));
    }

    @Override
    @Transactional
    public Result<SellerAdminRow, MarketplaceError> suspend(UUID sellerId, UUID adminId, String reason) {
        if (reason == null || reason.isBlank()) {
            return Result.failure(MarketplaceError.REASON_REQUIRED);
        }
        return decide(sellerId, SellerStatus.ACTIVE, s -> s.suspend(adminId, reason.trim(), Instant.now()));
    }

    @Override
    @Transactional
    public Result<SellerAdminRow, MarketplaceError> reinstate(UUID sellerId, UUID adminId) {
        Optional<SellerProfileEntity> found = sellers.findById(sellerId);
        // Only a seller who has paid goes back into the directory.
        if (found.isPresent() && !views.listingFeePaid(sellerId)) {
            return Result.failure(MarketplaceError.INVALID_TRANSITION);
        }
        return decide(sellerId, SellerStatus.SUSPENDED, s -> s.reinstate(adminId, Instant.now()));
    }

    private Result<SellerAdminRow, MarketplaceError> decide(UUID sellerId, SellerStatus from,
                                                          Consumer<SellerProfileEntity> change) {
        Optional<SellerProfileEntity> found = sellers.findLockedById(sellerId);
        if (found.isEmpty()) {
            return Result.failure(MarketplaceError.SELLER_NOT_FOUND);
        }
        SellerProfileEntity seller = found.get();
        if (seller.getStatus() != from) {
            return Result.failure(MarketplaceError.INVALID_TRANSITION);
        }
        change.accept(seller);
        sellers.save(seller);
        log.info("Seller {} is now {}", seller.getId(), seller.getStatus());
        events.publish(new SellerStatusChanged(seller.getId(), seller.getAccountId(), seller.getStatus(),
                seller.getListingFeeAmount()));
        return Result.success(views.adminRow(seller));
    }

    // ------------------------------------------------------------ account deletion

    /**
     * Her account is going: out of the directory, her photos deleted, and
     * her numbers and products gone. The profile row stays, anonymised,
     * because her listing fee payment still points at it.
     */
    @EventListener
    @Transactional
    public void onAccountDeletionRequested(AccountDeletionRequested event) {
        sellers.findLockedByAccountId(event.accountId()).ifPresent(seller -> {
            for (ProductImageEntity image : images.findBySellerId(seller.getId())) {
                try {
                    documentStorage.delete(image.getStorageKey());
                } catch (RuntimeException e) {
                    log.warn("Could not delete product photo {} for a deleted account", image.getStorageKey());
                }
            }
            images.deleteAll(images.findBySellerId(seller.getId()));
            products.deleteAll(products.findBySellerIdOrderByCreatedAtAsc(seller.getId()));
            seller.forget(Instant.now());
            sellers.save(seller);
        });
    }

    // ------------------------------------------------------------ input

    /** Trimmed, with both numbers as ten digits; the shape of each field is checked where it arrives. */
    private static Result<SellerDetails, MarketplaceError> cleaned(SellerDetails d) {
        String phone = IndianMobile.normalize(d.contactPhone());
        if (phone == null) {
            return Result.failure(MarketplaceError.INVALID_PHONE);
        }
        String whatsapp = null;
        if (d.whatsappNumber() != null && !d.whatsappNumber().isBlank()) {
            whatsapp = IndianMobile.normalize(d.whatsappNumber());
            if (whatsapp == null) {
                return Result.failure(MarketplaceError.INVALID_PHONE);
            }
        }
        return Result.success(new SellerDetails(d.businessName().trim(), d.category(), phone, whatsapp));
    }
}
