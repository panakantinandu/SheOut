package com.sheout.users.internal;

import com.sheout.auth.AccountRegistered;
import com.sheout.driververification.AccountVerified;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class UserProfileEventListeners {

    private final CustomerProfileRepository customerProfileRepository;
    private final DriverProfileRepository driverProfileRepository;

    UserProfileEventListeners(CustomerProfileRepository customerProfileRepository,
                               DriverProfileRepository driverProfileRepository) {
        this.customerProfileRepository = customerProfileRepository;
        this.driverProfileRepository = driverProfileRepository;
    }

    /**
     * Creates the (empty) profile row every account needs one of, so a
     * profile always exists by the time a client can call any self-service
     * endpoint here - auth only issues a token after this listener has run
     * (same transaction as AuthService.verifyOtp).
     */
    @EventListener
    @Transactional
    public void onAccountRegistered(AccountRegistered event) {
        switch (event.role()) {
            case CUSTOMER -> customerProfileRepository.save(new CustomerProfileEntity(event.accountId()));
            case DRIVER -> driverProfileRepository.save(new DriverProfileEntity(event.accountId()));
            case ADMIN -> {
                // No profile in this module for admins.
            }
        }
    }

    /**
     * Updates the display-only `verified` cache exposed on
     * CustomerProfileSummary/DriverProfileSummary. This is what satisfies
     * "subscribe to the event, don't poll" - but it is NOT what
     * DriverProfileService.setOnlineStatus checks before allowing ONLINE;
     * that does a live call instead. Reconciling those two requirements
     * this way is a judgment call - see the module README note.
     */
    @EventListener
    @Transactional
    public void onAccountVerified(AccountVerified event) {
        switch (event.role()) {
            case CUSTOMER -> customerProfileRepository.findByAccountId(event.accountId())
                    .ifPresent(profile -> {
                        profile.setVerified(true);
                        customerProfileRepository.save(profile);
                    });
            case DRIVER -> driverProfileRepository.findByAccountId(event.accountId())
                    .ifPresent(profile -> {
                        profile.setVerified(true);
                        driverProfileRepository.save(profile);
                    });
            case ADMIN -> {
                // AccountVerified is never published for ADMIN accounts.
            }
        }
    }
}
