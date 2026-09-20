package com.sheout.users.internal;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface DriverProfileRepository extends JpaRepository<DriverProfileEntity, UUID> {

    Optional<DriverProfileEntity> findByAccountId(UUID accountId);

    /** The cancellation review queue: oldest crossing first, so nobody waits behind a newer case. */
    List<DriverProfileEntity> findByFlaggedAtIsNotNullOrderByFlaggedAtAsc();
    /** Addresses only, for an announcement - see DriverProfileApi.findEmailAddresses. */
    @Query("select p.email from DriverProfileEntity p where p.email is not null and p.email <> '' order by p.accountId")
    List<String> findEmailAddresses(Pageable pageable);
}
