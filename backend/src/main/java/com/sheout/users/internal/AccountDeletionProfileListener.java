package com.sheout.users.internal;

import com.sheout.privacy.AccountDeletionRequested;
import com.sheout.sharedkernel.storage.DocumentStorage;
import com.sheout.users.OnlineStatus;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The users half of an account deletion: the profile stays - other people's
 * bookings and ratings are joined to it - but nothing on it identifies the
 * person any more.
 * <p>
 * Its own listener rather than more of UserProfileEventListeners, which is
 * about keeping counters and flags up to date; this is about removing a
 * person, and reads better on its own.
 */
@Component
class AccountDeletionProfileListener {

    private final CustomerProfileRepository customerProfiles;
    private final DriverProfileRepository driverProfiles;
    private final EmergencyContactRepository emergencyContacts;
    private final DocumentStorage documentStorage;

    AccountDeletionProfileListener(CustomerProfileRepository customerProfiles,
                                   DriverProfileRepository driverProfiles,
                                   EmergencyContactRepository emergencyContacts,
                                   DocumentStorage documentStorage) {
        this.customerProfiles = customerProfiles;
        this.driverProfiles = driverProfiles;
        this.emergencyContacts = emergencyContacts;
        this.documentStorage = documentStorage;
    }

    @EventListener
    @Transactional
    public void onAccountDeletionRequested(AccountDeletionRequested event) {
        customerProfiles.findByAccountId(event.accountId()).ifPresent(profile -> {
            profile.setName(AccountDeletionRequested.DELETED_NAME);
            profile.setHomeAddress(null);
            profile.setWorkAddress(null);
            profile.setDateOfBirth(null);
            profile.setEmail(null);
            if (profile.getProfilePhotoKey() != null) {
                documentStorage.delete(profile.getProfilePhotoKey());
                profile.setProfilePhotoKey(null);
            }
            // Deleted outright, not anonymised. They are other people's names
            // and numbers, held only so this person's SOS could reach them.
            emergencyContacts.deleteAll(emergencyContacts.findByCustomerProfileId(profile.getId()));
            customerProfiles.save(profile);
        });

        driverProfiles.findByAccountId(event.accountId()).ifPresent(profile -> {
            profile.setName(AccountDeletionRequested.DELETED_NAME);
            // A registration number identifies a person as surely as a name.
            profile.setVehicleRegistrationNumber(null);
            profile.setDateOfBirth(null);
            profile.setEmail(null);
            // Permanently: nothing can set her ONLINE again, because the
            // account's token no longer authenticates.
            profile.setOnlineStatus(OnlineStatus.OFFLINE);
            if (profile.getProfilePhotoKey() != null) {
                // The file itself, before the key is dropped - a photo left in
                // storage with nothing pointing at it would be undeletable later.
                documentStorage.delete(profile.getProfilePhotoKey());
                profile.setProfilePhotoKey(null);
            }
            driverProfiles.save(profile);
        });
    }
}
