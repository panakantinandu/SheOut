package com.sheout.notifications;

/**
 * What raised an SOS. Stored by name.
 * <ul>
 *   <li>BUTTON - she pressed SOS.</li>
 *   <li>SHAKE - the discreet shake pattern, with the app open.</li>
 *   <li>BACK_TAP - the discreet double knock on the back of the phone, with the app open.</li>
 *   <li>SHORTCUT - the phone's own gesture (iOS Back Tap, Android Quick Tap)
 *       opening SheOut's SOS link; works with the app closed.</li>
 * </ul>
 */
public enum SosTriggerSource {
    BUTTON,
    SHAKE,
    BACK_TAP,
    SHORTCUT
}
