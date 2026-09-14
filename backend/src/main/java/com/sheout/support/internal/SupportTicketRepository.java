package com.sheout.support.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

/** JpaSpecificationExecutor for the paged, filtered lists - see SupportTicketSpecs. */
interface SupportTicketRepository extends JpaRepository<SupportTicketEntity, UUID>,
        JpaSpecificationExecutor<SupportTicketEntity> {

    /**
     * The row, locked until the transaction ends, for every status change and
     * assignment. Two operators resolving and reopening the same ticket at
     * once would otherwise each read the old status, each pass the transition
     * check, and the second write would silently undo the first - the same
     * lost update BookingRepository.findLockedById closes for pickup codes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SupportTicketEntity> findLockedById(UUID id);
}
