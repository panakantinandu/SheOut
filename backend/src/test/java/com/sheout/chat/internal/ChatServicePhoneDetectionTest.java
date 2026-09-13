package com.sheout.chat.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the rule that stops chat becoming the phone-number exchange it
 * replaced.
 * <p>
 * Worth a test where most of this codebase has none, because this is the
 * one piece of the trust-and-safety work that a small, innocent-looking
 * regex change could silently undo. A rule that quietly stops matching
 * leaves every screen looking exactly the same and the protection gone.
 */
class ChatServicePhoneDetectionTest {

    @Test
    @DisplayName("refuses a number however it is written")
    void refusesNumbersInEveryShape() {
        assertTrue(ChatService.looksLikeAPhoneNumber("9876543210"));
        assertTrue(ChatService.looksLikeAPhoneNumber("call me on 9876543210"));
        assertTrue(ChatService.looksLikeAPhoneNumber("+91 98765 43210"));
        assertTrue(ChatService.looksLikeAPhoneNumber("9876-543-210"));
        assertTrue(ChatService.looksLikeAPhoneNumber("(987) 654 3210"));
        // The one that a naive check misses, and the reason separators are
        // stripped before matching rather than matched around.
        assertTrue(ChatService.looksLikeAPhoneNumber("9 8 7 6 5 4 3 2 1 0"));
        assertTrue(ChatService.looksLikeAPhoneNumber("nine is 98.76.543.210 ok"));
    }

    @Test
    @DisplayName("lets ordinary messages through")
    void allowsRealMessages() {
        assertFalse(ChatService.looksLikeAPhoneNumber("I am at the blue gate"));
        assertFalse(ChatService.looksLikeAPhoneNumber("Flat 402, block 7"));
        assertFalse(ChatService.looksLikeAPhoneNumber("Give me 5 minutes"));
        assertFalse(ChatService.looksLikeAPhoneNumber("Meet at 10:30 near gate 2"));
        // Six digits is the widest thing a normal message plausibly carries
        // (a PIN code, a flat number); seven is where a number starts.
        assertFalse(ChatService.looksLikeAPhoneNumber("pin code 500081"));
    }
}
