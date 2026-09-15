package com.sheout.notifications;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The ops console registers static/admin/push-sw.js; the two apps build theirs
 * from frontend/design-system/push/push-sw.js. They are one handler in two
 * places, because the console is a dependency-free page with no build step.
 * This fails the build when they drift, so an SOS alert on an operator's
 * screen never behaves differently from an offer on a partner's.
 */
class PushWorkerCopyTest {

    @Test
    @DisplayName("the console's push handler is identical to the apps' handler")
    void consoleCopyMatchesDesignSystem() throws IOException {
        Path original = Path.of("..", "frontend", "design-system", "push", "push-sw.js");
        // Skipped, not failed, where only the backend is checked out.
        Assumptions.assumeTrue(Files.exists(original), "frontend not present next to backend");
        Path copy = Path.of("src", "main", "resources", "static", "admin", "push-sw.js");
        assertEquals(normalise(Files.readString(original)), normalise(Files.readString(copy)),
                "Copy frontend/design-system/push/push-sw.js to backend/src/main/resources/static/admin/push-sw.js");
    }

    private static String normalise(String text) {
        return text.replace("\r\n", "\n");
    }
}
