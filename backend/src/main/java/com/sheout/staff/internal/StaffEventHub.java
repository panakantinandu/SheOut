package com.sheout.staff.internal;

import com.sheout.staff.Permission;
import com.sheout.staff.StaffLiveEvents;
import com.sheout.staff.StaffPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Open console connections, and the one way events reach them.
 * <p>
 * ACROSS INSTANCES. publish() goes to a Redis channel; every instance
 * subscribes and hands what arrives to its own connections. Should Redis be
 * down, the event is delivered to this instance's connections only, and the
 * consoles' fallback polling covers the rest.
 * <p>
 * EVERY CONNECTION IS RE-CHECKED. A connection is opened with a valid
 * session, but sessions end - idle, end of shift, revoked, disabled. Every
 * 20 seconds each connection's session is checked again (without counting as
 * activity) and a dead one is closed, so a revoked console stops hearing
 * about SOS alerts within the same 20 seconds.
 */
@Component
class StaffEventHub implements StaffLiveEvents {

    private static final Logger log = LoggerFactory.getLogger(StaffEventHub.class);
    static final String CHANNEL = "sheout:staff-events";
    /** A connection lives at most this long; the browser simply reconnects. */
    static final Duration CONNECTION_LIFETIME = Duration.ofMinutes(30);

    private final StringRedisTemplate redis;
    private final StaffSessionService sessions;
    private final Map<SseEmitter, StaffPrincipal> open = new ConcurrentHashMap<>();

    StaffEventHub(StringRedisTemplate redis, StaffSessionService sessions) {
        this.redis = redis;
        this.sessions = sessions;
    }

    /**
     * Inside a transaction, sent only once it commits: a console must never
     * be told about an SOS or an approval that then rolled back.
     */
    @Override
    public void publish(String type, Permission audience, String json) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(type, audience, json);
                }
            });
        } else {
            send(type, audience, json);
        }
    }

    private void send(String type, Permission audience, String json) {
        String message = type + "\n" + audience.key() + "\n" + json;
        try {
            redis.convertAndSend(CHANNEL, message);
        } catch (RuntimeException e) {
            log.warn("Live event {} not published through Redis ({}); delivering on this instance only", type, e.getMessage());
            deliver(message);
        }
    }

    SseEmitter connect(StaffPrincipal principal) {
        SseEmitter emitter = new SseEmitter(CONNECTION_LIFETIME.toMillis());
        open.put(emitter, principal);
        emitter.onCompletion(() -> open.remove(emitter));
        emitter.onTimeout(() -> open.remove(emitter));
        emitter.onError(e -> open.remove(emitter));
        send(emitter, "hello", "{\"permissions\":" + principal.permissions().size() + "}");
        return emitter;
    }

    /** What arrived on the channel (or, without Redis, was published here). */
    void deliver(String message) {
        String[] parts = message.split("\n", 3);
        if (parts.length < 3) {
            return;
        }
        Optional<Permission> audience = Permission.fromKey(parts[1]);
        if (audience.isEmpty()) {
            return;
        }
        open.forEach((emitter, principal) -> {
            if (principal.has(audience.get())) {
                send(emitter, parts[0], parts[2]);
            }
        });
    }

    int openConnections() {
        return open.size();
    }

    @Scheduled(fixedDelayString = "${sheout.staff.events-check-ms:20000}")
    void recheck() {
        open.forEach((emitter, principal) -> {
            if (!principal.legacy() && !sessions.stillValid(principal.sessionId())) {
                send(emitter, "signed-out", "{}");
                emitter.complete();
                open.remove(emitter);
            } else {
                // A comment line: keeps proxies from closing a quiet connection.
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException e) {
                    open.remove(emitter);
                }
            }
        });
    }

    private void send(SseEmitter emitter, String type, String json) {
        try {
            emitter.send(SseEmitter.event().name(type).data(json));
        } catch (IOException | IllegalStateException e) {
            open.remove(emitter);
        }
    }

    @Configuration
    static class Subscription {

        @Bean
        RedisMessageListenerContainer staffEventsListener(RedisConnectionFactory connections, StaffEventHub hub) {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connections);
            container.addMessageListener((message, pattern) -> hub.deliver(new String(message.getBody(), StandardCharsets.UTF_8)),
                    new ChannelTopic(CHANNEL));
            // Redis being down at startup must not stop the server: the hub then delivers locally.
            container.setRecoveryInterval(5000);
            return container;
        }
    }
}
