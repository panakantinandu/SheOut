package com.sheout.notifications.internal;

import com.sheout.notifications.SosEscalationApi;
import com.sheout.notifications.internal.channel.OutboundMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.UUID;

/**
 * SosEscalationApi over the dispatcher: an urgent push to each named member of
 * staff, opening the console's SOS page. The tag is the first SOS push's own,
 * so each reminder replaces the last on her phone and rings again, rather
 * than stacking up.
 */
@Service
class SosEscalationService implements SosEscalationApi {

    private static final Logger log = LoggerFactory.getLogger(SosEscalationService.class);

    private final NotificationDispatcher dispatcher;

    SosEscalationService(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void pushUnanswered(Collection<UUID> staffAccountIds, UUID alertId, boolean duringTrip, long minutesWaiting) {
        OutboundMessage message = new OutboundMessage(
                "SOS: nobody has taken it",
                (duringTrip ? "Raised during a trip " : "Raised ") + minutesWaiting
                        + (minutesWaiting == 1 ? " minute" : " minutes") + " ago and still waiting. Open the console and take it now.",
                "/admin/#/sos",
                "sos-" + alertId,
                OutboundMessage.Urgency.ALERT);
        for (UUID accountId : staffAccountIds) {
            try {
                dispatcher.deliver(accountId, NotificationType.SOS_ESCALATION, message);
            } catch (RuntimeException ex) {
                // One account's failure must not keep the reminder from the rest.
                log.error("SOS reminder for alert {} not delivered to {}: {}", alertId, accountId, ex.toString());
            }
        }
    }
}
