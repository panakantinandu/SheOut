package com.sheout.sharedkernel.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Pins log masking. A regression here fails silently - nothing breaks, a
 * phone number just starts appearing in the logs again - so it is worth a
 * test even though the code is small.
 */
class RedactTest {

    @Test
    @DisplayName("a phone number keeps its country code and last two digits only")
    void masksPhone() {
        assertEquals("+91********10", Redact.phone("+919876543210"));
        assertEquals("null", Redact.phone(null));
        assertEquals("****", Redact.phone("1234"));
    }

    @Test
    @DisplayName("numbers inside provider error text are masked, the rest of the text is kept")
    void masksNumbersInText() {
        String twilio = "HTTP 400 (Twilio error 21211) The 'To' number +919876543210 is not a valid phone number.";
        String masked = Redact.phoneNumbersIn(twilio);
        assertFalse(masked.contains("9876543210"), masked);
        assertEquals("HTTP 400 (Twilio error 21211) The 'To' number +91********10 is not a valid phone number.", masked);
    }

    @Test
    @DisplayName("short numbers like status and error codes are left alone")
    void leavesShortNumbers() {
        assertEquals("HTTP 401 (Twilio error 20003)", Redact.phoneNumbersIn("HTTP 401 (Twilio error 20003)"));
    }
}
