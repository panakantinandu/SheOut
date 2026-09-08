/**
 * Notifications module - push/SMS/email fan-out triggered by domain events
 * from other modules (e.g. booking confirmed, driver arriving). Talks to
 * Firebase Cloud Messaging.
 * <p>
 * Only classes declared directly in this package are this module's public
 * API. Everything under {@code com.sheout.notifications.internal} is
 * private to this module - other modules must not import from it.
 */
package com.sheout.notifications;
