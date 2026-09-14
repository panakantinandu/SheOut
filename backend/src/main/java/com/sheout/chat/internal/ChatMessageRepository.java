package com.sheout.chat.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {

    /** Oldest first: a conversation is read in the order it happened. */
    List<ChatMessageEntity> findByBookingIdOrderByCreatedAtAsc(UUID bookingId);

    List<ChatMessageEntity> findBySenderAccountId(UUID senderAccountId);
}
