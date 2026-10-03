package com.sheout.notifications;

import java.util.UUID;

/**
 * The browsers a member of staff gets SOS and trip alerts on.
 * <p>
 * The console registers through the staff module, under its cookie session,
 * rather than through the apps' /api/v1/notifications/devices: a console
 * sign-in never carries a bearer token, and staff alerts are not announcements
 * - an operator's browser is not put in any app's broadcast group.
 */
public interface OperatorDeviceApi {

    /** Idempotent: the console calls it whenever Firebase hands it a token. */
    void registerOperatorDevice(UUID accountId, String token, String userAgent);

    /** This browser stops receiving alerts. Nothing to say if it was never registered. */
    void unregisterOperatorDevice(UUID accountId, String token);

    /** Every browser of this account stops receiving alerts - she was disabled. */
    int forgetOperatorDevices(UUID accountId);
}
