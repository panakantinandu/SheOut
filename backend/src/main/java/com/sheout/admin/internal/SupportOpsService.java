package com.sheout.admin.internal;

import com.sheout.auth.AccountRole;
import com.sheout.auth.AccountSummary;
import com.sheout.auth.AuthApi;
import com.sheout.booking.BookingApi;
import com.sheout.notifications.SosApi;
import com.sheout.sharedkernel.Result;
import com.sheout.support.SupportApi;
import com.sheout.support.SupportError;
import com.sheout.support.SupportTicketCategory;
import com.sheout.support.SupportTicketDetail;
import com.sheout.support.SupportTicketMessage;
import com.sheout.support.SupportTicketPriority;
import com.sheout.support.SupportTicketQuery;
import com.sheout.support.SupportTicketStatus;
import com.sheout.support.SupportTicketSummary;
import com.sheout.users.CustomerProfileApi;
import com.sheout.users.DriverProfileApi;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The ops console's support-ticket views, composed from support, auth,
 * users, booking and notifications' public interfaces.
 * <p>
 * Its own class beside AdminService rather than more of AdminService: the
 * tickets section has its own set of collaborators, and none of AdminService's
 * existing views need it. Same rule as AdminService - no repository, no
 * entity; joining other modules' facts is the whole job.
 */
@Service
public class SupportOpsService {

    private final SupportApi supportApi;
    private final AuthApi authApi;
    private final CustomerProfileApi customerProfileApi;
    private final DriverProfileApi driverProfileApi;
    private final BookingApi bookingApi;
    private final SosApi sosApi;

    public SupportOpsService(SupportApi supportApi, AuthApi authApi, CustomerProfileApi customerProfileApi,
                             DriverProfileApi driverProfileApi, BookingApi bookingApi, SosApi sosApi) {
        this.supportApi = supportApi;
        this.authApi = authApi;
        this.customerProfileApi = customerProfileApi;
        this.driverProfileApi = driverProfileApi;
        this.bookingApi = bookingApi;
        this.sosApi = sosApi;
    }

    public Page<SupportTicketOpsRow> tickets(SupportTicketQuery query, Pageable pageable) {
        return supportApi.listTickets(query, pageable).map(this::toRow);
    }

    /**
     * What the sidebar badge and the SOS page's cross-link read. Three count
     * queries with a page size of one - totals, not rows.
     */
    public SupportCounts counts() {
        Set<SupportTicketStatus> unresolved = SupportTicketStatus.unresolved();
        return new SupportCounts(
                count(new SupportTicketQuery(null, unresolved, null, null, null, null)),
                count(new SupportTicketQuery(null, unresolved, null, EnumSet.of(SupportTicketPriority.HIGH), null, null)),
                count(new SupportTicketQuery(null, unresolved, EnumSet.of(SupportTicketCategory.SAFETY_CONCERN), null, null, null)));
    }

    public Optional<SupportTicketOpsDetail> ticket(UUID ticketId, UUID adminAccountId) {
        return supportApi.getTicket(ticketId, adminAccountId, AccountRole.ADMIN).map(this::toDetail);
    }

    public Result<SupportTicketMessage, SupportError> addMessage(UUID ticketId, UUID adminAccountId, String message,
                                                                 boolean internalOnly) {
        return supportApi.addMessage(ticketId, adminAccountId, AccountRole.ADMIN, message, internalOnly);
    }

    public Result<SupportTicketSummary, SupportError> updateStatus(UUID ticketId, SupportTicketStatus status,
                                                                   UUID adminAccountId) {
        return supportApi.updateStatus(ticketId, status, adminAccountId);
    }

    public Result<SupportTicketSummary, SupportError> assign(UUID ticketId, UUID assigneeAdminId, UUID adminAccountId) {
        return supportApi.assignTicket(ticketId, assigneeAdminId, adminAccountId);
    }

    /** Operations accounts a ticket can be assigned to. A small list; one page is all of them. */
    public List<Operator> operators() {
        return authApi.searchAccounts(null, Set.of(AccountRole.ADMIN), false, PageRequest.of(0, 100)).stream()
                .map(account -> new Operator(account.id(), account.phoneNumber()))
                .toList();
    }

    private long count(SupportTicketQuery query) {
        return supportApi.listTickets(query, PageRequest.of(0, 1)).getTotalElements();
    }

    private SupportTicketOpsRow toRow(SupportTicketSummary t) {
        Optional<AccountSummary> raiser = authApi.findAccount(t.raisedByAccountId());
        return new SupportTicketOpsRow(
                t.id(),
                t.raisedByAccountId(),
                t.role(),
                nameFor(t.raisedByAccountId(), t.role()),
                raiser.map(AccountSummary::phoneNumber).orElse(null),
                raiser.map(AccountSummary::blocked).orElse(false),
                t.category(),
                t.subject(),
                t.description(),
                t.linkedBookingId(),
                t.status(),
                t.priority(),
                t.assignedAdminId(),
                phoneFor(t.assignedAdminId()),
                t.createdAt(),
                t.lastActivityAt(),
                t.resolvedAt(),
                phoneFor(t.resolvedBy()));
    }

    private SupportTicketOpsDetail toDetail(SupportTicketDetail detail) {
        SupportTicketSummary t = detail.ticket();
        String raiserName = nameFor(t.raisedByAccountId(), t.role());
        List<SupportTicketOpsDetail.Message> thread = detail.messages().stream()
                .map(m -> new SupportTicketOpsDetail.Message(
                        m.id(),
                        m.authorRole(),
                        m.authorRole() == AccountRole.ADMIN
                                ? Optional.ofNullable(phoneFor(m.authorAccountId())).orElse("Operator")
                                : Optional.ofNullable(raiserName).orElse("Raiser"),
                        m.message(),
                        m.internalOnly(),
                        m.createdAt()))
                .toList();

        SupportTicketOpsDetail.LinkedBooking booking = null;
        List<SupportTicketOpsDetail.LinkedSosAlert> alerts = List.of();
        if (t.linkedBookingId() != null) {
            booking = bookingApi.findById(t.linkedBookingId())
                    .map(b -> new SupportTicketOpsDetail.LinkedBooking(
                            b.id(), b.status(), b.category(), b.pickup().label(), b.drop().label(), b.requestedAt(),
                            nameFor(b.customerId(), AccountRole.CUSTOMER),
                            b.driverId() == null ? null : nameFor(b.driverId(), AccountRole.DRIVER)))
                    .orElse(null);
            alerts = sosApi.findByBookingId(t.linkedBookingId()).stream()
                    .map(a -> new SupportTicketOpsDetail.LinkedSosAlert(
                            a.id(), a.status().name(), a.lat(), a.lng(), a.contactsNotified(), a.createdAt(), a.resolvedAt()))
                    .toList();
        }
        return new SupportTicketOpsDetail(toRow(t), thread, booking, alerts);
    }

    private String nameFor(UUID accountId, AccountRole role) {
        if (accountId == null) return null;
        return role == AccountRole.DRIVER
                ? driverProfileApi.findByAccountId(accountId).map(p -> p.name()).orElse(null)
                : customerProfileApi.findByAccountId(accountId).map(p -> p.name()).orElse(null);
    }

    private String phoneFor(UUID accountId) {
        if (accountId == null) return null;
        return authApi.findAccount(accountId).map(AccountSummary::phoneNumber).orElse(null);
    }

    /** unresolved: OPEN or IN_PROGRESS. high and safety are subsets of it. */
    public record SupportCounts(long unresolved, long unresolvedHigh, long unresolvedSafety) {
    }

    public record Operator(UUID accountId, String phoneNumber) {
    }
}
