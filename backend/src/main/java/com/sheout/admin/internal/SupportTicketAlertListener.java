package com.sheout.admin.internal;

import com.sheout.support.SupportTicketPriority;
import com.sheout.support.SupportTicketRaised;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The admin side of "a ticket was raised".
 * <p>
 * There is no pushed operator alert anywhere in this codebase today -
 * verification submissions reach an operator only as the queue count in the
 * console sidebar, recomputed when the console loads. Tickets reach them the
 * same way (SupportOpsService.counts). This listener is the subscription
 * that exists so a real alert - an SMS to the support line, a push to an
 * operator app - is added here, in one place, without support changing.
 * Until then it records the fact in the log, with a HIGH-priority ticket at
 * WARN so it stands out in the hosting dashboard.
 * <p>
 * AFTER_COMMIT, so a ticket whose transaction rolled back is never reported.
 * Account and ticket ids only - no subject or description in a log line.
 */
@Component
class SupportTicketAlertListener {

    private static final Logger log = LoggerFactory.getLogger(SupportTicketAlertListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketRaised(SupportTicketRaised event) {
        if (event.priority() == SupportTicketPriority.HIGH) {
            log.warn("HIGH-priority support ticket {} raised by {} {} ({})",
                    event.ticketId(), event.role(), event.raisedByAccountId(), event.category());
        } else {
            log.info("Support ticket {} raised by {} {} ({})",
                    event.ticketId(), event.role(), event.raisedByAccountId(), event.category());
        }
    }
}
