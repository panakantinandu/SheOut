package com.sheout.notifications.internal;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface AnnouncementRepository extends JpaRepository<AnnouncementEntity, UUID> {

    List<AnnouncementEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
