package com.sheout.booking.internal;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** One change to the service hours, for the console's history. */
@Entity
@Table(name = "service_hours_changes")
class ServiceHoursChangeEntity extends BaseEntity {

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(nullable = false, length = 500)
    private String summary;

    protected ServiceHoursChangeEntity() {
    }

    ServiceHoursChangeEntity(UUID changedBy, String summary) {
        this.changedBy = changedBy;
        this.summary = summary.length() > 500 ? summary.substring(0, 500) : summary;
    }

    UUID getChangedBy() {
        return changedBy;
    }

    String getSummary() {
        return summary;
    }
}
