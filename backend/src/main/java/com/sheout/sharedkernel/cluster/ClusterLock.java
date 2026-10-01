package com.sheout.sharedkernel.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * "Only one server runs this right now" for the scheduled jobs.
 * <p>
 * SheOut runs on one server today, so this changes nothing yet. It is here
 * so that adding a second server is a settings change rather than a bug
 * hunt: without it every timed job - the search sweeper, the shift-check
 * expiry, the nudges, the trip watch - would run once per server, sending
 * each reminder and each operator alert twice.
 * <p>
 * A Redis key with an expiry, set only if absent, and released only by the
 * holder that set it. The expiry bounds how long a server that dies mid-run
 * can hold the job. If Redis cannot be reached the run is skipped, not run
 * unguarded: every job here runs again within a minute or two.
 */
@Component
public class ClusterLock {

    private static final Logger log = LoggerFactory.getLogger(ClusterLock.class);
    private static final String PREFIX = "lock:job:";
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;

    public ClusterLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Runs the job if no other server is running it; returns whether it ran. */
    public boolean runExclusively(String job, Duration maxRunTime, Runnable work) {
        String key = PREFIX + job;
        String token = UUID.randomUUID().toString();
        Boolean acquired;
        try {
            acquired = redis.opsForValue().setIfAbsent(key, token, maxRunTime);
        } catch (RuntimeException ex) {
            log.warn("Job lock unavailable, skipping this run of {}: {}", job, ex.getClass().getSimpleName());
            return false;
        }
        if (!Boolean.TRUE.equals(acquired)) {
            return false;
        }
        try {
            work.run();
            return true;
        } finally {
            try {
                redis.execute(RELEASE, List.of(key), token);
            } catch (RuntimeException ex) {
                // It expires on its own.
                log.warn("Job lock for {} not released: {}", job, ex.getClass().getSimpleName());
            }
        }
    }
}
