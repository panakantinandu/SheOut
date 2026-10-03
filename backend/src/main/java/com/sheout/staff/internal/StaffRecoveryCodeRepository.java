package com.sheout.staff.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface StaffRecoveryCodeRepository extends JpaRepository<StaffRecoveryCodeEntity, UUID> {

    List<StaffRecoveryCodeEntity> findByStaffId(UUID staffId);

    void deleteByStaffId(UUID staffId);
}
