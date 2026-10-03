package com.sheout.driververification.internal;

import com.sheout.driververification.PartnerDocumentStatus;
import com.sheout.driververification.PartnerDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PartnerDocumentRepository extends JpaRepository<PartnerDocumentEntity, UUID> {

    /** Every version of every document she has ever sent, newest first. A partner has a handful. */
    List<PartnerDocumentEntity> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

    Optional<PartnerDocumentEntity> findByAccountIdAndTypeAndSupersededAtIsNull(UUID accountId, PartnerDocumentType type);

    /** Locked, so an upload and a review of the same type cannot interleave. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from PartnerDocumentEntity d where d.id = :id")
    Optional<PartnerDocumentEntity> findLockedById(@Param("id") UUID id);

    /** The current documents waiting for an operator, oldest submission first - the order they should be read in. */
    List<PartnerDocumentEntity> findBySupersededAtIsNullAndStatusInOrderBySubmittedAtAsc(
            Collection<PartnerDocumentStatus> statuses);

    /** Current, approved documents whose printed validity ends on or before the given day. */
    @Query("""
            select d from PartnerDocumentEntity d
            where d.supersededAt is null
              and d.status = com.sheout.driververification.PartnerDocumentStatus.VERIFIED
              and d.validUntil is not null
              and d.validUntil <= :until
            order by d.validUntil asc
            """)
    List<PartnerDocumentEntity> findVerifiedValidUntilOnOrBefore(@Param("until") LocalDate until);

    /**
     * Documents still in force whose validity has run out - checked every
     * sweep. "In force" is the current row, or the earlier row a renewal
     * under review is waiting to replace: that one still counts, so it is
     * its date that runs out. An older version an approved renewal has
     * replaced is history and is left as it was approved.
     */
    @Query("""
            select d from PartnerDocumentEntity d
            where d.status = com.sheout.driververification.PartnerDocumentStatus.VERIFIED
              and d.validUntil is not null
              and d.validUntil < :today
              and (d.supersededAt is null or exists (
                    select r.id from PartnerDocumentEntity r
                    where r.replacesDocumentId = d.id
                      and r.supersededAt is null
                      and r.status in (com.sheout.driververification.PartnerDocumentStatus.PENDING,
                                       com.sheout.driververification.PartnerDocumentStatus.UNDER_REVIEW)))
            """)
    List<PartnerDocumentEntity> findVerifiedExpiredBefore(@Param("today") LocalDate today);

    /** Current rows already expired - the console's "Expired / blocked" queue. */
    List<PartnerDocumentEntity> findBySupersededAtIsNullAndStatusOrderByExpiredAtDesc(PartnerDocumentStatus status);
}
