/**
 * Staff module - the people who run SheOut, as opposed to the riders and
 * partners who use it: their sign-in (work email, password and an
 * authenticator code), invitations, console sessions, and what each role may
 * do.
 * <p>
 * WHY A MODULE OF ITS OWN, AND NOT MORE OF auth. auth answers "which rider or
 * partner is this phone"; it is phone-and-code, one role per account, and
 * every app depends on it. Staff sign in differently (no SMS - SIM swap is
 * how ops consoles get taken over), are invited rather than signing up, and
 * carry permissions that every module's console endpoints check. Keeping that
 * here means auth's public API does not grow a permission system, and nothing
 * in the apps can reach a staff credential.
 * <p>
 * WHY EACH MEMBER OF STAFF STILL HAS AN accounts ROW. Every module already
 * records operator decisions by account id - accounts.blocked_by, a ticket's
 * assignee, an announcement's sender, a review's decider - and SOS alerts are
 * pushed to the ADMIN role's devices. Each member of staff is therefore backed
 * by an ADMIN account with no phone number and no email: an id for those
 * columns, never a way in. There is nothing to sign in to an app with, and the
 * old phone-and-code path to ADMIN is off in production
 * (ADMIN_PHONE_LOGIN_ENABLED).
 * <p>
 * Public API: {@link com.sheout.staff.Permission}, {@link com.sheout.staff.StaffRole},
 * the annotations every console endpoint carries, {@link com.sheout.staff.StaffContext}
 * and {@link com.sheout.staff.StaffDirectory}. Everything under
 * {@code com.sheout.staff.internal} is private to this module.
 */
package com.sheout.staff;
