package com.sheout.notifications.internal;

import com.sheout.privacy.AccountDeletionRequested;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes phone numbers from a deleted account's notification history.
 * <p>
 * SOS alerts are kept as they are: they hold a location and an account id,
 * no name or number, and a record that a woman raised an alert on a trip is
 * exactly what a later investigation would need. Their names come from the
 * profile, which already reads "Deleted User".
 */
@Component
class AccountDeletionNotificationListener {

    private final NotificationLogRepository logs;

    AccountDeletionNotificationListener(NotificationLogRepository logs) {
        this.logs = logs;
    }

    @EventListener
    @Transactional
    public void onAccountDeletionRequested(AccountDeletionRequested event) {
        var entries = logs.findByRecipientAccountIdAndRecipientAddressIsNotNull(event.accountId());
        entries.forEach(NotificationLogEntity::eraseRecipientAddress);
        logs.saveAll(entries);
    }
}
