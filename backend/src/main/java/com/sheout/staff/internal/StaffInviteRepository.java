package com.sheout.staff.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface StaffInviteRepository extends JpaRepository<StaffInviteEntity, UUID> {

    Optional<StaffInviteEntity> findByTokenHash(String tokenHash);

    /** Locked, so the same link opened twice at once is used once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from StaffInviteEntity i where i.tokenHash = :hash")
    Optional<StaffInviteEntity> findByTokenHashForUpdate(@Param("hash") String tokenHash);

    @Query("select i from StaffInviteEntity i where lower(i.email) = lower(:email) and i.status = :status")
    List<StaffInviteEntity> findByEmailAndStatus(@Param("email") String email,
                                                 @Param("status") StaffInviteEntity.Status status);

    List<StaffInviteEntity> findByStatusOrderByCreatedAtDesc(StaffInviteEntity.Status status);

    List<StaffInviteEntity> findByStaffMemberIdAndStatus(UUID staffMemberId, StaffInviteEntity.Status status);
}
