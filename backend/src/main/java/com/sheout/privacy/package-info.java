/**
 * Privacy module - the account holder's data rights under India's DPDP Act:
 * a copy of their data, and deleting their account.
 * <p>
 * It owns almost nothing. The export is composed from each module's public
 * interface (auth, users, driver-verification, booking, payments, ratings,
 * support) and never reads another module's tables. Deletion is an
 * AccountDeletionRequested event that each module answers for its own data.
 * The one table here is the deletion audit log.
 * <p>
 * DELETION IS NOT ERASURE OF EVERYTHING. Completed bookings and payments are
 * retained for the period Indian tax law and dispute resolution require -
 * the exact period is for legal counsel to confirm - with the person already
 * removed from them: the account row survives only as an id, with no phone
 * number or email, and every name reads "Deleted User". ID documents and
 * profile photos are deleted from storage outright, not flagged.
 * <p>
 * Corrections are already self-service through each profile's own edit
 * screen and are not duplicated here.
 */
package com.sheout.privacy;
