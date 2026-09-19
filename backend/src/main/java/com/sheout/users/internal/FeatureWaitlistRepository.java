package com.sheout.users.internal;

import com.sheout.users.WaitlistFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface FeatureWaitlistRepository extends JpaRepository<FeatureWaitlistEntity, UUID> {

    Optional<FeatureWaitlistEntity> findByAccountIdAndFeature(UUID accountId, WaitlistFeature feature);

    long countByFeature(WaitlistFeature feature);

    void deleteByAccountId(UUID accountId);

    /**
     * Joins, or does nothing if she already has. ON CONFLICT rather than
     * insert-and-catch, so two taps racing each other are one signal and
     * neither of them fails.
     */
    @Modifying
    @Query(value = """
            insert into feature_waitlist (id, account_id, feature, created_at, updated_at)
            values (gen_random_uuid(), :accountId, :feature, now(), now())
            on conflict (account_id, feature) do nothing
            """, nativeQuery = true)
    void joinIfAbsent(@Param("accountId") UUID accountId, @Param("feature") String feature);
}
