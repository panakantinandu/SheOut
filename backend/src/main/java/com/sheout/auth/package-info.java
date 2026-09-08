/**
 * Auth module - authentication and session/token issuance for customers,
 * drivers, and admins (login, OTP verification, token refresh).
 * <p>
 * Only classes declared directly in this package are this module's public
 * API. Everything under {@code com.sheout.auth.internal} is private to this
 * module - other modules must not import from it.
 */
package com.sheout.auth;
