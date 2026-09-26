package com.sheout.notifications.internal;

import com.sheout.auth.AccountRole;
import com.sheout.booking.BookingAccepted;
import com.sheout.booking.BookingApi;
import com.sheout.booking.BookingCancelled;
import com.sheout.booking.BookingCompleted;
import com.sheout.booking.BookingRequested;
import com.sheout.booking.BookingSummary;
import com.sheout.dispatch.DispatchExhausted;
import com.sheout.dispatch.DriverArriving;
import com.sheout.dispatch.DriverOffered;
import com.sheout.driververification.AccountVerified;
import com.sheout.driververification.VerificationRejected;
import com.sheout.driververification.VerificationSubmitted;
import com.sheout.notifications.SosAlertRaised;
import com.sheout.notifications.internal.channel.OutboundMessage;
import com.sheout.payments.PaymentCaptured;
import com.sheout.payments.PaymentMethod;
import com.sheout.payouts.PayoutMarkedPaid;
import com.sheout.campaigns.DriverIncentiveAwarded;
import com.sheout.support.SupportReplyPosted;
import com.sheout.users.AppLanguage;
import com.sheout.users.DriverProfileApi;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Reacts to other modules' domain events with notifications - no module calls
 * into notifications to send one. ALL user-facing notification copy lives
 * here; NotificationDispatcher decides how each one is delivered.
 * <p>
 * Every handler is AFTER_COMMIT, so nobody is told about something that then
 * rolled back, and {@code fallbackExecution = true}, because several events
 * are published outside any transaction (dispatch's sweeper offering the next
 * round, a partner's location report) and without it Spring silently drops
 * them. Every handler is also {@code @Async}: see NotificationDeliveryConfig.
 * <p>
 * Lock-screen rule: a notification is read by whoever is holding the phone.
 * No copy here includes what a support reply says, a rider's pickup address,
 * or anybody's phone number.
 */
@Component
class NotificationEventListeners {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter RECEIPT_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);

    private final NotificationDispatcher dispatcher;
    private final DriverProfileApi driverProfileApi;
    private final BookingApi bookingApi;
    private final NotificationCopy copy;

    NotificationEventListeners(NotificationDispatcher dispatcher, DriverProfileApi driverProfileApi, BookingApi bookingApi,
                               NotificationCopy copy) {
        this.dispatcher = dispatcher;
        this.driverProfileApi = driverProfileApi;
        this.bookingApi = bookingApi;
        this.copy = copy;
    }

    /**
     * A message to a rider or partner, in her language - see NotificationCopy.
     * The SMS, if one goes out, is the English, which is what the DLT
     * template is registered in.
     */
    private OutboundMessage localized(UUID to, String key, Function<AppLanguage, Map<String, String>> params,
                                      String link, String tag, OutboundMessage.Urgency urgency) {
        NotificationCopy.Localized m = copy.render(to, key, params);
        return new OutboundMessage(m.title(), m.body(), link, tag, urgency,
                "SheOut: " + m.englishTitle() + ". " + m.englishBody());
    }

    private OutboundMessage localized(UUID to, String key, Function<AppLanguage, Map<String, String>> params, String link) {
        return localized(to, key, params, link, null, OutboundMessage.Urgency.NORMAL);
    }

    private static Function<AppLanguage, Map<String, String>> none() {
        return language -> Map.of();
    }

    @Async(NotificationDeliveryConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBookingRequested(BookingRequested event) {
        dispatcher.deliver(event.customerId(), NotificationType.BOOKING_REQUESTED, localized(event.customerId(), "bookingRequested",
                language -> Map.of("category", copy.categoryName(event.category(), language)),
                "/tracking/" + event.bookingId()));
    }

    /**
     * The partner-facing alert that matters most: an offer she has seconds to
     * take. ALERT urgency - see FcmPushChannel and the apps' push-sw.js for
     * what that does on each platform.
     */
    @Async(NotificationDeliveryConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDriverOffered(DriverOffered event) {
        // No countdown in the text: the same words are her inbox history,
        // where "in 14 seconds" would be wrong a minute later.
        // Rounded up: the offer is created a few milliseconds before this event,
        // and a 15-second window must not read as 14.
        long windowSeconds = Math.max(1, (Duration.between(event.occurredAt(), event.expiresAt()).toMillis() + 999) / 1000);
        String km = String.format(Locale.ENGLISH, "%.1f", event.distanceKm());
        dispatcher.deliver(event.driverId(), NotificationType.DRIVER_OFFER, localized(event.driverId(), "driverOffer",
                language -> Map.of("category", copy.categoryName(event.category(), language), "km", km,
                        "seconds", String.valueOf(windowSeconds)),
                "/offer/" + event.bookingId(), "offer-" + event.bookingId(), OutboundMessage.Urgency.ALERT));
    }

    @Async(NotificationDeliveryConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBookingAccepted(BookingAccepted event) {
        dispatcher.deliver(event.customerId(), NotificationType.BOOKING_ACCEPTED, localized(event.customerId(), "bookingAccepted",
                language -> Map.of("driver", driverName(event.driverId(), language)),
                "/tracking/" + event.bookingId(), "booking-" + event.bookingId(), OutboundMessage.Urgency.NORMAL));
    }

    @Async(NotificationDeliveryConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDriverArriving(DriverArriving event) {
        dispatcher.deliver(event.customerId(), NotificationType.DRIVER_ARRIVING, localized(event.customerId(), "driverArriving",
                language -> Map.of("driver", driverName(event.driverId(), language)),
                "/tracking/" + event.bookingId(), "booking-" + event.bookingId(), OutboundMessage.Urgency.NORMAL));
    }

    @Async(NotificationDeliveryConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBookingCompleted(BookingCompleted event) {
        dispatcher.deliver(event.customerId(), NotificationType.BOOKING_COMPLETED, localized(event.customerId(), "bookingCompleted",
                language -> Map.of("fare", rupees(event.finalFare())),
                "/tracking/" + event.bookingId(), "booking-" + event.bookingId(), OutboundMessage.Urgency.NORMAL));
    }

    /**
     * Only the person who did NOT cancel is told. Notifying somebody of the
     * cancellation she just tapped is noise; a cancellation by the system
     * (no one account) tells both.
     */
    @Async(NotificationDeliveryConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBookingCancelled(BookingCancelled event) {
        UUID by = event.cancelledBy();
        if (!event.customerId().equals(by)) {
            boolean partnerCancelled = event.driverId() != null && event.driverId().equals(by);
            dispatcher.deliver(event.customerId(), NotificationType.BOOKING_CANCELLED, localized(event.customerId(),
                    partnerCancelled ? "cancelledByPartner" : "cancelledForRider", none(),
                    "/tracking/" + event.bookingId()));
        }
        if (event.driverId() != null && !event.driverId().equals(by)) {
            dispatcher.deliver(event.driverId(), NotificationType.BOOKING_CANCELLED, localized(event.driverId(),
                    "cancelledForPartner", none(),
                    // The trip itself, which now shows as a record once it
                    // has ended. Home said nothing about which trip, so the
                    // tap looked like it had done nothing.
                    "/trip/" + event.bookingId()));
        }
    }

    @Async(NotificationDeliveryConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDispatchExhausted(DispatchExhausted event) {
        dispatcher.deliver(event.customerId(), NotificationType.NO_DRIVERS_AVAILABLE, localized(event.customerId(), "noDrivers",
                none(), "/tracking/" + event.bookingId()));
    }

    /**
     * Operations hears that somebody is waiting.
     * <p>
     * How long she waits is mostly how long it takes anybody to notice, and
     * until this nobody was told at all: the queue grew and was found on the
     * next visit to the console. No names and no document - an alert on a
     * lock screen says that there is work, and the work itself is behind a
     * login.
     */
    @Async(NotificationDeliveryConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onVerificationSubmitted(VerificationSubmitted event) {
        boolean driver = event.role() == AccountRole.DRIVER;
        dispatcher.deliverToRole(AccountRole.ADMIN, NotificationType.VERIFICATION_SUBMITTED, new OutboundMessage(
                driver ? "A partner is waiting for verification" : "A rider is waiting for verification",
                "Somebody has submitted an ID and cannot use SheOut until it is reviewed. Open the console.",
                "/admin/index.html",
                "verification-" + event.accountId(),
                OutboundMessage.Urgency.NORMAL));
    }

    /**
     * The answer, when it is no. She used to be told nothing: the app went on
     * saying "being reviewed" and the reason an operator typed stayed in the
     * database.
     */
    @Async(NotificationDeliveryConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onVerificationRejected(VerificationRejected event) {
        // The reason is what the operator typed, in the operator's words.
        dispatcher.deliver(event.accountId(), NotificationType.ACCOUNT_VERIFICATION_REJECTED, localized(event.accountId(),
                "verificationRejected", language -> Map.of("reason", event.reason() == null ? "" : event.reason()),
                "/verification"));
    }

    @Async(NotificationDeliveryConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAccountVerified(AccountVerified event) {
        boolean driver = event.role() == AccountRole.DRIVER;
        dispatcher.deliver(event.accountId(), NotificationType.ACCOUNT_VERIFIED, localized(event.accountId(),
                driver ? "verifiedPartner" : "verifiedRider", none(), "/home"));
    }

    /**
     * Says a reply is waiting and where, never what it says: a reply about a
     * safety concern or a dispute is not for a lock screen, and neither is
     * the subject she wrote.
     */
    @Async(NotificationDeliveryConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSupportReplyPosted(SupportReplyPosted event) {
        dispatcher.deliver(event.recipientAccountId(), NotificationType.SUPPORT_REPLY, localized(event.recipientAccountId(),
                "supportReply", none(),
                "/help/tickets/" + event.ticketId(), "ticket-" + event.ticketId(), OutboundMessage.Urgency.NORMAL));
    }

    @Async(NotificationDeliveryConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPayoutMarkedPaid(PayoutMarkedPaid event) {
        dispatcher.deliver(event.driverAccountId(), NotificationType.PAYOUT_PAID, localized(event.driverAccountId(), "payoutPaid",
                language -> Map.of("amount", rupees(event.amount()), "reference", String.valueOf(event.paymentReference())),
                "/payouts"));
    }

    @Async(NotificationDeliveryConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onIncentiveAwarded(DriverIncentiveAwarded event) {
        dispatcher.deliver(event.driverId(), NotificationType.INCENTIVE_EARNED, localized(event.driverId(), "incentive",
                language -> Map.of("amount", rupees(event.amount()), "name", event.incentiveName()),
                "/earnings"));
    }

    /** The receipt: emailed if she has an address, and kept in her inbox either way. */
    @Async(NotificationDeliveryConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPaymentCaptured(PaymentCaptured event) {
        bookingApi.findById(event.bookingId()).ifPresent(booking -> dispatcher.deliver(
                booking.customerId(), NotificationType.PAYMENT_RECEIPT, localized(booking.customerId(), "receipt",
                        language -> receiptParams(event, booking, language), "/profile/payments")));
    }

    /**
     * Every operator device, for every press. No rider name or location on
     * the lock screen - both are one tap away in the console, behind a login.
     */
    @Async(NotificationDeliveryConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSosAlertRaised(SosAlertRaised event) {
        dispatcher.deliverToRole(AccountRole.ADMIN, NotificationType.SOS_OPERATOR_ALERT, new OutboundMessage(
                "SOS raised",
                event.bookingId() == null
                        ? "Raised with no trip under way. Open the console now."
                        : "Raised during a trip. Open the console now.",
                "/admin/index.html",
                "sos-" + event.alertId(),
                OutboundMessage.Urgency.ALERT));
    }

    private Map<String, String> receiptParams(PaymentCaptured event, BookingSummary booking, AppLanguage language) {
        // Each way of paying in its own words. A SheOut wallet payment fell to
        // the catch-all and read "Paid online by online payment".
        String how = switch (event.method()) {
            case CASH -> copy.one(language, "receipt.cash");
            case SHEOUT_WALLET -> copy.one(language, "receipt.sheoutWallet");
            case PROMO_CREDIT -> copy.one(language, "receipt.promoCredit");
            default -> copy.one(language, "receipt.online").replace("{method}", methodName(event.method(), language));
        };
        return Map.of(
                "amount", rupees(event.amount()),
                "how", how,
                "when", RECEIPT_TIME.format(event.capturedAt().atZone(INDIA)),
                "category", copy.categoryName(booking.category(), language),
                "from", booking.pickup().label(),
                "to", booking.drop().label());
    }

    private String driverName(UUID driverId, AppLanguage language) {
        return driverProfileApi.findByAccountId(driverId)
                .map(driver -> driver.name())
                .filter(name -> name != null && !name.isBlank())
                .orElse(copy.one(language, "yourPartner"));
    }

    private String methodName(PaymentMethod method, AppLanguage language) {
        return switch (method) {
            case UPI, CARD, NETBANKING, WALLET -> copy.one(language, "method." + method.name());
            default -> copy.one(language, "method.OTHER");
        };
    }

    private static String rupees(BigDecimal amount) {
        return "₹" + (amount == null ? "0.00" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }
}
