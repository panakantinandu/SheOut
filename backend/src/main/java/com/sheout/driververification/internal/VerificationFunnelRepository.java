package com.sheout.driververification.internal;

import com.sheout.auth.AccountRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

interface VerificationFunnelRepository extends JpaRepository<VerificationFunnelEntity, UUID> {

    /**
     * The step, the first time - and nothing at all if it is already there.
     * The database decides, so two requests at the same moment (a screen
     * mounting twice) cannot both get past a check and collide.
     */
    @Modifying
    @Query(value = """
            insert into verification_funnel_events (id, account_id, role, step, occurred_at, created_at, updated_at)
            values (gen_random_uuid(), :accountId, :role, :step, now(), now(), now())
            on conflict (account_id, step) do nothing
            """, nativeQuery = true)
    int recordOnce(@Param("accountId") UUID accountId, @Param("role") String role, @Param("step") String step);

    boolean existsByAccountIdAndStep(UUID accountId, VerificationFunnelStep step);

    /** How many accounts of this role reached this step since a given moment. */
    @Query("""
            select count(distinct e.accountId) from VerificationFunnelEntity e
            where e.role = :role and e.step = :step and e.occurredAt >= :since
            """)
    long countReaching(@Param("role") AccountRole role,
                       @Param("step") VerificationFunnelStep step,
                       @Param("since") Instant since);

    /**
     * Accounts that reached a step and have still not submitted anything.
     * <p>
     * The join is to the verification record rather than to a second event,
     * because the submission is already recorded there - adding a third
     * event for it would give two sources for one fact.
     */
    @Query("""
            select count(distinct e.accountId) from VerificationFunnelEntity e
            where e.role = :role and e.step = :step and e.occurredAt >= :since
              and not exists (
                select 1 from VerificationRecordEntity r
                where r.accountId = e.accountId and r.aadhaarDocumentKey is not null
              )
            """)
    long countAbandoned(@Param("role") AccountRole role,
                        @Param("step") VerificationFunnelStep step,
                        @Param("since") Instant since);
}
