package com.sheout.notifications.internal.sos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SosAlertRepository extends JpaRepository<SosAlertEntity, UUID> {

    List<SosAlertEntity> findByStatusOrderByCreatedAtDesc(SosStatus status);
}
