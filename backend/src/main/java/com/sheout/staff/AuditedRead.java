package com.sheout.staff;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Reading this is itself recorded in the staff audit log: a document, live
 * location, the audit log. Every console change (POST, PUT, DELETE) is
 * recorded without being marked; reads are recorded only where looking is
 * the sensitive part.
 * <p>
 * value names the action as the audit log shows it, e.g. "documents.view".
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AuditedRead {

    String value();
}
