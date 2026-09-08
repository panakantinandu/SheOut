package com.sheout.users.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface EmergencyContactRepository extends JpaRepository<EmergencyContactEntity, UUID> {

    List<EmergencyContactEntity> findByCustomerProfileId(UUID customerProfileId);

    Optional<EmergencyContactEntity> findByIdAndCustomerProfileId(UUID id, UUID customerProfileId);
}
