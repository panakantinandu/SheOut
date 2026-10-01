package com.sheout.admin.internal.web;

import com.sheout.admin.internal.AdminOpsService;
import com.sheout.admin.internal.OpsViews.AccountDetail;
import com.sheout.admin.internal.OpsViews.BookingDetail;
import com.sheout.admin.internal.OpsViews.LiveOps;
import com.sheout.admin.internal.OpsViews.ServiceHoursView;
import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.booking.ServiceHoursApi;
import com.sheout.booking.ServiceHoursApi.ServiceHoursMode;
import com.sheout.sharedkernel.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * The console's full views - one person, one trip, the live service - and
 * the controls for when bookings are taken. ADMIN only, as every console
 * endpoint is; see AdminController for why that is a 403.
 * <p>
 * Every response is no-store: these are personal details (phone numbers,
 * emergency contacts, live positions), and nothing between the server and
 * the operator's browser should keep a copy.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminOpsController {

    /** A timed pause longer than this is a schedule, not a pause. */
    private static final Duration MAX_PAUSE = Duration.ofDays(7);

    private final AdminOpsService ops;
    private final ServiceHoursApi serviceHours;

    public AdminOpsController(AdminOpsService ops, ServiceHoursApi serviceHours) {
        this.ops = ops;
        this.serviceHours = serviceHours;
    }

    @GetMapping("/accounts/{accountId}/detail")
    public ResponseEntity<AccountDetail> accountDetail(@PathVariable UUID accountId) {
        requireAdmin();
        return noStore(ops.accountDetail(accountId).orElseThrow(() -> ApiException.notFound("No such account")));
    }

    @GetMapping("/bookings/{bookingId}")
    public ResponseEntity<BookingDetail> bookingDetail(@PathVariable UUID bookingId) {
        requireAdmin();
        return noStore(ops.bookingDetail(bookingId).orElseThrow(() -> ApiException.notFound("No such booking")));
    }

    /** Partners online and trips under way, for the console's Live page; it asks every few seconds. */
    @GetMapping("/live")
    public ResponseEntity<LiveOps> live() {
        requireAdmin();
        return noStore(ops.live());
    }

    @GetMapping("/service-hours")
    public ResponseEntity<ServiceHoursView> serviceHours() {
        requireAdmin();
        return noStore(ops.serviceHoursView());
    }

    /**
     * Sets the daily window and whether it applies. The two ends may not be
     * equal - that is "always open", which has its own mode, and saving it
     * as a window would read as "closed all day" to anyone looking later.
     */
    @PutMapping("/service-hours/schedule")
    public ResponseEntity<ServiceHoursView> schedule(@Valid @RequestBody ScheduleRequest request) {
        CurrentAccount admin = requireAdmin();
        if (request.mode() == ServiceHoursMode.SCHEDULED && request.opensAt().equals(request.closesAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request",
                    "Opening and closing times are the same. Choose \"Always open\" for bookings at every hour.");
        }
        serviceHours.updateSchedule(request.mode(), request.opensAt().withSecond(0).withNano(0),
                request.closesAt().withSecond(0).withNano(0), admin.accountId());
        return noStore(ops.serviceHoursView());
    }

    /**
     * Stops new bookings now. The reason is required because riders are
     * shown it - "paused" with no reason reads as broken. until is optional;
     * without it bookings stay paused until somebody resumes them.
     */
    @PostMapping("/service-hours/pause")
    public ResponseEntity<ServiceHoursView> pause(@Valid @RequestBody PauseRequest request) {
        CurrentAccount admin = requireAdmin();
        Instant now = Instant.now();
        if (request.until() != null && !request.until().isAfter(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "The resume time must be in the future.");
        }
        if (request.until() != null && request.until().isAfter(now.plus(MAX_PAUSE))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request",
                    "A pause can last at most 7 days. For regular closing times, set the operating hours instead.");
        }
        serviceHours.pause(request.reason().trim(), request.until(), admin.accountId());
        return noStore(ops.serviceHoursView());
    }

    @PostMapping("/service-hours/resume")
    public ResponseEntity<ServiceHoursView> resume() {
        CurrentAccount admin = requireAdmin();
        serviceHours.resume(admin.accountId());
        return noStore(ops.serviceHoursView());
    }

    public record ScheduleRequest(@NotNull ServiceHoursMode mode, @NotNull LocalTime opensAt, @NotNull LocalTime closesAt) {
    }

    public record PauseRequest(@NotBlank @Size(max = 300) String reason, Instant until) {
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private CurrentAccount requireAdmin() {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (caller.role() != AccountRole.ADMIN) {
            throw ApiException.forbidden("Admin role required");
        }
        return caller;
    }
}
