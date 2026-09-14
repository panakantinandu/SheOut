package com.sheout.support;

import com.sheout.auth.AccountRole;
import com.sheout.sharedkernel.Result;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

/**
 * How a rider or a partner reaches a person at SheOut: the support phone
 * number, and tickets.
 * <p>
 * Every rule about who may see or change a ticket lives behind this
 * interface, not in the controllers that call it. The viewer's account and
 * role are passed in on every read and write, and the implementation
 * decides: a raiser sees only their own tickets, never an internal note, and
 * cannot write one; an operator sees everything.
 */
public interface SupportApi {

    /**
     * The number to offer a rider or a partner who needs to speak to
     * somebody, or empty when none is configured.
     * <p>
     * Empty rather than a placeholder, so a caller can tell "we have no
     * number" from "here is a number" and hide the action instead of
     * offering a button that dials nothing.
     */
    Optional<String> supportPhoneNumber();

    /**
     * Raises a ticket. Priority comes from the category, never from the
     * caller. A linked booking must be one the raiser is on, otherwise
     * BOOKING_NOT_FOUND - the same answer as a booking that does not exist.
     * Publishes SupportTicketRaised.
     */
    Result<SupportTicketSummary, SupportError> createTicket(CreateTicketCommand command);

    /**
     * Adds to a ticket's thread.
     * <p>
     * An ADMIN author may post a reply or, with internalOnly, a note other
     * operators alone can read; a reply publishes SupportReplyPosted. Any
     * other author must be the raiser, can never post internalOnly (the flag
     * is ignored), and cannot write to a CLOSED ticket. A raiser writing to a
     * RESOLVED ticket reopens it, because they are saying it is not.
     */
    Result<SupportTicketMessage, SupportError> addMessage(UUID ticketId, UUID authorAccountId, AccountRole authorRole,
                                                          String message, boolean internalOnly);

    /** Operator-only. Moves are limited by SupportTicketStatus.canMoveTo; RESOLVED records who and when. */
    Result<SupportTicketSummary, SupportError> updateStatus(UUID ticketId, SupportTicketStatus status, UUID adminAccountId);

    /** Operator-only. assigneeAdminId must be an ADMIN account, or null to unassign. */
    Result<SupportTicketSummary, SupportError> assignTicket(UUID ticketId, UUID assigneeAdminId, UUID adminAccountId);

    /** Paged, filtered. Pass the caller's id as query.raisedBy for a self-service list. */
    Page<SupportTicketSummary> listTickets(SupportTicketQuery query, Pageable pageable);

    /**
     * A ticket and its thread, as this viewer is allowed to see it: all of it
     * for an ADMIN, their own ticket without internal notes for anyone else,
     * and empty for a ticket that is not theirs - indistinguishable from one
     * that does not exist.
     */
    Optional<SupportTicketDetail> getTicket(UUID ticketId, UUID viewerAccountId, AccountRole viewerRole);
}
