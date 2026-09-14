package com.sheout.support.internal;

import com.sheout.auth.AccountRole;
import com.sheout.auth.AuthApi;
import com.sheout.booking.BookingApi;
import com.sheout.booking.BookingError;
import com.sheout.booking.BookingParticipants;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import com.sheout.support.CreateTicketCommand;
import com.sheout.support.SupportApi;
import com.sheout.support.SupportError;
import com.sheout.support.SupportReplyPosted;
import com.sheout.support.SupportTicketDetail;
import com.sheout.support.SupportTicketMessage;
import com.sheout.support.SupportTicketQuery;
import com.sheout.support.SupportTicketRaised;
import com.sheout.support.SupportTicketStatus;
import com.sheout.support.SupportTicketSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SupportService implements SupportApi {

    private static final Logger log = LoggerFactory.getLogger(SupportService.class);

    private final String phoneNumber;
    private final String grievanceEmail;
    private final SupportTicketRepository tickets;
    private final SupportTicketMessageRepository messages;
    private final BookingApi bookingApi;
    private final AuthApi authApi;
    private final DomainEventPublisher eventPublisher;

    SupportService(@Value("${sheout.support.phone-number:}") String phoneNumber,
                   @Value("${sheout.support.grievance-officer-email:}") String grievanceEmail,
                   SupportTicketRepository tickets,
                   SupportTicketMessageRepository messages,
                   BookingApi bookingApi,
                   AuthApi authApi,
                   DomainEventPublisher eventPublisher) {
        this.phoneNumber = phoneNumber == null ? "" : phoneNumber.trim();
        this.grievanceEmail = grievanceEmail == null ? "" : grievanceEmail.trim();
        if (this.grievanceEmail.isBlank()) {
            log.warn("GRIEVANCE_OFFICER_EMAIL is not set - both apps will say a grievance contact is not yet available");
        }
        this.tickets = tickets;
        this.messages = messages;
        this.bookingApi = bookingApi;
        this.authApi = authApi;
        this.eventPublisher = eventPublisher;
        if (this.phoneNumber.isBlank()) {
            // A warning, not a startup failure. Chat covers everything
            // routine, and SOS does not depend on this at all, so an
            // unconfigured support line degrades the product rather than
            // breaking it - but somebody should know it is missing.
            log.warn("SUPPORT_PHONE_NUMBER is not set - both apps will hide their Contact Support action");
        }
    }

    @Override
    public Optional<String> supportPhoneNumber() {
        return phoneNumber.isBlank() ? Optional.empty() : Optional.of(phoneNumber);
    }

    @Override
    public Optional<String> grievanceOfficerEmail() {
        return grievanceEmail.isBlank() ? Optional.empty() : Optional.of(grievanceEmail);
    }

    @Override
    @Transactional
    public Result<SupportTicketSummary, SupportError> createTicket(CreateTicketCommand command) {
        if (command.linkedBookingId() != null) {
            // Booking's own participants read and its one definition of "on
            // this booking" - not a second copy of either here.
            Result<BookingParticipants, BookingError> participants = bookingApi.getParticipants(command.linkedBookingId());
            if (participants.isFailure() || !participants.value().includes(command.raisedByAccountId())) {
                return Result.failure(SupportError.BOOKING_NOT_FOUND);
            }
        }
        SupportTicketEntity ticket = tickets.save(new SupportTicketEntity(
                command.raisedByAccountId(), command.role(), command.category(),
                command.subject().trim(), command.description().trim(), command.linkedBookingId()));
        eventPublisher.publish(new SupportTicketRaised(
                ticket.getId(), ticket.getRaisedByAccountId(), ticket.getRole(), ticket.getCategory(), ticket.getPriority()));
        return Result.success(toSummary(ticket));
    }

    @Override
    @Transactional
    public Result<SupportTicketMessage, SupportError> addMessage(UUID ticketId, UUID authorAccountId, AccountRole authorRole,
                                                                 String message, boolean internalOnly) {
        Optional<SupportTicketEntity> found = tickets.findLockedById(ticketId);
        if (found.isEmpty()) {
            return Result.failure(SupportError.TICKET_NOT_FOUND);
        }
        SupportTicketEntity ticket = found.get();
        boolean fromAdmin = authorRole == AccountRole.ADMIN;

        if (!fromAdmin && !ticket.getRaisedByAccountId().equals(authorAccountId)) {
            return Result.failure(SupportError.TICKET_NOT_FOUND);
        }
        // Only an operator can write a note the raiser will not see. Coming
        // from anyone else the flag is dropped, not refused: it can only be
        // set by a client that is not ours, and the message is still theirs.
        boolean internal = fromAdmin && internalOnly;

        // A closed ticket is finished for the raiser and for replies they
        // would be told about. Operators may still add a note for the record.
        if (ticket.getStatus() == SupportTicketStatus.CLOSED && !internal) {
            return Result.failure(SupportError.TICKET_CLOSED);
        }

        if (!fromAdmin && ticket.getStatus() == SupportTicketStatus.RESOLVED) {
            // Writing back to a resolved ticket is saying it is not resolved.
            // Left RESOLVED it would sit outside the unresolved count the
            // console badges, and the reply would go unread.
            recordNote(ticket, authorAccountId, authorRole, resolutionNote(ticket, "Reopened by a reply from the "
                    + (ticket.getRole() == AccountRole.DRIVER ? "partner" : "rider") + "."));
            ticket.moveTo(SupportTicketStatus.OPEN, authorAccountId);
        } else {
            ticket.touch();
        }

        SupportTicketMessageEntity saved = messages.save(
                new SupportTicketMessageEntity(ticketId, authorAccountId, authorRole, message.trim(), internal));
        tickets.save(ticket);

        if (fromAdmin && !internal) {
            eventPublisher.publish(new SupportReplyPosted(ticketId, ticket.getRaisedByAccountId(), ticket.getSubject()));
        }
        return Result.success(toMessage(saved));
    }

    @Override
    @Transactional
    public Result<SupportTicketSummary, SupportError> updateStatus(UUID ticketId, SupportTicketStatus next, UUID adminAccountId) {
        Optional<SupportTicketEntity> found = tickets.findLockedById(ticketId);
        if (found.isEmpty()) {
            return Result.failure(SupportError.TICKET_NOT_FOUND);
        }
        SupportTicketEntity ticket = found.get();
        if (ticket.getStatus() == SupportTicketStatus.CLOSED) {
            return Result.failure(SupportError.TICKET_CLOSED);
        }
        if (!ticket.getStatus().canMoveTo(next)) {
            return Result.failure(SupportError.INVALID_STATUS_TRANSITION);
        }

        // Every status change leaves a line in the thread, so the history of
        // who did what survives even though resolvedAt/resolvedBy describe
        // only the current state.
        String note = "Status changed from " + ticket.getStatus() + " to " + next + ".";
        if (ticket.getStatus() == SupportTicketStatus.RESOLVED && next != SupportTicketStatus.CLOSED) {
            note = resolutionNote(ticket, note);
        }
        recordNote(ticket, adminAccountId, AccountRole.ADMIN, note);
        ticket.moveTo(next, adminAccountId);
        return Result.success(toSummary(tickets.save(ticket)));
    }

    @Override
    @Transactional
    public Result<SupportTicketSummary, SupportError> assignTicket(UUID ticketId, UUID assigneeAdminId, UUID adminAccountId) {
        Optional<SupportTicketEntity> found = tickets.findLockedById(ticketId);
        if (found.isEmpty()) {
            return Result.failure(SupportError.TICKET_NOT_FOUND);
        }
        if (assigneeAdminId != null) {
            boolean isAdmin = authApi.findAccount(assigneeAdminId)
                    .map(account -> account.role() == AccountRole.ADMIN)
                    .orElse(false);
            if (!isAdmin) {
                return Result.failure(SupportError.ASSIGNEE_NOT_ADMIN);
            }
        }
        SupportTicketEntity ticket = found.get();
        // Who it went to is the ticket's assignedAdminId, which the console
        // shows; the note records that it changed and, through its author,
        // who changed it. An account id in the text would be unreadable.
        recordNote(ticket, adminAccountId, AccountRole.ADMIN, assigneeAdminId == null
                ? "Unassigned."
                : assigneeAdminId.equals(adminAccountId) ? "Assigned the ticket to themselves." : "Reassigned the ticket.");
        ticket.assignTo(assigneeAdminId);
        return Result.success(toSummary(tickets.save(ticket)));
    }

    @Override
    public boolean hasUnresolvedTicketForBooking(UUID bookingId) {
        return tickets.existsByLinkedBookingIdAndStatusIn(bookingId, SupportTicketStatus.unresolved());
    }

    /**
     * What a deleted account wrote to support.
     * <p>
     * An unresolved ticket is an open dispute and is kept as written - it is
     * the reason anyone would need the record. A resolved or closed one keeps
     * its category, status, dates and the operators' side, but the subject,
     * description and the person's own messages become [deleted]. The raiser's
     * name is already "Deleted User" wherever the console shows it.
     */
    @org.springframework.context.event.EventListener
    @Transactional
    public void onAccountDeletionRequested(com.sheout.privacy.AccountDeletionRequested event) {
        for (SupportTicketEntity ticket : tickets.findByRaisedByAccountId(event.accountId())) {
            if (SupportTicketStatus.unresolved().contains(ticket.getStatus())) {
                continue;
            }
            ticket.redactRaiserText();
            tickets.save(ticket);
            List<SupportTicketMessageEntity> authored =
                    messages.findByTicketIdAndAuthorAccountId(ticket.getId(), event.accountId());
            authored.forEach(SupportTicketMessageEntity::redact);
            messages.saveAll(authored);
        }
    }

    @Override
    public Page<SupportTicketSummary> listTickets(SupportTicketQuery query, Pageable pageable) {
        // The raiser's own list is newest activity first. The operator queue
        // is unresolved-and-urgent first, which is what queueRank encodes.
        Sort sort = query.raisedBy() != null
                ? Sort.by(Sort.Direction.DESC, "lastActivityAt")
                : Sort.by(Sort.Order.asc("queueRank"), Sort.Order.desc("lastActivityAt"));
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        return tickets.findAll(SupportTicketSpecs.matching(query), sorted).map(SupportService::toSummary);
    }

    @Override
    public Optional<SupportTicketDetail> getTicket(UUID ticketId, UUID viewerAccountId, AccountRole viewerRole) {
        boolean admin = viewerRole == AccountRole.ADMIN;
        return tickets.findById(ticketId)
                .filter(ticket -> admin || ticket.getRaisedByAccountId().equals(viewerAccountId))
                .map(ticket -> {
                    List<SupportTicketMessage> thread = messages.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                            // Filtered here, not in a controller: there is no
                            // path by which a raiser's read includes a note.
                            .filter(m -> admin || !m.isInternalOnly())
                            .map(SupportService::toMessage)
                            .toList();
                    return new SupportTicketDetail(toSummary(ticket), thread);
                });
    }

    private void recordNote(SupportTicketEntity ticket, UUID authorAccountId, AccountRole authorRole, String note) {
        messages.save(new SupportTicketMessageEntity(ticket.getId(), authorAccountId, authorRole, note, true));
    }

    /**
     * Keeps the resolution time a reopening is about to clear. Who resolved
     * it needs no repeating: that is the author of the earlier "to RESOLVED"
     * note in the same thread.
     */
    private static String resolutionNote(SupportTicketEntity ticket, String prefix) {
        return prefix + " It had been marked resolved at " + ticket.getResolvedAt() + ".";
    }

    private static SupportTicketSummary toSummary(SupportTicketEntity t) {
        return new SupportTicketSummary(
                t.getId(), t.getRaisedByAccountId(), t.getRole(), t.getCategory(), t.getSubject(), t.getDescription(),
                t.getLinkedBookingId(), t.getStatus(), t.getPriority(), t.getAssignedAdminId(),
                t.getCreatedAt(), t.getLastActivityAt(), t.getResolvedAt(), t.getResolvedBy());
    }

    private static SupportTicketMessage toMessage(SupportTicketMessageEntity m) {
        return new SupportTicketMessage(
                m.getId(), m.getTicketId(), m.getAuthorAccountId(), m.getAuthorRole(),
                m.getMessage(), m.isInternalOnly(), m.getCreatedAt());
    }
}
