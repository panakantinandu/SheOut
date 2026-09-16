package com.sheout.users.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PanNumberTest {

    @Test
    void acceptsAWellFormedPanHoweverItIsTyped() {
        assertThat(PanNumber.isValidOrBlank("ABCDE1234F")).isTrue();
        assertThat(PanNumber.isValidOrBlank("abcde1234f")).isTrue();
        assertThat(PanNumber.isValidOrBlank(" abcde 1234 f ")).isTrue();
    }

    @Test
    void blankMeansNoPanRatherThanABadOne() {
        assertThat(PanNumber.isValidOrBlank(null)).isTrue();
        assertThat(PanNumber.isValidOrBlank("   ")).isTrue();
        assertThat(PanNumber.normalize("   ")).isNull();
        assertThat(PanNumber.normalize(null)).isNull();
    }

    @Test
    void rejectsAnythingThatIsNotThePanShape() {
        assertThat(PanNumber.isValidOrBlank("ABCD1234F")).isFalse();     // four letters
        assertThat(PanNumber.isValidOrBlank("ABCDE12345")).isFalse();    // ends in a digit
        assertThat(PanNumber.isValidOrBlank("ABCDE1234FG")).isFalse();   // too long
        assertThat(PanNumber.isValidOrBlank("ABCDE123AF")).isFalse();    // letter among the digits
    }

    @Test
    void oneNumberHasOneSpelling() {
        assertThat(PanNumber.normalize("abcde-1234-f")).isEqualTo("ABCDE1234F");
        assertThat(PanNumber.normalize("ABCDE1234F")).isEqualTo("ABCDE1234F");
    }
}
