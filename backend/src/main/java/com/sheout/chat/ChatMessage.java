package com.sheout.chat;

import com.sheout.auth.AccountRole;

import java.time.Instant;
import java.util.UUID;

/**
 * One line of a booking's thread.
 * <p>
 * senderRole rather than a name: a thread only ever has two sides, and
 * "you" versus "your partner" is all either end needs. It also means
 * rendering a thread never requires looking up who someone is.
 */
public record ChatMessage(
        UUID id,
        UUID bookingId,
        UUID senderAccountId,
        AccountRole senderRole,
        String body,
        Instant sentAt
) {
}
