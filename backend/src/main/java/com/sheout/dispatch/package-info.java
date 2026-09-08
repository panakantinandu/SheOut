/**
 * Dispatch module - matches a confirmed booking to an available nearby
 * driver, handles driver accept/reject/timeout, and driver location
 * tracking during an active trip.
 * <p>
 * Only classes declared directly in this package are this module's public
 * API. Everything under {@code com.sheout.dispatch.internal} is private to
 * this module - other modules must not import from it.
 */
package com.sheout.dispatch;
