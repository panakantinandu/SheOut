package com.sheout.sharedkernel.event;

/**
 * Abstraction modules depend on to publish {@link DomainEvent}s. Business
 * code must depend on this interface, never on Spring's
 * ApplicationEventPublisher (or a future Kafka/SQS client) directly - that
 * is what lets the in-process implementation be swapped for a message
 * broker later without touching any module's business logic.
 */
public interface DomainEventPublisher {

    void publish(DomainEvent event);
}
