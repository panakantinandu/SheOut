package com.sheout.auth.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SessionRepository extends JpaRepository<SessionEntity, UUID> {

    /** Every live session for an account: for signing the others out, and for her own device list. */
    List<SessionEntity> findByAccountIdAndStatusOrderByLastActiveAtDesc(UUID accountId, SessionStatus status);

    /** One of her own sessions, by id. Scoped by account so another account's id is simply not found. */
    Optional<SessionEntity> findByIdAndAccountId(UUID id, UUID accountId);
}
