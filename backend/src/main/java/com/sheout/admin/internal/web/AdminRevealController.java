package com.sheout.admin.internal.web;

import com.sheout.admin.internal.AdminScopes;
import com.sheout.auth.AccountSummary;
import com.sheout.auth.AuthApi;
import com.sheout.booking.BookingApi;
import com.sheout.booking.BookingSummary;
import com.sheout.dispatch.DriverLocation;
import com.sheout.dispatch.DriverLocationApi;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.staff.AuditedRead;
import com.sheout.staff.Permission;
import com.sheout.staff.RequiresAnyPermission;
import com.sheout.staff.RequiresPermission;
import com.sheout.staff.RequiresStepUp;
import com.sheout.staff.StaffAudit;
import com.sheout.staff.StaffContext;
import com.sheout.staff.StaffPrincipal;
import com.sheout.users.CustomerProfileApi;
import com.sheout.users.DriverProfileApi;
import com.sheout.users.EmergencyContact;
import com.sheout.users.EmergencyContactsApi;
import com.sheout.users.SavedPlace;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Seeing one masked value in full, and the safety desk's view of a trip in
 * an alert.
 * <p>
 * EVERY CONSOLE RESPONSE IS MASKED (staff.internal.ConsoleMasking): phone
 * numbers as 98•••••210, addresses as their area. A full value comes only
 * from here, one at a time, and needs all four of: the permission for that
 * kind of data, a fresh authenticator code, a typed reason, and the record
 * being within her reach (below). Each reveal is written to the audit log
 * with the reason, and more than 20 in ten minutes alerts every owner.
 * <p>
 * WITHIN REACH. Anyone who may see every account (users.view) or every trip
 * (trips.view) may reveal any. The safety desk may reveal only for a trip in
 * an open alert, or one closed in the last 30 minutes, and the people in it
 * (AlertScope) - the phone to call, not a directory.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminRevealController {

    private final AdminScopes scopes;
    private final AuthApi authApi;
    private final BookingApi bookings;
    private final CustomerProfileApi customers;
    private final DriverProfileApi drivers;
    private final EmergencyContactsApi contacts;
    private final DriverLocationApi locations;
    private final StaffAudit audit;

    public AdminRevealController(AdminScopes scopes, AuthApi authApi, BookingApi bookings, CustomerProfileApi customers,
                                 DriverProfileApi drivers, EmergencyContactsApi contacts, DriverLocationApi locations,
                                 StaffAudit audit) {
        this.scopes = scopes;
        this.authApi = authApi;
        this.bookings = bookings;
        this.customers = customers;
        this.drivers = drivers;
        this.contacts = contacts;
        this.locations = locations;
        this.audit = audit;
    }

    public enum Subject {
        /** The phone number of an account. */
        ACCOUNT_PHONE,
        /** One of a rider's emergency contacts' numbers, by position (index). */
        EMERGENCY_CONTACT_PHONE,
        /** A trip's pickup address. */
        PICKUP_ADDRESS,
        /** A trip's drop address. */
        DROP_ADDRESS,
        /** A rider's saved home address. */
        HOME_ADDRESS,
        /** A rider's saved work address. */
        WORK_ADDRESS
    }

    /**
     * @param bookingId for a phone: the trip she is looking at it through -
     *                  the safety desk's reach is decided by it
     */
    public record RevealRequest(@NotNull Subject subject, UUID accountId, UUID bookingId, Integer index,
                                @NotBlank @Size(min = 5, max = 300) String reason) {
    }

    public record Revealed(String value) {
    }

    @RequiresAnyPermission({Permission.PII_PHONE_REVEAL, Permission.PII_ADDRESS_REVEAL})
    @RequiresStepUp
    @PostMapping("/reveal")
    public ResponseEntity<Revealed> reveal(@Valid @RequestBody RevealRequest request) {
        StaffPrincipal staff = StaffContext.requireSignedIn();
        boolean phone = request.subject() == Subject.ACCOUNT_PHONE || request.subject() == Subject.EMERGENCY_CONTACT_PHONE;
        Permission needed = phone ? Permission.PII_PHONE_REVEAL : Permission.PII_ADDRESS_REVEAL;
        StaffContext.require(needed);
        String value = switch (request.subject()) {
            case ACCOUNT_PHONE -> {
                UUID account = need(request.accountId());
                scopes.requireAccountInReach(staff, account, request.bookingId());
                yield authApi.findAccount(account).map(AccountSummary::phoneNumber).orElse(null);
            }
            case EMERGENCY_CONTACT_PHONE -> {
                UUID account = need(request.accountId());
                scopes.requireAccountInReach(staff, account, request.bookingId());
                List<EmergencyContact> list = contacts.findContactsByAccountId(account);
                int i = request.index() == null ? -1 : request.index();
                yield i >= 0 && i < list.size() ? list.get(i).phoneNumber() : null;
            }
            case PICKUP_ADDRESS, DROP_ADDRESS -> {
                UUID booking = need(request.bookingId());
                scopes.requireBookingInReach(staff, booking);
                yield bookings.findById(booking)
                        .map(b -> request.subject() == Subject.PICKUP_ADDRESS ? b.pickup() : b.drop())
                        .map(a -> a.label()).orElse(null);
            }
            case HOME_ADDRESS, WORK_ADDRESS -> {
                UUID account = need(request.accountId());
                StaffContext.require(Permission.USERS_VIEW);
                yield customers.findByAccountId(account)
                        .map(p -> request.subject() == Subject.HOME_ADDRESS ? p.home() : p.work())
                        .map(SavedPlace::label).orElse(null);
            }
        };
        if (value == null || value.isBlank()) {
            throw ApiException.notFound("Nothing on file to show");
        }
        audit.record(new StaffAudit.Entry(phone ? "pii.phone.reveal" : "pii.address.reveal", needed, StaffAudit.Result.OK,
                request.subject().name(), String.valueOf(request.accountId() != null ? request.accountId() : request.bookingId()),
                request.reason().trim(), request.bookingId() == null ? null : "{\"bookingId\":\"" + request.bookingId() + "\"}"));
        return ResponseEntity.ok(new Revealed(value));
    }

    public record AlertTrip(UUID bookingId, String status, UUID riderId, String riderName, UUID partnerId,
                            String partnerName, BookingSummary booking, DriverLocation partnerNow, Instant at) {
    }

    /**
     * Where the partner of a trip in an alert is right now, for the safety
     * desk. The rest of the trip comes masked like everywhere else; phone
     * numbers through a reveal.
     */
    @RequiresPermission(Permission.TRIPS_LIVE_VIEW)
    @AuditedRead("trips.live.alert")
    @GetMapping("/alerts/trips/{bookingId}/live")
    public ResponseEntity<AlertTrip> alertTrip(@PathVariable UUID bookingId) {
        StaffPrincipal staff = StaffContext.requireSignedIn();
        scopes.requireBookingInReach(staff, bookingId);
        BookingSummary b = bookings.findById(bookingId).orElseThrow(() -> ApiException.notFound("No such trip"));
        String riderName = customers.findByAccountId(b.customerId()).map(p -> p.name()).orElse(null);
        String partnerName = b.driverId() == null ? null : drivers.findByAccountId(b.driverId()).map(p -> p.name()).orElse(null);
        DriverLocation now = b.driverId() == null ? null : locations.findLocation(b.driverId()).orElse(null);
        return ResponseEntity.ok(new AlertTrip(bookingId, b.status().name(), b.customerId(), riderName, b.driverId(),
                partnerName, b, now, Instant.now()));
    }

    private static UUID need(UUID id) {
        if (id == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_ID", "Say which record.");
        }
        return id;
    }
}
