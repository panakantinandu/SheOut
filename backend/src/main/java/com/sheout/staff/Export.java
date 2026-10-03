package com.sheout.staff;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A bulk export: a file of personal or financial data that leaves the
 * console. Recorded in the audit log, needs a fresh authenticator code
 * (as {@link RequiresStepUp}), and every owner is alerted. Phase 3 adds a
 * second person's approval.
 * <p>
 * value names the export, e.g. "insurance.bordereau".
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Export {

    String value();
}
