package com.sheout.support;

import com.sheout.auth.AccountRole;

import java.time.Instant;
import java.util.UUID;

/**
 * One ticket, without its thread. What a list row needs.
 * <p>
 * Only this module's own data. A name or phone number for raisedByAccountId
 * is the caller's to look up through auth/users, the same rule
 * SosAlertSummary follows.
 */
public record SupportTicketSummary(
        UUID id,
        UUID raisedByAccountId,
        AccountRole role,
        SupportTicketCategory category,
        String subject,
        String description,
        UUID linkedBookingId,
        SupportTicketStatus status,
        SupportTicketPriority priority,
        UUID assignedAdminId,
        Instant createdAt,
        Instant lastActivityAt,
        Instant resolvedAt,
        UUID resolvedBy
) {
}
