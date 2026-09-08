package com.sheout.sharedkernel.event;

import java.time.Instant;

/**
 * Marker base for anything a module publishes when something other modules
 * might eventually care about happens (e.g. "booking confirmed", "driver
 * verified"). Modules communicate reactions to these facts through events,
 * not by calling each other's internals directly.
 */
public abstract class DomainEvent {

    private final Instant occurredAt = Instant.now();

    public Instant occurredAt() {
        return occurredAt;
    }
}
