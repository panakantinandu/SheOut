package com.sheout.admin.internal;

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
                summary.updatedAt()
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
                alert.createdAt()
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

    private String phoneFor(UUID accountId) {
        return authApi.findAccount(accountId).map(a -> a.phoneNumber()).orElse(null);
    }
}
