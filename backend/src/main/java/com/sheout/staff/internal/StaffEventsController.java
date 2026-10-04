package com.sheout.staff.internal;

import com.sheout.staff.StaffContext;
import com.sheout.staff.StaffSignedIn;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The console's live channel (Server-Sent Events, one-way, plain HTTP with
 * the session cookie). It carries only nudges - "a new SOS", "an approval is
 * waiting" - each to the roles allowed to act on it; see StaffEventHub.
 */
@RestController
@RequestMapping("/api/v1/admin/events")
@StaffSignedIn
class StaffEventsController {

    private final StaffEventHub hub;

    StaffEventsController(StaffEventHub hub) {
        this.hub = hub;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(HttpServletResponse response) {
        // Proxies (nginx-style, Cloudflare) must pass each event on at once.
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-store");
        return hub.connect(StaffContext.requireSignedIn());
    }
}
