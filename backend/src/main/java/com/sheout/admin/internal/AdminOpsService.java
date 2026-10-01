package com.sheout.admin.internal;

import com.sheout.admin.internal.OpsViews.AccountDetail;
import com.sheout.admin.internal.OpsViews.BookingDetail;
import com.sheout.admin.internal.OpsViews.LiveOps;
import com.sheout.admin.internal.OpsViews.LivePartner;
import com.sheout.admin.internal.OpsViews.LiveTrip;
import com.sheout.admin.internal.OpsViews.PartnerFacts;
import com.sheout.admin.internal.OpsViews.Party;
import com.sheout.admin.internal.OpsViews.ServiceHoursChangeRow;
import com.sheout.admin.internal.OpsViews.ServiceHoursView;
import com.sheout.admin.internal.OpsViews.TripRow;
import com.sheout.auth.AccountRole;
import com.sheout.auth.AccountSummary;
import com.sheout.auth.AuthApi;
import com.sheout.booking.BookingApi;
import com.sheout.booking.BookingQuery;
import com.sheout.booking.BookingStatus;
import com.sheout.booking.BookingSummary;
import com.sheout.booking.ServiceHoursApi;
import com.sheout.dispatch.DriverLocation;
import com.sheout.dispatch.DriverLocationApi;
import com.sheout.dispatch.TripTrailApi;
import com.sheout.driververification.ShiftCheckApi;
import com.sheout.driververification.VerificationApi;
import com.sheout.driververification.VerificationSummary;
import com.sheout.notifications.SosAlertSummary;
import com.sheout.notifications.SosApi;
import com.sheout.notifications.SosStatus;
import com.sheout.payments.PaymentApi;
import com.sheout.payments.PaymentSummary;
import com.sheout.payouts.PayoutAccount;
import com.sheout.payouts.PayoutApi;
import com.sheout.ratings.Rating;
import com.sheout.ratings.RatingsApi;
import com.sheout.users.CustomerProfileApi;
import com.sheout.users.CustomerProfileSummary;
import com.sheout.users.DriverProfileApi;
import com.sheout.users.DriverProfileSummary;
import com.sheout.users.EmergencyContactsApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The console's full views of a person, a trip and the live service.
 * <p>
 * Like AdminService, this owns no data: every fact is read through another
 * module's public interface and joined here. The lookups are per row - a
 * live view of a few hundred partners is a few hundred profile reads every
 * refresh - which is the same trade AdminService makes and is fine at
 * operations scale. If the live view ever grows past that, the fix is a
 * batch read on the profile API, not reaching into its tables.
 */
@Service
public class AdminOpsService {

    private static final Set<BookingStatus> LIVE = EnumSet.of(
            BookingStatus.REQUESTED, BookingStatus.MATCHED, BookingStatus.ACCEPTED, BookingStatus.IN_PROGRESS);
    private static final int RECENT_TRIPS = 25;
    private static final int MAX_LIVE_TRIPS = 300;

    private final AdminService adminService;
    private final AuthApi authApi;
    private final BookingApi bookingApi;
    private final CustomerProfileApi customers;
    private final DriverProfileApi drivers;
    private final EmergencyContactsApi emergencyContacts;
    private final VerificationApi verificationApi;
    private final ShiftCheckApi shiftChecks;
    private final PaymentApi paymentApi;
    private final PayoutApi payoutApi;
    private final SosApi sosApi;
    private final DriverLocationApi locations;
    private final TripTrailApi trails;
    private final RatingsApi ratings;
    private final ServiceHoursApi serviceHours;
    private final long staleAfterSeconds;

    public AdminOpsService(AdminService adminService, AuthApi authApi, BookingApi bookingApi,
                           CustomerProfileApi customers, DriverProfileApi drivers,
                           EmergencyContactsApi emergencyContacts, VerificationApi verificationApi,
                           ShiftCheckApi shiftChecks, PaymentApi paymentApi, PayoutApi payoutApi, SosApi sosApi,
                           DriverLocationApi locations, TripTrailApi trails, RatingsApi ratings,
                           ServiceHoursApi serviceHours,
                           // The same age past which dispatch stops trusting a fix.
                           @Value("${sheout.booking.completion.driver-location-max-age-seconds:30}") long fixMaxAge) {
        this.adminService = adminService;
        this.authApi = authApi;
        this.bookingApi = bookingApi;
        this.customers = customers;
        this.drivers = drivers;
        this.emergencyContacts = emergencyContacts;
        this.verificationApi = verificationApi;
        this.shiftChecks = shiftChecks;
        this.paymentApi = paymentApi;
        this.payoutApi = payoutApi;
        this.sosApi = sosApi;
        this.locations = locations;
        this.trails = trails;
        this.ratings = ratings;
        this.serviceHours = serviceHours;
        // A partner whose app has been quiet for a couple of minutes is
        // shown, marked, rather than dropped: "online but not reporting" is
        // itself something an operator may need to ring her about.
        this.staleAfterSeconds = Math.max(120, fixMaxAge * 4);
    }

    // ---- one person -------------------------------------------------------

    public Optional<AccountDetail> accountDetail(UUID accountId) {
        Optional<AccountOpsRow> row = adminService.findAccountRow(accountId);
        if (row.isEmpty()) {
            return Optional.empty();
        }
        AccountOpsRow account = row.get();
        Optional<VerificationSummary> verification = verificationApi.findByAccountId(accountId);
        List<TripRow> trips = bookingApi.findRecentForAccount(accountId, RECENT_TRIPS).stream()
                .map(b -> toTripRow(b, accountId))
                .toList();
        List<SosAlertSummary> sos = sosApi.findRecentForAccount(accountId);
        Instant lastActive = authApi.lastActiveAt(accountId).orElse(null);

        if (account.role() == AccountRole.DRIVER) {
            Optional<DriverProfileSummary> p = drivers.findByAccountId(accountId);
            Optional<PayoutAccount> payout = payoutApi.getPayoutAccount(accountId);
            PartnerFacts partner = new PartnerFacts(
                    p.map(DriverProfileSummary::vehicleType).orElse(null),
                    p.map(DriverProfileSummary::vehicleRegistrationNumber).orElse(null),
                    p.map(DriverProfileSummary::panNumber).map(AdminOpsService::lastFour).orElse(null),
                    p.map(DriverProfileSummary::onlineStatus).orElse(null),
                    locations.findLocation(accountId).orElse(null),
                    bookingApi.hasActiveTripAsDriver(accountId),
                    payoutApi.getWallet(accountId),
                    payout.map(PayoutAccount::hasBankAccount).orElse(false),
                    payout.map(PayoutAccount::maskedAccountNumber).orElse(null),
                    payout.map(PayoutAccount::ifsc).orElse(null),
                    payout.map(PayoutAccount::upiVpa).orElse(null),
                    shiftChecks.stateFor(accountId));
            return Optional.of(new AccountDetail(account, lastActive,
                    p.map(DriverProfileSummary::dateOfBirth).orElse(null),
                    p.map(DriverProfileSummary::profilePhotoUrl).orElse(null),
                    p.map(DriverProfileSummary::profileComplete).orElse(false),
                    verification.map(VerificationSummary::documentSubmitted).orElse(false),
                    verification.map(VerificationSummary::documentSubmittedAt).orElse(null),
                    verification.map(VerificationSummary::rejectionReason).orElse(null),
                    null, null, null, null, partner, trips, sos));
        }
        Optional<CustomerProfileSummary> p = customers.findByAccountId(accountId);
        return Optional.of(new AccountDetail(account, lastActive,
                p.map(CustomerProfileSummary::dateOfBirth).orElse(null),
                p.map(CustomerProfileSummary::profilePhotoUrl).orElse(null),
                p.map(CustomerProfileSummary::profileComplete).orElse(false),
                verification.map(VerificationSummary::documentSubmitted).orElse(false),
                verification.map(VerificationSummary::documentSubmittedAt).orElse(null),
                verification.map(VerificationSummary::rejectionReason).orElse(null),
                p.map(CustomerProfileSummary::home).orElse(null),
                p.map(CustomerProfileSummary::work).orElse(null),
                emergencyContacts.findContactsByAccountId(accountId),
                paymentApi.riderWalletBalance(accountId),
                null, trips, sos));
    }

    private TripRow toTripRow(BookingSummary b, UUID viewer) {
        boolean asPartner = viewer.equals(b.driverId());
        UUID other = asPartner ? b.customerId() : b.driverId();
        return new TripRow(b.id(), b.status(), b.type(), b.category(),
                asPartner ? AccountRole.DRIVER : AccountRole.CUSTOMER,
                other == null ? null : asPartner ? riderName(other) : partnerName(other),
                b.pickup() == null ? null : b.pickup().label(),
                b.drop() == null ? null : b.drop().label(),
                b.fareEstimate(), b.finalFare(), b.requestedAt());
    }

    // ---- one trip ---------------------------------------------------------

    public Optional<BookingDetail> bookingDetail(UUID bookingId) {
        return bookingApi.findById(bookingId).map(b -> {
            var facts = bookingApi.findOpsFacts(bookingId).orElse(null);
            String side = null;
            if (facts != null && facts.cancelledBy() != null) {
                side = facts.cancelledBy().equals(b.customerId()) ? "RIDER"
                        : facts.cancelledBy().equals(b.driverId()) ? "PARTNER" : "OPERATIONS";
            }
            var payment = paymentApi.getPaymentStatus(bookingId);
            PaymentSummary paid = payment.isSuccess() ? payment.value() : null;
            boolean live = LIVE.contains(b.status());
            DriverLocation partnerNow = live && b.driverId() != null ? locations.findLocation(b.driverId()).orElse(null) : null;
            Rating byRider = ratings.findForBooking(bookingId, b.customerId()).filter(Rating::submitted).orElse(null);
            Rating byPartner = b.driverId() == null ? null
                    : ratings.findForBooking(bookingId, b.driverId()).filter(Rating::submitted).orElse(null);
            return new BookingDetail(b, facts, side,
                    party(b.customerId(), AccountRole.CUSTOMER),
                    b.driverId() == null ? null : party(b.driverId(), AccountRole.DRIVER),
                    paid, sosApi.findByBookingId(bookingId), trails.trail(bookingId), partnerNow,
                    byRider, byPartner);
        });
    }

    private Party party(UUID accountId, AccountRole role) {
        Optional<AccountSummary> account = authApi.findAccount(accountId);
        String phone = account.map(AccountSummary::phoneNumber).orElse(null);
        boolean blocked = account.map(AccountSummary::blocked).orElse(false);
        if (role == AccountRole.DRIVER) {
            Optional<DriverProfileSummary> p = drivers.findByAccountId(accountId);
            return new Party(accountId, p.map(DriverProfileSummary::name).orElse(null), phone, role,
                    p.map(DriverProfileSummary::vehicleType).orElse(null),
                    p.map(DriverProfileSummary::vehicleRegistrationNumber).orElse(null),
                    p.map(DriverProfileSummary::trustStats).orElse(null), blocked);
        }
        Optional<CustomerProfileSummary> p = customers.findByAccountId(accountId);
        return new Party(accountId, p.map(CustomerProfileSummary::name).orElse(null), phone, role, null, null,
                p.map(CustomerProfileSummary::trustStats).orElse(null), blocked);
    }

    // ---- right now --------------------------------------------------------

    public LiveOps live() {
        Instant now = Instant.now();
        List<BookingSummary> trips = bookingApi.pageBookings(
                        new BookingQuery(LIVE, null, null, BookingQuery.allCategories(), null),
                        PageRequest.of(0, MAX_LIVE_TRIPS, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
        Set<UUID> sosTrips = sosApi.findActiveAlerts().stream()
                .filter(a -> a.status() == SosStatus.ACTIVE && a.bookingId() != null)
                .map(SosAlertSummary::bookingId)
                .collect(Collectors.toSet());
        Map<UUID, DriverLocation> fixes = locations.findAllReporting();
        Map<UUID, UUID> tripOfPartner = new HashMap<>();
        trips.forEach(t -> {
            if (t.driverId() != null) {
                tripOfPartner.put(t.driverId(), t.id());
            }
        });

        List<LiveTrip> liveTrips = trips.stream().map(t -> new LiveTrip(
                t.id(), t.status(), t.category(),
                t.customerId(), riderName(t.customerId()), phone(t.customerId()),
                t.driverId(), t.driverId() == null ? null : partnerName(t.driverId()),
                t.driverId() == null ? null : phone(t.driverId()),
                t.pickup(), t.drop(), t.fareEstimate(), t.requestedAt(), statusSince(t),
                t.driverId() == null ? null : fixes.get(t.driverId()),
                sosTrips.contains(t.id()))).toList();

        List<LivePartner> partners = fixes.entrySet().stream().map(e -> {
            Optional<DriverProfileSummary> p = drivers.findByAccountId(e.getKey());
            return new LivePartner(e.getKey(),
                    p.map(DriverProfileSummary::name).orElse(null),
                    p.map(DriverProfileSummary::phoneNumber).orElseGet(() -> phone(e.getKey())),
                    p.map(DriverProfileSummary::vehicleType).orElse(null),
                    p.map(DriverProfileSummary::vehicleRegistrationNumber).orElse(null),
                    e.getValue().lat(), e.getValue().lng(), e.getValue().recordedAt(),
                    tripOfPartner.get(e.getKey()));
        }).toList();

        return new LiveOps(now, serviceHours.currentStatus(), staleAfterSeconds, partners, liveTrips);
    }

    /** When the trip entered the state it is in - how long a search has run, how long she has been waiting. */
    private static Instant statusSince(BookingSummary t) {
        return switch (t.status()) {
            case IN_PROGRESS -> t.startedAt();
            case ACCEPTED -> t.acceptedAt();
            case MATCHED -> t.matchedAt();
            default -> t.requestedAt();
        };
    }

    // ---- service hours ----------------------------------------------------

    public ServiceHoursView serviceHoursView() {
        var settings = serviceHours.settings();
        List<ServiceHoursChangeRow> changes = serviceHours.recentChanges(15).stream()
                .map(c -> new ServiceHoursChangeRow(phone(c.changedBy()), c.summary(), c.at()))
                .toList();
        return new ServiceHoursView(settings, serviceHours.currentStatus(),
                settings.updatedBy() == null ? null : phone(settings.updatedBy()),
                settings.pausedBy() == null ? null : phone(settings.pausedBy()),
                changes);
    }

    // ---- lookups ----------------------------------------------------------

    private String riderName(UUID id) {
        return customers.findByAccountId(id).map(CustomerProfileSummary::name).orElse(null);
    }

    private String partnerName(UUID id) {
        return drivers.findByAccountId(id).map(DriverProfileSummary::name).orElse(null);
    }

    private String phone(UUID id) {
        return id == null ? null : authApi.findAccount(id).map(AccountSummary::phoneNumber).orElse(null);
    }

    private static String lastFour(String value) {
        if (value == null || value.length() < 4) {
            return value;
        }
        return "•".repeat(value.length() - 4) + value.substring(value.length() - 4);
    }
}
