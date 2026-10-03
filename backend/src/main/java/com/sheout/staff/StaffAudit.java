package com.sheout.staff;

/**
 * The staff audit log, for other modules: who on the staff did what, to which
 * record, and why.
 * <p>
 * Who, when, from where (address, browser, session, request id) are filled
 * in here from the request; the caller says only what happened. Called
 * outside a console request (a scheduled job), the actor is the system.
 * <p>
 * Most entries need no call at all: every console change is recorded by the
 * staff module itself, and so is every read marked {@link AuditedRead}.
 * Call this for what a URL cannot say - the reason typed for a reveal, the
 * before and after of a change.
 * <p>
 * Never put a secret in reason or detail: no password, code, token, full
 * document or full Aadhaar number.
 */
public interface StaffAudit {

    void record(Entry entry);

    enum Result { OK, DENIED, FAILED }

    /**
     * @param action     what happened, e.g. "pii.phone.reveal"
     * @param permission the permission it used, if any
     * @param targetType what kind of record, e.g. "ACCOUNT", "BOOKING"
     * @param targetId   which one
     * @param reason     the reason she typed, if one was asked for
     * @param detail     before/after as JSON for a change; never a secret
     */
    record Entry(String action, Permission permission, Result result, String targetType, String targetId,
                 String reason, String detail) {

        public static Entry ok(String action, Permission permission, String targetType, String targetId) {
            return new Entry(action, permission, Result.OK, targetType, targetId, null, null);
        }

        public Entry withReason(String why) {
            return new Entry(action, permission, result, targetType, targetId, why, detail);
        }

        public Entry withDetail(String json) {
            return new Entry(action, permission, result, targetType, targetId, reason, json);
        }
    }
}
