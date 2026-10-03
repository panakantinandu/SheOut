package com.sheout.staff;

import java.util.UUID;

/**
 * Four-eyes: the actions one person must not be able to do alone.
 * <p>
 * One member of staff asks ({@link #submit}); a different one, holding the
 * approving permission, approves on the Approvals page; only then does the
 * module that owns the action do it, through its {@link ApprovalExecutor}.
 * <ul>
 *   <li>Nobody approves her own request.</li>
 *   <li>The one exception: while SheOut has a single active owner, an owner
 *       may approve her own - otherwise a one-owner company could never pay a
 *       partner. It is marked as such, recorded, alerted, and the console
 *       keeps saying "add a second owner".</li>
 *   <li>A request not decided in 72 hours expires.</li>
 * </ul>
 * Every request, decision and outcome is in the audit log, and a request is
 * itself an alert to every owner. With beforeJson, an approved configuration
 * change is that configuration's history.
 */
public interface Approvals {

    enum Kind {
        /** A payout marked paid: finance prepares it, a manager or owner approves. */
        PAYOUT_MARK_PAID,
        /** A refund above the issuer's own limit: an owner approves. */
        REFUND,
        /** Inviting, promoting or demoting a manager or owner. */
        STAFF_PRIVILEGED,
        /** Resetting someone's password and authenticator. */
        SECOND_FACTOR_RESET,
        /** Creating, changing, switching on or off an insurance policy. */
        INSURANCE_POLICY,
        /** A bulk export: approved once, downloaded once, by whoever asked. */
        EXPORT
    }

    /**
     * @param summary            one plain sentence: what happens if approved
     * @param payload            JSON the executor reads; never a secret
     * @param beforeJson         for a configuration change, what it was
     * @param approverPermission what the approver must hold
     */
    record Request(Kind kind, String summary, String payload, String beforeJson, Permission approverPermission,
                   String targetType, String targetId, String reason) {
    }

    /** What an endpoint answers (202) instead of doing the thing. */
    record Submitted(UUID approvalId, Kind kind, String status, String summary, boolean approvalRequired) {
    }

    /** Asks, as the signed-in member of staff. */
    Submitted submit(Request request);

    /** Whether something of this kind about this record is already waiting - to refuse a duplicate. */
    boolean pending(Kind kind, String targetId);

    /**
     * Payloads are written with a plain mapper, never the application's: that
     * one masks personal data during console requests, and a payload masked
     * on the way in would be carried out masked.
     */
    com.fasterxml.jackson.databind.ObjectMapper PAYLOAD_MAPPER = com.fasterxml.jackson.databind.json.JsonMapper.builder()
            .addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    static String payload(Object value) {
        try {
            return PAYLOAD_MAPPER.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    static <T> T read(String payload, Class<T> type) {
        try {
            return PAYLOAD_MAPPER.readValue(payload, type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Unreadable approval payload", e);
        }
    }
}
