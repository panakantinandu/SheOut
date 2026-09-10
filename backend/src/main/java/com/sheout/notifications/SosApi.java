package com.sheout.notifications;

import com.sheout.sharedkernel.Result;

import java.util.List;
import java.util.UUID;

/**
 * Notifications' first public interface. Until now this module exposed
 * nothing at all - triggering an alert and listing active ones were both
 * self-service over HTTP (see SosController), so no cross-module Java
 * caller existed. The admin module's alert dashboard is that caller, and
 * it must not reach into {@code notifications.internal.sos}.
 * <p>
 * Deliberately only the two operations an operator dashboard needs.
 * Triggering an alert stays internal: it is a customer action over HTTP
 * with no cross-module caller, and routing it through here would invite
 * another module to raise an alert on a customer's behalf.
 */
public interface SosApi {

    /** Newest first. Only ACTIVE alerts - a resolved alert leaves this list. */
    List<SosAlertSummary> findActiveAlerts();

    /**
     * ACTIVE to RESOLVED, recording which admin account closed it and when.
     * Idempotent in the sense that resolving an already-resolved alert is
     * reported as ALREADY_RESOLVED rather than silently overwriting the
     * original resolver - who first closed a safety alert is not something
     * a second call should be able to rewrite.
     */
    Result<SosAlertSummary, SosError> resolve(UUID alertId, UUID resolvedByAccountId);
}
