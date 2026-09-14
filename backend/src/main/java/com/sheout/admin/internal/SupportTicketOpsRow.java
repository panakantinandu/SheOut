package com.sheout.admin.internal;

import com.sheout.auth.AccountRole;
import com.sheout.support.SupportTicketCategory;
import com.sheout.support.SupportTicketPriority;
import com.sheout.support.SupportTicketStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * A ticket as the operator queue shows it: support's own facts, plus who
 * raised it and who holds it, resolved through auth and users.
 */
public record SupportTicketOpsRow(
        UUID id,
        UUID raisedByAccountId,
        AccountRole role,
        String raisedByName,
        String raisedByPhone,
        boolean raisedByBlocked,
        SupportTicketCategory category,
        String subject,
        String description,
        UUID linkedBookingId,
        SupportTicketStatus status,
        SupportTicketPriority priority,
        UUID assignedAdminId,
        String assignedAdminPhone,
        Instant createdAt,
        Instant lastActivityAt,
        Instant resolvedAt,
        String resolvedByPhone
) {
}
