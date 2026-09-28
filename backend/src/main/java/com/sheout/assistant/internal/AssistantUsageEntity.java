package com.sheout.assistant.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One account's use of the help assistant on one day (India time): the
 * daily cap is read from it, and the console's cost figures are summed from
 * it. Written only by AssistantUsageRepository.add, an atomic upsert.
 */
@Entity
@Table(name = "assistant_usage")
@IdClass(AssistantUsageEntity.Key.class)
class AssistantUsageEntity {

    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @Id
    @Column(name = "day")
    private LocalDate day;

    private int messages;
    private long inputTokens;
    private long outputTokens;
    private long cacheReadTokens;
    private long cacheWriteTokens;
    private int escalations;
    private int emergencies;

    protected AssistantUsageEntity() {
        // JPA
    }

    int getMessages() {
        return messages;
    }

    record Key(UUID accountId, LocalDate day) implements Serializable {
        Key() {
            this(null, null);
        }
    }
}
