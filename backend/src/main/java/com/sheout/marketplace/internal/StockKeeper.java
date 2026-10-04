package com.sheout.marketplace.internal;

import com.sheout.booking.ParcelOrderDelivered;
import com.sheout.marketplace.ProductTerms;
import com.sheout.marketplace.SellerStockNotice;
import com.sheout.sharedkernel.cluster.ClusterLock;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Keeps a seller's stock as right as SheOut can see it, so a buyer is not
 * told "in stock" about a saree that went last week.
 * <p>
 * SheOut does not see a sale - it happens on a call between two women - but
 * it sees two things near one:
 * <ul>
 *   <li>A Marketplace parcel delivered: its product and quantity came with
 *       the booking, so her counted stock goes down by that many, and at
 *       none the product is marked out of stock. Only on delivery, never at
 *       booking: a cancelled parcel changes nothing. She is told.</li>
 *   <li>Buyers asking: each tap on Call or WhatsApp is an enquiry. In the
 *       evening a seller who had any is asked once, "Sold any? Update your
 *       stock" - opening on that product - so a sale made off the app is not
 *       left on the shelf.</li>
 * </ul>
 */
@Component
class StockKeeper {

    private static final Logger log = LoggerFactory.getLogger(StockKeeper.class);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final ProductRepository products;
    private final SellerProfileRepository sellers;
    private final ProductEnquiries enquiries;
    private final DomainEventPublisher events;
    private final ClusterLock lock;
    private final TransactionTemplate tx;

    StockKeeper(ProductRepository products, SellerProfileRepository sellers, ProductEnquiries enquiries,
                DomainEventPublisher events, ClusterLock lock, PlatformTransactionManager transactions) {
        this.products = products;
        this.sellers = sellers;
        this.enquiries = enquiries;
        this.events = events;
        this.lock = lock;
        this.tx = new TransactionTemplate(transactions);
    }

    // ------------------------------------------------------------ a parcel delivered

    /**
     * After the trip commits, in its own transaction: a stock mistake here
     * must never undo a delivered trip.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onParcelOrderDelivered(ParcelOrderDelivered event) {
        Optional<ProductEntity> found = products.findById(event.orderProductId());
        if (found.isEmpty()) {
            return;
        }
        ProductEntity product = found.get();
        Optional<SellerProfileEntity> seller = sellers.findLockedById(product.getSellerId())
                .filter(s -> !s.isSample());
        if (seller.isEmpty()) {
            return;
        }
        ProductTerms terms = product.getTerms();
        // Only a counted stock can be counted down. "In stock", made to
        // order or already sold out: nothing to subtract - she is still told
        // it was delivered, so she can decide.
        Integer left = null;
        if (terms.availability() == ProductTerms.Availability.IN_STOCK && terms.quantityAvailable() != null) {
            int remaining = terms.quantityAvailable() - event.orderQuantity();
            product.setTerms(remaining > 0
                    ? withStock(terms, ProductTerms.Availability.IN_STOCK, remaining)
                    : withStock(terms, ProductTerms.Availability.OUT_OF_STOCK, null));
            products.save(product);
            left = Math.max(0, remaining);
            log.info("Parcel {} delivered {} of product {}: {} left", event.bookingId(), event.orderQuantity(),
                    product.getCode(), left);
        }
        events.publish(new SellerStockNotice(seller.get().getAccountId(), SellerStockNotice.Kind.DELIVERED,
                product.getTitle(), product.getCode(), event.orderQuantity(), left));
    }

    static ProductTerms withStock(ProductTerms t, ProductTerms.Availability availability, Integer quantity) {
        return new ProductTerms(availability, quantity, t.readyInDays(), t.priceUnit(), t.minOrderQuantity(), t.options(),
                t.fulfilment(), t.deliveryNote(), t.returnPolicy());
    }

    // ------------------------------------------------------------ buyers asking

    /**
     * A buyer tapped Call or WhatsApp on a product. Not her own, not a
     * sample, and only a live listing; once a day per buyer.
     */
    @Transactional
    public void recordEnquiry(UUID productId, UUID buyerAccountId) {
        products.findById(productId)
                .filter(ProductEntity::isActive)
                .ifPresent(product -> sellers.findById(product.getSellerId())
                        .filter(ShopViews::live)
                        .filter(s -> !s.isSample())
                        .filter(s -> !s.getAccountId().equals(buyerAccountId))
                        .ifPresent(s -> enquiries.record(productId, buyerAccountId, LocalDate.now(INDIA))));
    }

    /** Enquiries today, by product - "3 enquiries today" on her product cards. */
    Map<UUID, Integer> enquiriesToday(List<UUID> productIds) {
        return enquiries.countsOn(productIds, LocalDate.now(INDIA));
    }

    /** At 19:30 in India: one "sold any?" to each seller buyers asked today. */
    @Scheduled(cron = "${sheout.marketplace.enquiry-reminder-cron:0 30 19 * * *}", zone = "Asia/Kolkata")
    void sweep() {
        lock.runExclusively("seller-enquiry-reminders", Duration.ofMinutes(10), () -> remindSellers(LocalDate.now(INDIA)));
    }

    /** Package-visible for tests, which pass the day. Returns how many sellers were reminded. */
    int remindSellers(LocalDate day) {
        List<UUID> asked = enquiries.productsAskedAbout(day);
        if (asked.isEmpty()) {
            return 0;
        }
        Map<UUID, Integer> counts = enquiries.countsOn(asked, day);
        // Still on sale: a product already sold out or hidden has nothing to update.
        Map<UUID, List<ProductEntity>> bySeller = products.findAllById(asked).stream()
                .filter(ProductEntity::isActive)
                .filter(p -> p.getTerms().availability() != ProductTerms.Availability.OUT_OF_STOCK)
                .collect(Collectors.groupingBy(ProductEntity::getSellerId));
        int reminded = 0;
        for (Map.Entry<UUID, List<ProductEntity>> entry : bySeller.entrySet()) {
            ProductEntity top = entry.getValue().stream()
                    .max(Comparator.comparingInt(p -> counts.getOrDefault(p.getId(), 0)))
                    .orElseThrow();
            Boolean sent = tx.execute(status -> sellers.findLockedById(entry.getKey())
                    .filter(ShopViews::live)
                    .filter(s -> !s.isSample())
                    .filter(s -> !day.equals(s.getLastEnquiryNudge()))
                    .map(s -> {
                        s.enquiryNudgeSent(day);
                        sellers.save(s);
                        events.publish(new SellerStockNotice(s.getAccountId(), SellerStockNotice.Kind.ENQUIRIES,
                                top.getTitle(), top.getCode(), counts.getOrDefault(top.getId(), 1), null));
                        return true;
                    })
                    .orElse(false));
            if (Boolean.TRUE.equals(sent)) {
                reminded++;
            }
        }
        return reminded;
    }
}
