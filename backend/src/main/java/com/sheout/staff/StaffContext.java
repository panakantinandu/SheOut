package com.sheout.staff;

import com.sheout.sharedkernel.web.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Optional;

/**
 * Who on the staff is calling, for the length of one console request - set
 * by staff's session filter from the console cookie and cleared after.
 * <p>
 * {@link #require} is the check for logic inside an endpoint, where what is
 * needed depends on the data (inviting a MANAGER needs more than inviting an
 * agent). The endpoint's own annotation has already run by then; this is the
 * second, narrower gate, never a replacement for it.
 */
public final class StaffContext {

    private static final ThreadLocal<StaffPrincipal> CURRENT = new ThreadLocal<>();

    private StaffContext() {
    }

    public static Optional<StaffPrincipal> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** The signed-in member of staff, or 401. */
    public static StaffPrincipal requireSignedIn() {
        return current().orElseThrow(StaffContext::signInRequired);
    }

    /** The signed-in member of staff if she holds this permission, or 401/403. */
    public static StaffPrincipal require(Permission permission) {
        StaffPrincipal staff = requireSignedIn();
        if (!staff.has(permission)) {
            throw forbidden(permission);
        }
        return staff;
    }

    public static boolean has(Permission permission) {
        return current().map(s -> s.has(permission)).orElse(false);
    }

    public static ApiException signInRequired() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "STAFF_SIGN_IN_REQUIRED", "Sign in to the console to continue.");
    }

    /**
     * The one 403 every refusal gives. It names the permission, which tells
     * the caller nothing she cannot read in the role matrix, and tells
     * whoever reads the screenshot exactly what to ask for.
     */
    public static ApiException forbidden(Permission permission) {
        return new ApiException(HttpStatus.FORBIDDEN, "PERMISSION_REQUIRED",
                "Your role does not allow this (" + permission.key() + ").");
    }

    public static void set(StaffPrincipal principal) {
        CURRENT.set(principal);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
