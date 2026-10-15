package com.sheout.driververification.internal;

import com.sheout.driververification.CourtScreeningStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CourtScreeningRepository extends JpaRepository<CourtScreeningEntity, UUID> {

    List<CourtScreeningEntity> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

    List<CourtScreeningEntity> findByStatusInOrderByUpdatedAtAsc(Collection<CourtScreeningStatus> statuses);

    List<CourtScreeningEntity> findAllByOrderByUpdatedAtDesc(Pageable page);

    /** The screening still in progress for her, if any (not decided, not withdrawn). */
    default Optional<CourtScreeningEntity> findOpen(UUID accountId) {
        return findByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .filter(s -> !s.getStatus().decided() && s.getStatus() != CourtScreeningStatus.WITHDRAWN)
                .findFirst();
    }

    @Query("""
            select s from CourtScreeningEntity s
            where s.status = :rejected and s.decidedAt < :before and s.anonymisedAt is null
            """)
    List<CourtScreeningEntity> findRejectedToAnonymise(@Param("rejected") CourtScreeningStatus rejected,
                                                       @Param("before") Instant before, Pageable page);

    @Query("select s from CourtScreeningEntity s where s.leftAt < :before and s.anonymisedAt is null")
    List<CourtScreeningEntity> findDepartedToAnonymise(@Param("before") Instant before, Pageable page);
}
