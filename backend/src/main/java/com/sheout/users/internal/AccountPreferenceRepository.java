package com.sheout.users.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface AccountPreferenceRepository extends JpaRepository<AccountPreferenceEntity, UUID> {

    Optional<AccountPreferenceEntity> findByAccountId(UUID accountId);

    void deleteByAccountId(UUID accountId);
}
