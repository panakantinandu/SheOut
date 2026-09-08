package com.sheout.auth.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

    Optional<AccountEntity> findByPhoneNumber(String phoneNumber);
}
