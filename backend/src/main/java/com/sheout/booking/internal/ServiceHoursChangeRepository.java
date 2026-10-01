package com.sheout.booking.internal;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ServiceHoursChangeRepository extends JpaRepository<ServiceHoursChangeEntity, UUID> {

    List<ServiceHoursChangeEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
