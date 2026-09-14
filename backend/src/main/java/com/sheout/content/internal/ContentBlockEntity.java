package com.sheout.content.internal;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

@Entity
@Table(name = "content_blocks")
public class ContentBlockEntity extends BaseEntity {

    @Column(name = "content_key", nullable = false, unique = true, length = 100, updatable = false)
    private String contentKey;

    @Column(nullable = false, length = 2000)
    private String value;

    @Column(nullable = false, length = 200, updatable = false)
    private String description;

    /**
     * Hibernate increments this on every update and refuses an update whose
     * version no longer matches the row - the backstop behind the explicit
     * expectedVersion check in ContentService.
     */
    @Version
    private long version;

    private UUID updatedBy;

    protected ContentBlockEntity() {
        // JPA
    }

    void edit(String value, UUID updatedBy) {
        this.value = value;
        this.updatedBy = updatedBy;
    }

    public String getKey() {
        return contentKey;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public long getVersion() {
        return version;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}
