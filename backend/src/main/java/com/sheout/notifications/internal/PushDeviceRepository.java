package com.sheout.notifications.internal;

import com.sheout.auth.AccountRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
