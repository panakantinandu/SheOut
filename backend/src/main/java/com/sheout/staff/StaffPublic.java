package com.sheout.staff;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A console endpoint that needs no session: signing in, and accepting an
 * invitation. The deliberate, greppable exception to "every /api/v1/admin
 * endpoint names a permission" - each one is rate limited and gives nothing
 * away about who has an account.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface StaffPublic {
}
