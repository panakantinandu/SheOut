package com.sheout.notifications.internal;

import com.sheout.auth.AccountRole;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushDeviceRepository extends JpaRepository<PushDeviceEntity, UUID> {

    List<PushDeviceEntity> findByAccountId(UUID accountId);

    List<PushDeviceEntity> findByAccountRole(AccountRole accountRole);

    Optional<PushDeviceEntity> findByToken(String token);

    @Modifying
    @Query("delete from PushDeviceEntity d where d.token = :token")
    int deleteByToken(@Param("token") String token);

    /** Signing out: only the caller's own device, so a token cannot be used to unregister somebody else. */
    @Modifying
    @Query("delete from PushDeviceEntity d where d.token = :token and d.accountId = :accountId")
    int deleteByTokenAndAccountId(@Param("token") String token, @Param("accountId") UUID accountId);

    @Modifying
    @Query("delete from PushDeviceEntity d where d.accountId = :accountId")
    int deleteByAccountId(@Param("accountId") UUID accountId);

    /** One account, the last time any of her devices checked in - see ReengagementNudger. */
    interface LapsedAccount {
        UUID getAccountId();

        AccountRole getAccountRole();

        Instant getLastSeen();
    }

    /**
     * Accounts in these roles whose devices last checked in between
     * seenAfter and seenBefore, skipping anyone sent a reminder since
     * nudgedSince. Longest away first.
     */
    @Query("select d.accountId as accountId, d.accountRole as accountRole, max(d.lastSeenAt) as lastSeen "
            + "from PushDeviceEntity d "
            + "where d.accountRole in :roles "
            + "and not exists (select 1 from ReengagementNudgeEntity n where n.accountId = d.accountId and n.lastNudgedAt > :nudgedSince) "
            + "group by d.accountId, d.accountRole "
            + "having max(d.lastSeenAt) < :seenBefore and max(d.lastSeenAt) >= :seenAfter "
            + "order by max(d.lastSeenAt) asc")
    List<LapsedAccount> findLapsed(@Param("roles") Collection<AccountRole> roles, @Param("seenBefore") Instant seenBefore,
                                   @Param("seenAfter") Instant seenAfter, @Param("nudgedSince") Instant nudgedSince, Pageable page);
}
