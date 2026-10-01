package com.sheout.booking.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ServiceHoursRepository extends JpaRepository<ServiceHoursEntity, UUID> {

    /** The single settings row. Oldest first, so a stray second row can never take over. */
    Optional<ServiceHoursEntity> findFirstByOrderByCreatedAtAsc();
}
