package com.sheout.staff;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * As {@link RequiresPermission}, but holding ANY ONE of the listed
 * permissions is enough - for a read several roles share for different
 * reasons (finance and the operations head both read the payout list).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresAnyPermission {

    Permission[] value();
}
