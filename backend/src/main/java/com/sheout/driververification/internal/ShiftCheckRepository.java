package com.sheout.driververification.internal;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ShiftCheckRepository extends JpaRepository<ShiftCheckEntity, UUID> {

    Optional<ShiftCheckEntity> findByChallengeIdAndAccountId(String challengeId, UUID accountId);

    /** Her answered checks, newest first - ISSUED rows are prompts nobody answered and say nothing. */
    @Query("""
            select c from ShiftCheckEntity c
            where c.accountId = :accountId and c.submittedAt is not null
            order by c.submittedAt desc
            """)
    List<ShiftCheckEntity> findAnsweredByAccount(@Param("accountId") UUID accountId, Pageable pageable);

    List<ShiftCheckEntity> findByAccountId(UUID accountId);

    List<ShiftCheckEntity> findByStatusInOrderBySubmittedAtAsc(Collection<ShiftCheckEntity.Status> statuses);

    /** Passed on the photo alone, not yet looked at - the audit list. */
    @Query("""
            select c from ShiftCheckEntity c
            where c.status = :passed and c.faceResult = :unavailable
              and c.reviewedAt is null and c.submittedAt >= :since
            order by c.submittedAt asc
            """)
    List<ShiftCheckEntity> findUnmatchedPasses(@Param("passed") ShiftCheckEntity.Status passed,
                                               @Param("unavailable") ShiftCheckEntity.FaceResult unavailable,
                                               @Param("since") Instant since);

    /** Photos past the retention window, for the housekeeping in ShiftCheckService. */
    @Query("""
            select c from ShiftCheckEntity c
            where c.accountId = :accountId and c.submittedAt < :before
              and c.selfieKey is not null and c.status in :settled
            """)
    List<ShiftCheckEntity> findExpiredPhotos(@Param("accountId") UUID accountId,
                                             @Param("before") Instant before,
                                             @Param("settled") Collection<ShiftCheckEntity.Status> settled);
}
