package com.sheout.admin.internal;

import com.sheout.sharedkernel.web.ApiException;
import com.sheout.staff.Permission;
import com.sheout.staff.StaffPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Whether a record is within a member of staff's reach, beyond the endpoint's
 * permission. By permission, never by role name: whoever sees every trip or
 * every account reaches any; anyone else only what an open alert puts in
 * front of her (AlertScope).
 */
@Component
public class AdminScopes {

    private final AlertScope alerts;

    AdminScopes(AlertScope alerts) {
        this.alerts = alerts;
    }

    public void requireBookingInReach(StaffPrincipal staff, UUID bookingId) {
        if (staff.has(Permission.TRIPS_VIEW) || alerts.bookingInScope(bookingId)) {
            return;
        }
        throw outOfReach();
    }

    public void requireAccountInReach(StaffPrincipal staff, UUID accountId, UUID throughBookingId) {
        if (staff.has(Permission.USERS_VIEW) || alerts.accountInScope(accountId, throughBookingId)) {
            return;
        }
        throw outOfReach();
    }

    private static ApiException outOfReach() {
        return new ApiException(HttpStatus.FORBIDDEN, "OUT_OF_REACH",
                "This is only available for a trip in an open alert, or one closed in the last 30 minutes.");
    }
}
