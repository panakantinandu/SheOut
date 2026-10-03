package com.sheout.admin.internal.web;

import com.sheout.staff.Permission;
import com.sheout.staff.RequiresPermission;
import com.sheout.staff.RequiresAnyPermission;
import com.sheout.staff.StaffContext;
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
    private final com.sheout.users.DriverProfileApi driverProfiles;

    public AdminOpsController(AdminOpsService ops, ServiceHoursApi serviceHours,
                              com.sheout.users.DriverProfileApi driverProfiles) {
        this.ops = ops;
        this.serviceHours = serviceHours;
        this.driverProfiles = driverProfiles;
    }

    /**
     * Verified partners' changes to what riders identify them by - name,
     * date of birth, photo, vehicle - waiting for a decision. See
     * DriverProfileChangeService.
     */
    @RequiresPermission(Permission.VERIFICATION_REVIEW)
    @GetMapping("/profile-changes")
    public ResponseEntity<java.util.List<com.sheout.users.ProfileChangeReview>> profileChanges() {
        return noStore(driverProfiles.findPendingProfileChanges());
    }

    @RequiresPermission(Permission.VERIFICATION_REVIEW)
    @PostMapping("/profile-changes/{changeId}/approve")
    public ResponseEntity<Void> approveProfileChange(@PathVariable UUID changeId) {
        CurrentAccount admin = caller();
        decided(driverProfiles.decideProfileChange(changeId, true, admin.accountId(), null));
        return ResponseEntity.noContent().build();
    }

    /** The note is shown to her, so it says what to do next ("the RC photo is blurred - send it again"). */
    @RequiresPermission(Permission.VERIFICATION_REVIEW)
    @PostMapping("/profile-changes/{changeId}/reject")
    public ResponseEntity<Void> rejectProfileChange(@PathVariable UUID changeId, @Valid @RequestBody DecisionRequest request) {
        CurrentAccount admin = caller();
        decided(driverProfiles.decideProfileChange(changeId, false, admin.accountId(), request.note()));
        return ResponseEntity.noContent().build();
    }

    public record DecisionRequest(@NotBlank @Size(max = 500) String note) {
    }

    /** "I have checked on this trip" - what was found or done goes in the note. The trip is not touched. */
    @RequiresPermission(Permission.SOS_RESPOND)
    @PostMapping("/trip-alerts/{alertId}/ack")
    public ResponseEntity<Void> acknowledgeTripAlert(@PathVariable UUID alertId, @Valid @RequestBody DecisionRequest request) {
        CurrentAccount admin = caller();
        if (!ops.acknowledgeTripAlert(alertId, admin.accountId(), request.note().trim())) {
            throw ApiException.notFound("No open alert with that id");
        }
        return ResponseEntity.noContent().build();
    }

    private static void decided(com.sheout.sharedkernel.Result<Void, com.sheout.users.ProfileChangeDecisionError> result) {
        if (result.isSuccess()) {
            return;
        }
        throw switch (result.error()) {
            case CHANGE_NOT_FOUND, PROFILE_NOT_FOUND -> ApiException.notFound("No pending change with that id");
            case RC_DOCUMENT_REQUIRED -> new ApiException(HttpStatus.CONFLICT, "RC_DOCUMENT_REQUIRED",
                    "This vehicle change has no registration certificate yet. Ask her to send a photo of it, or turn the change down.");
            case DECISION_NOTE_REQUIRED -> new ApiException(HttpStatus.BAD_REQUEST, "DECISION_NOTE_REQUIRED",
                    "Say why, so she knows what to do next.");
        };
    }

    @RequiresPermission(Permission.USERS_VIEW)
    @GetMapping("/accounts/{accountId}/detail")
    public ResponseEntity<AccountDetail> accountDetail(@PathVariable UUID accountId) {
        return noStore(ops.accountDetail(accountId).orElseThrow(() -> ApiException.notFound("No such account")));
    }

    @RequiresPermission(Permission.TRIPS_VIEW)
    @GetMapping("/bookings/{bookingId}")
    public ResponseEntity<BookingDetail> bookingDetail(@PathVariable UUID bookingId) {
        return noStore(ops.bookingDetail(bookingId).orElseThrow(() -> ApiException.notFound("No such booking")));
    }

    /** Partners online and trips under way, for the console's Live page; it asks every few seconds. */
    @RequiresPermission({Permission.TRIPS_VIEW, Permission.TRIPS_LIVE_VIEW})
    @GetMapping("/live")
    public ResponseEntity<LiveOps> live() {
        return noStore(ops.live());
    }

    @RequiresAnyPermission({Permission.SERVICE_HOURS_MANAGE, Permission.REPORTS_OPS})
    @GetMapping("/service-hours")
    public ResponseEntity<ServiceHoursView> serviceHours() {
        return noStore(ops.serviceHoursView());
    }

    /**
     * Sets the daily window and whether it applies. The two ends may not be
     * equal - that is "always open", which has its own mode, and saving it
     * as a window would read as "closed all day" to anyone looking later.
     */
    @RequiresPermission(Permission.SERVICE_HOURS_MANAGE)
    @PutMapping("/service-hours/schedule")
    public ResponseEntity<ServiceHoursView> schedule(@Valid @RequestBody ScheduleRequest request) {
        CurrentAccount admin = caller();
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
    @RequiresPermission(Permission.SERVICE_HOURS_MANAGE)
    @PostMapping("/service-hours/pause")
    public ResponseEntity<ServiceHoursView> pause(@Valid @RequestBody PauseRequest request) {
        CurrentAccount admin = caller();
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

    @RequiresPermission(Permission.SERVICE_HOURS_MANAGE)
    @PostMapping("/service-hours/resume")
    public ResponseEntity<ServiceHoursView> resume() {
        CurrentAccount admin = caller();
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

    /**
     * Who is acting, for the records that say who decided. Whether she may is
     * already settled: the endpoint's permission was checked before it ran
     * (staff's StaffPermissionInterceptor).
     */
    private static CurrentAccount caller() {
        return CurrentAccountContext.get().orElseThrow(StaffContext::signInRequired);
    }
}
