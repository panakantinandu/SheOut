package com.sheout.booking.internal;

import com.sheout.sharedkernel.cluster.ClusterLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Deletes parcel photos and receivers' numbers once they are past
 * PARCEL_PHOTO_RETENTION_DAYS (90), except while a dispute is open. Hourly,
 * on one server at a time; each run takes at most a few hundred, so a
 * backlog clears over a few runs without one long transaction.
 */
@Component
class ParcelRetentionSweeper {

    private static final Logger log = LoggerFactory.getLogger(ParcelRetentionSweeper.class);

    private final ParcelDeliveryService parcels;
    private final ClusterLock lock;

    ParcelRetentionSweeper(ParcelDeliveryService parcels, ClusterLock lock) {
        this.parcels = parcels;
        this.lock = lock;
    }

    @Scheduled(fixedDelayString = "${sheout.parcel.retention-sweep-interval-ms:3600000}", initialDelayString = "${sheout.parcel.retention-sweep-initial-delay-ms:300000}")
    public void sweep() {
        lock.runExclusively("parcel-retention", Duration.ofMinutes(10), () -> {
            int deleted = parcels.sweepRetention(Instant.now());
            if (deleted > 0) {
                log.info("Parcel retention: deleted {} photos past the retention period", deleted);
            }
        });
    }
}
