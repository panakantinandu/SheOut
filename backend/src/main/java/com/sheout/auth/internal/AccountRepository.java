package com.sheout.auth.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

/** Specification support is for the ops console's account list - see AccountSpecs. */
interface AccountRepository extends JpaRepository<AccountEntity, UUID>, JpaSpecificationExecutor<AccountEntity> {

    Optional<AccountEntity> findByPhoneNumber(String phoneNumber);

    Optional<AccountEntity> findByEmail(String email);
}
