package com.sheout.notifications;

/**
 * How an SOS reached SheOut. Stored by name.
 * <ul>
 *   <li>DATA - over her data connection while she was raising it.</li>
 *   <li>DELAYED_QUEUE - she had no signal; the app kept it and sent it when a
 *       connection came back, with the time she actually raised it.</li>
 * </ul>
 * The SMS fallback is recorded beside this, not as a channel of its own:
 * that text goes from her phone straight to her contacts, and SheOut only
 * ever hears about it through one of these two.
 */
public enum SosDeliveryChannel {
    DATA,
    DELAYED_QUEUE
}
