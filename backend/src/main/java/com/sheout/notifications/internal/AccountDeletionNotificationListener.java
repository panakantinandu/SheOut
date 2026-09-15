package com.sheout.notifications.internal;

import com.sheout.privacy.AccountDeletionRequested;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes every address from a deleted account's notification history and
 * forgets her devices.
 * <p>
 * The addresses are phone numbers and emails - her own, or for an SOS send,
 * one of her emergency contacts' - and device tokens. The notifications
 * themselves stay, like her trips, as a record of what was sent; their text
 * never contains another person's details.
 * <p>
 * SOS alerts are kept as they are: they hold a location and an account id,
 * no name or number, and a record that a woman raised an alert on a trip is
 * exactly what a later investigation would need. Their names come from the
 * profile, which already reads "Deleted User".
 */
@Component
class AccountDeletionNotificationListener {

    private final NotificationDeliveryRepository deliveries;
    private final PushDeviceRepository devices;

    AccountDeletionNotificationListener(NotificationDeliveryRepository deliveries, PushDeviceRepository devices) {
        this.deliveries = deliveries;
        this.devices = devices;
    }

    @EventListener
    @Transactional
    public void onAccountDeletionRequested(AccountDeletionRequested event) {
        deliveries.eraseAddressesForAccount(event.accountId());
        // A device left registered would keep receiving pushes for an account
        // that no longer exists - or, once her number is reused, for nobody.
        devices.deleteByAccountId(event.accountId());
    }
}
