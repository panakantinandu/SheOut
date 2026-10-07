package com.sheout.driververification.internal;

import com.sheout.driververification.internal.NameMatcher.Match;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NameMatcherTest {

    @Test
    void theSameNameWrittenTheSameWayIsTheSame() {
        assertThat(NameMatcher.compare("Lakshmi Devi", "  LAKSHMI   devi ")).isEqualTo(Match.SAME);
    }

    @Test
    void transliterationsOfOneNameAreVariants() {
        assertThat(NameMatcher.compare("Laxmi", "Lakshmi")).isEqualTo(Match.VARIANT);
        assertThat(NameMatcher.compare("Swathi", "Swati")).isEqualTo(Match.VARIANT);
        assertThat(NameMatcher.compare("Seetha", "Sita")).isEqualTo(Match.VARIANT);
        assertThat(NameMatcher.compare("Anitha Reddy", "Anita Reddi")).isEqualTo(Match.VARIANT);
        assertThat(NameMatcher.compare("Priyanka", "Priyankaa")).isEqualTo(Match.VARIANT);
        assertThat(NameMatcher.compare("Lalitha", "Lalita")).isEqualTo(Match.VARIANT);
        assertThat(NameMatcher.compare("Bhavani", "Bavani")).isEqualTo(Match.VARIANT);
    }

    @Test
    void initialsReversedOrderAndAMissingSurnameAreVariants() {
        assertThat(NameMatcher.compare("K. Lakshmi", "Kondapalli Lakshmi")).isEqualTo(Match.VARIANT);
        assertThat(NameMatcher.compare("Reddy Anitha", "Anitha Reddy")).isEqualTo(Match.VARIANT);
        assertThat(NameMatcher.compare("Priya", "Priya Sharma")).isEqualTo(Match.VARIANT);
        assertThat(NameMatcher.compare("Smt. Swathi", "Swati")).isEqualTo(Match.VARIANT);
    }

    @Test
    void differentPeopleAreDifferent() {
        assertThat(NameMatcher.compare("Priya Sharma", "Anjali Verma")).isEqualTo(Match.DIFFERENT);
        assertThat(NameMatcher.compare("Divya", "Kavya")).isEqualTo(Match.DIFFERENT);
        assertThat(NameMatcher.compare("Ramesh Kumar", "Lakshmi Devi")).isEqualTo(Match.DIFFERENT);
        assertThat(NameMatcher.compare("K. L.", "Kavitha Lakshmi")).as("initials alone say nothing").isEqualTo(Match.DIFFERENT);
        assertThat(NameMatcher.compare("", "Lakshmi")).isEqualTo(Match.DIFFERENT);
        assertThat(NameMatcher.compare("Lakshmi", null)).isEqualTo(Match.DIFFERENT);
    }
}
