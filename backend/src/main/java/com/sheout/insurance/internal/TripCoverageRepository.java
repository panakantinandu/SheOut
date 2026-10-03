package com.sheout.insurance.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TripCoverageRepository extends JpaRepository<TripCoverageEntity, UUID> {

    Optional<TripCoverageEntity> findByBookingId(UUID bookingId);

    List<TripCoverageEntity> findByCoverageStartedAtGreaterThanEqualAndCoverageStartedAtLessThanOrderByCoverageStartedAtAsc(
            Instant from, Instant until);

    long countByReportedStatusAndCoverageStartedAtLessThan(TripCoverageEntity.ReportedStatus status, Instant before);

    long countByReportedStatus(TripCoverageEntity.ReportedStatus status);

    long countByCoverageStartedAtGreaterThanEqual(Instant since);

    /** The premium SheOut owes for trips started in a window, per policy. */
    @Query("""
            select new com.sheout.insurance.internal.PremiumLine(c.policyId, count(c), sum(c.premiumAmount))
            from TripCoverageEntity c
            where c.coverageStartedAt >= :from and c.coverageStartedAt < :until
            group by c.policyId
            """)
    List<PremiumLine> premiumByPolicy(@Param("from") Instant from, @Param("until") Instant until);
}
