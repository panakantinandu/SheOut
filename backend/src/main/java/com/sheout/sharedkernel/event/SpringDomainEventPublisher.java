package com.sheout.sharedkernel.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * In-process implementation of {@link DomainEventPublisher} backed by
 * Spring's ApplicationEventPublisher. Listeners in other modules react with
 * a plain {@code @EventListener} (or {@code @TransactionalEventListener})
 * method on the concrete DomainEvent subtype - no direct coupling between
 * publisher and listener modules.
 */
@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher springPublisher;

    public SpringDomainEventPublisher(ApplicationEventPublisher springPublisher) {
        this.springPublisher = springPublisher;
    }

    @Override
    public void publish(DomainEvent event) {
        springPublisher.publishEvent(event);
    }
}
