package com.sheout.notifications.internal;

import com.sheout.auth.AccountRole;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The "we miss you" ladder: when a reminder is due, and when it is not. */
class ReengagementPlanTest {

    /** 11:00 on a weekday in India. */
    private static final Instant NOW = LocalDateTime.of(2026, 10, 6, 11, 0).atZone(ReengagementPlan.INDIA).toInstant();

    private static Instant daysAgo(double days) {
        return NOW.minus(Duration.ofMinutes((long) (days * 24 * 60)));
    }

    private static int due(double deviceDaysAgo, Double activeDaysAgo, ReengagementPlan.Previous previous) {
        return ReengagementPlan.stageDue(NOW, daysAgo(deviceDaysAgo),
                activeDaysAgo == null ? Optional.empty() : Optional.of(daysAgo(activeDaysAgo)), Optional.ofNullable(previous));
    }

    @Test
    void theLadderIsAWeekTwoWeeksAndAMonthThenSilence() {
        assertThat(due(3, 3.0, null)).as("three days away").isZero();
        assertThat(due(8, 8.0, null)).as("a week").isEqualTo(1);
        assertThat(due(15, 15.0, null)).as("two weeks").isEqualTo(2);
        assertThat(due(31, 31.0, null)).as("a month").isEqualTo(3);
        assertThat(due(50, 50.0, null)).as("gone for good: left alone").isZero();
    }

    @Test
    void activityOnAnotherDeviceCountsAsBeingHere() {
        // Her notifying phone has been quiet for 10 days, but she used SheOut yesterday elsewhere.
        assertThat(due(10, 1.0, null)).isZero();
    }

    @Test
    void signedOutEverywhereIsLeftAlone() {
        assertThat(due(10, null, null)).isZero();
    }

    @Test
    void eachStepIsSentOnceAndNeverTwoInSixDays() {
        // Step 1 sent at day 8; now day 12 - same stretch, too soon, and step 1 already sent.
        ReengagementPlan.Previous step1 = new ReengagementPlan.Previous(1, daysAgo(4), daysAgo(12));
        assertThat(due(12, 12.0, step1)).isZero();
        // Day 15: step 2 is due and it is a week since step 1.
        ReengagementPlan.Previous step1Earlier = new ReengagementPlan.Previous(1, daysAgo(7), daysAgo(15));
        assertThat(due(15, 15.0, step1Earlier)).isEqualTo(2);
        // Day 31 after step 3 already went at day 30: nothing more.
        ReengagementPlan.Previous step3 = new ReengagementPlan.Previous(3, daysAgo(7), daysAgo(38));
        assertThat(due(38, 38.0, step3)).isZero();
    }

    @Test
    void comingBackStartsTheLadderOver() {
        // She was sent step 2, then came back (seen after that), then went quiet again for 8 days.
        ReengagementPlan.Previous step2LongAgo = new ReengagementPlan.Previous(2, daysAgo(30), daysAgo(44));
        assertThat(due(8, 8.0, step2LongAgo)).isEqualTo(1);
    }

    @Test
    void firstNoticedLateStartsAtTheStepSheIsAt() {
        // Away 20 days when reminders began: step 2, not step 1 then step 2 a week later.
        assertThat(due(20, 20.0, null)).isEqualTo(2);
    }

    @Test
    void onlyInDaytimeHoursAndPartnersInTheMorning() {
        Instant ist = LocalDateTime.of(2026, 10, 6, 7, 30).atZone(ReengagementPlan.INDIA).toInstant();
        assertThat(ReengagementPlan.inSendingHours(AccountRole.CUSTOMER, ist)).as("rider 7:30").isFalse();
        assertThat(ReengagementPlan.inSendingHours(AccountRole.DRIVER, ist)).as("partner 7:30").isFalse();
        Instant nine = LocalDateTime.of(2026, 10, 6, 9, 0).atZone(ReengagementPlan.INDIA).toInstant();
        assertThat(ReengagementPlan.inSendingHours(AccountRole.CUSTOMER, nine)).isTrue();
        assertThat(ReengagementPlan.inSendingHours(AccountRole.DRIVER, nine)).isTrue();
        Instant afternoon = LocalDateTime.of(2026, 10, 6, 15, 0).atZone(ReengagementPlan.INDIA).toInstant();
        assertThat(ReengagementPlan.inSendingHours(AccountRole.CUSTOMER, afternoon)).isTrue();
        assertThat(ReengagementPlan.inSendingHours(AccountRole.DRIVER, afternoon)).as("partner after noon").isFalse();
        Instant night = LocalDateTime.of(2026, 10, 6, 21, 0).atZone(ReengagementPlan.INDIA).toInstant();
        assertThat(ReengagementPlan.inSendingHours(AccountRole.CUSTOMER, night)).as("rider 9 pm").isFalse();
        assertThat(ReengagementPlan.inSendingHours(AccountRole.ADMIN, afternoon)).isFalse();
    }

    @Test
    void anUnverifiedPartnerIsAskedToFinishNotToGoOnline() {
        assertThat(ReengagementPlan.copyKey(AccountRole.DRIVER, 2, false)).isEqualTo("reengage.partnerVerify");
        assertThat(ReengagementPlan.copyKey(AccountRole.DRIVER, 2, true)).isEqualTo("reengage.partner2");
        assertThat(ReengagementPlan.copyKey(AccountRole.CUSTOMER, 3, false)).isEqualTo("reengage.rider3");
    }
}
