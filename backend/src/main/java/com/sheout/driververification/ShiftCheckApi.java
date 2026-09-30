package com.sheout.driververification;

import java.util.UUID;

/**
 * The start-of-shift safety check, read by other modules.
 * <p>
 * Uber calls theirs a Real-Time ID Check and Rapido asks captains for a
 * selfie before a shift; the reason is the same. Her ID and selfie were
 * checked by a person once, at sign-up. That proves who owns the account,
 * not who is holding the phone today - and on a women-only service the
 * failure that matters most is an account being handed to somebody else.
 * <p>
 * What the check is and is not: a live selfie with a random prompt, compared
 * ON HER PHONE with the selfie she was verified with, and kept here so an
 * operator can look at any of them later. A modified app could lie about the
 * comparison; it cannot fake the photo it has to send, and the rider still
 * sees her face at the kerb. It is a strong deterrent, not proof.
 */
public interface ShiftCheckApi {

    ShiftCheckState stateFor(UUID accountId);
}
