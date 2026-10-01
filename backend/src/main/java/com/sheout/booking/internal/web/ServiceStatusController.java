package com.sheout.booking.internal.web;

import com.sheout.booking.ServiceHoursApi;
import com.sheout.booking.ServiceHoursApi.ClosedReason;
import com.sheout.booking.ServiceHoursApi.ServiceStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Whether SheOut is taking bookings right now, for the apps to say so before
 * she fills in a trip rather than after.
 * <p>
 * Public, like the service area: the hours are not a secret, and the rider
 * app may want them before sign-in. Never cached - an operator's pause has to
 * show up on the next look, not on the next deploy.
 */
@RestController
public class ServiceStatusController {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final ServiceHoursApi serviceHours;

    public ServiceStatusController(ServiceHoursApi serviceHours) {
        this.serviceHours = serviceHours;
    }

    @GetMapping("/api/v1/service-status")
    public ResponseEntity<ServiceStatusResponse> status() {
        ServiceStatus status = serviceHours.currentStatus();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ServiceStatusResponse(status.open(), status.closedReason(), status.pauseReason(),
                        status.reopensAt(), status.closesAt(), status.mode(), status.opensAt(),
                        status.closesAtLocal(), status.timeZone(), status.open() ? null : closedMessage(status)));
    }

    /**
     * message is English, for a client that has no copy of its own. The
     * apps build theirs from the fields, in her language.
     */
    public record ServiceStatusResponse(
            boolean open,
            ClosedReason closedReason,
            String pauseReason,
            Instant reopensAt,
            Instant closesAt,
            ServiceHoursApi.ServiceHoursMode mode,
            LocalTime opensAt,
            LocalTime closesAtLocal,
            String timeZone,
            String message
    ) {
    }

    /** What a refused booking says - the hours, and when she can book again. */
    static String closedMessage(ServiceStatus status) {
        if (status.open()) {
            return "SheOut is taking bookings.";
        }
        String reopens = status.reopensAt() == null ? null
                : CLOCK.format(status.reopensAt().atZone(ZoneId.of(status.timeZone())));
        if (status.closedReason() == ClosedReason.PAUSED) {
            return "New bookings are paused right now"
                    + (status.pauseReason() == null || status.pauseReason().isBlank() ? "" : ": " + status.pauseReason())
                    + (reopens == null ? ". Please check back soon." : ". You can book again from " + reopens + ".");
        }
        return "For everyone's safety, SheOut takes bookings from " + CLOCK.format(status.opensAt())
                + " to " + CLOCK.format(status.closesAtLocal()) + "."
                + (reopens == null ? "" : " You can book again from " + reopens + ".");
    }
}
