package com.sheout.notifications.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDeliveryEntity, UUID> {

    List<NotificationDeliveryEntity> findByNotificationId(UUID notificationId);

    /**
     * Account deletion: every address a notification to this account was sent
     * to - her own number and email, her devices, her SOS contacts' numbers.
     */
    @Modifying
    @Query("""
            update NotificationDeliveryEntity d set d.recipientAddress = null
            where d.recipientAddress is not null and d.notificationId in (
                select n.id from NotificationLogEntity n where n.recipientAccountId = :accountId)
            """)
    int eraseAddressesForAccount(@Param("accountId") UUID accountId);
}
