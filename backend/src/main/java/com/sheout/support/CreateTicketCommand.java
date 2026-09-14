package com.sheout.support;

import com.sheout.auth.AccountRole;

import java.util.UUID;

/**
 * Everything needed to raise a ticket. raisedByAccountId and role come from
 * the caller's token, never from the request body; linkedBookingId is
 * optional.
 */
public record CreateTicketCommand(
        UUID raisedByAccountId,
        AccountRole role,
        SupportTicketCategory category,
        String subject,
        String description,
        UUID linkedBookingId
) {
}
