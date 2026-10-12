package com.sheout.booking.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface ParcelDeliveryRepository extends JpaRepository<ParcelDeliveryEntity, UUID> {

    /** Receivers' numbers past retention, for the sweep - a delivery under dispute keeps its own. */
    List<ParcelDeliveryEntity> findTop500ByCreatedAtBeforeAndReceiverPhoneIsNotNull(Instant cutoff);
}
