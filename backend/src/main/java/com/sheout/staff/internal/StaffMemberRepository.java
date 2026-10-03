package com.sheout.staff.internal;

import com.sheout.staff.StaffRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface StaffMemberRepository extends JpaRepository<StaffMemberEntity, UUID> {

    @Query("select m from StaffMemberEntity m where lower(m.email) = lower(:email)")
    Optional<StaffMemberEntity> findByEmail(@Param("email") String email);

    /**
     * Locked for the length of the sign-in, so two attempts at once cannot
     * both read "four failures so far" and both get a fifth try.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from StaffMemberEntity m where lower(m.email) = lower(:email)")
    Optional<StaffMemberEntity> findByEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from StaffMemberEntity m where m.id = :id")
    Optional<StaffMemberEntity> findByIdForUpdate(@Param("id") UUID id);

    Optional<StaffMemberEntity> findByAccountId(UUID accountId);

    long countByRoleAndStatus(StaffRole role, StaffStatus status);

    List<StaffMemberEntity> findByStatusOrderByDisplayNameAsc(StaffStatus status);

    List<StaffMemberEntity> findAllByOrderByStatusAscDisplayNameAsc();
}
