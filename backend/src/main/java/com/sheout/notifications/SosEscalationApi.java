package com.sheout.notifications;

import java.util.Collection;
import java.util.UUID;

/**
 * The push that says an SOS is still waiting: nobody has taken it. The admin
 * module decides when and to whom (see its SosEscalationSweeper); this module
 * only delivers it.
 * <p>
 * Like the first SOS push, the text names nobody and says nothing about
 * where: a lock screen is not the place for either.
 */
public interface SosEscalationApi {

    /**
     * @param staffAccountIds the accounts standing for the staff to reach
     * @param minutesWaiting  how long the SOS has waited, for the text
     */
    void pushUnanswered(Collection<UUID> staffAccountIds, UUID alertId, boolean duringTrip, long minutesWaiting);
}
