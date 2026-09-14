package com.sheout.notifications.internal.sos;

import com.sheout.notifications.SosStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SosAlertRepository extends JpaRepository<SosAlertEntity, UUID> {

    List<SosAlertEntity> findByStatusOrderByCreatedAtDesc(SosStatus status);

    List<SosAlertEntity> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    /** Whether any of her alerts since {@code since} actually reached at least one contact. */
    boolean existsByCustomerAccountIdAndContactsNotifiedGreaterThanAndCreatedAtAfter(
            UUID customerAccountId, int contactsNotified, Instant since);
}
