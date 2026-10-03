package com.sheout.sharedkernel.privacy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This field is personal data, and the ops console shows it masked unless a
 * member of staff reveals it (with the permission, a fresh authenticator code
 * and a typed reason - see the staff module).
 * <p>
 * It changes nothing anywhere else: a rider's own app gets her own address in
 * full. Masking happens only when a response is written for a console
 * request. Phone numbers need no marking - any field whose name says "phone"
 * and whose value is a mobile number is masked anyway, so a new field cannot
 * leak one by being forgotten.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
public @interface Pii {

    Kind value();

    enum Kind {
        /** A street address: shown as its area ("Banjara Hills, Hyderabad"). */
        ADDRESS,
        /** A home or work coordinate: shown to about a kilometre. */
        COORDINATE,
        /** A PAN: the first five characters hidden. */
        PAN,
        /** A bank account number or UPI id: only the last characters, unless her role pays partners. */
        BANK
    }
}
