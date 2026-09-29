package com.sheout.notifications.internal;

import com.sheout.auth.AccountRole;
import com.sheout.auth.AccountSummary;
import com.sheout.auth.AuthApi;
import com.sheout.users.DriverProfileApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

/**
 * "We miss you" reminders against the real database: the lapsed-accounts
 * query, the reminder record, and the inbox entry. Accounts and sign-in
 * activity are stood in for through the auth and partner APIs.
 * <p>
 * Like the other flow tests, this needs the local Postgres and Redis.
 */
@SpringBootTest(properties = {
        "REDIS_PORT=6380", "sheout.warm-up.enabled=false",
        // The sweep is driven by hand here, never by the clock.
        "sheout.notifications.reengagement.cron=-",
        "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
@DirtiesContext
class ReengagementFlowTest {

    /** 10:00 in India: rider and partner hours both open. */
    private static final Instant MORNING = LocalDateTime.of(2026, 10, 6, 10, 0).atZone(ReengagementPlan.INDIA).toInstant();

    @Autowired ReengagementNudger nudger;
    @Autowired PushDeviceRepository devices;
    @Autowired JdbcTemplate jdbc;
    @SpyBean AuthApi authApi;
    @SpyBean DriverProfileApi drivers;

    private final List<UUID> accounts = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID account : accounts) {
            jdbc.update("delete from notification_deliveries where notification_id in (select id from notification_log where recipient_account_id = ?)", account);
            jdbc.update("delete from notification_log where recipient_account_id = ?", account);
            jdbc.update("delete from reengagement_nudges where account_id = ?", account);
            jdbc.update("delete from push_devices where account_id = ?", account);
        }
    }

    private UUID person(AccountRole role, Duration away, boolean verifiedPartner) {
        UUID id = UUID.randomUUID();
        accounts.add(id);
        Instant seen = MORNING.minus(away);
        devices.save(new PushDeviceEntity(id, role, "test-token-" + id, "test", seen));
        doReturn(Optional.of(new AccountSummary(id, "+9190000" + id.toString().substring(0, 5), null, role, seen.minus(Duration.ofDays(90)), false)))
                .when(authApi).findAccount(id);
        doReturn(Optional.of(seen)).when(authApi).lastActiveAt(id);
        if (role == AccountRole.DRIVER) {
            doReturn(verifiedPartner).when(drivers).isCurrentlyVerified(id);
        }
        return id;
    }

    private List<String> titles(UUID account) {
        return jdbc.queryForList("select title from notification_log where recipient_account_id = ? and type = 'REENGAGEMENT' order by created_at",
                String.class, account);
    }

    private Integer stage(UUID account) {
        return jdbc.query("select stage from reengagement_nudges where account_id = ?", rs -> rs.next() ? rs.getInt(1) : null, account);
    }

    @Test
    void aWeekAwayGetsOneReminderAndNoMoreThatWeek() {
        UUID rider = person(AccountRole.CUSTOMER, Duration.ofDays(8), false);
        UUID recent = person(AccountRole.CUSTOMER, Duration.ofDays(2), false);
        UUID longGone = person(AccountRole.CUSTOMER, Duration.ofDays(60), false);

        nudger.run(MORNING);
        assertThat(titles(rider)).containsExactly("We miss you on SheOut");
        assertThat(stage(rider)).isEqualTo(1);
        assertThat(titles(recent)).as("opened the app two days ago").isEmpty();
        assertThat(titles(longGone)).as("away two months: left alone").isEmpty();

        // Half an hour later, and the next day: nothing more for her.
        nudger.run(MORNING.plus(Duration.ofMinutes(30)));
        nudger.run(MORNING.plus(Duration.ofDays(1)));
        assertThat(titles(rider)).hasSize(1);

        // A week on, still away: the two-week step.
        nudger.run(MORNING.plus(Duration.ofDays(7)));
        assertThat(titles(rider)).containsExactly("We miss you on SheOut", "Your safe ride is a tap away");
        assertThat(stage(rider)).isEqualTo(2);
    }

    @Test
    void partnersHearAboutGoingOnlineOrFinishingVerification() {
        UUID verified = person(AccountRole.DRIVER, Duration.ofDays(9), true);
        UUID pending = person(AccountRole.DRIVER, Duration.ofDays(9), false);

        nudger.run(MORNING);
        assertThat(titles(verified)).containsExactly("Ready to earn today?");
        assertThat(titles(pending)).containsExactly("Finish your verification");
    }

    @Test
    void nothingAtNightAndNothingToBlockedAccounts() {
        UUID rider = person(AccountRole.CUSTOMER, Duration.ofDays(8), false);
        UUID blocked = person(AccountRole.CUSTOMER, Duration.ofDays(8), false);
        doReturn(Optional.of(new AccountSummary(blocked, "+919000011111", null, AccountRole.CUSTOMER, MORNING.minus(Duration.ofDays(90)), true)))
                .when(authApi).findAccount(blocked);

        Instant night = LocalDateTime.of(2026, 10, 6, 22, 0).atZone(ReengagementPlan.INDIA).toInstant();
        nudger.run(night);
        assertThat(titles(rider)).as("10 pm").isEmpty();

        nudger.run(MORNING);
        assertThat(titles(rider)).hasSize(1);
        assertThat(titles(blocked)).isEmpty();
    }
}
