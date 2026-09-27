package com.sheout.platform;

import com.sheout.sharedkernel.warmup.WarmUp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Runs every module's {@link WarmUp} after the application has started and
 * before it says it is ready for traffic.
 * <p>
 * Spring marks an application ACCEPTING_TRAFFIC only once every
 * ApplicationReadyEvent listener has returned, and this one does its work
 * synchronously inside that event. Until it finishes,
 * /actuator/health/readiness answers 503 - which is the path Render's health
 * check reads (render.yaml), so Render keeps sending traffic to the old
 * instance until this one is warm, then switches.
 * <p>
 * Bounded: a warm-up that fails is logged and skipped, and the whole thing
 * stops at its time budget. Warming is an optimisation; it must never be the
 * reason a deploy does not go live.
 */
@Component
public class WarmUpRunner {

    private static final Logger log = LoggerFactory.getLogger(WarmUpRunner.class);

    private final List<WarmUp> warmUps;
    private final boolean enabled;
    private final int rounds;
    private final Duration budget;

    public WarmUpRunner(List<WarmUp> warmUps,
                        @Value("${sheout.warm-up.enabled:true}") boolean enabled,
                        @Value("${sheout.warm-up.rounds:3}") int rounds,
                        @Value("${sheout.warm-up.budget-seconds:60}") long budgetSeconds) {
        this.warmUps = warmUps;
        this.enabled = enabled;
        this.rounds = Math.max(1, rounds);
        this.budget = Duration.ofSeconds(budgetSeconds);
    }

    @EventListener
    @Order(0)
    public void onReady(ApplicationReadyEvent event) {
        if (!enabled || warmUps.isEmpty()) {
            return;
        }
        Integer port = event.getApplicationContext() instanceof WebServerApplicationContext web
                ? web.getWebServer().getPort() : null;
        warmUp(port);
    }

    /** Every module's warm-up, {@code rounds} times within the budget; loopback requests only when a port is given. */
    public void warmUp(Integer port) {
        long started = System.nanoTime();
        long deadline = started + budget.toNanos();
        Map<String, Long> spentMs = new LinkedHashMap<>();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        int requests = 0, failures = 0;

        for (int round = 1; round <= rounds && System.nanoTime() < deadline; round++) {
            for (WarmUp warmUp : warmUps) {
                if (System.nanoTime() >= deadline) break;
                long t0 = System.nanoTime();
                try {
                    warmUp.inProcess();
                } catch (RuntimeException e) {
                    failures++;
                    if (round == 1) log.warn("Warm-up {}: {}", warmUp.name(), e.toString());
                }
                if (port != null && port > 0) {
                    for (WarmUp.Request request : warmUp.requests()) {
                        if (System.nanoTime() >= deadline) break;
                        requests++;
                        if (!send(http, port, request) && round == 1) failures++;
                    }
                }
                spentMs.merge(warmUp.name(), (System.nanoTime() - t0) / 1_000_000, Long::sum);
            }
        }
        long totalMs = (System.nanoTime() - started) / 1_000_000;
        log.info("Warm-up done in {} ms ({} rounds, {} loopback requests{}): {}. Ready for traffic.",
                totalMs, rounds, requests, failures > 0 ? ", " + failures + " step(s) failed" : "",
                spentMs.entrySet().stream().map(e -> e.getKey() + " " + e.getValue() + " ms").collect(Collectors.joining(", ")));
    }

    /** True when the request got any HTTP answer; a 4xx is the expected answer. */
    private boolean send(HttpClient http, int port, WarmUp.Request request) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + request.path()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "sheout-warm-up");
            if (request.bearerToken() != null) {
                builder.header("Authorization", "Bearer " + request.bearerToken());
            }
            builder.method(request.method(), request.jsonBody() == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(request.jsonBody()));
            http.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.debug("Warm-up request {} {} failed: {}", request.method(), request.path(), e.toString());
            return false;
        }
    }
}
