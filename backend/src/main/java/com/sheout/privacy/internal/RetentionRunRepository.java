package com.sheout.privacy.internal;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface RetentionRunRepository extends JpaRepository<RetentionRunEntity, UUID> {

    List<RetentionRunEntity> findAllByOrderByCreatedAtDesc(Pageable page);
}
