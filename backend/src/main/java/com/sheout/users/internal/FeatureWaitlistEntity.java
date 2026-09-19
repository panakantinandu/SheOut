package com.sheout.users.internal;

import com.sheout.sharedkernel.BaseEntity;
import com.sheout.users.WaitlistFeature;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** Somebody asked to hear when a feature launches. Who and when - nothing else. */
@Entity
@Table(name = "feature_waitlist")
public class FeatureWaitlistEntity extends BaseEntity {

    @Column(nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WaitlistFeature feature;

    protected FeatureWaitlistEntity() {
        // JPA
    }

    FeatureWaitlistEntity(UUID accountId, WaitlistFeature feature) {
        this.accountId = accountId;
        this.feature = feature;
    }
}
