package com.sheout.notifications.internal.sos;

/**
 * RESOLVED exists for a future admin module to set (see SosService's
 * Javadoc on GET /notifications/sos/active) - nothing in this pass ever
 * transitions an alert to it, so today every alert ever created is, and
 * stays, ACTIVE. Flagged rather than silently built as if a resolve path
 * already existed.
 */
public enum SosStatus {
    ACTIVE,
    RESOLVED
}
