package com.sheout.chat.internal;

import com.sheout.privacy.AccountDeletionRequested;
import com.sheout.support.SupportApi;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What a deleted account wrote in trip chats.
 * <p>
 * On a trip with an unresolved support ticket - an open dispute - each of
 * their messages stays as a "[deleted]" line: whoever is settling the dispute
 * can still see that the person wrote, and when, which is often the point in
 * question. On every other trip their messages are deleted outright; there is
 * nothing left for them to be evidence of.
 * <p>
 * The other person's messages are theirs and are not touched either way.
 * Whether a dispute is open is support's answer, asked through SupportApi.
 */
@Component
class AccountDeletionChatListener {

    private final ChatMessageRepository messages;
    private final SupportApi supportApi;

    AccountDeletionChatListener(ChatMessageRepository messages, SupportApi supportApi) {
        this.messages = messages;
        this.supportApi = supportApi;
    }

    @EventListener
    @Transactional
    public void onAccountDeletionRequested(AccountDeletionRequested event) {
        Map<UUID, List<ChatMessageEntity>> byBooking = messages.findBySenderAccountId(event.accountId()).stream()
                .collect(Collectors.groupingBy(ChatMessageEntity::getBookingId));

        byBooking.forEach((bookingId, authored) -> {
            if (supportApi.hasUnresolvedTicketForBooking(bookingId)) {
                authored.forEach(ChatMessageEntity::redact);
                messages.saveAll(authored);
            } else {
                messages.deleteAll(authored);
            }
        });
    }
}
