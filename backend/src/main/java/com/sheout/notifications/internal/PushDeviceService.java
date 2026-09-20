package com.sheout.notifications.internal;

import com.sheout.notifications.internal.channel.FcmPushChannel;
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
    private final FcmPushChannel push;

    public PushDeviceService(PushDeviceRepository devices, FcmPushChannel push) {
        this.devices = devices;
        this.push = push;
    }

    /** The topic every device of one app listens to - see FcmPushChannel.sendToTopic. */
    public static String announcementTopic(AccountRole role) {
        return role == AccountRole.DRIVER ? "driver-announcements" : "customer-announcements";
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
        // And into its app's announcements group, so a broadcast is one call
        // to FCM rather than a loop over every token we hold.
        push.subscribeToTopic(token, announcementTopic(role));
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
