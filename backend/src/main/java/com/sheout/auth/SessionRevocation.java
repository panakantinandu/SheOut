package com.sheout.auth;

/**
 * Why a session ended - the one thing the device that lost it is told.
 * <p>
 * Public because it is what the apps show the person holding that device: a
 * partner whose phone was signed out by her own second sign-in is told
 * exactly that, rather than being dropped at the sign-in screen with no
 * explanation. It never carries who did it or why an account was blocked.
 */
public enum SessionRevocation {

    /** A DRIVER account signed in somewhere else. Partners get one device at a time. */
    SIGNED_IN_ELSEWHERE,

    /** An admin blocked the account, which ends every session it has. */
    ACCOUNT_BLOCKED,

    /** She signed out herself, here or from her list of devices. */
    SIGNED_OUT,

    /** The account was deleted. */
    ACCOUNT_DELETED
}
