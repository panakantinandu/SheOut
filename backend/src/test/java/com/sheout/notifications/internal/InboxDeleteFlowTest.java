package com.sheout.notifications.internal;

import com.sheout.notifications.internal.channel.OutboundMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deleting from her inbox, against the real database: gone from her list and
 * her unread count, still on record, and only ever her own.
 */
@SpringBootTest(properties = {"sheout.warm-up.enabled=false", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
@DirtiesContext
class InboxDeleteFlowTest {

    @Autowired NotificationLogService log;
    @Autowired NotificationInboxService inbox;
    @Autowired JdbcTemplate jdbc;

    private final UUID her = UUID.randomUUID();
    private final UUID someoneElse = UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from notification_log where recipient_account_id in (?, ?)", her, someoneElse);
    }

    private UUID notify(UUID to, String title) {
        return log.recordNotification(to, NotificationType.BOOKING_COMPLETED,
                new OutboundMessage(title, "body", "/tracking/x", null, OutboundMessage.Urgency.NORMAL, "SheOut: " + title));
    }

    private int listed(UUID account) {
        return inbox.page(account, PageRequest.of(0, 50)).getContent().size();
    }

    @Test
    void aDeletedNotificationLeavesHerInboxAndCountButStaysOnRecord() {
        UUID first = notify(her, "Trip completed");
        notify(her, "Another trip");
        UUID theirs = notify(someoneElse, "Not hers");
        assertThat(listed(her)).isEqualTo(2);
        assertThat(inbox.unreadCount(her)).isEqualTo(2);

        assertThat(inbox.delete(her, theirs)).as("somebody else's is not found").isFalse();
        assertThat(listed(someoneElse)).isEqualTo(1);

        assertThat(inbox.delete(her, first)).isTrue();
        assertThat(inbox.delete(her, first)).as("already deleted").isFalse();
        assertThat(listed(her)).isEqualTo(1);
        assertThat(inbox.unreadCount(her)).as("a deleted one is never unread").isEqualTo(1);
        assertThat(inbox.markRead(her, first)).as("cannot be opened once deleted").isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from notification_log where id = ? and deleted_at is not null",
                Integer.class, first)).as("kept as the record of what was sent").isEqualTo(1);

        assertThat(inbox.deleteAll(her)).isEqualTo(1);
        assertThat(listed(her)).isZero();
        assertThat(inbox.unreadCount(her)).isZero();
        assertThat(listed(someoneElse)).as("clearing hers leaves everyone else's").isEqualTo(1);
    }
}
