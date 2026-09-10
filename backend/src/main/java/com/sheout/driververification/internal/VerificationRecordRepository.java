package com.sheout.driververification.internal;

import com.sheout.driververification.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface VerificationRecordRepository extends JpaRepository<VerificationRecordEntity, UUID> {

    Optional<VerificationRecordEntity> findByAccountId(UUID accountId);

    List<VerificationRecordEntity> findByGenderVerificationStatus(VerificationStatus status);

    /**
     * Outstanding review work. The only @Query in this codebase - every
     * other repository method here is a derived query name, and this one
     * would be too if it could be: Spring Data derived names have no way to
     * group terms, so "A or (B and C)" cannot be written as a method name
     * without relying on undefined precedence. Spelling the JPQL out is the
     * boring option, and it stays behind this interface either way.
     * <p>
     * The two checks match different statuses on purpose - see
     * VerificationApi.findAwaitingReview. The aadhaarDocumentKey test is
     * what keeps a driver who signed up and did nothing out of the queue,
     * while keeping one who submitted and is mid-pipeline in it. CUSTOMER
     * accounts have a null policeVerificationStatus, so they never match
     * the second clause.
     */
    @Query("""
            select r from VerificationRecordEntity r
            where r.genderVerificationStatus = :underReview
               or (r.policeVerificationStatus = :pending and r.aadhaarDocumentKey is not null)
            order by r.updatedAt desc
            """)
    List<VerificationRecordEntity> findAwaitingReview(@Param("underReview") VerificationStatus underReview,
                                                      @Param("pending") VerificationStatus pending);
}
