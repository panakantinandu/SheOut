package com.sheout.chat.internal;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {

    /** Oldest first: a conversation is read in the order it happened. */
    List<ChatMessageEntity> findByBookingIdOrderByCreatedAtAsc(UUID bookingId);

    List<ChatMessageEntity> findBySenderAccountId(UUID senderAccountId);

    /** Trips with at least one message older than the cutoff, for retention. */
    @Query("select distinct m.bookingId from ChatMessageEntity m where m.createdAt < :cutoff")
    List<UUID> findBookingIdsWithMessagesBefore(@Param("cutoff") Instant cutoff, Pageable page);

    long countByBookingIdAndCreatedAtBefore(UUID bookingId, Instant cutoff);

    @Modifying
    @Query("delete from ChatMessageEntity m where m.bookingId = :bookingId and m.createdAt < :cutoff")
    int deleteByBookingIdAndCreatedAtBefore(@Param("bookingId") UUID bookingId, @Param("cutoff") Instant cutoff);
}
