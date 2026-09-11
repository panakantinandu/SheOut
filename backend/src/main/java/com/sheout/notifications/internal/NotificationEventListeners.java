package com.sheout.notifications.internal;

import com.sheout.auth.AccountRole;
import com.sheout.auth.AuthApi;
import com.sheout.booking.BookingAccepted;
import com.sheout.booking.BookingCancelled;
import com.sheout.booking.BookingCompleted;
import com.sheout.booking.BookingRequested;
import com.sheout.driververification.AccountVerified;
import com.sheout.notifications.internal.channel.NotificationChannel;
import com.sheout.sharedkernel.Result;
import com.sheout.users.DriverProfileApi;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Reacts to domain events from booking/driver-verification rather than
 * those modules calling into notifications directly - same convention as
 * dispatch's DispatchService.onBookingRequested and payments'
 * BookingCompletedListener. ALL notification copy lives here (see the
 * module's package-info) - no other module constructs a user-facing
 * message.
 * <p>
 * Scoped to exactly the 5 events the notifications spec named:
 * BookingRequested, BookingAccepted, BookingCompleted, BookingCancelled,
 * AccountVerified. Real events exist for other transitions too
 * (BookingMatched, BookingStarted, AccountRegistered) - wiring any of them
 * is the same pattern as the methods below, just not done in this pass
 * since they weren't named.
 * <p>
 * AFTER_COMMIT (not a plain @EventListener, contrast
 * UserProfileEventListeners) for the same reason payments' listener uses
 * it: an SMS provider call is a slow external call that shouldn't run
 * inside - or be able to roll back - the publishing module's own
 * transaction. See NotificationLogService's Javadoc for the self-invocation
 * trap this shape has to avoid.
 */
@Component
class NotificationEventListeners {

    private final NotificationChannel smsChannel;
    private final NotificationLogService notificationLogService;
    private final AuthApi authApi;
    private final DriverProfileApi driverProfileApi;

    NotificationEventListeners(NotificationChannel smsChannel, NotificationLogService notificationLogService,
                                AuthApi authApi, DriverProfileApi driverProfileApi) {
        this.smsChannel = smsChannel;
        this.notificationLogService = notificationLogService;
        this.authApi = authApi;
        this.driverProfileApi = driverProfileApi;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingRequested(BookingRequested event) {
        notify(event.customerId(), NotificationType.BOOKING_REQUESTED,
                "SheOut: We're finding a nearby " + event.category() + " for you. We'll text you the moment a driver is assigned.");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingAccepted(BookingAccepted event) {
        String driverName = driverProfileApi.findByAccountId(event.driverId())
                .map(driver -> driver.name())
                .filter(name -> name != null && !name.isBlank())
                .orElse("Your driver");
        notify(event.customerId(), NotificationType.BOOKING_ACCEPTED,
                "SheOut: " + driverName + " has accepted your ride and is on the way!");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCompleted(BookingCompleted event) {
        notify(event.customerId(), NotificationType.BOOKING_COMPLETED,
                "SheOut: Your trip is complete. Fare: Rs " + event.finalFare() + ". Thanks for riding with SheOut!");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCancelled(BookingCancelled event) {
        notify(event.customerId(), NotificationType.BOOKING_CANCELLED, "SheOut: Your booking has been cancelled.");
        if (event.driverId() != null) {
            notify(event.driverId(), NotificationType.BOOKING_CANCELLED, "SheOut: The booking assigned to you has been cancelled.");
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountVerified(AccountVerified event) {
        String message = event.role() == AccountRole.DRIVER
                ? "SheOut: Your account is verified! You can now go online and accept rides."
                : "SheOut: Your account is verified! You can now book rides.";
        notify(event.accountId(), NotificationType.ACCOUNT_VERIFIED, message);
    }

    private void notify(UUID accountId, NotificationType type, String message) {
        String phone = authApi.findAccount(accountId).map(account -> account.phoneNumber()).orElse(null);
        if (phone == null) {
            notificationLogService.recordLog(accountId, null, type, NotificationChannelType.SMS,
                    Result.failure(SendFailure.of(NotificationError.NO_RECIPIENT_ADDRESS)));
            return;
        }
        Result<Void, SendFailure> outcome = smsChannel.send(phone, message);
        notificationLogService.recordLog(accountId, phone, type, NotificationChannelType.SMS, outcome);
    }
}
