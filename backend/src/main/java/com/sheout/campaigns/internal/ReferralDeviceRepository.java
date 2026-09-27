package com.sheout.campaigns.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ReferralDeviceRepository extends JpaRepository<ReferralDeviceEntity, UUID> {

    Optional<ReferralDeviceEntity> findByAccountIdAndInstallId(UUID accountId, String installId);

    boolean existsByAccountIdAndInstallId(UUID accountId, String installId);

    /** Whether the two accounts have ever used the referral screens from the same app install. */
    @org.springframework.data.jpa.repository.Query("select count(a) > 0 from ReferralDeviceEntity a, ReferralDeviceEntity b "
            + "where a.accountId = :x and b.accountId = :y and a.installId = b.installId")
    boolean shareAnInstall(@org.springframework.data.repository.query.Param("x") UUID x,
                           @org.springframework.data.repository.query.Param("y") UUID y);
}
