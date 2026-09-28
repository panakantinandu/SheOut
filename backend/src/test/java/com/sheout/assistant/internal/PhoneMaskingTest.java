package com.sheout.assistant.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneMaskingTest {

    @Test
    void masksPhoneNumbersHoweverTheyAreTyped() {
        assertThat(PhoneMasking.mask("call me on +91 98765 43210 please")).isEqualTo("call me on +**********10 please");
        assertThat(PhoneMasking.mask("9876543210")).isEqualTo("********10");
        assertThat(PhoneMasking.mask("(040) 2345-6789")).isEqualTo("(*********89");
        assertThat(PhoneMasking.mask("ref 98765.43210 end")).isEqualTo("ref ********10 end");
    }

    @Test
    void leavesShortNumbersAndPlainTextAlone() {
        assertThat(PhoneMasking.mask("Error 400: max_tokens 300 exceeded")).isEqualTo("Error 400: max_tokens 300 exceeded");
        assertThat(PhoneMasking.mask("code 1234")).isEqualTo("code 1234");
        assertThat(PhoneMasking.mask(null)).isNull();
    }

    @Test
    void effortAndFallbacksAreOnlySentToModelsThatTakeThem() {
        assertThat(ClaudeHelpModel.supportsEffortAndFallbacks("claude-haiku-4-5-20251001")).isFalse();
        assertThat(ClaudeHelpModel.supportsEffortAndFallbacks("claude-opus-5")).isTrue();
        assertThat(ClaudeHelpModel.supportsEffortAndFallbacks("claude-sonnet-5")).isTrue();
    }
}
