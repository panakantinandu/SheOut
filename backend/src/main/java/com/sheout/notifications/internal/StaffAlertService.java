package com.sheout.notifications.internal;

import com.sheout.notifications.StaffAlertApi;
import com.sheout.notifications.internal.channel.OutboundMessage;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** StaffAlertApi over the dispatcher: push, urgent, opening the console's Audit page. */
@Service
class StaffAlertService implements StaffAlertApi {

    private final NotificationDispatcher dispatcher;

    StaffAlertService(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void alertStaff(UUID staffAccountId, String title, String body) {
        dispatcher.deliver(staffAccountId, NotificationType.STAFF_SECURITY_ALERT,
                new OutboundMessage(title, body, "/admin/#/audit", "staff-security", OutboundMessage.Urgency.ALERT));
    }
}
