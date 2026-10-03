package com.sheout.staff;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * What a console endpoint needs. The signed-in member of staff must hold
 * EVERY permission listed, or the request is refused with 403 before the
 * endpoint runs.
 * <p>
 * Checked by staff's interceptor, not by the endpoint. Every endpoint under
 * /api/v1/admin must carry this, {@link RequiresAnyPermission},
 * {@link StaffSignedIn} or {@link StaffPublic}: one with none of them stops
 * the server from starting (AdminEndpointGuard), so a new endpoint can never
 * be open by omission.
 * <p>
 * On a class, it applies to every method that does not carry its own.
 * <p>
 * The endpoint check is the floor, not the whole answer: an endpoint whose
 * rule depends on the data (which role is being granted) also calls
 * {@link StaffContext#require} inside.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresPermission {

    Permission[] value();
}
