package com.sheout.staff.internal;

import com.sheout.staff.Approvals;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ApprovalRepository extends JpaRepository<ApprovalEntity, UUID> {

    /** Locked, so two approvers clicking at once decide it once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ApprovalEntity a where a.id = :id")
    Optional<ApprovalEntity> findByIdForUpdate(@Param("id") UUID id);

    List<ApprovalEntity> findByStatusOrderByCreatedAtAsc(ApprovalEntity.Status status);

    List<ApprovalEntity> findTop100ByOrderByCreatedAtDesc();

    List<ApprovalEntity> findTop50ByRequestedByStaffIdOrderByCreatedAtDesc(UUID staffId);

    boolean existsByKindAndTargetIdAndStatusAndExpiresAtAfter(Approvals.Kind kind, String targetId, ApprovalEntity.Status status,
                                                              java.time.Instant now);
}
