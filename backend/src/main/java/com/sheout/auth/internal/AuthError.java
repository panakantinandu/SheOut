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

    /**
     * An admin has blocked this account - see AccountEntity. Checked only
     * after the OTP is verified, so the endpoint cannot be used to discover
     * whether an arbitrary number is blocked.
     */
    ACCOUNT_BLOCKED,

    /**
     * Signup tried to create a NEW account with role=ADMIN. Refused at
     * account creation rather than on any request carrying role=ADMIN,
     * because an existing ADMIN still has to be able to sign in - see
     * AuthService.verifyOtp. ADMIN is granted only by
     * AuthApi.grantAdminRole, driven by admin's deploy-time bootstrap.
     */
    ADMIN_SELF_SIGNUP_FORBIDDEN,

    /**
     * A Google sign-in's email matches an account that also has a phone
     * number on file - deliberately refused rather than silently signing
     * into it, since that would combine two different auth methods into
     * one account without the account owner's knowledge. See
     * AuthService.verifyGoogleSignIn's Javadoc: no current flow can
     * actually produce this today (phone signup never collects an email),
     * this exists so it's handled correctly the moment one does.
     */
    EMAIL_LINKED_TO_PHONE_ACCOUNT,
    /**
     * Google sign-in for any app but the rider app. Partners and operators
     * sign in with a phone and a code only: dispatch, SOS and the operations
     * team all depend on every one of them having a verified number, which a
     * Google account does not give. The partner app never offered Google,
     * but the server accepted whatever role a request named, so a direct call
     * could have created a partner account with no number at all.
     */
    GOOGLE_NOT_FOR_ROLE,
    /**
     * Adding a number to an account that already has one. This step exists
     * to give a Google account its first number, not to change a number.
     */
    PHONE_ALREADY_SET,
    /**
     * The number, verified by its code, already belongs to another rider
     * account - most likely her own, from signing up with the number before.
     * Said only after the code proves the number is hers, so it cannot be
     * used to learn whether a number is registered.
     */
    PHONE_ALREADY_REGISTERED
}
