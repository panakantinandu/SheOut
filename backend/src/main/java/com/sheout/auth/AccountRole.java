package com.sheout.auth;

/**
 * Every account is exactly one of these. A phone number maps to a single
 * account with a single fixed role - see the auth module README note on
 * why this was chosen over letting one phone number hold both a customer
 * and a driver identity.
 */
public enum AccountRole {
    CUSTOMER,
    DRIVER,
    ADMIN
}
