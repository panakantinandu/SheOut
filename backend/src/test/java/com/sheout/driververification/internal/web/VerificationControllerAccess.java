package com.sheout.driververification.internal.web;

import com.sheout.driververification.internal.VerificationError;

/** Lets tests outside this package read what an error says to the person who gets it. */
public final class VerificationControllerAccess {

    private VerificationControllerAccess() {
    }

    public static String message(VerificationError error) {
        return VerificationController.toApiException(error).getMessage();
    }
}
