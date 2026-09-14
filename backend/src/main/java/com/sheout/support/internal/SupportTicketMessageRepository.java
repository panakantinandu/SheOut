package com.sheout.support.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SupportTicketMessageRepository extends JpaRepository<SupportTicketMessageEntity, UUID> {

    /** Oldest first: a thread is read in the order it happened. */
    List<SupportTicketMessageEntity> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
