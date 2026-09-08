package com.sheout.auth.internal;

/**
 * Expected failure outcomes of the OTP request/verify flow, returned via
 * {@link com.sheout.sharedkernel.Result} rather than thrown - these are
 * normal business outcomes a client is expected to handle, not exceptional
 * conditions.
 */
public enum AuthError {

    /** OTP delivery failed (provider error) - see {@link OtpSender}. */
    OTP_DELIVERY_FAILED,

    /** No OTP was requested for this phone number, or it already expired. */
    OTP_NOT_FOUND_OR_EXPIRED,

    /** Code did not match the one on file for this phone number. */
    OTP_CODE_MISMATCH,

    /** Account already exists for this phone number under a different role. */
    ROLE_MISMATCH
}
