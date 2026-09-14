package com.sheout.support;

import com.sheout.auth.AccountRole;

import java.time.Instant;
import java.util.UUID;

/**
 * One line of a ticket's thread.
 * <p>
 * internalOnly messages are operators' notes to each other. The
 * raiser-facing reads in SupportApi never return them; only the
 * operator-facing getTicket does.
 */
public record SupportTicketMessage(
        UUID id,
        UUID ticketId,
        UUID authorAccountId,
        AccountRole authorRole,
        String message,
        boolean internalOnly,
        Instant createdAt
) {
}
