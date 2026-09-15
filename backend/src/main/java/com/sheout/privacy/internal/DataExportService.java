package com.sheout.privacy.internal;

import com.sheout.auth.AccountRole;
import com.sheout.auth.AccountSummary;
import com.sheout.auth.AuthApi;
import com.sheout.booking.BookingApi;
import com.sheout.booking.BookingSummary;
import com.sheout.driververification.VerificationApi;
import com.sheout.payments.PaymentApi;
import com.sheout.payments.PaymentError;
import com.sheout.payments.PaymentSummary;
import com.sheout.ratings.AggregateRating;
import com.sheout.ratings.Rating;
import com.sheout.ratings.RatingsApi;
import com.sheout.sharedkernel.Result;
import com.sheout.support.SupportApi;
import com.sheout.support.SupportTicketQuery;
import com.sheout.users.CustomerProfileApi;
import com.sheout.users.DriverProfileApi;
import com.sheout.users.EmergencyContactsApi;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds a DataExport for one account from the public interfaces of the
 * modules that own each fact. This service reads no table of its own and
 * none of theirs.
 */
@Service
class DataExportService {

    /** A person's own ticket list, whole. Five hundred tickets is far beyond any real account. */
    private static final int MAX_TICKETS = 500;

    private final AuthApi authApi;
    private final CustomerProfileApi customerProfileApi;
    private final DriverProfileApi driverProfileApi;
    private final EmergencyContactsApi emergencyContactsApi;
    private final VerificationApi verificationApi;
    private final BookingApi bookingApi;
    private final PaymentApi paymentApi;
    private final RatingsApi ratingsApi;
    private final SupportApi supportApi;
    private final com.sheout.payouts.PayoutApi payoutApi;

    DataExportService(AuthApi authApi, CustomerProfileApi customerProfileApi, DriverProfileApi driverProfileApi,
                      EmergencyContactsApi emergencyContactsApi, VerificationApi verificationApi, BookingApi bookingApi,
                      PaymentApi paymentApi, RatingsApi ratingsApi, SupportApi supportApi,
                      com.sheout.payouts.PayoutApi payoutApi) {
        this.payoutApi = payoutApi;
        this.authApi = authApi;
        this.customerProfileApi = customerProfileApi;
        this.driverProfileApi = driverProfileApi;
        this.emergencyContactsApi = emergencyContactsApi;
        this.verificationApi = verificationApi;
        this.bookingApi = bookingApi;
        this.paymentApi = paymentApi;
        this.ratingsApi = ratingsApi;
        this.supportApi = supportApi;
    }

    Optional<DataExport> exportFor(UUID accountId, AccountRole role) {
        return authApi.findAccount(accountId).map(account -> build(account, role));
    }

    private DataExport build(AccountSummary account, AccountRole role) {
        UUID id = account.id();
        boolean driver = role == AccountRole.DRIVER;

        List<BookingSummary> bookings = bookingApi.findAllForAccount(id);
        Map<UUID, Rating> given = ratingsApi.findForBookings(bookings.stream().map(BookingSummary::id).toList(), id);
        AggregateRating received = ratingsApi.getAggregateRating(id);

        return new DataExport(
                Instant.now(),
                "Everything SheOut holds about your account, generated on request. Other people on your trips appear "
                        + "only by the name you were shown. Operators' internal notes on support tickets are not part of "
                        + "your data and are not included.",
                new DataExport.Account(id, account.role(), account.phoneNumber(), account.email(), account.createdAt()),
                profile(id, driver),
                driver ? List.of() : emergencyContactsApi.findContactsByAccountId(id).stream()
                        .map(c -> new DataExport.EmergencyContact(c.name(), c.phoneNumber(), c.relationship()))
                        .toList(),
                verificationApi.findByAccountId(id)
                        .map(v -> new DataExport.Verification(
                                String.valueOf(v.genderVerificationStatus()),
                                v.policeVerificationStatus() == null ? null : v.policeVerificationStatus().name(),
                                v.documentSubmitted()))
                        .orElse(null),
                bookings.stream().map(b -> trip(b, id, given.get(b.id()))).toList(),
                new DataExport.RatingsReceived(received.averageStars(), received.totalRatings()),
                tickets(id, role),
                driver ? new DataExport.Payouts(
                        payoutApi.getWallet(id),
                        payoutApi.getPayoutAccount(id).orElse(null),
                        payoutApi.listForDriver(id).stream()
                                .map(r -> new DataExport.PayoutRequest(r.amount(), r.status().name(), r.accountNumber(),
                                        r.upiVpa(), r.requestedAt(), r.paidAt(), r.paymentReference()))
                                .toList())
                        : null);
    }

    private DataExport.Profile profile(UUID id, boolean driver) {
        if (driver) {
            return driverProfileApi.findByAccountId(id)
                    .map(p -> new DataExport.Profile(p.name(), p.dateOfBirth(), p.email(), null, null,
                            p.vehicleType() == null ? null : p.vehicleType().name(),
                            // hasProfilePhoto, not the URL: on local-disk storage the
                            // URL is null even though a photo is on file.
                            p.vehicleRegistrationNumber(), p.hasProfilePhoto()))
                    .orElse(null);
        }
        return customerProfileApi.findByAccountId(id)
                .map(p -> new DataExport.Profile(p.name(), p.dateOfBirth(), p.email(), p.homeAddress(), p.workAddress(),
                        null, null, p.hasProfilePhoto()))
                .orElse(null);
    }

    private DataExport.Trip trip(BookingSummary b, UUID me, Rating myRating) {
        boolean iAmCustomer = me.equals(b.customerId());
        // The other person's name, and only their name - the same thing the
        // app showed during the trip.
        String counterpart = iAmCustomer
                ? (b.driverId() == null ? null : driverProfileApi.findByAccountId(b.driverId()).map(p -> p.name()).orElse(null))
                : customerProfileApi.findByAccountId(b.customerId()).map(p -> p.name()).orElse(null);

        Result<PaymentSummary, PaymentError> payment = paymentApi.getPaymentStatus(b.id());
        DataExport.Payment paymentView = payment.isFailure() ? null : new DataExport.Payment(
                payment.value().amount(),
                String.valueOf(payment.value().method()),
                String.valueOf(payment.value().status()),
                iAmCustomer ? null : payment.value().driverPayout(),
                payment.value().capturedAt());

        return new DataExport.Trip(
                b.id(), iAmCustomer ? "RIDER" : "PARTNER", b.type().name(), b.category().name(), b.status().name(),
                b.pickup().label(), b.drop().label(), b.fareEstimate(), b.finalFare(),
                b.requestedAt(), b.completedAt(), b.cancelledAt(),
                counterpart, paymentView,
                myRating == null || myRating.stars() == null ? null
                        : new DataExport.RatingGiven(myRating.stars(), myRating.comment(), myRating.submittedAt()));
    }

    private List<DataExport.SupportTicket> tickets(UUID id, AccountRole role) {
        return supportApi.listTickets(SupportTicketQuery.forRaiser(id), PageRequest.of(0, MAX_TICKETS)).stream()
                // getTicket with the person's own role filters internal notes
                // out, the same read the app's ticket screen uses.
                .map(t -> supportApi.getTicket(t.id(), id, role))
                .flatMap(Optional::stream)
                .map(d -> new DataExport.SupportTicket(
                        d.ticket().id(), d.ticket().category().name(), d.ticket().subject(), d.ticket().description(),
                        d.ticket().status().name(), d.ticket().createdAt(), d.ticket().resolvedAt(),
                        d.messages().stream()
                                .map(m -> new DataExport.TicketMessage(
                                        m.authorAccountId().equals(id) ? "You" : "SheOut Support", m.message(), m.createdAt()))
                                .collect(Collectors.toList())))
                .toList();
    }
}
