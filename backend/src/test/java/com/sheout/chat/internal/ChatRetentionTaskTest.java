package com.sheout.chat.internal;

import com.sheout.support.SupportApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Old trip chat goes; recent chat, and chat on a trip with an open dispute,
 * stays. A dry run counts and deletes nothing.
 */
@SpringBootTest(properties = {
        "sheout.warm-up.enabled=false",
        "sheout.retention.cron=-",
        "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
@DirtiesContext
class ChatRetentionTaskTest {

    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");
    private static final Instant CUTOFF = NOW.minus(Duration.ofDays(90));

    @Autowired ChatRetentionTask task;
    @Autowired JdbcTemplate jdbc;
    @MockBean SupportApi supportApi;

    private final UUID oldTrip = UUID.randomUUID();
    private final UUID disputedTrip = UUID.randomUUID();
    private final UUID recentTrip = UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from chat_messages where booking_id in (?, ?, ?)", oldTrip, disputedTrip, recentTrip);
    }

    private void message(UUID booking, Instant at) {
        jdbc.update("insert into chat_messages (id, booking_id, sender_account_id, sender_role, body, created_at, updated_at) "
                        + "values (?, ?, ?, 'CUSTOMER', 'On my way', ?, ?)",
                UUID.randomUUID(), booking, UUID.randomUUID(), Timestamp.from(at), Timestamp.from(at));
    }

    private int count(UUID booking) {
        return jdbc.queryForObject("select count(*) from chat_messages where booking_id = ?", Integer.class, booking);
    }

    @Test
    void oldChatGoesButRecentAndDisputedChatStays() {
        message(oldTrip, CUTOFF.minus(Duration.ofDays(5)));
        message(oldTrip, CUTOFF.minus(Duration.ofDays(5)).plusSeconds(60));
        message(disputedTrip, CUTOFF.minus(Duration.ofDays(5)));
        message(recentTrip, NOW.minus(Duration.ofDays(2)));
        when(supportApi.hasUnresolvedTicketForBooking(any())).thenReturn(false);
        when(supportApi.hasUnresolvedTicketForBooking(disputedTrip)).thenReturn(true);

        assertThat(task.sweep(CUTOFF, true, 500)).isGreaterThanOrEqualTo(2);
        assertThat(count(oldTrip)).as("a dry run deletes nothing").isEqualTo(2);

        task.sweep(CUTOFF, false, 500);

        assertThat(count(oldTrip)).isZero();
        assertThat(count(disputedTrip)).as("an open dispute keeps the conversation").isEqualTo(1);
        assertThat(count(recentTrip)).isEqualTo(1);
    }
}
