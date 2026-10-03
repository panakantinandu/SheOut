/**
 * Insurance module - the master policies SheOut holds with insurers, the
 * cover each trip had, and partners' enrolment in group covers.
 * <p>
 * Every trip is covered under a master group policy, built in rather than
 * sold as an add-on: the premium is a platform cost recorded per trip and
 * paid out of SheOut's commission. It is never added to the rider's fare
 * and never taken from the partner's share. With no active policy, nothing
 * is covered and nothing in the apps says otherwise.
 * <p>
 * Only classes declared directly in this package are this module's public
 * API. Everything under {@code com.sheout.insurance.internal} is private to
 * this module - other modules must not import from it.
 */
package com.sheout.insurance;
