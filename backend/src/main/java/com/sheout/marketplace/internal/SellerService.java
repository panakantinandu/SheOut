package com.sheout.marketplace.internal;

import com.sheout.marketplace.MarketplaceAdminApi;
import com.sheout.marketplace.MarketplaceError;
import com.sheout.marketplace.SellerCategory;
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
import com.sheout.payments.PaymentStatus;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A seller's shop from application to the directory, and every decision an
 * operator makes about it.
 * <p>
 * She builds her shop and a person reviews it, free. Approval puts it live
 * at once on its free first month; after that she pays a flat fee a month
 * (SellerPlan has the rules). Each month is charged through payments'
 * ordinary Razorpay flow, or her wallet, as a SELLER_LISTING_FEE for that
 * period; its capture (Checkout or webhook, whichever is first) publishes
 * ListingFeePaid, and that - nothing else - adds the month.
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
                d.customCategory(), d.contactPhone(), d.whatsappNumber(), d.area(), d.websiteUrl()));
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
        seller.setDetails(d.businessName(), d.category(), d.customCategory(), d.contactPhone(), d.whatsappNumber(), d.area(), d.websiteUrl());
        seller.touchedWhileLive(Instant.now());
        return Result.success(views.sellerView(sellers.save(seller)));
    }

    @Override
    @Transactional
    public Result<SellerView, MarketplaceError> setParcelPickup(UUID accountId, com.sheout.marketplace.MarketplaceViews.PickupPoint pickup) {
        Optional<SellerProfileEntity> found = sellers.findLockedByAccountId(accountId);
        if (found.isEmpty()) {
            return Result.failure(MarketplaceError.NOT_A_SELLER);
        }
        SellerProfileEntity seller = found.get();
        if (pickup == null) {
            seller.setParcelPickup(null, null, null);
        } else {
            if (Math.abs(pickup.lat()) > 90 || Math.abs(pickup.lng()) > 180) {
                return Result.failure(MarketplaceError.INVALID_PICKUP);
            }
            String label = pickup.label() == null ? "" : pickup.label().trim().replaceAll("\\s+", " ");
            seller.setParcelPickup(label.isEmpty() ? null : label.substring(0, Math.min(label.length(), 200)),
                    pickup.lat(), pickup.lng());
        }
        return Result.success(views.sellerView(sellers.save(seller)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<com.sheout.marketplace.MarketplaceViews.PickupPoint> parcelPickupFor(UUID productId) {
        return products.findById(productId)
                .filter(ProductEntity::isActive)
                .flatMap(p -> sellers.findById(p.getSellerId()))
                .filter(ShopViews::live)
                .filter(s -> !s.isSample())
                .map(ShopViews::pickup);
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
     * The Razorpay order for her next month. No transaction is held open: the
     * gateway call can take seconds, and payments opens its own short ones.
     */
    @Override
    public Result<ListingFeeCheckout, MarketplaceError> startListingFeePayment(UUID accountId) {
        Optional<SellerProfileEntity> found = sellers.findByAccountId(accountId);
        if (found.isEmpty()) {
            return Result.failure(MarketplaceError.NOT_A_SELLER);
        }
        SellerProfileEntity seller = found.get();
        Optional<MarketplaceError> refused = payRefusal(seller);
        if (refused.isPresent()) {
            return Result.failure(refused.get());
        }
        int period = SellerPlan.nextPeriod(seller);
        Result<ListingFeeCheckout, PaymentError> checkout =
                paymentApi.startListingFeeCheckout(accountId, seller.getId(), period, limits.monthlyFee());
        if (checkout.isFailure()) {
            if (checkout.error() == PaymentError.ALREADY_CAPTURED) {
                // Paid, but her month was not added: the capture's event was
                // missed. Put that right now rather than ask her twice.
                renewIfPaid(seller.getId(), period);
                return Result.failure(MarketplaceError.ALREADY_PAID);
            }
            log.warn("Listing fee checkout for seller {} failed: {}", seller.getId(), checkout.error());
            return Result.failure(MarketplaceError.PAYMENT_FAILED);
        }
        return Result.success(checkout.value());
    }

    @Override
    public Result<SellerView, MarketplaceError> payListingFeeFromWallet(UUID accountId) {
        Optional<SellerProfileEntity> found = sellers.findByAccountId(accountId);
        if (found.isEmpty()) {
            return Result.failure(MarketplaceError.NOT_A_SELLER);
        }
        SellerProfileEntity seller = found.get();
        Optional<MarketplaceError> refused = payRefusal(seller);
        if (refused.isPresent()) {
            return Result.failure(refused.get());
        }
        int period = SellerPlan.nextPeriod(seller);
        Result<PaymentSummary, PaymentError> paid =
                paymentApi.payListingFeeFromWallet(accountId, seller.getId(), period, limits.monthlyFee());
        if (paid.isFailure()) {
            return Result.failure(switch (paid.error()) {
                case INSUFFICIENT_BALANCE -> MarketplaceError.INSUFFICIENT_BALANCE;
                case ALREADY_CAPTURED -> MarketplaceError.ALREADY_PAID;
                default -> MarketplaceError.PAYMENT_FAILED;
            });
        }
        // ListingFeePaid has run as the payment committed; this covers it if it could not.
        renewIfPaid(seller.getId(), period);
        return Result.success(mySeller(accountId).orElseThrow());
    }

    @Override
    public Result<SellerView, MarketplaceError> confirmListingFeePayment(UUID accountId, String orderId,
                                                                         String razorpayPaymentId, String signature) {
        Optional<SellerProfileEntity> found = sellers.findByAccountId(accountId);
        if (found.isEmpty()) {
            return Result.failure(MarketplaceError.NOT_A_SELLER);
        }
        int period = SellerPlan.nextPeriod(found.get());
        Result<PaymentSummary, PaymentError> confirmed = paymentApi.confirmListingFeeCheckout(
                accountId, found.get().getId(), period, orderId, razorpayPaymentId, signature);
        if (confirmed.isFailure()) {
            log.warn("Listing fee confirmation for seller {} failed: {}", found.get().getId(), confirmed.error());
            return Result.failure(confirmed.error() == PaymentError.SIGNATURE_INVALID
                    ? MarketplaceError.PAYMENT_NOT_VERIFIED : MarketplaceError.PAYMENT_FAILED);
        }
        // ListingFeePaid has been handled by now (it runs as the capture
        // commits); this also covers the case where it could not be.
        renewIfPaid(found.get().getId(), period);
        return Result.success(mySeller(accountId).orElseThrow());
    }

    /** Why she may not pay for her next month now, if she may not - before any order is made. */
    private Optional<MarketplaceError> payRefusal(SellerProfileEntity seller) {
        if (SellerPlan.canPay(seller, limits, Instant.now())) {
            return Optional.empty();
        }
        // Live with time to spare: this month is covered; the next can be paid in its last days.
        return Optional.of(seller.getStatus() == SellerStatus.ACTIVE
                ? MarketplaceError.ALREADY_PAID : MarketplaceError.NOT_AWAITING_PAYMENT);
    }

    /**
     * A month was captured. After the payment commits, in a transaction of
     * its own, so a problem here can never undo a payment Razorpay has taken.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onListingFeePaid(ListingFeePaid event) {
        sellers.findLockedById(event.sellerId()).ifPresentOrElse(seller -> renew(seller, event.period(), event.amount()),
                () -> log.error("Listing fee {} paid for unknown seller {}", event.paymentId(), event.sellerId()));
    }

    /** Its own short transaction, from calls that deliberately hold none open. */
    private void renewIfPaid(UUID sellerId, int period) {
        paymentApi.listingFeeFor(sellerId, period)
                .filter(p -> p.status() == PaymentStatus.CAPTURED)
                .ifPresent(p -> transactions.executeWithoutResult(status -> sellers.findLockedById(sellerId)
                        .ifPresent(s -> renew(s, period, p.amount()))));
    }

    /**
     * Adds the month - once: a repeat of the same capture finds it already
     * counted. Paid while suspended, the month is still hers, used if she is
     * reinstated.
     */
    private void renew(SellerProfileEntity seller, int period, BigDecimal amount) {
        SellerStatus before = seller.getStatus();
        if (!seller.paidFor(period, amount, Instant.now())) {
            return;
        }
        sellers.save(seller);
        log.info("Seller {} paid for month {}; listed until {}", seller.getId(), period, seller.getPaidUntil());
        if (before == SellerStatus.LAPSED) {
            events.publish(new SellerStatusChanged(seller.getId(), seller.getAccountId(), SellerStatus.ACTIVE,
                    amount, seller.getPaidUntil(), false));
        }
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
                s -> s.approve(adminId, limits.monthlyFee(), limits.freeMonths(), Instant.now()));
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
        // Back live if her month still runs; otherwise hidden until she pays.
        return decide(sellerId, SellerStatus.SUSPENDED, s -> s.reinstate(adminId, Instant.now(), limits.grace()));
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
        boolean approvedNow = from == SellerStatus.SUBMITTED_FOR_REVIEW && seller.getStatus() == SellerStatus.ACTIVE;
        events.publish(new SellerStatusChanged(seller.getId(), seller.getAccountId(), seller.getStatus(),
                limits.monthlyFee(), seller.getPaidUntil(), approvedNow));
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

    /**
     * Trimmed, with both numbers as ten digits and a blank area as none; the
     * shape of each field is checked where it arrives.
     */
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
        String area = d.area() == null || d.area().isBlank() ? null : d.area().trim().replaceAll("\\s+", " ");
        String website = null;
        if (d.websiteUrl() != null && !d.websiteUrl().isBlank()) {
            website = WebsiteAddress.normalize(d.websiteUrl());
            if (website == null) {
                return Result.failure(MarketplaceError.INVALID_WEBSITE);
            }
        }
        // OTHER needs her own words for what she sells; any other category has none.
        String custom = null;
        if (d.category() == SellerCategory.OTHER) {
            custom = d.customCategory() == null ? "" : d.customCategory().trim().replaceAll("\\s+", " ");
            if (custom.length() < 2 || custom.length() > 40) {
                return Result.failure(MarketplaceError.CUSTOM_CATEGORY_REQUIRED);
            }
        }
        return Result.success(new SellerDetails(d.businessName().trim(), d.category(), phone, whatsapp, area, website, custom));
    }
}
