package com.sheout.dispatch.internal;

import com.sheout.auth.AccountRole;
import com.sheout.dispatch.internal.redis.DriverLocationStore;
import com.sheout.dispatch.internal.redis.OfferStore;
import com.sheout.privacy.AccountDeletionRequested;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Takes a deleted partner out of matching at once: her last position is
 * removed from the geo index, so no search can find her, and any offer
 * waiting for her answer is withdrawn so the booking is not left waiting on
 * someone who no longer exists. Her profile is set OFFLINE by users.
 * <p>
 * Redis, not the database, so this does not roll back with the rest of a
 * failed deletion. That is harmless: she was about to be removed anyway,
 * and going back online sends a fresh position.
 */
@Component
class AccountDeletionDispatchListener {

    private final DriverLocationStore locations;
    private final OfferStore offers;

    AccountDeletionDispatchListener(DriverLocationStore locations, OfferStore offers) {
        this.locations = locations;
        this.offers = offers;
    }

    @EventListener
    public void onAccountDeletionRequested(AccountDeletionRequested event) {
        if (event.role() != AccountRole.DRIVER) {
            return;
        }
        locations.remove(event.accountId());
        offers.findActiveOfferForDriver(event.accountId())
                .ifPresent(bookingId -> offers.removeOffer(bookingId, event.accountId()));
    }
}
