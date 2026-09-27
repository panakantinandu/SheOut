package com.sheout.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The warm-up runs on every production start, so it must leave nothing
 * behind that anybody could see: no rows, no money spent from a campaign, no
 * entry in dispatch's Redis (a made-up partner on riders' maps, or an offer
 * on a real partner's phone), and no event for another module to act on.
 * <p>
 * Like SheOutApplicationTests, this needs the local Postgres and Redis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"REDIS_PORT=6380", "sheout.warm-up.enabled=false"})
@ActiveProfiles("local")
@RecordApplicationEvents
class WarmUpLeavesNoTraceTest {

    @Autowired
    WarmUpRunner runner;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    ApplicationEvents events;
    @LocalServerPort
    int port;

    @Test
    void aFullWarmUpChangesNothingAnybodyCanSee() {
        Map<String, Object> before = snapshot();
        Set<String> keysBefore = redis.keys("dispatch:*");
        events.clear();

        runner.warmUp(port);

        assertThat(snapshot()).isEqualTo(before);
        // New keys, not a count: keys from earlier runs expire while this runs.
        assertThat(redis.keys("dispatch:*")).as("dispatch keys created by the warm-up").isSubsetOf(keysBefore);
        assertThat(events.stream().filter(e -> e.getClass().getName().startsWith("com.sheout.")))
                .as("SheOut events published during the warm-up")
                .isEmpty();
    }

    private Map<String, Object> snapshot() {
        Map<String, Object> s = new LinkedHashMap<>();
        for (String table : new String[] {"bookings", "payments", "promotion_redemptions", "promotion_grants",
                "incentive_awards", "wallet_entries", "rider_wallet_entries", "accounts", "account_sessions", "notification_log", "notification_deliveries"}) {
            s.put(table, count(table));
        }
        s.put("promotion spend", jdbc.queryForObject("select coalesce(sum(spent), 0) from promotions", Object.class));
        s.put("incentive spend", jdbc.queryForObject("select coalesce(sum(spent), 0) from driver_incentives", Object.class));
        s.put("partners in the geo index", redis.opsForZSet().zCard("dispatch:driver-geo"));

        return s;
    }

    private Object count(String table) {
        Boolean exists = jdbc.queryForObject("select to_regclass(?) is not null", Boolean.class, table);
        return Boolean.TRUE.equals(exists) ? jdbc.queryForObject("select count(*) from " + table, Long.class) : "absent";
    }
}
