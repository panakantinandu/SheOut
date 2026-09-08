package com.sheout;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Activates `local` since the default profile now requires DATABASE_URL /
 * REDIS_URL / JWT_SECRET with no fallback (see application.yml) - this
 * still needs a real Postgres + Redis reachable at localhost to actually
 * pass (e.g. `docker compose up postgres redis` first). No embedded/
 * in-memory or Testcontainers substitute was set up - not asked for, and
 * flagged here as a gap rather than silently added.
 */
@SpringBootTest
@ActiveProfiles("local")
class SheOutApplicationTests {

    @Test
    void contextLoads() {
        // Fails the build if the Spring context (module wiring, config) is broken.
    }
}
