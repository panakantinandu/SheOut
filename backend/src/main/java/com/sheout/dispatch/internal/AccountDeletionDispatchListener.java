package com.sheout.dispatch.internal;

import com.sheout.auth.AccountRole;
import com.sheout.booking.BookingApi;
import com.sheout.users.DriverWentOffline;
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
    private final BookingApi bookingApi;

    AccountDeletionDispatchListener(DriverLocationStore locations, OfferStore offers, BookingApi bookingApi) {
        this.locations = locations;
        this.offers = offers;
        this.bookingApi = bookingApi;
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

    /**
     * Stopped working: forget where she is, and withdraw any offer on her
     * screen. Kept while she still has a rider aboard or on the way - the
     * trip's pickup and drop checks read this position.
     */
    @EventListener
    public void onDriverWentOffline(DriverWentOffline event) {
        offers.findActiveOfferForDriver(event.driverId())
                .ifPresent(bookingId -> offers.removeOffer(bookingId, event.driverId()));
        if (!bookingApi.hasActiveTripAsDriver(event.driverId())) {
            locations.remove(event.driverId());
        }
    }
}
