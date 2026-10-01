package com.sheout.booking.internal;

import com.sheout.booking.ServiceHoursApi.ClosedReason;
import com.sheout.booking.ServiceHoursApi.ServiceHoursMode;
import com.sheout.booking.ServiceHoursApi.ServiceStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceWindowTest {

    private static final LocalTime SIX_AM = LocalTime.of(6, 0);
    private static final LocalTime TEN_PM = LocalTime.of(22, 0);

    /** A wall-clock time in Hyderabad, as an instant. */
    private static Instant ist(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ServiceWindow.INDIA).toInstant();
    }

    private static ServiceStatus scheduled(Instant now, LocalTime opens, LocalTime closes) {
        return ServiceWindow.statusAt(now, ServiceHoursMode.SCHEDULED, opens, closes, false, null, null);
    }

    @Test
    void alwaysOpenIsOpenAtThreeInTheMorning() {
        ServiceStatus s = ServiceWindow.statusAt(ist("2026-10-01T03:00"), ServiceHoursMode.ALWAYS_OPEN,
                SIX_AM, TEN_PM, false, null, null);
        assertThat(s.open()).isTrue();
        assertThat(s.closesAt()).isNull();
    }

    @Test
    void insideTheWindowIsOpenAndSaysWhenItCloses() {
        ServiceStatus s = scheduled(ist("2026-10-01T21:30"), SIX_AM, TEN_PM);
        assertThat(s.open()).isTrue();
        assertThat(s.closesAt()).isEqualTo(ist("2026-10-01T22:00"));
    }

    @Test
    void closingTimeItselfIsClosedAndReopensNextMorning() {
        ServiceStatus s = scheduled(ist("2026-10-01T22:00"), SIX_AM, TEN_PM);
        assertThat(s.open()).isFalse();
        assertThat(s.closedReason()).isEqualTo(ClosedReason.OUTSIDE_HOURS);
        assertThat(s.reopensAt()).isEqualTo(ist("2026-10-02T06:00"));
    }

    @Test
    void earlyMorningReopensTheSameDay() {
        ServiceStatus s = scheduled(ist("2026-10-02T04:15"), SIX_AM, TEN_PM);
        assertThat(s.open()).isFalse();
        assertThat(s.reopensAt()).isEqualTo(ist("2026-10-02T06:00"));
    }

    /** Render's clock is UTC: 22:00 in Hyderabad is 16:30 UTC, and that is when it must close. */
    @Test
    void theWindowIsIndiaTimeNotTheServersZone() {
        assertThat(scheduled(Instant.parse("2026-10-01T16:29:00Z"), SIX_AM, TEN_PM).open()).isTrue();
        assertThat(scheduled(Instant.parse("2026-10-01T16:30:00Z"), SIX_AM, TEN_PM).open()).isFalse();
    }

    @Test
    void aWindowCrossingMidnightIsOpenEitherSideOfIt() {
        LocalTime opens = LocalTime.of(5, 0);
        LocalTime closes = LocalTime.of(1, 0);
        assertThat(scheduled(ist("2026-10-01T23:30"), opens, closes).open()).isTrue();
        assertThat(scheduled(ist("2026-10-02T00:30"), opens, closes).open()).isTrue();
        ServiceStatus night = scheduled(ist("2026-10-02T02:00"), opens, closes);
        assertThat(night.open()).isFalse();
        assertThat(night.reopensAt()).isEqualTo(ist("2026-10-02T05:00"));
        assertThat(scheduled(ist("2026-10-01T23:30"), opens, closes).closesAt()).isEqualTo(ist("2026-10-02T01:00"));
    }

    /** Equal ends would mean closed all day; it is treated as no window rather than shutting SheOut for good. */
    @Test
    void equalEndsNeverCloseTheService() {
        assertThat(scheduled(ist("2026-10-01T03:00"), SIX_AM, SIX_AM).open()).isTrue();
    }

    @Test
    void aPauseClosesEvenInsideTheWindowAndCarriesItsReason() {
        ServiceStatus s = ServiceWindow.statusAt(ist("2026-10-01T12:00"), ServiceHoursMode.SCHEDULED,
                SIX_AM, TEN_PM, true, "Heavy rain", ist("2026-10-01T14:00"));
        assertThat(s.open()).isFalse();
        assertThat(s.closedReason()).isEqualTo(ClosedReason.PAUSED);
        assertThat(s.pauseReason()).isEqualTo("Heavy rain");
        assertThat(s.reopensAt()).isEqualTo(ist("2026-10-01T14:00"));
    }

    /** Paused until 11 PM under a 10 PM close: bookings come back in the morning, not at 11. */
    @Test
    void aPauseEndingAfterClosingReopensWithTheSchedule() {
        ServiceStatus s = ServiceWindow.statusAt(ist("2026-10-01T21:00"), ServiceHoursMode.SCHEDULED,
                SIX_AM, TEN_PM, true, "Unrest", ist("2026-10-01T23:00"));
        assertThat(s.reopensAt()).isEqualTo(ist("2026-10-02T06:00"));
    }

    @Test
    void aPauseWithNoEndHasNoReopeningTime() {
        ServiceStatus s = ServiceWindow.statusAt(ist("2026-10-01T12:00"), ServiceHoursMode.ALWAYS_OPEN,
                SIX_AM, TEN_PM, true, "Incident", null);
        assertThat(s.open()).isFalse();
        assertThat(s.reopensAt()).isNull();
    }
}
