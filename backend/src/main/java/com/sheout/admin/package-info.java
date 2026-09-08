/**
 * Admin module - internal operator tooling: reviewing driver verification
 * queues, viewing/handling flagged bookings, support actions. Composes
 * other modules' public interfaces; owns no domain data of its own beyond
 * admin-specific concerns (e.g. audit log of admin actions).
 * <p>
 * Only classes declared directly in this package are this module's public
 * API. Everything under {@code com.sheout.admin.internal} is private to
 * this module - other modules must not import from it.
 */
package com.sheout.admin;
