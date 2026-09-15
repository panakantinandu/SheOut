package com.sheout.notifications.internal;

import com.sheout.auth.AccountRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Device registration for push, and forgetting devices FCM says are gone. */
@Service
public class PushDeviceService {

    private final PushDeviceRepository devices;

    public PushDeviceService(PushDeviceRepository devices) {
        this.devices = devices;
    }

    /**
     * Idempotent: apps call this on every start, since FCM may rotate a token
     * at any time and the newest one is the only one that works.
     */
    @Transactional
    public void register(UUID accountId, AccountRole role, String token, String userAgent) {
        Instant now = Instant.now();
        String agent = userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 300));
        devices.findByToken(token).ifPresentOrElse(
                device -> device.refresh(accountId, role, agent, now),
                () -> devices.save(new PushDeviceEntity(accountId, role, token, agent, now)));
    }

    /** Signing out on this device. Nothing to say if it was never registered. */
    @Transactional
    public void unregister(UUID accountId, String token) {
        devices.deleteByTokenAndAccountId(token, accountId);
    }

    public List<PushDeviceEntity> devicesFor(UUID accountId) {
        return devices.findByAccountId(accountId);
    }

    public List<PushDeviceEntity> devicesForRole(AccountRole role) {
        return devices.findByAccountRole(role);
    }

    /** FCM has said this token is dead. Own transaction: called from delivery threads that have none. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void forget(String token) {
        devices.deleteByToken(token);
    }
}
