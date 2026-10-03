package com.sheout.staff.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface StaffSessionRepository extends JpaRepository<StaffSessionEntity, UUID> {

    Optional<StaffSessionEntity> findByTokenHash(String tokenHash);

    List<StaffSessionEntity> findByStaffId(UUID staffId);
}
