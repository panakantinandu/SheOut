package com.sheout.users;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * A partner switched herself OFFLINE.
 * <p>
 * Dispatch forgets where she was when it hears this. Her last position used
 * to stay in its store indefinitely after she stopped working - very often
 * her own front door - with nothing needing it.
 */
public class DriverWentOffline extends DomainEvent {

    private final UUID driverId;

    public DriverWentOffline(UUID driverId) {
        this.driverId = driverId;
    }

    public UUID driverId() {
        return driverId;
    }
}
