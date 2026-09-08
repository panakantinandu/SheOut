package com.sheout.platform;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Lightweight liveness endpoint for local dev / uptime checks, separate from
 * Spring Boot Actuator's /actuator/health (which is also enabled). Platform
 * package holds cross-cutting technical infrastructure - it is not a
 * business module and other modules must not depend on it.
 */
@RestController
public class HealthController {

    @GetMapping("/api/v1/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "sheout-backend",
                "timestamp", Instant.now().toString()
        );
    }
}
