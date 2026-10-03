package com.sheout.staff;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This endpoint needs her to have re-entered her authenticator code in the
 * last five minutes, on this session - on top of its permission.
 * <p>
 * For the actions where a session left open on a shared desk, or a stolen
 * cookie, must not be enough: revealing a phone number or address, opening an
 * identity or police document, marking payouts paid, changing anyone's role
 * or access, changing configuration, and every export.
 * <p>
 * Refused with 403 STEP_UP_REQUIRED; the console then asks for the code and
 * repeats the request.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresStepUp {
}
