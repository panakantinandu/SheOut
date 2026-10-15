package com.sheout.chat.internal;

import com.sheout.sharedkernel.retention.RetentionTask;
import com.sheout.support.SupportApi;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Trip chat, once it is old: sheout.retention.days.chat-messages after it
 * was written. A trip with an unresolved support ticket keeps its chat - the
 * same rule account deletion follows (AccountDeletionChatListener) - because
 * the conversation is often what the dispute is about.
 */
@Component
class ChatRetentionTask implements RetentionTask {

    private final ChatMessageRepository messages;
    private final SupportApi supportApi;

    ChatRetentionTask(ChatMessageRepository messages, SupportApi supportApi) {
        this.messages = messages;
        this.supportApi = supportApi;
    }

    @Override
    public String name() {
        return "chat-messages";
    }

    @Override
    @Transactional
    public int sweep(Instant cutoff, boolean dryRun, int limit) {
        int matched = 0;
        for (UUID bookingId : messages.findBookingIdsWithMessagesBefore(cutoff, PageRequest.of(0, limit))) {
            if (supportApi.hasUnresolvedTicketForBooking(bookingId)) {
                continue;
            }
            matched += dryRun
                    ? (int) messages.countByBookingIdAndCreatedAtBefore(bookingId, cutoff)
                    : messages.deleteByBookingIdAndCreatedAtBefore(bookingId, cutoff);
        }
        return matched;
    }
}
