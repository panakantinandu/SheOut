package com.sheout.notifications.internal;

import com.sheout.auth.AccountRole;
import com.sheout.booking.BookingAccepted;
import com.sheout.booking.BookingApi;
import com.sheout.booking.BookingCancelled;
import com.sheout.booking.BookingCategory;
import com.sheout.booking.BookingCompleted;
import com.sheout.booking.BookingRequested;
import com.sheout.booking.BookingSummary;
import com.sheout.dispatch.DispatchExhausted;
import com.sheout.dispatch.DriverArriving;
import com.sheout.dispatch.DriverOffered;
import com.sheout.driververification.AccountVerified;
import com.sheout.notifications.SosAlertRaised;
import com.sheout.notifications.internal.channel.OutboundMessage;
import com.sheout.payments.PaymentCaptured;
import com.sheout.payments.PaymentMethod;
import com.sheout.payouts.PayoutMarkedPaid;
import com.sheout.support.SupportReplyPosted;
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
import java.util.UUID;

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

    NotificationEventListeners(NotificationDispatcher dispatcher, DriverProfileApi driverProfileApi, BookingApi bookingApi) {
        this.dispatcher = dispatcher;
        this.driverProfileApi = driverProfileApi;
        this.bookingApi = bookingApi;
    }

    @Async(NotificationDeliveryConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBookingRequested(BookingRequested event) {
        dispatcher.deliver(event.customerId(), NotificationType.BOOKING_REQUESTED, OutboundMessage.of(
                "Booking requested",
                "We're finding a " + categoryName(event.category()) + " partner near you.",
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
        dispatcher.deliver(event.driverId(), NotificationType.DRIVER_OFFER, new OutboundMessage(
                "New " + categoryName(event.category()) + " trip request",
                String.format(Locale.ENGLISH, "%.1f km from you. Offers last %d seconds - open SheOut to accept.",
                        event.distanceKm(), windowSeconds),
                "/offer/" + event.bookingId(),
                "offer-" + event.bookingId(),
                OutboundMessage.Urgency.ALERT));
    }

    @Async(NotificationDeliveryConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBookingAccepted(BookingAccepted event) {
        dispatcher.deliver(event.customerId(), NotificationType.BOOKING_ACCEPTED, new OutboundMessage(
                driverName(event.driverId()) + " is on the way",
                "Check her photo and vehicle number in the app before you get in.",
                "/tracking/" + event.bookingId(), "booking-" + event.bookingId(), OutboundMessage.Urgency.NORMAL));
    }

    @Async(NotificationDeliveryConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDriverArriving(DriverArriving event) {
        dispatcher.deliver(event.customerId(), NotificationType.DRIVER_ARRIVING, new OutboundMessage(
                driverName(event.driverId()) + " is arriving",
                "She is almost at your pickup. Have your pickup code ready.",
                "/tracking/" + event.bookingId(), "booking-" + event.bookingId(), OutboundMessage.Urgency.NORMAL));
    }

    @Async(NotificationDeliveryConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBookingCompleted(BookingCompleted event) {
        dispatcher.deliver(event.customerId(), NotificationType.BOOKING_COMPLETED, new OutboundMessage(
                "You have arrived - payment due",
                "Fare " + rupees(event.finalFare()) + ". Pay in the app, from your SheOut wallet or online, to finish the trip.",
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
            dispatcher.deliver(event.customerId(), NotificationType.BOOKING_CANCELLED, OutboundMessage.of(
                    "Your booking was cancelled",
                    event.driverId() != null && event.driverId().equals(by)
                            ? "Your partner had to cancel. You have not been charged - book again whenever you are ready."
                            : "You have not been charged - book again whenever you are ready.",
                    "/tracking/" + event.bookingId()));
        }
        if (event.driverId() != null && !event.driverId().equals(by)) {
            dispatcher.deliver(event.driverId(), NotificationType.BOOKING_CANCELLED, OutboundMessage.of(
                    "Trip cancelled",
                    "The rider cancelled this trip. You do not need to go to the pickup.",
                    // The trip itself, which now shows as a record once it
                    // has ended. Home said nothing about which trip, so the
                    // tap looked like it had done nothing.
                    "/trip/" + event.bookingId()));
        }
    }

    @Async(NotificationDeliveryConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDispatchExhausted(DispatchExhausted event) {
        dispatcher.deliver(event.customerId(), NotificationType.NO_DRIVERS_AVAILABLE, OutboundMessage.of(
                "No partners available right now",
                "Nobody nearby could take this trip. Nothing was charged - try again in a few minutes.",
                "/tracking/" + event.bookingId()));
    }

    @Async(NotificationDeliveryConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAccountVerified(AccountVerified event) {
        boolean driver = event.role() == AccountRole.DRIVER;
        dispatcher.deliver(event.accountId(), NotificationType.ACCOUNT_VERIFIED, OutboundMessage.of(
                "Your account is verified",
                driver ? "You can go online and accept trips." : "You can now book rides and deliveries.",
                "/home"));
    }

    /**
     * Says a reply is waiting and where, never what it says: a reply about a
     * safety concern or a dispute is not for a lock screen, and neither is
     * the subject she wrote.
     */
    @Async(NotificationDeliveryConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSupportReplyPosted(SupportReplyPosted event) {
        dispatcher.deliver(event.recipientAccountId(), NotificationType.SUPPORT_REPLY, new OutboundMessage(
                "Support replied to your ticket",
                "Open Help & Support in the app to read the reply.",
                "/help/tickets/" + event.ticketId(), "ticket-" + event.ticketId(), OutboundMessage.Urgency.NORMAL));
    }

    @Async(NotificationDeliveryConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPayoutMarkedPaid(PayoutMarkedPaid event) {
        dispatcher.deliver(event.driverAccountId(), NotificationType.PAYOUT_PAID, OutboundMessage.of(
                "Payout sent: " + rupees(event.amount()),
                "SheOut has sent your payout. Bank or UPI reference: " + event.paymentReference() + ".",
                "/payouts"));
    }

    /** The receipt: emailed if she has an address, and kept in her inbox either way. */
    @Async(NotificationDeliveryConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPaymentCaptured(PaymentCaptured event) {
        bookingApi.findById(event.bookingId()).ifPresent(booking -> dispatcher.deliver(
                booking.customerId(), NotificationType.PAYMENT_RECEIPT, OutboundMessage.of(
                        "Receipt: " + rupees(event.amount()) + " paid",
                        receiptBody(event, booking),
                        "/profile/payments")));
    }

    /**
     * Every operator device, for every press. No rider name or location on
     * the lock screen - both are one tap away in the console, behind a login.
     */
    @Async(NotificationDeliveryConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSosAlertRaised(SosAlertRaised event) {
        dispatcher.deliverToRole(AccountRole.ADMIN, NotificationType.SOS_OPERATOR_ALERT, new OutboundMessage(
                "SOS raised by a rider",
                event.bookingId() == null
                        ? "Raised with no trip under way. Open the console now."
                        : "Raised during a trip. Open the console now.",
                "/admin/index.html",
                "sos-" + event.alertId(),
                OutboundMessage.Urgency.ALERT));
    }

    private String receiptBody(PaymentCaptured event, BookingSummary booking) {
        String how = event.method() == PaymentMethod.CASH ? "in cash to your partner" : "online by " + methodName(event.method());
        return "Paid " + how + " on " + RECEIPT_TIME.format(event.capturedAt().atZone(INDIA))
                + " for your " + categoryName(booking.category()) + " trip from " + booking.pickup().label()
                + " to " + booking.drop().label() + ".";
    }

    private String driverName(UUID driverId) {
        return driverProfileApi.findByAccountId(driverId)
                .map(driver -> driver.name())
                .filter(name -> name != null && !name.isBlank())
                .orElse("Your partner");
    }

    private static String categoryName(BookingCategory category) {
        return switch (category) {
            case BIKE -> "bike taxi";
            case AUTO -> "auto";
            case CAB -> "cab";
            case PARCEL -> "parcel delivery";
            case LUNCHBOX -> "lunch box delivery";
        };
    }

    private static String methodName(PaymentMethod method) {
        return switch (method) {
            case UPI -> "UPI";
            case CARD -> "card";
            case NETBANKING -> "netbanking";
            case WALLET -> "wallet";
            default -> "online payment";
        };
    }

    private static String rupees(BigDecimal amount) {
        return "₹" + (amount == null ? "0.00" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }
}
