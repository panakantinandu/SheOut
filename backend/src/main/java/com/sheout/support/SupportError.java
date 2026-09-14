package com.sheout.support;

/**
 * Expected refusals from SupportApi, returned via Result rather than thrown.
 */
public enum SupportError {

    /**
     * No such ticket, OR the caller did not raise it. One value for both, so
     * the controller answers 404 either way and a ticket id cannot be probed
     * for existence - the codebase's enumeration-safe rule.
     */
    TICKET_NOT_FOUND,

    /**
     * The linked booking does not exist, OR the raiser is not on it. One
     * value for both, for the same reason - see BookingParticipants.includes.
     */
    BOOKING_NOT_FOUND,

    /** CLOSED is final: no further messages from the raiser, no status change. */
    TICKET_CLOSED,

    /** The move is not allowed from the ticket's current status - see SupportTicketStatus.canMoveTo. */
    INVALID_STATUS_TRANSITION,

    /** Assigning to an account that is not an operator. */
    ASSIGNEE_NOT_ADMIN
}
